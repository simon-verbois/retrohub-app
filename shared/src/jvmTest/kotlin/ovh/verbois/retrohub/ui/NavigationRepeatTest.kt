@file:OptIn(kotlin.time.ExperimentalTime::class)

package ovh.verbois.retrohub.ui

import androidx.compose.ui.input.key.Key
import ovh.verbois.retrohub.ui.components.NavigationRepeat
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TestTimeSource

class NavigationRepeatTest {
    @Test fun heldDirectionsAccelerateAndReleaseOrDirectionChangeRespondImmediately() {
        val clock = TestTimeSource()
        val repeat = NavigationRepeat(clock)
        assertTrue(repeat.accept(Key.DirectionDown, true))
        clock += 100.milliseconds
        assertFalse(repeat.accept(Key.DirectionDown, true))
        clock += 40.milliseconds
        assertTrue(repeat.accept(Key.DirectionDown, true))
        clock += 800.milliseconds
        assertTrue(repeat.accept(Key.DirectionDown, true))
        clock += 90.milliseconds
        assertTrue(repeat.accept(Key.DirectionDown, true))
        assertTrue(repeat.accept(Key.DirectionDown, false))
        assertTrue(repeat.accept(Key.DirectionDown, true))
        assertTrue(repeat.accept(Key.DirectionUp, true))
        clock += 1900.milliseconds
        assertTrue(repeat.accept(Key.DirectionUp, true))
        clock += 55.milliseconds
        assertTrue(repeat.accept(Key.DirectionUp, true))
    }
}
