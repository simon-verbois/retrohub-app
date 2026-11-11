package ovh.verbois.retrohub.api

import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.LineEnding
import io.ktor.utils.io.readLineStrict
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull

const val FEATURE_LIBRARY_EVENTS = "library_events"

sealed interface LibraryEvent {
    data class Invalidation(val id: String?) : LibraryEvent
    data class Retry(val milliseconds: Long) : LibraryEvent
}

fun interface LibraryEventSource {
    suspend fun collect(lastEventId: String?, receive: suspend (LibraryEvent) -> Unit)
}

/** Bounded UTF-8 SSE decoder. Comments and unknown event types never invalidate the catalogue. */
internal suspend fun readLibraryEvents(channel: ByteReadChannel, initialId: String?, receive: suspend (LibraryEvent) -> Unit, revoked: suspend () -> Unit) {
    var id = initialId
    var event = ""
    val data = StringBuilder()
    var firstLine = true
    while (true) {
        val raw = channel.readLineStrict(limit = MAX_EVENT_BYTES.toLong(), lineEnding = LineEnding.Lenient) ?: return
        val line = if (firstLine) raw.removePrefix("\uFEFF") else raw
        firstLine = false
        if (line.isEmpty()) {
            if (event == "authorization_revoked") {
                revoked()
                throw ApiException.Unauthorized("Cet appareil n’est plus connecté. Reliez-le de nouveau à votre compte.")
            }
            if (data.isNotEmpty()) {
                val payload = runCatching { ApiJson.parseToJsonElement(data.toString()) as? JsonObject }.getOrNull()
                when {
                    event == "maintenance" -> {
                        val seconds = (payload?.get("retry_after") as? JsonPrimitive)?.longOrNull ?: 3L
                        throw ApiException.Maintenance(seconds.coerceAtLeast(3))
                    }

                    payload != null && event in setOf("library_changed", "resync_required") -> receive(LibraryEvent.Invalidation(id))
                }
            }
            event = ""
            data.clear()
            continue
        }
        if (line.startsWith(':')) continue
        val field = line.substringBefore(':')
        val value = if (':' in line) line.substringAfter(':').removePrefix(" ") else ""
        when (field) {
            "event" -> event = value

            "id" -> if ('\u0000' !in value && value.length <= 256) id = value.ifEmpty { null }

            "retry" -> if (value.isNotEmpty() && value.all { it in '0'..'9' }) {
                value.toLongOrNull()?.let { receive(LibraryEvent.Retry(it)) }
            }

            "data" -> {
                if (data.length + value.length + 1 > MAX_EVENT_BYTES) throw ApiException.Http(0, "Réponse inattendue du serveur.")
                if (data.isNotEmpty()) data.append('\n')
                data.append(value)
            }
        }
    }
}

private const val MAX_EVENT_BYTES = 65_536
