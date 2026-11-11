package ovh.verbois.retrohub.library

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import ovh.verbois.retrohub.api.ApiException
import ovh.verbois.retrohub.api.LibraryEvent
import ovh.verbois.retrohub.api.LibraryEventSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ForegroundLibraryRefreshTest {
    @Test
    fun readsImmediatelyThenEveryTenSecondsAndStopsOnCancellation() = runTest {
        var reads = 0
        val job = launch { ForegroundLibraryRefresh { reads++ }.run() }
        runCurrent()
        assertEquals(1, reads)
        advanceTimeBy(9_999)
        runCurrent()
        assertEquals(1, reads)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(2, reads)
        job.cancelAndJoin()
        advanceTimeBy(60_000)
        assertEquals(2, reads)
    }

    @Test
    fun errorsBackOffAndAHealthyReadRestoresTheNormalInterval() = runTest {
        val times = mutableListOf<Long>()
        val job = launch {
            ForegroundLibraryRefresh {
                times += testScheduler.currentTime
                if (times.size < 3) throw ApiException.Offline()
            }.run()
        }
        runCurrent()
        advanceTimeBy(70_001)
        assertEquals(listOf(0L, 20_000L, 60_000L, 70_000L), times)
        job.cancelAndJoin()
    }

    @Test
    fun maintenanceHonorsRetryAfterAndRevocationStopsPolling() = runTest {
        var reads = 0
        val job = launch {
            ForegroundLibraryRefresh {
                reads++
                if (reads == 1) throw ApiException.Maintenance(90)
                throw ApiException.Unauthorized("Appareil révoqué")
            }.run()
        }
        runCurrent()
        advanceTimeBy(89_999)
        runCurrent()
        assertEquals(1, reads)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(2, reads)
        assertTrue(job.isCompleted)
    }

    @Test
    fun healthyStreamCoalescesChangesAndCancellationClosesIt() = runTest {
        val incoming = Channel<LibraryEvent>(Channel.UNLIMITED)
        var closed = false
        var reads = 0
        val refresh = ForegroundLibraryRefresh(
            events = LibraryEventSource { _, receive ->
                try {
                    receive(LibraryEvent.Invalidation("connected"))
                    for (event in incoming) receive(event)
                } finally {
                    closed = true
                }
            },
        ) { reads++ }
        val job = launch { refresh.run(useEvents = true) }
        runCurrent()
        advanceTimeBy(251)
        val initialReads = reads
        assertTrue(refresh.streaming.value)
        advanceTimeBy(30_000)
        runCurrent()
        assertEquals(initialReads, reads)
        repeat(30) { incoming.send(LibraryEvent.Invalidation("change-$it")) }
        runCurrent()
        advanceTimeBy(249)
        runCurrent()
        assertEquals(initialReads, reads)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(initialReads + 1, reads)
        job.cancelAndJoin()
        assertTrue(closed)
        assertFalse(refresh.streaming.value)
    }

    @Test
    fun changesDuringAReadCauseAFollowUpWithoutConcurrentManifestRequests() = runTest {
        val incoming = Channel<LibraryEvent>(Channel.UNLIMITED)
        var reads = 0
        var blockNext = false
        val blockedRead = CompletableDeferred<Unit>()
        val refresh = ForegroundLibraryRefresh(
            events = LibraryEventSource { _, receive ->
                receive(LibraryEvent.Invalidation("connected"))
                for (event in incoming) receive(event)
            },
        ) {
            reads++
            if (blockNext) {
                blockNext = false
                blockedRead.await()
            }
        }
        val job = launch { refresh.run(true) }
        advanceTimeBy(251)
        blockNext = true
        incoming.send(LibraryEvent.Invalidation("first"))
        advanceTimeBy(251)
        val activeReads = reads
        incoming.send(LibraryEvent.Invalidation("during-read"))
        advanceTimeBy(1_000)
        assertEquals(activeReads, reads)
        blockedRead.complete(Unit)
        advanceTimeBy(251)
        assertEquals(activeReads + 1, reads)
        job.cancelAndJoin()
    }

    @Test
    fun lostStreamPollsAndReconnectsWithTheLastCursorAndExponentialBackoff() = runTest {
        val disconnected = CompletableDeferred<Unit>()
        val cursors = mutableListOf<String?>()
        val times = mutableListOf<Long>()
        var reads = 0
        val refresh = ForegroundLibraryRefresh(
            events = LibraryEventSource { cursor, receive ->
                cursors += cursor
                times += testScheduler.currentTime
                if (times.size == 1) {
                    receive(LibraryEvent.Invalidation("last-seen"))
                    disconnected.await()
                }
                throw ApiException.Offline()
            },
            jitter = { 0 },
        ) { reads++ }
        val job = launch { refresh.run(true) }
        advanceTimeBy(251)
        disconnected.complete(Unit)
        runCurrent()
        val afterDisconnect = reads
        assertFalse(refresh.streaming.value)
        advanceTimeBy(21_001)
        assertEquals(listOf(0L, 3_251L, 9_251L, 21_251L), times)
        assertEquals(listOf(null, "last-seen", "last-seen", "last-seen"), cursors)
        assertEquals(afterDisconnect + 2, reads) // Failed reconnects do not bypass the ten-second polling timer.
        job.cancelAndJoin()
    }

    @Test
    fun unsupportedStreamsRetainPollingWithoutRepeatedConnectionAttempts() = runTest {
        var connections = 0
        var reads = 0
        val refresh = ForegroundLibraryRefresh(
            events = LibraryEventSource { _, _ ->
                connections++
                throw ApiException.Http(404, "Unavailable")
            },
        ) { reads++ }
        val job = launch { refresh.run(true) }
        advanceTimeBy(30_001)
        assertEquals(1, connections)
        assertEquals(4, reads)
        job.cancelAndJoin()
    }

    @Test
    fun serverRetryHintsControlReconnectionsAndRevocationStopsEverything() = runTest {
        val times = mutableListOf<Long>()
        val refresh = ForegroundLibraryRefresh(
            events = LibraryEventSource { _, receive ->
                times += testScheduler.currentTime
                when (times.size) {
                    1 -> throw ApiException.Http(429, "Too many streams", 45)

                    2 -> {
                        receive(LibraryEvent.Retry(10_000))
                        throw ApiException.Maintenance(60)
                    }

                    else -> throw ApiException.Unauthorized("Révoqué")
                }
            },
            jitter = { 0 },
        ) { }
        val job = launch { refresh.run(true) }
        advanceTimeBy(105_001)
        assertEquals(listOf(0L, 45_000L, 105_000L), times)
        assertTrue(job.isCompleted)
        assertFalse(refresh.streaming.value)
    }

    @Test
    fun invalidationsCannotBypassManifestFailureBackoff() = runTest {
        val incoming = Channel<LibraryEvent>(Channel.UNLIMITED)
        var fail = false
        val times = mutableListOf<Long>()
        val refresh = ForegroundLibraryRefresh(
            events = LibraryEventSource { _, receive ->
                receive(LibraryEvent.Invalidation("connected"))
                for (event in incoming) receive(event)
            },
        ) {
            times += testScheduler.currentTime
            if (fail) throw ApiException.Offline()
        }
        val job = launch { refresh.run(true) }
        advanceTimeBy(251)
        fail = true
        incoming.send(LibraryEvent.Invalidation("changed"))
        advanceTimeBy(251)
        val failedAt = times.last()
        repeat(10) {
            incoming.send(LibraryEvent.Invalidation("changed-$it"))
            advanceTimeBy(1_000)
        }
        assertEquals(failedAt, times.last())
        advanceTimeBy(10_000)
        assertEquals(failedAt + 20_000, times.last())
        job.cancelAndJoin()
    }
}
