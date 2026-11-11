package ovh.verbois.retrohub.game

import android.graphics.RectF
import android.view.KeyEvent
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.swordfish.libretrodroid.GLRetroView
import com.swordfish.libretrodroid.LibretroDroid
import com.swordfish.libretrodroid.ShaderConfig
import com.swordfish.libretrodroid.Variable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ovh.verbois.retrohub.emulation.CoreVariable
import ovh.verbois.retrohub.emulation.EmulatorEngine
import ovh.verbois.retrohub.emulation.NormRect
import ovh.verbois.retrohub.emulation.PadKey
import ovh.verbois.retrohub.emulation.Shader
import ovh.verbois.retrohub.emulation.Stick

/** Le moteur LibretroDroid derrière l’interface partagée [EmulatorEngine]. */
class LibretroEngine(private val view: GLRetroView) : EmulatorEngine {
    private val frameCount = MutableStateFlow(0L)
    override val frames: StateFlow<Long> = frameCount
    private val errorFlow = MutableSharedFlow<String>(extraBufferCapacity = 4)
    override val errors: SharedFlow<String> = errorFlow
    private var paused = false

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
        view.onPause()
        runCatching { LibretroDroid.pause() }
    }

    override fun resume() {
        if (!paused) return
        paused = false
        runCatching { LibretroDroid.resume() }
        view.onResume()
    }

    override suspend fun saveState(): ByteArray? = withContext(Dispatchers.IO) { runCatching { view.serializeState() }.getOrNull() }

    override suspend fun loadState(data: ByteArray): Boolean = withContext(Dispatchers.IO) { runCatching { view.unserializeState(data) }.getOrDefault(false) }

    override suspend fun saveSram(): ByteArray? = withContext(Dispatchers.IO) { runCatching { view.serializeSRAM() }.getOrNull() }

    override fun reset() {
        view.reset()
    }

    override fun setFastForward(speed: Int) {
        view.frameSpeed = speed.coerceIn(1, 8)
    }

    override fun setAudio(enabled: Boolean) {
        view.audioEnabled = enabled
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
        view.viewport = RectF(rect.left, rect.top, rect.right, rect.bottom)
    }

    override fun setOptions(options: Map<String, String>) {
        view.updateVariables(*options.map { (key, value) -> Variable(key, value) }.toTypedArray())
    }

    override fun variables(): List<CoreVariable> = runCatching {
        view.getVariables().map { CoreVariable.parse(it.key.orEmpty(), it.value.orEmpty(), it.description) }
    }.getOrDefault(emptyList())

    override fun setCheat(index: Int, enabled: Boolean, code: String) {
        runCatching { view.setCheat(index, enabled, code) }
    }

    override fun setControllerType(port: Int, type: Int) {
        runCatching { view.setControllerType(port, type) }
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
