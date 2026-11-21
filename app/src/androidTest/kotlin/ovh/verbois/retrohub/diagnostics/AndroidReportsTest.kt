package ovh.verbois.retrohub.diagnostics

import android.content.ContextWrapper
import android.os.StrictMode
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue

@RunWith(AndroidJUnit4::class)
class AndroidReportsTest {
    @Test
    @SdkSuppress(minSdkVersion = 28)
    fun constructionDoesNotReadOrWriteDiskAndReportsSurviveReinitialization() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val directory = File(instrumentation.targetContext.cacheDir, "reports-test-${System.nanoTime()}").apply { mkdirs() }
        val context = object : ContextWrapper(instrumentation.targetContext) {
            override fun getFilesDir(): File = directory
        }
        val handler = Thread.getDefaultUncaughtExceptionHandler()
        val violations = ConcurrentLinkedQueue<String>()
        lateinit var capture: AndroidReports
        try {
            instrumentation.runOnMainSync {
                val previous = StrictMode.getThreadPolicy()
                StrictMode.setThreadPolicy(
                    StrictMode.ThreadPolicy.Builder().detectDiskReads().detectDiskWrites().penaltyListener({ it.run() }) {
                        violations +=
                            it.toString()
                    }.build(),
                )
                try {
                    capture = AndroidReports(context)
                } finally {
                    StrictMode.setThreadPolicy(previous)
                }
            }
            assertTrue(violations.toString(), violations.isEmpty())
            capture.initialize()
            val report = capture.createReport("owner")
            val restored = AndroidReports(context)
            restored.initialize()
            assertTrue(restored.reports.value.any { it.id == report.id && it.owner == "owner" && it.approved == null })
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(handler)
            directory.deleteRecursively()
        }
    }
}
