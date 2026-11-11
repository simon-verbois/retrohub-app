package ovh.verbois.retrohub

import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okio.Buffer
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import ovh.verbois.retrohub.api.ApiSession
import ovh.verbois.retrohub.api.RetroHubApi
import ovh.verbois.retrohub.api.apiHttpClient
import ovh.verbois.retrohub.data.LocalSave
import ovh.verbois.retrohub.data.PlaySessionEntity
import ovh.verbois.retrohub.sync.SaveStore
import ovh.verbois.retrohub.sync.SessionStore

class FakeSession(override var server: String?, override var token: String? = "device-token") : ApiSession {
    var unauthorized: String? = null

    override suspend fun onUnauthorized(message: String) {
        unauthorized = message
        token = null
    }
}

class TestApi(val server: MockWebServer = MockWebServer().apply { start() }) {
    val session = FakeSession(server.url("/").toString().trimEnd('/'))
    val waits = mutableListOf<Long>()
    val api = RetroHubApi(apiHttpClient(), session, wait = { waits += it })

    fun enqueue(response: MockResponse) = server.enqueue(response)

    fun take(): RecordedRequest = server.takeRequest()

    fun close() = server.close()
}

fun json(body: String, code: Int = 200, vararg headers: Pair<String, String>): MockResponse =
    MockResponse.Builder().code(code).addHeader("Content-Type", "application/json").apply {
        headers.forEach { (k, v) -> addHeader(k, v) }
    }.body(body).build()

fun bytes(data: ByteArray, code: Int = 200, vararg headers: Pair<String, String>): MockResponse = MockResponse.Builder().code(code).apply {
    headers.forEach { (k, v) -> addHeader(k, v) }
}.body(Buffer().write(data)).build()

fun status(code: Int, vararg headers: Pair<String, String>): MockResponse =
    MockResponse.Builder().code(code).apply { headers.forEach { (k, v) -> addHeader(k, v) } }.build()

/** Magasin de sauvegardes en mémoire, sur un système de fichiers simulé. */
class MemorySaveStore(override val fs: FileSystem = FakeFileSystem(), private val root: Path = "/saves".toPath()) : SaveStore {
    val saves = mutableMapOf<Triple<String, String, Int>, LocalSave>()

    override suspend fun find(gameId: String, kind: String, slot: Int) = saves[Triple(gameId, kind, slot)]

    override suspend fun forGame(gameId: String) = saves.values.filter { it.gameId == gameId }

    override suspend fun all() = saves.values.toList()

    override suspend fun put(save: LocalSave) {
        saves[Triple(save.gameId, save.kind, save.slot)] = save
    }

    override fun file(gameId: String, kind: String, slot: Int) = root / gameId / "$kind-$slot.bin"

    override fun conflictFile(gameId: String, kind: String, slot: Int) = root / gameId / "server-$kind-$slot.bin"

    fun write(gameId: String, kind: String, slot: Int, data: ByteArray) {
        val path = file(gameId, kind, slot)
        fs.createDirectories(path.parent!!)
        fs.write(path) { write(data) }
    }

    fun read(gameId: String, kind: String, slot: Int): ByteArray = fs.read(file(gameId, kind, slot)) { readByteArray() }
}

class MemorySessionStore : SessionStore {
    val sessions = mutableListOf<PlaySessionEntity>()

    override suspend fun add(session: PlaySessionEntity) {
        sessions += session
    }

    override suspend fun oldest(limit: Int) = sessions.sortedBy { it.startedAt }.take(limit)

    override suspend fun remove(ids: List<String>) {
        sessions.removeAll { it.clientId in ids }
    }
}
