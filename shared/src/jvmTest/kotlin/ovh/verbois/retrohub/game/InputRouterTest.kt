@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package ovh.verbois.retrohub.game

import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import ovh.verbois.retrohub.emulation.PadKey
import ovh.verbois.retrohub.emulation.Stick
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class InputRouterTest {
    @Test
    fun triggersOnDifferentPortsAreIndependentAndDuplicateEventsAreIgnored() = runTest {
        val engine = FakeEngine()
        val router = InputRouter(engine, backgroundScope, emptySet(), 0.15f, 1f)
        router.key(PadKey.L2, true, 0)
        router.key(PadKey.L2, true, 1)
        router.key(PadKey.L2, true, 0)
        router.key(PadKey.L2, false, 1)
        assertEquals(listOf(Triple(PadKey.L2, true, 0), Triple(PadKey.L2, true, 1), Triple(PadKey.L2, false, 1)), engine.keys)
    }

    @Test
    fun releaseNeutralizesTurboButtonsRegularButtonsAndSticksOnEveryPort() = runTest {
        val engine = FakeEngine()
        val router = InputRouter(engine, backgroundScope, setOf(PadKey.A), 0.15f, 1f)
        router.key(PadKey.A, true, 1)
        router.key(PadKey.B, true, 0)
        router.stick(Stick.RIGHT, 1f, 0f, 1)
        runCurrent()
        router.releaseAll()
        val count = engine.keys.size
        runCurrent()
        assertEquals(count, engine.keys.size)
        assertTrue(Triple(PadKey.A, false, 1) in engine.keys)
        assertTrue(Triple(PadKey.B, false, 0) in engine.keys)
        assertEquals(Triple(Stick.RIGHT, 0f to 0f, 1), engine.sticks.last())
    }
}
