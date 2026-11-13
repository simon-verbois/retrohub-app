package ovh.verbois.retrohub.data

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.KSerializer
import okio.FileSystem
import okio.Path
import ovh.verbois.retrohub.api.ApiJson
import ovh.verbois.retrohub.sync.SaveStore
import ovh.verbois.retrohub.sync.SessionStore

/** Dossiers de l’application, sous le stockage interne. */
class AppPaths(
    val root: Path,
    /** Dossier des jeux choisi par le joueur (carte SD), sinon le stockage interne. */
    private val romsOverride: () -> Path? = { null },
) {
    val internalRoms: Path get() = root / "roms"
    val roms: Path get() = romsOverride() ?: internalRoms
    val system: Path get() = root / "system"
    val saves: Path get() = root / "saves"

    /** Dossier où un cœur range ses propres fichiers (cartes mémoire, caches). */
    val coreSaves: Path get() = root / "core-saves"
    val covers: Path get() = root / "covers"

    fun romDir(playGameId: String): Path = roms / playGameId

    fun gameSaves(gameId: String): Path = saves / gameId
}

class RoomSaveStore(private val dao: LocalSaveDao, override val fs: FileSystem, private val paths: AppPaths) : SaveStore {
    override suspend fun find(gameId: String, kind: String, slot: Int) = dao.find(gameId, kind, slot)

    override suspend fun forGame(gameId: String) = dao.forGame(gameId)

    override suspend fun all() = dao.all()

    override suspend fun put(save: LocalSave) = dao.upsert(save)

    override fun file(gameId: String, kind: String, slot: Int): Path = paths.gameSaves(gameId) / "$kind-$slot.bin"

    override fun conflictFile(gameId: String, kind: String, slot: Int): Path = paths.gameSaves(gameId) / "server-$kind-$slot.bin"

    override fun observe(gameId: String): Flow<List<LocalSave>> = dao.observeForGame(gameId)

    fun observeAll(): Flow<List<LocalSave>> = dao.observeAll()
}

class RoomSessionStore(private val dao: PlaySessionDao) : SessionStore {
    override suspend fun oldest(limit: Int) = dao.oldest(limit)

    override suspend fun remove(ids: List<String>) {
        if (ids.isNotEmpty()) dao.delete(ids)
    }

    override suspend fun add(session: PlaySessionEntity) = dao.upsert(session)
}

/** Petites valeurs persistantes en JSON (réglages, état de la session, caches). */
class KeyValueStore(private val dao: KeyValueDao) {
    suspend fun string(key: String): String? = dao.get(key)

    suspend fun putString(key: String, value: String?) = if (value == null) dao.remove(key) else dao.put(KeyValueEntity(key, value))

    suspend fun <T> get(key: String, serializer: KSerializer<T>): T? =
        dao.get(key)?.let { runCatching { ApiJson.decodeFromString(serializer, it) }.getOrNull() }

    suspend fun <T> put(key: String, serializer: KSerializer<T>, value: T) = dao.put(KeyValueEntity(key, ApiJson.encodeToString(serializer, value)))

    fun observe(key: String): Flow<String?> = dao.observe(key)
}
