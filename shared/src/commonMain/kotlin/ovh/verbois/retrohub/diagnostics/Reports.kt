package ovh.verbois.retrohub.diagnostics

import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import ovh.verbois.retrohub.api.ApiJson
import ovh.verbois.retrohub.core.sha256

const val FEATURE_DIAGNOSTICS = "emulator_diagnostics"
const val FEATURE_BUG_REPORTS = "bug_reports_v1"
const val MAX_LOG_CHARS = 16_000
const val MAX_DESCRIPTION_CHARS = 2_000

/** Exact v1 server schema. Unknown hardware stays explicit; no guessed GPU or driver. */
@Serializable
data class EmulatorDiagnostic(
    val id: String,
    val console: String,
    val core: String,
    @SerialName("core_build") val coreBuild: String,
    @SerialName("device_model") val deviceModel: String,
    val abi: String,
    @SerialName("android_version") val androidVersion: String,
    val gpu: String = "unknown",
    val driver: String = "unknown",
    val renderer: String = "opengles",
    val context: String = "unknown",
    @SerialName("last_stage") val lastStage: String = "created",
    val outcome: String = "startup_failure",
    val consent: Boolean = true,
)

/** Proposed contract, gated by bug_reports_v1; never sent to the legacy diagnostics route. */
@Serializable
data class BugReport(
    val id: String,
    @SerialName("app_version") val appVersion: String,
    val kind: String,
    val description: String,
    val logs: String,
    val diagnostic: EmulatorDiagnostic? = null,
    val consent: Boolean = true,
)

@Serializable
data class LocalReport(
    val id: String,
    val createdAt: Long,
    val appVersion: String,
    val kind: String,
    val owner: String? = null,
    val diagnostic: EmulatorDiagnostic? = null,
    val logs: String = "",
    val description: String = "",
    val prompt: Boolean = false,
    /** Frozen at consent time, including route and destination. Null means no permission. */
    val approved: ApprovedReport? = null,
    val sent: Boolean = false,
    val deliveryError: Boolean = false,
) {
    fun export(): String = buildString {
        appendLine("RetroHub $appVersion · $kind · $id")
        appendLine(description)
        diagnostic?.let { appendLine(ApiJson.encodeToString(EmulatorDiagnostic.serializer(), it.copy(consent = false))) }
        appendLine("--- Journal technique ---")
        append(logs)
    }
}

@Serializable
data class ApprovedReport(val owner: String, val route: String, val body: String)

fun reportOwner(server: String, userId: String, deviceId: String): String = sha256("$server\u0000$userId\u0000$deviceId".encodeToByteArray())

/** Implementations serialize access and durably publish every change before returning. */
interface ReportCapture {
    suspend fun initialize() = Unit
    val reports: StateFlow<List<LocalReport>>
    suspend fun createReport(owner: String?): LocalReport
    fun update(id: String, change: (LocalReport) -> LocalReport)
    fun remove(id: String)
}

private val credentials = Regex("(?i)authorization|cookie|password|passwd|secret|token|bearer|api[_-]?key")
private val logRedactions = listOf(
    Regex("(?i)https?://[^\\s]+") to "[address]",
    Regex("(?:/[A-Za-z0-9_.%+~@-]+){2,}[^\\s]*") to "[path]",
    Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}") to "[email]",
    Regex("\\b(?:[0-9]{1,3}\\.){3}[0-9]{1,3}\\b") to "[address]",
    Regex("[A-Za-z0-9_+/=-]{48,}") to "[opaque value]",
)

/** Bound raw lines before redaction, and never retain a credential-bearing line. */
fun sanitizeLog(input: String): String = input.lineSequence().take(1_000).map { line ->
    if (credentials.containsMatchIn(line)) {
        "[credential line removed]"
    } else {
        logRedactions.fold(line.take(2_000)) { text, (pattern, replacement) -> text.replace(pattern, replacement) }
    }
}.joinToString("\n").takeLast(MAX_LOG_CHARS)

fun diagnosticLabel(value: String): String = value.replace(Regex("[^A-Za-z0-9 ._()+,:@/-]"), "_").take(160).ifBlank { "unknown" }
