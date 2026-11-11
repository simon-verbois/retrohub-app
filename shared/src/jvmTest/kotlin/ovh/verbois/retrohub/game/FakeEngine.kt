package ovh.verbois.retrohub.game

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import ovh.verbois.retrohub.emulation.CoreVariable
import ovh.verbois.retrohub.emulation.EmulatorEngine
import ovh.verbois.retrohub.emulation.NormRect
import ovh.verbois.retrohub.emulation.PadKey
import ovh.verbois.retrohub.emulation.Stick

class FakeEngine : EmulatorEngine {
    override val frames = MutableStateFlow(1L)
    override val errors = emptyFlow<String>()
    override val rumble = emptyFlow<Float>()
    var snapshot = byteArrayOf(1, 2, 3)
    var sram = byteArrayOf(4, 5)
    var loaded = 0
    var beforeLoad: suspend () -> Unit = {}
    val keys = mutableListOf<Triple<PadKey, Boolean, Int>>()
    val sticks = mutableListOf<Triple<Stick, Pair<Float, Float>, Int>>()
    override fun pause() = Unit
    override fun resume() = Unit
    override suspend fun saveState() = snapshot
    override suspend fun saveSram() = sram
    override suspend fun loadState(data: ByteArray): Boolean {
        beforeLoad()
        snapshot = data.copyOf()
        loaded++
        return true
    }
    override fun reset() = Unit
    override fun setFastForward(speed: Int) = Unit
    override fun setAudio(enabled: Boolean) = Unit
    override fun key(key: PadKey, pressed: Boolean, port: Int) {
        keys += Triple(key, pressed, port)
    }
    override fun stick(stick: Stick, x: Float, y: Float, port: Int) {
        sticks += Triple(stick, x to y, port)
    }
    override fun touch(x: Float?, y: Float?) = Unit
    override fun setViewport(rect: NormRect) = Unit
    override fun setOptions(options: Map<String, String>) = Unit
    override fun variables() = emptyList<CoreVariable>()
    override fun setCheat(index: Int, enabled: Boolean, code: String) = Unit
    override fun setControllerType(port: Int, type: Int) = Unit
}
