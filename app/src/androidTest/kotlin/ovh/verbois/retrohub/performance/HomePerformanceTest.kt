@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package ovh.verbois.retrohub.performance

import android.content.pm.ActivityInfo
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.Choreographer
import android.view.FrameMetrics
import android.view.InputDevice
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import ovh.verbois.retrohub.RetroHubApp
import ovh.verbois.retrohub.account.Account
import ovh.verbois.retrohub.api.ApiJson
import ovh.verbois.retrohub.api.LibraryGame
import ovh.verbois.retrohub.api.RomInfo
import ovh.verbois.retrohub.data.GameEntity
import ovh.verbois.retrohub.data.LocalRomEntity
import ovh.verbois.retrohub.emulation.AppSettings
import ovh.verbois.retrohub.library.Game
import ovh.verbois.retrohub.ui.AppShell
import ovh.verbois.retrohub.ui.HomeScreen
import ovh.verbois.retrohub.ui.UiController
import ovh.verbois.retrohub.ui.components.AnimatedBackground
import ovh.verbois.retrohub.ui.home.homeColumns
import ovh.verbois.retrohub.ui.nav.Route
import ovh.verbois.retrohub.ui.theme.RetroHubTheme
import ovh.verbois.retrohub.ui.theme.consoleColor
import java.io.File
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Real-clock regression coverage: normal animations, rapid confirmation and large local libraries. */
@RunWith(AndroidJUnit4::class)
class HomePerformanceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val container get() = (instrumentation.targetContext.applicationContext as RetroHubApp).container
    private val output = StringBuilder()
    private var currentScenario: ActivityScenario<ComponentActivity>? = null
    private var initialSettings: AppSettings? = null

    @After fun cleanup() {
        currentScenario?.close()
        runBlocking {
            container.database.games().clear()
            initialSettings?.let { original -> container.settings.updateNow { original } }
        }
    }
    private fun report(value: String) {
        Log.i("RetroHubPerformance", value)
        output.appendLine(value)
    }
    private fun roots(v: View): List<ViewRootForTest> = if (v is ViewRootForTest) {
        listOf(v)
    } else if (v is ViewGroup) {
        (0 until v.childCount).flatMap { roots(v.getChildAt(it)) }
    } else {
        emptyList()
    }
    private fun summary(xs: List<Double>): String {
        val x = xs.sorted()
        fun p(q: Double) = if (x.isEmpty()) 0.0 else x[((x.size - 1) * q).toInt()]
        return "n=${x.size}, p50=${p(.5)}, p95=${p(.95)}, p99=${p(.99)}, max=${x.lastOrNull()}"
    }
    private fun remote(i: Int) = LibraryGame(
        "audit-$i",
        "audit-$i",
        title = "Jeu ${i.toString().padStart(5, '0')}",
        console = "nes",
        rom = RomInfo("a".repeat(64), 1, "nes", "audit.nes", "/rom"),
        lastPlayedAt = "2026-11-${(1 + (i * 17 % 19)).toString().padStart(2, '0')}T10:00:00Z",
    )

    @Test fun audit() {
        runBlocking { container.start() }
        initialSettings = container.settings.settings.value
        for (size in listOf(200, 2000, 10000)) {
            val data = (0 until size).map { i ->
                Game(remote(i), LocalRomEntity("audit-$i", "audit-$i", "nes", "Jeu", "/unused", "sha", 0, "2026-11-19T10:00:00Z"))
            }.shuffled(kotlin.random.Random(42))
            val settings = AppSettings(homeActivity = data.associate { it.id to "2026-11-18T10:00:00Z" })
            val times = mutableListOf<Double>()
            repeat(25) { sample ->
                instrumentation.runOnMainSync {
                    val t = System.nanoTime()
                    val result = homeColumns(data, settings)
                    check(result.size == 3)
                    if (sample >= 5) times += (System.nanoTime() - t) / 1e6
                }
                Thread.sleep(10)
            }
            report("homeColumns main thread size=$size ms: ${summary(times)}")
            check(times.sorted()[times.size / 2] < 75) { "Home sorting exceeded its 75 ms large-library budget: ${summary(times)}" }
        }
        for (config in listOf(Triple(200, false, false), Triple(200, true, false), Triple(200, true, true), Triple(10000, false, false))) {
            val (size, background, reduced) = config
            runBlocking {
                container.settings.updateNow { AppSettings(setupDone = true, animatedBackground = background, reduceMotion = reduced) }
                container.database.games().replaceAll(
                    (0 until size).map { i ->
                        val g = remote(i).copy(
                            console = if (i % 2 ==
                                0
                            ) {
                                "nes"
                            } else {
                                "snes"
                            },
                        )
                        GameEntity(g.id, g.playGameId, g.title, g.console, false, g.lastPlayedAt, 0, i, ApiJson.encodeToString(LibraryGame.serializer(), g))
                    },
                )
            }
            val deadline = SystemClock.uptimeMillis() + 20000
            while (container.library.games.value.size != size) {
                check(SystemClock.uptimeMillis() < deadline)
                Thread.sleep(20)
            }
            val scenario = ActivityScenario.launch(ComponentActivity::class.java).also { currentScenario = it }
            scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
            Thread.sleep(1200)
            lateinit var activity: ComponentActivity
            lateinit var ui: UiController
            scenario.onActivity { a ->
                activity = a
                a.setContent {
                    val scope = rememberCoroutineScope()
                    val controller = remember {
                        UiController(container, scope).also {
                            ui = it
                            it.selectHomeColumn("console:nes")
                        }
                    }
                    LocalInputModeManager.current.requestInputMode(InputMode.Keyboard)
                    RetroHubTheme {
                        Box(Modifier.fillMaxSize()) {
                            AnimatedBackground(
                                controller.backdrop.url,
                                controller.backdrop.console,
                                controller.backdrop.console?.let { consoleColor(it) } ?: ovh.verbois.retrohub.ui.theme.Palette.SkyTop,
                                animated = background && !reduced,
                                reducedMotion = reduced,
                                paused = controller.homeMoving,
                            )
                            AppShell(
                                controller,
                                Account("https://example.invalid", userId = "test", username = "Test", deviceId = "test", deviceName = "Test"),
                            ) { route ->
                                if (route ==
                                    Route.Home
                                ) {
                                    HomeScreen(controller)
                                }
                            }
                        }
                    }
                }
            }
            instrumentation.setInTouchMode(false)
            Thread.sleep(2000)
            // A enters a console's game lane before a second A activates its selected game.
            instrumentation.runOnMainSync {
                val now = SystemClock.uptimeMillis()
                fun press(code: Int) {
                    for (action in listOf(KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP)) {
                        activity.dispatchKeyEvent(KeyEvent(now, now, action, code, 0, 0, 0, 0, 0, InputDevice.SOURCE_GAMEPAD))
                    }
                }
                press(KeyEvent.KEYCODE_DPAD_UP)
                press(KeyEvent.KEYCODE_BUTTON_A)
                check(ui.downloadRequest == null) { "Entering a console activated a game" }
                press(KeyEvent.KEYCODE_BUTTON_A)
                check(ui.downloadRequest?.id == ui.homeGame) { "Game confirmation after entering a console was lost" }
                ui.dismissDownload()
            }
            val frames = Collections.synchronizedList(mutableListOf<Double>())
            val listener = android.view.Window.OnFrameMetricsAvailableListener { _, metric, _ -> frames += metric.getMetric(FrameMetrics.TOTAL_DURATION) / 1e6 }
            instrumentation.runOnMainSync { activity.window.addOnFrameMetricsAvailableListener(listener, Handler(Looper.getMainLooper())) }
            val cpuStart = android.os.Process.getElapsedCpuTime()
            Thread.sleep(2500)
            val idleCpu = android.os.Process.getElapsedCpuTime() - cpuStart
            report(
                "idle size=$size background=$background reduced=$reduced frame ms ${summary(
                    frames.toList(),
                )}; process CPU ms/2500ms=$idleCpu; PSS KiB=${android.os.Debug.getPss()}",
            )
            frames.clear()
            val selections = mutableListOf<Double>()
            val focus = mutableListOf<Double>()
            repeat(20) {
                val done = CountDownLatch(1)
                var selectedMs: Double? = null
                val before = ui.homeGame
                val start = System.nanoTime()
                lateinit var watch: Choreographer.FrameCallback
                watch = Choreographer.FrameCallback {
                    if (activity.isDestroyed) return@FrameCallback
                    if (ui.homeGame != before && selectedMs == null) selectedMs = (System.nanoTime() - start) / 1e6
                    val focused = roots(activity.window.decorView).flatMap {
                        it.semanticsOwner.getAllSemanticsNodes(false)
                    }.firstOrNull { it.config.getOrElse(SemanticsProperties.Focused) { false } }
                    val tag = focused?.config?.getOrElse(SemanticsProperties.TestTag) { "" }
                    if (selectedMs != null && tag == "home-game-${ui.homeGame}") {
                        selections += selectedMs
                        focus += (System.nanoTime() - start) / 1e6
                        done.countDown()
                    } else {
                        Choreographer.getInstance().postFrameCallback(watch)
                    }
                }
                instrumentation.runOnMainSync {
                    Choreographer.getInstance().postFrameCallback(watch)
                    val now = SystemClock.uptimeMillis()
                    for (action in listOf(
                        KeyEvent.ACTION_DOWN,
                        KeyEvent.ACTION_UP,
                    )) {
                        activity.dispatchKeyEvent(KeyEvent(now, now, action, KeyEvent.KEYCODE_DPAD_DOWN, 0, 0, 0, 0, 0, InputDevice.SOURCE_GAMEPAD))
                    }
                }
                check(done.await(5, TimeUnit.SECONDS)) { "Focus did not follow selection ${ui.homeGame}" }
                Thread.sleep(80)
            }
            report(
                "navigation size=$size background=$background reduced=$reduced selection ms ${summary(
                    selections,
                )}; focus ms ${summary(focus)}; frame ms ${summary(frames.toList())}; >16.67ms=${frames.count {
                    it > 16.67
                }} >33.33ms=${frames.count { it > 33.33 }}",
            )
            check(focus.sorted()[focus.size / 2] < 300) { "Navigation waited for presentation: ${summary(focus)}" }
            frames.clear()
            val categories = mutableListOf<Double>()
            repeat(10) { step ->
                val done = CountDownLatch(1)
                val previousColumn = ui.homeColumn
                val start = System.nanoTime()
                lateinit var watch: Choreographer.FrameCallback
                watch = Choreographer.FrameCallback {
                    if (activity.isDestroyed) return@FrameCallback
                    val focused = roots(activity.window.decorView).flatMap {
                        it.semanticsOwner.getAllSemanticsNodes(false)
                    }.firstOrNull { it.config.getOrElse(SemanticsProperties.Focused) { false } }
                    if (ui.homeColumn != previousColumn && focused?.config?.getOrElse(SemanticsProperties.TestTag) { "" } == "home-game-${ui.homeGame}") {
                        categories += (System.nanoTime() - start) / 1e6
                        done.countDown()
                    } else {
                        Choreographer.getInstance().postFrameCallback(watch)
                    }
                }
                instrumentation.runOnMainSync {
                    Choreographer.getInstance().postFrameCallback(watch)
                    val now = SystemClock.uptimeMillis()
                    val code = if (step % 2 == 0) KeyEvent.KEYCODE_BUTTON_R1 else KeyEvent.KEYCODE_BUTTON_L1
                    for (action in listOf(
                        KeyEvent.ACTION_DOWN,
                        KeyEvent.ACTION_UP,
                    )) {
                        activity.dispatchKeyEvent(KeyEvent(now, now, action, code, 0, 0, 0, 0, 0, InputDevice.SOURCE_GAMEPAD))
                    }
                }
                check(done.await(8, TimeUnit.SECONDS)) { "Category focus timeout ${ui.homeColumn}" }
                Thread.sleep(120)
            }
            report("categories size=$size background=$background reduced=$reduced focus ms ${summary(categories)}; frame ms ${summary(frames.toList())}")
            val targetBefore = ui.homeGame
            instrumentation.runOnMainSync {
                val now = SystemClock.uptimeMillis()
                activity.dispatchKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_DOWN, 0, 0, 0, 0, 0, InputDevice.SOURCE_GAMEPAD))
                activity.dispatchKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_DOWN, 0, 0, 0, 0, 0, InputDevice.SOURCE_GAMEPAD))
                activity.dispatchKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BUTTON_A, 0, 0, 0, 0, 0, InputDevice.SOURCE_GAMEPAD))
                activity.dispatchKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BUTTON_A, 0, 0, 0, 0, 0, InputDevice.SOURCE_GAMEPAD))
            }
            Thread.sleep(700)
            report(
                "immediate Down+A size=$size before=$targetBefore selected=${ui.homeGame} downloadRequest=${ui.downloadRequest?.id} launching=${ui.launching} toast=${ui.toast}",
            )
            check(ui.downloadRequest?.id == ui.homeGame) { "Immediate Down+A acted on ${ui.downloadRequest?.id}, selected ${ui.homeGame}" }
            instrumentation.runOnMainSync { ui.dismissDownload() }
            Thread.sleep(1000)
            val beforeDelayed = ui.homeGame
            instrumentation.runOnMainSync {
                val now = SystemClock.uptimeMillis()
                for (action in listOf(
                    KeyEvent.ACTION_DOWN,
                    KeyEvent.ACTION_UP,
                )) {
                    activity.dispatchKeyEvent(KeyEvent(now, now, action, KeyEvent.KEYCODE_DPAD_DOWN, 0, 0, 0, 0, 0, InputDevice.SOURCE_GAMEPAD))
                }
            }
            Thread.sleep(100)
            val selectedAtA = ui.homeGame
            instrumentation.runOnMainSync {
                val now = SystemClock.uptimeMillis()
                for (action in listOf(
                    KeyEvent.ACTION_DOWN,
                    KeyEvent.ACTION_UP,
                )) {
                    activity.dispatchKeyEvent(KeyEvent(now, now, action, KeyEvent.KEYCODE_BUTTON_A, 0, 0, 0, 0, 0, InputDevice.SOURCE_GAMEPAD))
                }
            }
            Thread.sleep(700)
            report(
                "Down then 100ms then A size=$size before=$beforeDelayed selectedAtA=$selectedAtA afterA=${ui.homeGame} downloadRequest=${ui.downloadRequest?.id}",
            )
            check(ui.downloadRequest?.id == selectedAtA) { "Down+A after 100 ms lost confirmation for $selectedAtA" }
            val dir = File(instrumentation.targetContext.getExternalFilesDir(null), "performance").apply { mkdirs() }
            File(dir, "results.txt").writeText(output.toString())
            File(dir, "home-$size-$background-$reduced.png").outputStream().use {
                instrumentation.uiAutomation.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
            instrumentation.runOnMainSync { activity.window.removeOnFrameMetricsAvailableListener(listener) }
            scenario.close()
            currentScenario = null
        }
        val dir = File(instrumentation.targetContext.getExternalFilesDir(null), "performance").apply { mkdirs() }
        File(dir, "results.txt").writeText(output.toString())
        runBlocking { container.database.games().clear() }
    }
}
