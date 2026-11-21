package ovh.verbois.retrohub.diagnostics

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ovh.verbois.retrohub.api.ApiException
import ovh.verbois.retrohub.api.ApiJson
import ovh.verbois.retrohub.api.RetroHubApi

/** Only an explicit approval creates an upload. Retries keep the exact bytes and destination. */
class ReportSender(private val capture: ReportCapture, private val api: RetroHubApi, private val owner: () -> String?) {
    private val lock = Mutex()

    fun approve(id: String, features: List<String>, description: String) {
        val destination = owner() ?: error("Reconnectez cet appareil avant d’envoyer le rapport.")
        capture.update(id) { report ->
            require(report.owner == null || report.owner == destination) { "Ce rapport appartient à un autre compte." }
            if (report.approved != null || report.sent) return@update report
            val text = sanitizeLog(description.take(MAX_DESCRIPTION_CHARS))
            val approval = when {
                FEATURE_BUG_REPORTS in features -> ApprovedReport(
                    destination,
                    "/native/bug-reports",
                    ApiJson.encodeToString(BugReport.serializer(), BugReport(report.id, report.appVersion, report.kind, text, report.logs, report.diagnostic)),
                )

                FEATURE_DIAGNOSTICS in features && report.diagnostic != null -> ApprovedReport(
                    destination,
                    "/native/diagnostics",
                    ApiJson.encodeToString(EmulatorDiagnostic.serializer(), report.diagnostic),
                )

                else -> error("Ce serveur ne reçoit pas encore ce type de rapport. Utilisez Partager.")
            }
            report.copy(owner = destination, description = text, approved = approval, prompt = false, deliveryError = false)
        }
    }

    /** True only when retryable reports remain for this account. Permanent errors stay reviewable. */
    suspend fun flush(): Boolean = lock.withLock {
        val destination = owner() ?: return@withLock false
        var retry = false
        for (report in capture.reports.value) {
            val approved = report.approved ?: continue
            if (report.sent || report.deliveryError || approved.owner != destination) continue
            if (owner() != destination) break
            try {
                api.sendReport(approved.route, approved.body, report.id)
                capture.update(report.id) { it.copy(sent = true) }
            } catch (_: ApiException.Offline) {
                retry = true
            } catch (_: ApiException.Maintenance) {
                retry = true
            } catch (_: ApiException.Unauthorized) {
                break
            } catch (error: ApiException.Http) {
                if (error.status == 429 || error.status >= 500) retry = true else capture.update(report.id) { it.copy(deliveryError = true) }
            } catch (_: ApiException.UnsupportedServer) {
                capture.update(report.id) { it.copy(deliveryError = true) }
            }
        }
        retry
    }
}
