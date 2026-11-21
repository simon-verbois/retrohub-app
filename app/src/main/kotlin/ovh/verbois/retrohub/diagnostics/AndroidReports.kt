package ovh.verbois.retrohub.diagnostics

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.opengl.GLES20
import android.os.Build
import android.os.Process
import android.util.AtomicFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import ovh.verbois.retrohub.BuildConfig
import ovh.verbois.retrohub.api.ApiJson
import ovh.verbois.retrohub.game.LaunchPlan
import java.io.File
import kotlin.uuid.Uuid

/** Private, bounded local reports. No network, tokens, ROMs, save files or raw tombstones. */
class AndroidReports(private val context: Context) : ReportCapture {
    private val directory = File(context.filesDir, "diagnostics")
    private val reportFile = AtomicFile(File(directory, "reports.json"))
    private val activeFile = AtomicFile(File(directory, "active.json"))
    private val logFile = File(directory, "emulator.log")
    private val state = MutableStateFlow<List<LocalReport>>(emptyList())
    override val reports = state.asStateFlow()
    private var active: ActiveGame? = null
    private var lastGame: LocalReport? = null
    private var logger: java.lang.Process? = null
    private var logThread: Thread? = null

    private var initialized = false
    private val background = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        installCrashHandler()
    }

    override suspend fun initialize() = withContext(Dispatchers.IO) { ensureInitialized() }

    @Synchronized
    private fun ensureInitialized() {
        if (initialized) return
        directory.mkdirs()
        state.value = readReports()
        recover()
        initialized = true
    }

    @Serializable
    private data class ActiveGame(val pid: Int, val startedAt: Long, val report: LocalReport)

    private fun readReports(): List<LocalReport> = runCatching {
        val cutoff = System.currentTimeMillis() - RETENTION_MILLIS
        ApiJson.decodeFromString(ListSerializer(LocalReport.serializer()), reportFile.openRead().bufferedReader().use { it.readText() })
            .filter { it.createdAt >= cutoff }.takeLast(MAX_REPORTS)
    }.getOrDefault(emptyList())

    private fun write(file: AtomicFile, value: String) {
        val output = file.startWrite()
        try {
            output.write(value.toByteArray(Charsets.UTF_8))
            file.finishWrite(output)
        } catch (error: Exception) {
            file.failWrite(output)
            throw error
        }
    }

    private fun publish(reports: List<LocalReport>) {
        val cutoff = System.currentTimeMillis() - RETENTION_MILLIS
        val retained = reports.filter { it.createdAt >= cutoff }.takeLast(MAX_REPORTS)
        write(reportFile, ApiJson.encodeToString(ListSerializer(LocalReport.serializer()), retained))
        state.value = retained
    }

    @Synchronized
    override fun update(id: String, change: (LocalReport) -> LocalReport) {
        publish(state.value.map { if (it.id == id) change(it) else it })
    }

    @Synchronized
    override fun remove(id: String) {
        publish(state.value.filterNot { it.id == id })
    }

    @Synchronized
    private fun add(report: LocalReport) {
        publish(state.value.filterNot { it.id == report.id } + report)
    }

    override suspend fun createReport(owner: String?): LocalReport = withContext(Dispatchers.IO) {
        synchronized(this@AndroidReports) {
            ensureInitialized()
            val previous = active?.report ?: lastGame
            val report = LocalReport(
                id = Uuid.random().toString(),
                createdAt = System.currentTimeMillis(),
                appVersion = BuildConfig.VERSION_NAME,
                kind = "manual",
                owner = owner,
                diagnostic = previous?.takeIf { it.owner == owner }?.diagnostic?.let { it.copy(id = Uuid.random().toString()) },
                logs = previous?.takeIf { it.owner == owner }?.let { readLog() }.orEmpty(),
            ).let { it.copy(diagnostic = it.diagnostic?.copy(id = it.id)) }
            add(report)
            report
        }
    }

    /** Persist before touching the native core, so SIGSEGV never depends on a JVM handler. */
    @Synchronized
    fun beginGame(plan: LaunchPlan, owner: String?, coreBuild: String?) {
        ensureInitialized()
        stopLogger()
        logFile.writeText("")
        val id = Uuid.random().toString()
        val diagnostic = coreBuild?.takeIf { it.matches(Regex("[a-f0-9]{64}")) }?.let {
            EmulatorDiagnostic(
                id,
                plan.console,
                plan.core,
                it,
                diagnosticLabel("${Build.MANUFACTURER} ${Build.MODEL}"),
                if (Process.is64Bit()) Build.SUPPORTED_64_BIT_ABIS.first() else Build.SUPPORTED_32_BIT_ABIS.first(),
                diagnosticLabel(Build.VERSION.RELEASE),
            )
        }
        active = ActiveGame(
            Process.myPid(),
            System.currentTimeMillis(),
            LocalReport(id, System.currentTimeMillis(), BuildConfig.VERSION_NAME, "game_failure", owner, diagnostic),
        )
        persistActive()
        appendLog("emulator created: console=${plan.console}, core=${plan.core}")
        startLogger()
    }

    @Synchronized
    fun stage(value: String) {
        val game = active ?: return
        active = game.copy(report = game.report.copy(diagnostic = game.report.diagnostic?.copy(lastStage = value)))
        persistActive()
        appendLog("stage=$value")
    }

    /** Read GL strings on the GL thread; persist the observation away from the render loop. */
    fun firstFrame() {
        val id = synchronized(this) { active?.report?.id } ?: return
        val gpu = diagnosticLabel(GLES20.glGetString(GLES20.GL_RENDERER).orEmpty())
        val version = diagnosticLabel(GLES20.glGetString(GLES20.GL_VERSION).orEmpty())
        background.launch {
            synchronized(this@AndroidReports) {
                val game = active?.takeIf { it.report.id == id } ?: return@synchronized
                active =
                    game.copy(
                        report = game.report.copy(
                            diagnostic = game.report.diagnostic?.let { diagnostic ->
                                diagnostic.copy(
                                    gpu = gpu,
                                    driver = version,
                                    context = version,
                                    lastStage = if (diagnostic.lastStage == "playing") "playing" else "first_frame",
                                )
                            },
                        ),
                    )
                persistActive()
                appendLog("first frame rendered")
            }
        }
    }

    @Synchronized
    fun failure(outcome: String, code: String) {
        val game = active ?: return
        appendLog("failure=$outcome, code=$code")
        val report = game.report.copy(
            diagnostic = game.report.diagnostic?.copy(outcome = outcome),
            logs = readLog(),
            prompt = true,
        )
        add(report)
        active = game.copy(report = report)
        persistActive()
    }

    @Synchronized
    fun closeGame() {
        val report = active?.report ?: return
        if (state.value.none { it.id == report.id }) {
            lastGame = report.copy(diagnostic = report.diagnostic?.copy(lastStage = "closed", outcome = "graceful_quit"))
        } else {
            update(report.id) { it.copy(logs = readLog()) }
            lastGame = state.value.firstOrNull { it.id == report.id }
        }
        active = null
        activeFile.delete()
        stopLogger()
    }

    private fun persistActive() {
        active?.let { write(activeFile, ApiJson.encodeToString(ActiveGame.serializer(), it)) }
    }

    /** Confirm crashes by PID and time, never mistake force-stop/low memory/updates for a bug. */
    private fun recover() {
        val previous = runCatching {
            ApiJson.decodeFromString(ActiveGame.serializer(), activeFile.openRead().bufferedReader().use { it.readText() })
        }.getOrNull()
        if (previous != null && System.currentTimeMillis() - previous.startedAt < RETENTION_MILLIS && Build.VERSION.SDK_INT >= 30) {
            val exit = runCatching {
                context.getSystemService(ActivityManager::class.java)
                    .getHistoricalProcessExitReasons(context.packageName, previous.pid, 8)
                    .firstOrNull { it.timestamp >= previous.startedAt && it.processName == context.packageName }
            }.getOrNull()
            if (exit?.reason in setOf(ApplicationExitInfo.REASON_CRASH, ApplicationExitInfo.REASON_CRASH_NATIVE, ApplicationExitInfo.REASON_ANR)) {
                if (state.value.none { it.id == previous.report.id }) {
                    add(
                        previous.report.copy(
                            kind = "game_crash",
                            prompt = true,
                            logs = sanitizeLog(readLog() + "\nAndroid exit reason=${exit?.reason}, signal=${exit?.status}"),
                            diagnostic = previous.report.diagnostic?.copy(outcome = "process_death"),
                        ),
                    )
                }
            }
        }
        activeFile.delete()
        // Do not keep an orphaned log after expiry or a normal system termination.
        logFile.delete()
    }

    private fun installCrashHandler() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                synchronized(this) {
                    ensureInitialized()
                    // Exception messages may contain credentials. Only class and stack locations survive.
                    val trace = generateSequence(error) { it.cause }.take(5).joinToString("\n") { cause ->
                        cause.javaClass.name + "\n" + cause.stackTrace.take(40).joinToString("\n") { "at $it" }
                    }
                    val game = active?.report
                    val report = game ?: LocalReport(
                        Uuid.random().toString(),
                        System.currentTimeMillis(),
                        BuildConfig.VERSION_NAME,
                        "app_crash",
                    )
                    add(
                        report.copy(
                            kind = if (game == null) "app_crash" else "game_crash",
                            prompt = true,
                            diagnostic = report.diagnostic?.copy(outcome = "process_death"),
                            logs = sanitizeLog(readLog() + "\n" + trace),
                        ),
                    )
                }
            } catch (_: Exception) {
                // Reporting must never replace Android's normal crash handling.
            } finally {
                if (previous != null) {
                    previous.uncaughtException(thread, error)
                } else {
                    Process.killProcess(Process.myPid())
                    kotlin.system.exitProcess(10)
                }
            }
        }
    }

    private fun startLogger() {
        val process = runCatching {
            ProcessBuilder(
                "logcat", "-v", "brief", "-T", "1", "--pid=${Process.myPid()}",
                "LibretroDroid:V", "libretrodroid:V", "Dolphin:V", "libc:F", "*:S",
            ).redirectErrorStream(true).start()
        }.getOrNull() ?: return
        logger = process
        logThread = Thread({
            runCatching {
                process.inputStream.bufferedReader().useLines { lines ->
                    lines.forEach { line ->
                        synchronized(this) {
                            if (logger === process) appendLog(sanitizeLog(line))
                        }
                    }
                }
            }
        }, "retrohub-diagnostics").apply {
            isDaemon = true
            start()
        }
    }

    private fun stopLogger() {
        logger?.destroy()
        logger = null
        logThread?.interrupt()
        logThread = null
    }

    private fun appendLog(line: String) {
        runCatching { logFile.writeText((readLog() + "\n" + line).takeLast(MAX_LOG_CHARS)) }
    }

    private fun readLog(): String = runCatching { sanitizeLog(logFile.readText()) }.getOrDefault("")

    companion object {
        const val MAX_REPORTS = 20
        const val RETENTION_MILLIS = 30L * 24 * 60 * 60 * 1000
    }
}
