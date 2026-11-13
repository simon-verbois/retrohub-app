package ovh.verbois.retrohub.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.*
import kotlin.time.TimeSource

/** Repeated pad directions start deliberately, then accelerate while remaining interruptible. */
class NavigationRepeat {
    private var held: Key? = null
    private var started = TimeSource.Monotonic.markNow()
    private var last = started

    fun accept(key: Key, down: Boolean): Boolean {
        if (!down) {
            if (held == key) held = null
            return true
        }
        if (held != key) {
            held = key
            started = TimeSource.Monotonic.markNow()
            last = started
            return true
        }
        val interval = when {
            started.elapsedNow().inWholeMilliseconds < 1000 -> 280
            started.elapsedNow().inWholeMilliseconds < 2200 -> 160
            else -> 90
        }
        if (last.elapsedNow().inWholeMilliseconds < interval) return false
        last = TimeSource.Monotonic.markNow()
        return true
    }
}

@Composable
fun Modifier.progressiveNavigation(): Modifier {
    val repeat = remember { NavigationRepeat() }
    return onPreviewKeyEvent { event ->
        event.key in setOf(Key.DirectionLeft, Key.DirectionRight, Key.DirectionUp, Key.DirectionDown) &&
            !repeat.accept(event.key, event.type == KeyEventType.KeyDown)
    }
}
