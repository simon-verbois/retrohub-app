package ovh.verbois.retrohub.sync

import kotlinx.coroutines.test.runTest
import ovh.verbois.retrohub.MemorySessionStore
import ovh.verbois.retrohub.TestApi
import ovh.verbois.retrohub.api.ApiException
import ovh.verbois.retrohub.api.ApiJson
import ovh.verbois.retrohub.api.SessionBatch
import ovh.verbois.retrohub.data.PlaySessionEntity
import ovh.verbois.retrohub.json
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** File des sessions hors ligne : lots de 200, retrait des sessions qui ont une réponse. */
class SessionUploaderTest {
    private val t = TestApi()
    private val store = MemorySessionStore()
    private val uploader = SessionUploader(t.api, store)

    @AfterTest
    fun close() = t.close()

    private fun session(index: Int) = PlaySessionEntity(
        clientId = "s-${index.toString().padStart(4, '0')}",
        gameId = "jeu",
        startedAt = "2026-11-10T08:${(index % 60).toString().padStart(2, '0')}:00Z",
        endedAt = "2026-11-10T09:00:00Z",
        playedSeconds = 60,
    )

    private fun answer(ids: List<String>, status: (String) -> String) =
        json("""{"results": [${ids.joinToString(",") { """{"client_id": "$it", "status": "${status(it)}"}""" }}]}""")

    @Test
    fun recordedDuplicateAndRejectedAllLeaveTheQueue() = runTest {
        store.sessions += listOf(session(1), session(2), session(3))
        t.enqueue(
            answer(listOf("s-0001", "s-0002", "s-0003")) {
                mapOf(
                    "s-0001" to "recorded",
                    "s-0002" to "duplicate",
                    "s-0003" to "rejected",
                )[it]!!
            },
        )
        val report = uploader.upload()
        assertEquals(SessionReport(recorded = 1, duplicate = 1, rejected = 1), report)
        assertEquals(0, store.sessions.size)
    }

    @Test
    fun largeQueuesGoInBatchesOf200() = runTest {
        store.sessions += (1..250).map { session(it) }
        val sorted = store.sessions.sortedBy { it.startedAt }.map { it.clientId }
        t.enqueue(answer(sorted.take(200)) { "recorded" })
        t.enqueue(answer(sorted.drop(200)) { "recorded" })
        assertEquals(250, uploader.upload().recorded)
        val first = ApiJson.decodeFromString(SessionBatch.serializer(), t.take().body!!.utf8())
        val second = ApiJson.decodeFromString(SessionBatch.serializer(), t.take().body!!.utf8())
        assertEquals(200, first.sessions.size)
        assertEquals(50, second.sessions.size)
        assertEquals(0, store.sessions.size)
    }

    @Test
    fun sessionsWithoutAnswerStayQueued() = runTest {
        store.sessions += listOf(session(1), session(2))
        t.enqueue(answer(listOf("s-0001")) { "recorded" })
        uploader.upload()
        assertEquals(listOf("s-0002"), store.sessions.map { it.clientId })
    }

    @Test
    fun networkFailureKeepsEverything() = runTest {
        store.sessions += listOf(session(1))
        t.close()
        assertFailsWith<ApiException.Offline> { uploader.upload() }
        assertEquals(1, store.sessions.size)
    }
}
