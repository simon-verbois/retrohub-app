package ovh.verbois.retrohub.game

import android.content.pm.ActivityInfo
import android.hardware.input.InputManager
import android.os.Bundle
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.swordfish.libretrodroid.GLRetroView
import com.swordfish.libretrodroid.GLRetroViewData
import com.swordfish.libretrodroid.Variable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okio.Path.Companion.toPath
import ovh.verbois.retrohub.RetroHubApp
import ovh.verbois.retrohub.api.ApiJson
import ovh.verbois.retrohub.emulation.MenuShortcut
import ovh.verbois.retrohub.emulation.Orientation
import ovh.verbois.retrohub.emulation.PadKey
import ovh.verbois.retrohub.emulation.Stick
import ovh.verbois.retrohub.sync.unzip
import ovh.verbois.retrohub.ui.components.ProvideHaptics
import ovh.verbois.retrohub.ui.play.GameScreen
import ovh.verbois.retrohub.ui.theme.RetroHubTheme
import java.io.File

/**
 * Une partie : la vue LibretroDroid sous l’interface de jeu partagée (Compose). Les manettes et le
 * clavier vont au cœur ; ☰, Select + Start ou Retour ouvrent le menu.
 */
class GameActivity : ComponentActivity() {
    private lateinit var engine: LibretroEngine
    private lateinit var session: GameSession
    private lateinit var retroView: GLRetroView
    private val menuRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val held = mutableSetOf<Int>()
    private lateinit var plan: LaunchPlan
    private lateinit var router: InputRouter
    private var quitting = false
    private val inputListener = object : InputManager.InputDeviceListener {
        override fun onInputDeviceAdded(deviceId: Int) = Unit
        override fun onInputDeviceChanged(deviceId: Int) = Unit
        override fun onInputDeviceRemoved(deviceId: Int) {
            releaseInputs()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        immersive()
        val app = application as RetroHubApp
        val container = app.container
        plan = ApiJson.decodeFromString(LaunchPlan.serializer(), intent.getStringExtra(EXTRA_PLAN) ?: return finish())
        if (android.os.Build.VERSION.SDK_INT >= 28) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode = if (android.os.Build.VERSION.SDK_INT >= 30) {
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                } else {
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
            }
        }
        requestedOrientation =
            when (plan.orientation) {
                Orientation.AUTO -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                Orientation.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                Orientation.PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            }
        setContent {
            val settings by container.settings.settings.collectAsState()
            RetroHubTheme(settings.theme) {
                androidx.compose.foundation.layout.Box(
                    androidx.compose.ui.Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black),
                    contentAlignment = androidx.compose.ui.Alignment.Center,
                ) { androidx.compose.material3.Text("Préparation du jeu…") }
            }
        }
        lifecycleScope.launch {
            val sram = withContext(Dispatchers.IO) {
                prepareSystem(plan)
                if (plan.loadSram) File(plan.sramPath).takeIf { it.exists() }?.readBytes() else null
            }
            if (!isFinishing) createGame(app, sram)
        }
    }

    private fun createGame(app: RetroHubApp, sram: ByteArray?) {
        val container = app.container
        val data =
            GLRetroViewData(this).apply {
                coreFilePath = app.platform.corePath(plan.core)
                gameFilePath = plan.romPath
                systemDirectory = plan.systemDir
                savesDirectory = plan.savesDir.also { File(it).mkdirs() }
                variables = plan.options.map { (key, value) -> Variable(key, value) }.toTypedArray()
                saveRAMState = sram
                shader = LibretroEngine.shaderConfig(plan.shader)
                preferLowLatencyAudio = plan.lowLatencyAudio
                rumbleEventsEnabled = plan.rumble
                skipDuplicateFrames = false
            }
        retroView = GLRetroView(this, data)
        lifecycle.addObserver(retroView)
        engine = LibretroEngine(retroView, restoreOnReset = plan.core == "melonds").also { it.bind(this) }
        current = java.lang.ref.WeakReference(this)
        engine.setAudio(plan.audio)
        router =
            InputRouter(
                engine,
                lifecycleScope,
                plan.turbo.mapNotNull { name -> PadKey.entries.firstOrNull { it.name == name } }.toSet(),
                plan.stickDeadzone,
                plan.stickSensitivity,
            )
        session = container.gameSession(plan, engine, lifecycleScope)
        getSystemService(InputManager::class.java).registerInputDeviceListener(inputListener, null)
        lifecycleScope.launch {
            session.ui.map { it.paused }.distinctUntilChanged().collect { if (it) releaseInputs() }
        }
        lifecycleScope.launch { engine.errors.collect { session.flash(it) } }
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    menuRequests.tryEmit(Unit)
                }
            },
        )
        setContent {
            val settings by container.settings.settings.collectAsState()
            RetroHubTheme(settings.theme) {
                ProvideHaptics(plan.haptics, { app.platform.vibrate() }) {
                    GameScreen(
                        session,
                        engine,
                        app.platform.gamepadConnected,
                        container.sync.status,
                        menuRequests,
                        haptic = { app.platform.vibrate(10) },
                        onQuit = { finish() },
                        onConsoleSettings = { change -> container.settings.update { it.withConsole(plan.console, change) } },
                        onCheats = { cheats -> container.scope.launch { container.cheats.put(plan.playGameId, cheats) } },
                        onRumble = { app.platform.rumble(it) },
                    ) { AndroidView({ retroView }) }
                }
            }
        }
    }

    /** Fichiers que certains cœurs attendent dans le dossier système (Dolphin : dossier Sys). */
    private fun prepareSystem(plan: LaunchPlan) {
        val system = File(plan.systemDir).also { it.mkdirs() }
        if (plan.core == "dolphin" && !File(system, "dolphin-emu/Sys").isDirectory) {
            val archive = File(cacheDir, "dolphin-sys.zip")
            assets.open("dolphin-sys.zip").use { input -> archive.outputStream().use { input.copyTo(it) } }
            unzip(okio.FileSystem.SYSTEM, archive.absolutePath.toPath(), system.absolutePath.toPath())
            archive.delete()
        }
        if (plan.console == "dreamcast") File(system, "dc").mkdirs()
    }

    // Faux positif de lint : super.dispatchKeyEvent est l’API publique d’Activity.
    @android.annotation.SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (!::session.isInitialized) return super.dispatchKeyEvent(event)
        val paused = session.ui.value.paused
        val code = event.keyCode
        // Menu : bouton Guide / Menu, ou la combinaison choisie (Select + Start, L3 + R3).
        if (event.action == KeyEvent.ACTION_DOWN) held += code else held -= code
        val pair =
            when (plan.menuShortcut) {
                MenuShortcut.SELECT_START -> KeyEvent.KEYCODE_BUTTON_SELECT to KeyEvent.KEYCODE_BUTTON_START
                MenuShortcut.L3_R3 -> KeyEvent.KEYCODE_BUTTON_THUMBL to KeyEvent.KEYCODE_BUTTON_THUMBR
                MenuShortcut.GUIDE -> null
            }
        val combo = pair != null && pair.first in held && pair.second in held
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0 &&
            (code == KeyEvent.KEYCODE_BUTTON_MODE || code == KeyEvent.KEYCODE_MENU || combo)
        ) {
            if (combo) {
                router.releaseAll()
                PadKey.entries.forEach { engine.key(it, false, port(event)) }
            }
            menuRequests.tryEmit(Unit)
            return true
        }
        if (paused) {
            if (event.action == KeyEvent.ACTION_UP && (code == KeyEvent.KEYCODE_BUTTON_B || code == KeyEvent.KEYCODE_ESCAPE)) {
                session.resume()
                return true
            }
            return super.dispatchKeyEvent(event)
        }
        val key = remapped(code) ?: padKey(event) ?: return super.dispatchKeyEvent(event)
        if (event.repeatCount > 0) return true
        router.key(key, event.action == KeyEvent.ACTION_DOWN, port(event))
        return true
    }

    /** Une manette par joueur ; sans le multijoueur, toutes jouent le joueur 1. */
    private fun port(event: android.view.InputEvent): Int = if (!plan.multiplayer) 0 else ((event.device?.controllerNumber ?: 1) - 1).coerceIn(0, 3)

    /** Touches choisies par le joueur pour cette console (page Commandes). */
    private fun remapped(code: Int): PadKey? = plan.remap.entries.firstOrNull { it.value == code }?.key?.let { name ->
        PadKey.entries.firstOrNull { it.name == name }
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        if (!::session.isInitialized || session.ui.value.paused ||
            event.source and InputDevice.SOURCE_JOYSTICK != InputDevice.SOURCE_JOYSTICK
        ) {
            return super.dispatchGenericMotionEvent(event)
        }
        val port = port(event)
        val hatX = event.getAxisValue(MotionEvent.AXIS_HAT_X)
        val hatY = event.getAxisValue(MotionEvent.AXIS_HAT_Y)
        router.key(PadKey.LEFT, hatX < -0.5f, port)
        router.key(PadKey.RIGHT, hatX > 0.5f, port)
        router.key(PadKey.UP, hatY < -0.5f, port)
        router.key(PadKey.DOWN, hatY > 0.5f, port)
        router.stick(Stick.LEFT, event.getAxisValue(MotionEvent.AXIS_X), event.getAxisValue(MotionEvent.AXIS_Y), port)
        router.stick(Stick.RIGHT, event.getAxisValue(MotionEvent.AXIS_Z), event.getAxisValue(MotionEvent.AXIS_RZ), port)
        // Gâchettes analogiques (GameCube, Dreamcast) : au-delà de la moitié, on enfonce L2/R2.
        val left = maxOf(event.getAxisValue(MotionEvent.AXIS_LTRIGGER), event.getAxisValue(MotionEvent.AXIS_BRAKE))
        val right = maxOf(event.getAxisValue(MotionEvent.AXIS_RTRIGGER), event.getAxisValue(MotionEvent.AXIS_GAS))
        router.key(PadKey.L2, left > 0.5f, port)
        router.key(PadKey.R2, right > 0.5f, port)
        return true
    }

    private fun releaseInputs() {
        if (::router.isInitialized) router.releaseAll()
        held.clear()
    }

    /** Manette Android → RetroPad (A et B, X et Y inversés), et un clavier pour jouer au bureau. */
    private fun padKey(event: KeyEvent): PadKey? = when (event.keyCode) {
        KeyEvent.KEYCODE_DPAD_UP -> PadKey.UP
        KeyEvent.KEYCODE_DPAD_DOWN -> PadKey.DOWN
        KeyEvent.KEYCODE_DPAD_LEFT -> PadKey.LEFT
        KeyEvent.KEYCODE_DPAD_RIGHT -> PadKey.RIGHT
        KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_Z -> PadKey.B
        KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_X -> PadKey.A
        KeyEvent.KEYCODE_BUTTON_X, KeyEvent.KEYCODE_A -> PadKey.Y
        KeyEvent.KEYCODE_BUTTON_Y, KeyEvent.KEYCODE_S -> PadKey.X
        KeyEvent.KEYCODE_BUTTON_L1, KeyEvent.KEYCODE_Q -> PadKey.L
        KeyEvent.KEYCODE_BUTTON_R1, KeyEvent.KEYCODE_W -> PadKey.R
        KeyEvent.KEYCODE_BUTTON_L2, KeyEvent.KEYCODE_1 -> PadKey.L2
        KeyEvent.KEYCODE_BUTTON_R2, KeyEvent.KEYCODE_2 -> PadKey.R2
        KeyEvent.KEYCODE_BUTTON_THUMBL -> PadKey.L3
        KeyEvent.KEYCODE_BUTTON_THUMBR -> PadKey.R3
        KeyEvent.KEYCODE_BUTTON_START, KeyEvent.KEYCODE_ENTER -> PadKey.START
        KeyEvent.KEYCODE_BUTTON_SELECT, KeyEvent.KEYCODE_SHIFT_RIGHT, KeyEvent.KEYCODE_SHIFT_LEFT -> PadKey.SELECT
        else -> null
    }

    override fun onPause() {
        super.onPause()
        // L’appareil passe à autre chose : la partie est mise en pause et gardée.
        if (!isFinishing && ::session.isInitialized) {
            session.pause()
            (application as RetroHubApp).container.scope.launch { session.autoSave() }
        }
    }

    override fun onDestroy() {
        releaseInputs()
        getSystemService(InputManager::class.java).unregisterInputDeviceListener(inputListener)
        super.onDestroy()
    }

    override fun finish() {
        if (!::session.isInitialized) {
            super.finish()
            return
        }
        if (quitting) return
        quitting = true
        session.pause()
        lifecycleScope.launch {
            session.finish()
            super@GameActivity.finish()
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) immersive()
    }

    private fun immersive() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    /** Images rendues par le cœur (les tests de bout en bout vérifient que le jeu tourne). */
    val frames get() = engine.frames

    companion object {
        const val EXTRA_PLAN = "plan"

        /** Partie en cours, pour les tests instrumentés. */
        @Volatile
        var current: java.lang.ref.WeakReference<GameActivity>? = null
    }
}
