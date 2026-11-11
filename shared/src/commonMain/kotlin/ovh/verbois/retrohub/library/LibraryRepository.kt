package ovh.verbois.retrohub.library

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import ovh.verbois.retrohub.api.ApiJson
import ovh.verbois.retrohub.api.ConsoleCapabilities
import ovh.verbois.retrohub.api.LibraryGame
import ovh.verbois.retrohub.api.LibraryManifest
import ovh.verbois.retrohub.api.LibraryResult
import ovh.verbois.retrohub.api.LibraryUpdate
import ovh.verbois.retrohub.api.RetroHubApi
import ovh.verbois.retrohub.core.AppClock
import ovh.verbois.retrohub.core.parseInstant
import ovh.verbois.retrohub.core.searchKey
import ovh.verbois.retrohub.data.AppDatabase
import ovh.verbois.retrohub.data.GameEntity
import ovh.verbois.retrohub.data.KeyValueStore
import ovh.verbois.retrohub.data.LocalRomEntity
import ovh.verbois.retrohub.emulation.ConsoleSpec
import ovh.verbois.retrohub.emulation.Consoles
import ovh.verbois.retrohub.sync.SaveSync
import kotlin.time.Instant

/** Une fiche telle que l’interface l’affiche : le catalogue du serveur et l’état sur l’appareil. */
data class Game(val remote: LibraryGame, val local: LocalRomEntity?) {
    val id: String get() = remote.id
    val playGameId: String get() = remote.playGameId
    val title: String get() = remote.title
    val console: String get() = remote.console
    val spec: ConsoleSpec? get() = Consoles[remote.console]
    val supported: Boolean get() = spec != null

    /** La ROM est sur l’appareil, dans la version que connaît le serveur (ou la seule connue). */
    val downloaded: Boolean get() = local != null
    val updateAvailable: Boolean get() = local != null && remote.rom != null && remote.rom.sha256 != local.sha256
    val downloadable: Boolean get() = remote.rom != null && supported
    val lastPlayed: Instant? = parseInstant(remote.lastPlayedAt)
    val sortTitle: String = title.lowercase()
    val searchText: String by lazy { searchKey(title + " " + Consoles.name(console) + " " + (remote.genre ?: "")) }
    val favorite: Boolean get() = remote.favorite
}

class LibraryRepository(
    private val api: RetroHubApi,
    private val db: AppDatabase,
    private val kv: KeyValueStore,
    private val saveSync: SaveSync,
    scope: CoroutineScope,
    private val clock: AppClock = AppClock.System,
) {
    private val refreshLock = Mutex()

    private val catalog = db.games().observeAll().distinctUntilChanged().map { entities ->
        entities.mapNotNull { entity ->
            runCatching { ApiJson.decodeFromString(LibraryGame.serializer(), entity.json) }.getOrNull()
        }
    }.flowOn(Dispatchers.Default)

    val games: StateFlow<List<Game>> =
        combine(catalog, db.roms().observeAll().distinctUntilChanged()) { catalog, roms ->
            val local = roms.associateBy { it.playGameId }
            catalog.map { Game(it, local[it.playGameId]) }
        }.flowOn(Dispatchers.Default).stateIn(scope, SharingStarted.Eagerly, emptyList())

    suspend fun saveEpoch(): String = kv.string(EPOCH) ?: "initial"

    /**
     * Relit le catalogue (ETag : un 304 ne coûte presque rien). Un save_epoch différent de celui
     * connu signale une restauration du serveur : les sauvegardes locales sont réconciliées.
     */
    suspend fun refresh(): Boolean = withContext(Dispatchers.IO) {
        refreshLock.withLock {
            when (val result = api.library(kv.string(ETAG))) {
                LibraryResult.NotModified -> false

                is LibraryResult.Updated -> {
                    store(result.manifest)
                    kv.putString(ETAG, result.etag)
                    true
                }
            }
        }
    }

    private suspend fun store(manifest: LibraryManifest) {
        val known = kv.string(EPOCH)
        if (known != null && known != manifest.saveEpoch) saveSync.onServerRestored(manifest)
        kv.putString(EPOCH, manifest.saveEpoch)
        db.games().replaceAll(
            manifest.games.mapIndexed { index, game ->
                GameEntity(
                    id = game.id,
                    playGameId = game.playGameId,
                    title = game.title,
                    console = game.console,
                    favorite = game.favorite,
                    lastPlayedAt = game.lastPlayedAt,
                    playedSeconds = game.playedSeconds,
                    position = index,
                    json = ApiJson.encodeToString(LibraryGame.serializer(), game),
                )
            },
        )
    }

    /** Oublie l’ETag : la prochaine lecture renverra tout le catalogue. */
    suspend fun invalidate() = kv.putString(ETAG, null)

    suspend fun find(id: String): Game? {
        val entity = db.games().find(id) ?: return null
        val game = runCatching { ApiJson.decodeFromString(LibraryGame.serializer(), entity.json) }.getOrNull() ?: return null
        return Game(game, db.roms().find(game.playGameId))
    }

    suspend fun setFavorite(game: Game, favorite: Boolean) {
        // Affichage immédiat, puis confirmation par le serveur.
        val entity = db.games().find(game.id)
        if (entity != null) {
            val updated = game.remote.copy(favorite = favorite)
            db.games().upsert(listOf(entity.copy(favorite = favorite, json = ApiJson.encodeToString(LibraryGame.serializer(), updated))))
        }
        try {
            api.updateLibrary(game.id, LibraryUpdate(favorite = favorite))
            invalidate()
        } catch (error: Exception) {
            if (entity != null) db.games().upsert(listOf(entity))
            throw error
        }
    }

    suspend fun chooseProgression(game: Game, versionId: String) {
        api.updateLibrary(game.id, LibraryUpdate(versionId = versionId))
        invalidate()
        refresh()
    }

    suspend fun consoles(online: Boolean): List<ConsoleCapabilities> {
        val serializer = ListSerializer(ConsoleCapabilities.serializer())
        val cached = kv.get(CONSOLES, serializer)
        val fetched = kv.string(CONSOLES_FETCHED)?.toLongOrNull() ?: 0L
        if (online && (cached == null || clock.now().toEpochMilliseconds() - fetched >= 300_000)) {
            api.consoles().let {
                kv.put(CONSOLES, serializer, it)
                kv.putString(CONSOLES_FETCHED, clock.now().toEpochMilliseconds().toString())
                return it
            }
        }
        return cached ?: emptyList()
    }

    /** Un autre compte s’est connecté : la bibliothèque locale ne lui appartient pas. */
    suspend fun clear() {
        db.games().clear()
        kv.putString(ETAG, null)
        kv.putString(EPOCH, null)
        kv.putString(CONSOLES, null)
        kv.putString(CONSOLES_FETCHED, null)
    }

    private companion object {
        const val ETAG = "library_etag"
        const val EPOCH = "save_epoch"
        const val CONSOLES = "consoles"
        const val CONSOLES_FETCHED = "consoles_fetched"
    }
}
