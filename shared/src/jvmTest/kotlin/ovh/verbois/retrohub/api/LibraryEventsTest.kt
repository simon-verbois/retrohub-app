package ovh.verbois.retrohub.api

import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import ovh.verbois.retrohub.TestApi
import ovh.verbois.retrohub.bytes
import ovh.verbois.retrohub.status
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LibraryEventsTest {
    @Test
    fun decodesUtf8MultilineFramesAndAllLineEndingsWithPersistentCursors() = runTest {
        val frames = "\uFEFF: heartbeat\r\nretry: 3000\r\nid: révision-1\r\nevent: resync_required\r\ndata: {\r\ndata: \"reason\":\"connected\"}\r\n\r\n" +
            "event: library_changed\rdata: {}\r\r" +
            "id:\nevent: library_changed\ndata: {}\n\n" +
            "event: library_changed\ndata: {}\n" // No dispatch without the terminating blank line.
        val received = mutableListOf<LibraryEvent>()
        readLibraryEvents(ByteReadChannel(frames.encodeToByteArray()), "old", received::add) { error("revoked") }
        assertEquals(
            listOf(LibraryEvent.Retry(3000), LibraryEvent.Invalidation("révision-1"), LibraryEvent.Invalidation("révision-1"), LibraryEvent.Invalidation(null)),
            received,
        )
    }

    @Test
    fun ignoresUnknownEventsInvalidPayloadsAndUnsafeCursors() = runTest {
        val frames = "event: unknown\ndata: {}\n\n" +
            "event: library_changed\ndata: broken\n\n" +
            "retry: -1\nretry: 3.5\nretry: 999999999999999999999999\n" +
            "id: invalid\u0000\nid: ${"x".repeat(257)}\nevent: library_changed\ndata: {}\n\n"
        val received = mutableListOf<LibraryEvent>()
        readLibraryEvents(ByteReadChannel(frames.encodeToByteArray()), "safe", received::add) { error("revoked") }
        assertEquals(listOf<LibraryEvent>(LibraryEvent.Invalidation("safe")), received)
    }

    @Test
    fun boundsAccumulatedEventData() = runTest {
        val frames = "event: library_changed\n" + "data: ${"x".repeat(16_384)}\n".repeat(5)
        assertFailsWith<ApiException.Http> {
            readLibraryEvents(ByteReadChannel(frames.encodeToByteArray()), null, {}) {}
        }
    }

    @Test
    fun usesAuthenticatedStreamingHeadersAndForwardsTheCursor() = runTest {
        val t = TestApi()
        try {
            t.enqueue(bytes("event: resync_required\nid: next\ndata: {}\n\n".encodeToByteArray(), 200, "Content-Type" to "text/event-stream; charset=utf-8"))
            val received = mutableListOf<LibraryEvent>()
            t.api.libraryEvents("previous", received::add)
            assertEquals(listOf<LibraryEvent>(LibraryEvent.Invalidation("next")), received)
            val request = t.take()
            assertEquals("/api/v1/native/events", request.url.encodedPath)
            assertEquals("Bearer device-token", request.headers["Authorization"])
            assertEquals("text/event-stream", request.headers["Accept"])
            assertEquals("previous", request.headers["Last-Event-ID"])
            assertNull(request.url.query)
        } finally {
            t.close()
        }
    }

    @Test
    fun revocationForgetsTheDeviceAndStopsConsumption() = runTest {
        val t = TestApi()
        try {
            t.enqueue(
                bytes(
                    "event: authorization_revoked\ndata: {}\n\nevent: library_changed\ndata: {}\n\n".encodeToByteArray(),
                    200,
                    "Content-Type" to "text/event-stream",
                ),
            )
            assertFailsWith<ApiException.Unauthorized> { t.api.libraryEvents(null) { error("must stop") } }
            assertNull(t.session.token)
            assertNotNull(t.session.unauthorized)
        } finally {
            t.close()
        }
    }

    @Test
    fun maintenanceAndRateLimitsAreReturnedToTheReconnectSchedulerWithoutInlineRetry() = runTest {
        val t = TestApi()
        try {
            t.enqueue(bytes("event: maintenance\ndata: {\"retry_after\":45}\n\n".encodeToByteArray(), 200, "Content-Type" to "text/event-stream"))
            assertEquals(45L, assertFailsWith<ApiException.Maintenance> { t.api.libraryEvents(null) {} }.retryAfterSeconds)
            t.take()
            t.enqueue(status(503, "Retry-After" to "3"))
            assertEquals(3L, assertFailsWith<ApiException.Maintenance> { t.api.libraryEvents(null) {} }.retryAfterSeconds)
            t.take()
            t.enqueue(status(429, "Retry-After" to "60"))
            val limited = assertFailsWith<ApiException.Http> { t.api.libraryEvents(null) {} }
            assertEquals(429, limited.status)
            assertEquals(60L, limited.retryAfterSeconds)
            assertTrue(t.waits.isEmpty())
        } finally {
            t.close()
        }
    }

    @Test
    fun bufferedOrNonStreamingResponsesUseThePollingFallback() = runTest {
        val t = TestApi()
        try {
            t.enqueue(bytes("{}".encodeToByteArray(), 200, "Content-Type" to "application/json"))
            assertFailsWith<ApiException.UnsupportedServer> { t.api.libraryEvents(null) {} }
        } finally {
            t.close()
        }
    }
}
