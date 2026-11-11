package ovh.verbois.retrohub.library

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import ovh.verbois.retrohub.api.ApiException
import ovh.verbois.retrohub.api.LibraryEvent
import ovh.verbois.retrohub.api.LibraryEventSource
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/** One visible-library subscription, with conditional polling for older or unreachable streams. */
class ForegroundLibraryRefresh(
    private val events: LibraryEventSource? = null,
    private val jitter: (Long) -> Long = { Random.nextLong(it / 4 + 1) },
    private val refresh: suspend () -> Unit,
) {
    private val connected = MutableStateFlow(false)
    val streaming: StateFlow<Boolean> = connected.asStateFlow()

    suspend fun run(useEvents: Boolean = false) = coroutineScope {
        val signals = Channel<Signal>(Channel.CONFLATED)
        val source = if (useEvents && events != null) launch { reconnect(events, signals) } else null
        try {
            refreshLoop(signals)
        } finally {
            withContext(NonCancellable) {
                source?.cancelAndJoin()
                signals.close()
                connected.value = false
            }
        }
    }

    private suspend fun refreshLoop(signals: Channel<Signal>) {
        var nextWait: Long? = 0L
        var retryMillis = INTERVAL_MILLIS
        while (currentCoroutineContext().isActive) {
            val waitMillis = nextWait
            val signal = if (waitMillis == null) signals.receive() else withTimeoutOrNull(waitMillis) { signals.receive() }
            if (signal == Signal.STOP) return
            if (signal == Signal.INVALIDATED) {
                delay(DEBOUNCE_MILLIS)
                while (true) {
                    val pending = signals.tryReceive().getOrNull() ?: break
                    if (pending == Signal.STOP) return
                }
            }
            val retry = try {
                refresh()
                retryMillis = INTERVAL_MILLIS
                null
            } catch (_: ApiException.Unauthorized) {
                return
            } catch (error: ApiException.Maintenance) {
                error.retryAfterSeconds.seconds.inWholeMilliseconds.coerceAtLeast(INTERVAL_MILLIS)
            } catch (_: ApiException) {
                retryMillis = (retryMillis * 2).coerceAtMost(MAX_RETRY_MILLIS)
                retryMillis
            }
            if (retry != null) {
                // New invalidations cannot bypass a failed manifest's retry delay. Revocation can.
                val stopped = withTimeoutOrNull(retry) {
                    while (signals.receive() != Signal.STOP) { /* Coalesce changes until the next authoritative read. */ }
                    true
                } ?: false
                if (stopped) return
                nextWait = 0L
            } else {
                nextWait = if (connected.value) null else INTERVAL_MILLIS
            }
        }
    }

    private suspend fun reconnect(source: LibraryEventSource, signals: Channel<Signal>) {
        fun disconnect() {
            if (connected.value) {
                connected.value = false
                signals.trySend(Signal.DISCONNECTED)
            }
        }
        var lastId: String? = null
        var minimumRetry = MIN_RETRY_MILLIS
        var failures = 0
        while (currentCoroutineContext().isActive) {
            val started = TimeSource.Monotonic.markNow()
            var serverDelay = 0L
            try {
                source.collect(lastId) { event ->
                    when (event) {
                        is LibraryEvent.Invalidation -> {
                            lastId = event.id
                            connected.value = true
                            signals.trySend(Signal.INVALIDATED)
                        }

                        is LibraryEvent.Retry -> minimumRetry = event.milliseconds.coerceIn(MIN_RETRY_MILLIS, MAX_RETRY_MILLIS)
                    }
                }
            } catch (_: ApiException.Unauthorized) {
                signals.trySend(Signal.STOP)
                return
            } catch (error: ApiException.Maintenance) {
                serverDelay = error.retryAfterSeconds.seconds.inWholeMilliseconds
            } catch (error: ApiException.Http) {
                if (error.status in setOf(404, 405, 501)) {
                    disconnect()
                    awaitCancellation() // The advertised endpoint is unavailable; retain the older-server fallback.
                }
                serverDelay = error.retryAfterSeconds?.seconds?.inWholeMilliseconds ?: 0L
            } catch (_: ApiException.UnsupportedServer) {
                disconnect()
                awaitCancellation()
            } catch (_: ApiException) {
                // A temporary stream failure only changes how catalogue checks are scheduled.
            } catch (error: CancellationException) {
                throw error
            }
            disconnect()
            failures = if (started.elapsedNow().inWholeMilliseconds >= 20_000) 0 else (failures + 1).coerceAtMost(7)
            val backoff = (minimumRetry * (1L shl (failures - 1).coerceAtLeast(0))).coerceAtMost(MAX_RETRY_MILLIS)
            val wait = (backoff + jitter(backoff).coerceIn(0, MAX_RETRY_MILLIS - backoff)).coerceAtLeast(serverDelay)
            delay(wait)
        }
    }

    private enum class Signal { INVALIDATED, DISCONNECTED, STOP }

    companion object {
        const val INTERVAL_MILLIS = 10_000L
        private const val DEBOUNCE_MILLIS = 250L
        private const val MIN_RETRY_MILLIS = 3_000L
        private const val MAX_RETRY_MILLIS = 120_000L
    }
}
