package ovh.verbois.retrohub.game

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import ovh.verbois.retrohub.emulation.EmulatorEngine
import ovh.verbois.retrohub.emulation.PadKey
import ovh.verbois.retrohub.emulation.Stick
import kotlin.math.hypot

/**
 * Entre les commandes (tactile, manette, clavier) et le cœur : tir automatique des boutons turbo,
 * zone morte et sensibilité des sticks.
 */
class InputRouter(
    private val engine: EmulatorEngine,
    private val scope: CoroutineScope,
    private val turbo: Set<PadKey>,
    private val deadzone: Float,
    private val sensitivity: Float,
    private val period: Long = 50,
) {
    private val turbos = mutableMapOf<Pair<PadKey, Int>, Job>()
    private val held = mutableSetOf<Pair<PadKey, Int>>()
    private val sticks = mutableSetOf<Pair<Stick, Int>>()

    fun key(key: PadKey, pressed: Boolean, port: Int = 0) {
        val id = key to port
        if (pressed) {
            if (!held.add(id)) return
        } else {
            if (!held.remove(id)) return
        }
        if (key !in turbo) {
            engine.key(key, pressed, port)
            return
        }
        if (pressed) {
            if (turbos[id]?.isActive == true) return
            turbos[id] =
                scope.launch {
                    var down = true
                    while (isActive) {
                        engine.key(key, down, port)
                        down = !down
                        delay(period)
                    }
                }
        } else {
            turbos.remove(id)?.cancel()
            engine.key(key, false, port)
        }
    }

    fun stick(stick: Stick, x: Float, y: Float, port: Int = 0) {
        val (sx, sy) = shape(x, y, deadzone, sensitivity)
        val id = stick to port
        if (sx == 0f && sy == 0f) {
            if (!sticks.remove(id)) return
        } else {
            sticks += id
        }
        engine.stick(stick, sx, sy, port)
    }

    fun releaseAll() {
        turbos.values.forEach { it.cancel() }
        turbos.clear()
        held.forEach { (key, port) -> engine.key(key, false, port) }
        held.clear()
        sticks.forEach { (stick, port) -> engine.stick(stick, 0f, 0f, port) }
        sticks.clear()
    }

    companion object {
        /** Zone morte radiale puis sensibilité ; le résultat reste dans le disque unité. */
        fun shape(x: Float, y: Float, deadzone: Float, sensitivity: Float): Pair<Float, Float> {
            val length = hypot(x, y)
            if (length <= deadzone || length == 0f) return 0f to 0f
            val scaled = ((length - deadzone) / (1f - deadzone) * sensitivity).coerceAtMost(1f)
            return x / length * scaled to y / length * scaled
        }
    }
}
