package ovh.verbois.retrohub.game

import android.opengl.GLSurfaceView
import android.view.KeyEvent
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.swordfish.libretrodroid.GLRetroView
import com.swordfish.libretrodroid.LibretroDroid
import com.swordfish.libretrodroid.ShaderConfig
import com.swordfish.libretrodroid.Variable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import ovh.verbois.retrohub.emulation.CoreVariable
import ovh.verbois.retrohub.emulation.EmulatorEngine
import ovh.verbois.retrohub.emulation.GameRotation
import ovh.verbois.retrohub.emulation.NormRect
import ovh.verbois.retrohub.emulation.PadKey
import ovh.verbois.retrohub.emulation.Shader
import ovh.verbois.retrohub.emulation.Stick
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Le moteur LibretroDroid derrière l’interface partagée [EmulatorEngine]. */
class LibretroEngine(private val view: GLRetroView, private val corePath: String, private val restoreOnReset: Boolean = false) : EmulatorEngine {
    private val frameCount = MutableStateFlow(0L)
    override val frames: StateFlow<Long> = frameCount
    private val errorFlow = MutableSharedFlow<String>(extraBufferCapacity = 4)
    override val errors: SharedFlow<String> = errorFlow
    private var paused = false
    private var audioEnabled = true
    private var bootState: ByteArray? = null

    fun bind(owner: LifecycleOwner) {
        owner.lifecycleScope.launch {
            view.getGLRetroEvents().collect { event ->
                if (event is GLRetroView.GLRetroEvents.FrameRendered) frameCount.value++
            }
        }
        owner.lifecycleScope.launch {
            view.getGLRetroErrors().collect { code ->
                errorFlow.emit(
                    when (code) {
                        GLRetroView.ERROR_LOAD_LIBRARY -> "L’émulateur de cette console ne se charge pas sur ce téléphone."

                        GLRetroView.ERROR_LOAD_GAME ->
                            "Ce jeu ne démarre pas : son fichier est peut-être abîmé. " +
                                "Supprimez-le du téléphone puis téléchargez-le de nouveau."

                        GLRetroView.ERROR_GL_NOT_COMPATIBLE -> "Ce téléphone n’a pas l’accélération graphique nécessaire pour cette console."

                        else -> "L’émulateur s’est arrêté. Votre dernière sauvegarde automatique est conservée."
                    },
                )
            }
        }
    }

    override fun pause() {
        if (paused) return
        paused = true
        // The view's lifecycle owns EGL and native pause/resume. A menu pause only stops frames;
        // stopping EGL a second time can block Android's main thread during a core operation.
        view.renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY
        view.audioEnabled = false
    }

    override fun resume() {
        if (!paused) return
        paused = false
        view.audioEnabled = audioEnabled
        view.renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
    }

    override suspend fun saveState(): ByteArray? = onEmulationThread { runCatching { view.serializeState(false) }.getOrNull() }

    override suspend fun loadState(data: ByteArray): Boolean = onEmulationThread { runCatching { view.unserializeState(data, false) }.getOrDefault(false) }

    override suspend fun saveSram(): ByteArray? = onEmulationThread { runCatching { view.serializeSRAM(false) }.getOrNull() }

    override suspend fun prepareReset() {
        if (restoreOnReset) bootState = saveState()?.takeIf { it.isNotEmpty() }
    }

    override suspend fun reset() = onEmulationThread {
        if (restoreOnReset) {
            // Legacy melonDS caches the address of retro_game_info, which LibretroDroid creates
            // on the stack. retro_reset then dereferences invalid metadata. Restore the initial
            // machine state instead, while retaining the current in-game battery save.
            val initial = checkNotNull(bootState) { "No restart checkpoint" }
            val battery = view.serializeSRAM(false)
            val previous = view.serializeState(false)
            if (!view.unserializeState(initial, false) || (battery.isNotEmpty() && !view.unserializeSRAM(battery, false))) {
                check(view.unserializeState(previous, false)) { "Restart rollback rejected" }
                error("Restart checkpoint rejected")
            }
        } else {
            view.reset(false)
        }
    }

    /** Suspend the caller instead of waiting on LibretroDroid's unbounded CountDownLatch. */
    private suspend fun <T> onEmulationThread(block: () -> T): T = suspendCancellableCoroutine { continuation ->
        view.queueEvent {
            if (continuation.isActive) {
                try {
                    continuation.resume(block())
                } catch (error: Exception) {
                    continuation.resumeWithException(error)
                }
            }
        }
    }

    override fun setFastForward(speed: Int) {
        view.frameSpeed = speed.coerceIn(1, 8)
    }

    override fun setAudio(enabled: Boolean) {
        audioEnabled = enabled
        view.audioEnabled = enabled && !paused
    }

    override fun key(key: PadKey, pressed: Boolean, port: Int) {
        view.sendKeyEvent(if (pressed) KeyEvent.ACTION_DOWN else KeyEvent.ACTION_UP, keyCode(key), port)
    }

    override fun stick(stick: Stick, x: Float, y: Float, port: Int) {
        view.sendMotionEvent(if (stick == Stick.LEFT) GLRetroView.MOTION_SOURCE_ANALOG_LEFT else GLRetroView.MOTION_SOURCE_ANALOG_RIGHT, x, y, port)
    }

    override fun touch(x: Float?, y: Float?) {
        if (x == null || y == null || view.width == 0) {
            LibretroDroid.onTouchEvent(-10f, 10f)
        } else {
            LibretroDroid.onTouchEvent((2f * x / view.width - 1f).coerceIn(-1f, 1f), (2f * y / view.height - 1f).coerceIn(-1f, 1f))
        }
    }

    override fun setViewport(rect: NormRect) {
        view.queueEvent { LibretroDroid.setViewport(rect.left, rect.top, rect.width, rect.height) }
    }

    override fun setRotation(rotation: GameRotation, aspect: Float) {
        view.queueEvent {
            if (NativeDisplay.configure(corePath, rotation.degrees, aspect)) {
                LibretroDroid.refreshAspectRatio()
            } else {
                errorFlow.tryEmit("L’image n’a pas pu être tournée. Réessayez depuis le menu du jeu.")
            }
        }
    }

    override fun setOptions(options: Map<String, String>) {
        view.queueEvent { view.updateVariables(*options.map { (key, value) -> Variable(key, value) }.toTypedArray()) }
    }

    override fun variables(): List<CoreVariable> = runCatching {
        view.getVariables().map { CoreVariable.parse(it.key.orEmpty(), it.value.orEmpty(), it.description) }
    }.getOrDefault(emptyList())

    override fun setCheat(index: Int, enabled: Boolean, code: String) {
        view.queueEvent { runCatching { view.setCheat(index, enabled, code, false) } }
    }

    override fun setControllerType(port: Int, type: Int) {
        view.queueEvent { runCatching { view.setControllerType(port, type) } }
    }

    override val rumble: Flow<Float> = view.getRumbleEvents().map { maxOf(it.strengthWeak, it.strengthStrong) }

    companion object {
        fun shaderConfig(shader: Shader): ShaderConfig = when (shader) {
            Shader.SHARP -> ShaderConfig.Sharp
            Shader.SMOOTH -> ShaderConfig.Default
            Shader.CRT -> ShaderConfig.CRT
            Shader.LCD -> ShaderConfig.LCD
            Shader.UPSCALE -> ShaderConfig.CUT2()
        }

        fun keyCode(key: PadKey): Int = when (key) {
            PadKey.UP -> KeyEvent.KEYCODE_DPAD_UP
            PadKey.DOWN -> KeyEvent.KEYCODE_DPAD_DOWN
            PadKey.LEFT -> KeyEvent.KEYCODE_DPAD_LEFT
            PadKey.RIGHT -> KeyEvent.KEYCODE_DPAD_RIGHT
            PadKey.A -> KeyEvent.KEYCODE_BUTTON_A
            PadKey.B -> KeyEvent.KEYCODE_BUTTON_B
            PadKey.X -> KeyEvent.KEYCODE_BUTTON_X
            PadKey.Y -> KeyEvent.KEYCODE_BUTTON_Y
            PadKey.L -> KeyEvent.KEYCODE_BUTTON_L1
            PadKey.R -> KeyEvent.KEYCODE_BUTTON_R1
            PadKey.L2 -> KeyEvent.KEYCODE_BUTTON_L2
            PadKey.R2 -> KeyEvent.KEYCODE_BUTTON_R2
            PadKey.L3 -> KeyEvent.KEYCODE_BUTTON_THUMBL
            PadKey.R3 -> KeyEvent.KEYCODE_BUTTON_THUMBR
            PadKey.START -> KeyEvent.KEYCODE_BUTTON_START
            PadKey.SELECT -> KeyEvent.KEYCODE_BUTTON_SELECT
        }
    }
}
