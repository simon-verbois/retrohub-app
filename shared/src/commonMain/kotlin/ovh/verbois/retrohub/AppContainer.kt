package ovh.verbois.retrohub

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ovh.verbois.retrohub.account.AccountPreferences
import ovh.verbois.retrohub.account.AccountRepository
import ovh.verbois.retrohub.account.AuthState
import ovh.verbois.retrohub.api.RetroHubApi
import ovh.verbois.retrohub.api.ServerStatus
import ovh.verbois.retrohub.api.apiHttpClient
import ovh.verbois.retrohub.api.artworkHttpClient
import ovh.verbois.retrohub.core.AppClock
import ovh.verbois.retrohub.core.Platform
import ovh.verbois.retrohub.data.AppDatabase
import ovh.verbois.retrohub.data.AppPaths
import ovh.verbois.retrohub.data.KeyValueStore
import ovh.verbois.retrohub.data.RoomSaveStore
import ovh.verbois.retrohub.data.RoomSessionStore
import ovh.verbois.retrohub.data.SettingsRepository
import ovh.verbois.retrohub.emulation.EmulatorEngine
import ovh.verbois.retrohub.game.CheatsRepository
import ovh.verbois.retrohub.game.GameLauncher
import ovh.verbois.retrohub.game.GameSession
import ovh.verbois.retrohub.game.LaunchPlan
import ovh.verbois.retrohub.library.DownloadManager
import ovh.verbois.retrohub.library.LibraryRepository
import ovh.verbois.retrohub.sync.FileDownloader
import ovh.verbois.retrohub.sync.SaveSync
import ovh.verbois.retrohub.sync.SessionUploader
import ovh.verbois.retrohub.sync.SyncCoordinator

/** Injection manuelle : un seul graphe d’objets pour toute l’application. */
class AppContainer(val platform: Platform, val database: AppDatabase, val clock: AppClock = AppClock.System) {
    val scope = CoroutineScope(SupervisorJob())
    val fs = platform.fs
    val keyValues = KeyValueStore(database.settings())
    val settings = SettingsRepository(keyValues, scope)
    val paths =
        AppPaths(platform.filesDir) {
            if (settings.settings.value.storage == ovh.verbois.retrohub.emulation.StorageLocation.SD_CARD) platform.sdCardRoot()?.let { it / "roms" } else null
        }
    val account = AccountRepository(platform, keyValues)
    val api = RetroHubApi(apiHttpClient(), account).also { account.api = it }
    val artworkClient = artworkHttpClient(account)
    val saveStore = RoomSaveStore(database.saves(), fs, paths)
    val sessionStore = RoomSessionStore(database.sessions())
    val saveSync = SaveSync(api, saveStore, clock)
    val library = LibraryRepository(api, database, keyValues, saveSync, scope)
    val downloads =
        DownloadManager(FileDownloader(api, fs), fs, paths, database, library, scope, platform::freeBytes, clock) {
            if (platform.meteredNetwork() && !settings.settings.value.cellularDownloads) {
                "Téléchargement en attente du Wi-Fi. Pour télécharger en données mobiles : Paramètres → Téléchargements et stockage."
            } else {
                null
            }
        }
    val preferences = AccountPreferences(api, settings, scope) { library.consoles(online = true).associate { it.id to it.cores } }

    /** Dernier état connu du serveur (versions de l’application, fonctions disponibles). */
    val serverStatus = MutableStateFlow<ServerStatus?>(null)

    val sync =
        SyncCoordinator(library, saveSync, saveStore, SessionUploader(api, sessionStore), { database.sessions().count() }, clock) {
            afterSync()
        }
    val cheats = CheatsRepository(keyValues)
    val launcher =
        GameLauncher(library, downloads, saveSync, saveStore, fs, paths, platform::availableCores, platform.deviceClass, cheats::get, platform::batteryLow)

    private val startLock = Mutex()
    private var started = false
    private var statusServer: String? = null
    private var statusCheckedAt: kotlin.time.Instant? = null
    private var notifiedConflicts = 0

    init {
        downloads.backgroundLauncher = { id, title -> platform.startBackgroundDownload(id, title) }
    }

    /** Après chaque synchro : compte, version du serveur, favoris à télécharger, place à libérer. */
    private suspend fun afterSync() {
        try {
            preferences.pull()
        } catch (error: ovh.verbois.retrohub.api.ApiException) { /* Retry at the next sync. */ }
        account.server?.let { server ->
            if (statusServer != server || statusCheckedAt == null || (clock.now() - statusCheckedAt!!).inWholeMinutes >= 15) {
                try {
                    serverStatus.value = api.status(server)
                    statusServer = server
                    statusCheckedAt = clock.now()
                } catch (error: ovh.verbois.retrohub.api.ApiException) { /* Keep the last known version. */ }
            }
        }
        val current = settings.settings.value
        val games = library.games.value
        if (current.autoDownloadFavorites) {
            games.filter { it.favorite && !it.downloaded && it.downloadable && it.playGameId !in downloads.states.value }.forEach { downloads.start(it) }
        }
        if (current.cleanupDays > 0) downloads.cleanup(current.cleanupDays, games)
        val conflicts = saveStore.all().count { it.inConflict }
        if (current.conflictNotifications && conflicts > notifiedConflicts) {
            platform.notify(
                NOTIFY_CONFLICT,
                "Une sauvegarde attend votre choix",
                "Elle a avancé sur ce téléphone et ailleurs. Ouvrez la fiche du jeu pour choisir la version à garder.",
            )
        }
        notifiedConflicts = conflicts
    }

    /** Chargement initial : réglages, compte, puis synchro à chaque retour du réseau. */
    suspend fun start() = startLock.withLock {
        if (started) return@withLock
        settings.load()
        account.load()
        sync.refreshCounts()
        scope.launch {
            platform.online.drop(1).collect { online ->
                if (online) {
                    sync.markOnline()
                    if (account.auth.value is AuthState.SignedIn) requestSync()
                } else {
                    sync.markOffline()
                }
            }
        }
        if (!platform.online.value) sync.markOffline()
        applyPeriodicSync()
        started = true
    }

    /** Synchro périodique selon les réglages (désactivée si la synchro automatique l’est). */
    fun applyPeriodicSync() {
        val current = settings.settings.value
        platform.schedulePeriodicSync(if (current.autoSync) current.periodicSyncHours else 0)
    }

    /** Synchronisation demandée par l’application (pas par le joueur) : respecte le réglage. */
    fun requestSync() {
        if (settings.settings.value.autoSync) platform.scheduleSync()
    }

    /** Après une connexion : un autre compte remplace la bibliothèque locale de l’ancien. */
    suspend fun afterSignIn() {
        if (account.accountChanged) {
            library.clear()
            account.acknowledgeAccountChange()
        }
        platform.scheduleSync()
    }

    fun gameSession(plan: LaunchPlan, engine: EmulatorEngine, scope: CoroutineScope) =
        GameSession(plan, engine, fs, saveStore, saveSync, sessionStore, { requestSync() }, scope, clock)

    companion object {
        const val NOTIFY_CONFLICT = 1001
    }
}
