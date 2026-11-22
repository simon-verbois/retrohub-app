package ovh.verbois.retrohub.mods

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import okio.FileSystem
import okio.Path
import ovh.verbois.retrohub.api.*
import ovh.verbois.retrohub.core.sha256
import ovh.verbois.retrohub.library.Game
import ovh.verbois.retrohub.sync.FileDownloader

@Serializable
data class ModInstallation(val profile: ModProfile, val releases: List<ModRelease>)

@Serializable(with = ModStateSerializer::class)
data class ModLocalState(
    val catalogue: ModCatalogue? = null,
    val active: ModInstallation? = null,
    val history: List<ModInstallation> = emptyList(),
    val installed: List<ModRelease> = emptyList(),
    val pending: Boolean = false,
    val recoveryRequired: Boolean = false,
    val profileConflict: Boolean = false,
    val pendingBaseRevision: Int? = null,
)

data class ModTransfer(val received: Long = 0, val total: Long = 0)
data class ModRuntime(val plan: ModPlan, val saveGameId: String, val saves: List<RemoteSave>, val owner: String)

/** Account-scoped, pinned local profiles. Remote changes are offered for explicit installation. */
class ModRepository(
    private val api: RetroHubApi,
    private val fs: FileSystem,
    private val root: Path,
    private val owner: () -> String,
    private val supported: () -> Boolean,
    private val freeBytes: (Path) -> Long?,
    private val environment: (Game) -> ModEnvironment,
    private val activeGame: (String) -> Boolean,
    private val downloadAllowed: () -> Boolean = { true },
    private val changed: suspend () -> Unit = {},
) {
    private val lock = Mutex()
    private val updates = MutableStateFlow(0L)
    val changes: StateFlow<Long> = updates
    private val transfers = MutableStateFlow<Map<String, ModTransfer>>(emptyMap())
    val progress: StateFlow<Map<String, ModTransfer>> = transfers

    private fun directory(account: String) = root / account
    private fun stateFile(account: String, gameId: String) = directory(account) / "profiles" / (sha256(gameId.encodeToByteArray()) + ".json")
    private fun files(account: String) = ModFiles(fs, directory(account), FileDownloader(api, fs), freeBytes)

    suspend fun state(game: Game): ModLocalState = withContext(Dispatchers.IO) { lock.withLock { read(owner(), game.id) } }

    private fun read(account: String, gameId: String): ModLocalState {
        val path = stateFile(account, gameId)
        if (!fs.exists(path)) return ModLocalState()
        return try {
            fs.read(path) { ApiJson.decodeFromString(ModLocalState.serializer(), readUtf8()) }
        } catch (error: Exception) {
            throw ModProblem("Le profil de mods ne se lit plus. Reconnectez-vous au serveur pour le récupérer.")
        }
    }

    private fun write(account: String, gameId: String, state: ModLocalState) {
        checkOwner(account)
        val target = stateFile(account, gameId)
        fs.createDirectories(target.parent!!)
        val temporary = target.parent!! / (target.name + ".tmp")
        fs.write(temporary) { writeUtf8(ApiJson.encodeToString(ModLocalState.serializer(), state)) }
        fs.atomicMove(temporary, target)
        updates.update { it + 1 }
    }

    private fun checkOwner(account: String) {
        if (owner() != account) throw ModProblem("Le compte a changé. Rouvrez les mods de ce jeu.")
    }

    private fun stopped(game: Game, state: ModLocalState) {
        val ids = listOf(game.playGameId) + state.history.mapNotNull { it.profile.saveContextId } + listOfNotNull(state.active?.profile?.saveContextId)
        if (ids.any(activeGame)) throw ModProblem("Quittez la partie avant de modifier ses mods.")
    }

    private suspend fun refreshLocked(account: String, game: Game, state: ModLocalState): ModLocalState {
        if (!supported()) return state
        checkOwner(account)
        val runtime = environment(game)
        val installers = if (runtime.console == "3ds" && runtime.core == "azahar" && runtime.build != null) ModPlanner.installers else emptySet()
        val catalogue = api.gameMods(game.id, installers)
        checkOwner(account)
        if (catalogue.schemaVersion != 1 || catalogue.gameId != game.id || catalogue.releases.size > 4096 ||
            catalogue.releases.distinctBy { it.selection }.size != catalogue.releases.size || catalogue.profile.revision < 0 ||
            catalogue.profileHistory.size > 100
        ) {
            throw ModProblem("Mettez l’application à jour pour lire les mods de ce serveur.")
        }
        val pinned = (state.installed + state.history.flatMap { it.releases } + state.active?.releases.orEmpty()).associateBy { it.selection }
        for (release in catalogue.releases) {
            val previous = pinned[release.selection] ?: continue
            if (previous.components != release.components || previous.titleIds != release.titleIds || previous.romSha256 != release.romSha256 ||
                previous.core != release.core || previous.coreBuilds != release.coreBuilds || previous.requires != release.requires ||
                previous.conflicts != release.conflicts
            ) {
                throw ModProblem("Une version du mod a changé sur le serveur. Demandez sa publication sous un nouveau numéro de version.")
            }
        }
        val pendingBase = state.pendingBaseRevision ?: state.catalogue?.profile?.revision ?: 0
        return state.copy(
            catalogue = catalogue,
            profileConflict = state.profileConflict || (state.pending && catalogue.profile.revision != pendingBase),
        ).also { write(account, game.id, it) }
    }

    suspend fun refresh(game: Game, online: Boolean): ModLocalState = withContext(Dispatchers.IO) {
        lock.withLock {
            val account = owner()
            val current = try {
                read(account, game.id)
            } catch (error: ModProblem) {
                if (!online || !supported()) throw error
                val path = stateFile(account, game.id)
                fs.copy(path, path.parent!! / (path.name + ".damaged"))
                ModLocalState(recoveryRequired = true)
            }
            if (online) refreshLocked(account, game, current) else current
        }
    }

    fun compatibility(game: Game, release: ModRelease): String? = ModPlanner.compatibility(release, environment(game))

    /** Add dependencies automatically; conflicts and overlapping destinations require a player choice. */
    fun selecting(state: ModLocalState, selection: ModSelection, enabled: Boolean): List<ModSelection> {
        val selected = state.active?.releases.orEmpty().map { it.selection }.filterNot { it.id == selection.id }.toMutableList()
        if (!enabled) return selected
        val available = state.catalogue?.releases.orEmpty() + state.installed
        val visiting = mutableSetOf<ModSelection>()
        fun add(value: ModSelection) {
            if (!visiting.add(value)) return
            val release = available.firstOrNull { it.selection == value } ?: throw ModProblem("Un mod nécessaire manque sur le serveur.")
            release.requires.forEach(::add)
            val previous = selected.firstOrNull { it.id == value.id }
            if (previous != null && previous != value) throw ModProblem("Deux mods demandent des versions différentes du même mod.")
            if (value !in selected) selected += value
        }
        add(selection)
        return selected
    }

    suspend fun apply(game: Game, selections: List<ModSelection>, online: Boolean, localVariant: Boolean = false) = withContext(Dispatchers.IO) {
        lock.withLock {
            val account = owner()
            var current = read(account, game.id)
            stopped(game, current)
            val available = (current.catalogue?.releases.orEmpty() + current.installed + current.history.flatMap { it.releases }).distinctBy { it.selection }
            val plan = ModPlanner.plan(selections, available, environment(game))
            val store = files(account)
            if (!downloadAllowed() && plan.releases.flatMap { it.components }.mapNotNull { it.artifact }.any { !store.availableOffline(it) }) {
                throw ModProblem("Connectez-vous au Wi-Fi ou autorisez les données mobiles dans les paramètres de téléchargement.")
            }
            transfers.update { it + (game.id to ModTransfer()) }
            try {
                store.install(plan, online) { received, total ->
                    checkOwner(account)
                    transfers.update { it + (game.id to ModTransfer(received, total)) }
                }
                checkOwner(account)
                val desired = plan.releases.map { it.selection }
                val localOnly =
                    localVariant && sameVersions(desired, current.active?.profile?.selections.orEmpty()) &&
                        plan.gameplayFingerprint == current.active?.profile?.gameplayFingerprint
                val profile = when {
                    localOnly -> current.active!!.profile

                    online && supported() -> {
                        val revision = current.catalogue?.profile?.revision ?: 0
                        api.updateModProfile(game.id, ModProfileUpdate(revision, desired, plan.gameplayFingerprint)).also {
                            checkOwner(account)
                            validateProfile(game, it, plan)
                            if (it.revision <= revision) throw ModProblem("Le serveur n’a pas confirmé le nouveau profil. Actualisez les mods.")
                        }
                    }

                    else -> {
                        val serverHistory = current.catalogue?.profileHistory.orEmpty().map { profile -> ModInstallation(profile, plan.releases) }
                        val known = (listOfNotNull(current.active) + current.history + serverHistory).firstOrNull {
                            sameVersions(it.profile.selections, desired) &&
                                it.profile.gameplayFingerprint == plan.gameplayFingerprint
                        }
                        if (plan.gameplayFingerprint != null &&
                            known == null
                        ) {
                            throw ModProblem("Connectez-vous une fois pour préparer la progression de cette combinaison de mods.")
                        }
                        known?.profile?.copy(selections = desired) ?: ModProfile(selections = desired)
                    }
                }
                validateProfile(game, profile, plan)
                stopped(game, current)
                val saveId = if (plan.gameplayFingerprint == null) game.playGameId else profile.saveContextId!!
                val romSha = environment(game).romSha
                if (ModPlanner.hash(romSha)) {
                    val base = root.parent!! / "core-saves" / account / game.console / sha256(saveId.encodeToByteArray()) / romSha
                    store.prepare(plan, plan.fingerprint?.let { base / "mod-profiles" / it } ?: base)
                }
                checkOwner(account)
                val next = ModInstallation(profile, plan.releases)
                val previous = current.active ?: ModInstallation(ModProfile(), emptyList())
                val history = if (previous.releases != next.releases) {
                    (listOf(previous) + current.history).distinctBy { it.releases.map { release -> release.selection } }.take(20)
                } else {
                    current.history
                }
                current = current.copy(
                    active = next,
                    history = history,
                    installed = (current.installed + plan.releases).distinctBy { it.selection },
                    pending = if (localOnly) current.pending else !(online && supported()),
                    pendingBaseRevision = when {
                        localOnly -> current.pendingBaseRevision
                        online && supported() -> null
                        else -> current.pendingBaseRevision ?: current.catalogue?.profile?.revision ?: 0
                    },
                    recoveryRequired = false,
                    profileConflict = if (online && supported() && !localOnly) false else current.profileConflict,
                    catalogue = if (!localOnly && online && supported()) current.catalogue?.copy(profile = profile) else current.catalogue,
                )
                write(account, game.id, current)
                changed()
            } finally {
                transfers.update { it - game.id }
            }
        }
    }

    private fun sameVersions(a: List<ModSelection>, b: List<ModSelection>): Boolean =
        a.map { it.id to it.version }.toSet() == b.map { it.id to it.version }.toSet()

    private fun validateProfile(game: Game, profile: ModProfile, plan: ModPlan) {
        if (!sameVersions(
                profile.selections,
                plan.releases.map {
                    it.selection
                },
            )
        ) {
            throw ModProblem("Le serveur a confirmé un autre profil. Actualisez les mods.")
        }
        if (profile.gameplayFingerprint != plan.gameplayFingerprint) throw ModProblem("Le serveur n’a pas confirmé la compatibilité de la progression.")
        if (plan.gameplayFingerprint != null &&
            (
                profile.saveContextId == game.playGameId ||
                    profile.saveContextId?.let { Regex("[A-Za-z0-9_.:-]{1,200}").matches(it) && it != "." && it != ".." } != true
                )
        ) {
            throw ModProblem("Le serveur doit préparer une progression séparée pour ce mod.")
        }
    }

    suspend fun runtime(game: Game, environment: ModEnvironment, online: Boolean): ModRuntime = withContext(Dispatchers.IO) {
        lock.withLock {
            val account = owner()
            var current = read(account, game.id)
            if (online && supported()) {
                try {
                    current = refreshLocked(account, game, current)
                } catch (_: ApiException.Offline) {
                } catch (_: ApiException.Maintenance) {
                }
            }
            val active = current.active
            if (current.recoveryRequired) throw ModProblem("Ouvrez Mods et installez le profil du serveur pour retrouver cette partie.")
            val remoteProfile = listOfNotNull(game.remote.modProfile, current.catalogue?.profile).maxByOrNull { it.revision }
            if (active == null && remoteProfile?.selections?.isNotEmpty() == true) {
                throw ModProblem("Ce jeu utilise des mods. Ouvrez Mods pour installer le profil du serveur ou choisir le jeu original.")
            }
            val plan = ModPlanner.plan(active?.releases.orEmpty().map { it.selection }, active?.releases.orEmpty(), environment)
            if (active != null) validateProfile(game, active.profile, plan)
            val id = if (plan.gameplayFingerprint == null) game.playGameId else active!!.profile.saveContextId!!
            val context = game.remote.modSaveContexts.firstOrNull { it.gameId == id && it.gameplayFingerprint == plan.gameplayFingerprint }
            val remote = if (id == game.playGameId) {
                game.remote.saves
            } else {
                context?.saves
                    ?: current.catalogue?.profile?.takeIf { it.saveContextId == id && it.gameplayFingerprint == plan.gameplayFingerprint }?.saves
                    ?: active!!.profile.saves
            }
            if (id != game.playGameId && remote.any { it.gameId != id }) {
                throw ModProblem("Le serveur a envoyé une progression d’un autre profil. Actualisez la bibliothèque.")
            }
            ModRuntime(plan, id, remote, account)
        }
    }

    suspend fun prepare(runtime: ModRuntime, gameRoot: Path) = withContext(Dispatchers.IO) {
        lock.withLock {
            checkOwner(runtime.owner)
            val store = files(runtime.owner)
            try {
                store.prepare(runtime.plan, gameRoot)
            } catch (error: ModProblem) {
                val artifacts = runtime.plan.releases.flatMap { it.components }.mapNotNull { it.artifact }
                if (artifacts.all { store.availableOffline(it) }) {
                    store.install(runtime.plan, online = false) { _, _ -> }
                    store.prepare(runtime.plan, gameRoot)
                } else {
                    throw error
                }
            }
            checkOwner(runtime.owner)
        }
    }

    /** Reconnect publishes queued choices only if their base revision still matches. */
    suspend fun synchronize(games: List<Game>) = withContext(Dispatchers.IO) {
        if (!supported()) return@withContext
        lock.withLock {
            val account = owner()
            for (game in games.filter { it.console == "3ds" }) {
                val current = read(account, game.id)
                if (!current.pending || current.active == null || current.profileConflict) continue
                val refreshed = refreshLocked(account, game, current)
                val remote = refreshed.catalogue!!.profile
                val previousRevision = current.pendingBaseRevision ?: current.catalogue?.profile?.revision ?: 0
                if (remote.revision != previousRevision) {
                    write(account, game.id, refreshed.copy(profileConflict = true))
                    continue
                }
                stopped(game, current)
                val plan = ModPlanner.plan(current.active.releases.map { it.selection }, current.active.releases, environment(game))
                val profile = api.updateModProfile(game.id, ModProfileUpdate(remote.revision, current.active.profile.selections, plan.gameplayFingerprint))
                checkOwner(account)
                validateProfile(game, profile, plan)
                if (profile.revision <= remote.revision || profile.saveContextId != current.active.profile.saveContextId) continue
                write(
                    account,
                    game.id,
                    refreshed.copy(
                        active = current.active.copy(profile = profile),
                        pending = false,
                        pendingBaseRevision = null,
                        catalogue = refreshed.catalogue.copy(profile = profile),
                    ),
                )
            }
        }
    }

    suspend fun uninstall(game: Game, selection: ModSelection) = withContext(Dispatchers.IO) {
        lock.withLock {
            val account = owner()
            val current = read(account, game.id)
            stopped(game, current)
            if (current.active?.releases?.any { it.selection == selection } == true) throw ModProblem("Désactivez ce mod avant de le supprimer de l’appareil.")
            val removed = current.installed.filter {
                it.selection == selection
            }.flatMap { it.components }.mapNotNull { it.artifact?.sha256?.lowercase() }.toSet()
            val next = current.copy(installed = current.installed.filterNot { it.selection == selection })
            write(account, game.id, next)
            // All games in this account participate in reference counting. History keeps its
            // descriptors; removed versions can be downloaded again when rolling back.
            val profiles = directory(account) / "profiles"
            val retained = fs.list(profiles).filter { it.name.endsWith(".json") }.flatMap { path ->
                fs.read(path) { ApiJson.decodeFromString(ModLocalState.serializer(), readUtf8()) }.let { it.installed + it.active?.releases.orEmpty() }
            }.flatMap { it.components }.mapNotNull { it.artifact?.sha256?.lowercase() }.toSet()
            files(account).remove(removed, retained)
            val romSha = environment(game).romSha
            if (ModPlanner.hash(romSha)) {
                for (installation in current.history.filter { it.releases.any { release -> release.selection == selection } }) {
                    val fingerprint = ModPlanner.fingerprint(installation.releases, false) ?: continue
                    val context = installation.profile.saveContextId ?: game.playGameId
                    val runtime =
                        root.parent!! / "core-saves" / account / game.console / sha256(context.encodeToByteArray()) / romSha / "mod-profiles" / fingerprint
                    for (folder in listOf("load", "cheats")) {
                        val data = runtime / "Azahar" / folder
                        if (fs.exists(data)) fs.deleteRecursively(data)
                    }
                }
            }
        }
    }
}
