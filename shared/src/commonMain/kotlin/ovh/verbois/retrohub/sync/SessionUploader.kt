package ovh.verbois.retrohub.sync

import ovh.verbois.retrohub.api.OfflineSession
import ovh.verbois.retrohub.api.RetroHubApi
import ovh.verbois.retrohub.data.PlaySessionEntity

interface SessionStore {
    suspend fun add(session: PlaySessionEntity)

    suspend fun oldest(limit: Int): List<PlaySessionEntity>

    suspend fun remove(ids: List<String>)
}

data class SessionReport(val recorded: Int = 0, val duplicate: Int = 0, val rejected: Int = 0)

/**
 * Envoie les sessions jouées hors ligne par lots de 200 au plus. Une session qui a reçu une
 * réponse (recorded, duplicate, rejected) quitte la file ; les autres restent pour plus tard.
 */
class SessionUploader(private val api: RetroHubApi, private val store: SessionStore) {
    suspend fun upload(): SessionReport {
        var report = SessionReport()
        var sent = mutableSetOf<String>()
        while (true) {
            val batch = store.oldest(RetroHubApi.MAX_SESSIONS_PER_BATCH).filter { it.clientId !in sent }
            if (batch.isEmpty()) return report
            val results = api.postSessions(batch.map { it.toDto() }).results
            val answered = results.map { it.clientId }.toSet()
            sent = (sent + batch.map { it.clientId }).toMutableSet()
            store.remove(batch.map { it.clientId }.filter { it in answered })
            report =
                report.copy(
                    recorded = report.recorded + results.count { it.status == "recorded" },
                    duplicate = report.duplicate + results.count { it.status == "duplicate" },
                    rejected = report.rejected + results.count { it.status == "rejected" },
                )
            if (batch.size < RetroHubApi.MAX_SESSIONS_PER_BATCH) return report
        }
    }
}

fun PlaySessionEntity.toDto() = OfflineSession(clientId = clientId, gameId = gameId, startedAt = startedAt, endedAt = endedAt, playedSeconds = playedSeconds)
