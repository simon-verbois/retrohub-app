package ovh.verbois.retrohub.ui.console.kit

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import ovh.verbois.retrohub.ui.components.AutoFocus

/**
 * Remembers which control of a page had the focus, across navigation (the page state is saved by the
 * shell), so Back returns exactly where the player was.
 */
class FocusMemory internal constructor(private val saved: MutableState<String?>) {
    private val requesters = mutableMapOf<String, FocusRequester>()

    /** The last focused key, or null on a first visit. */
    val key: String? get() = saved.value

    fun requester(key: String): FocusRequester = requesters.getOrPut(key) { FocusRequester() }

    fun remember(key: String) {
        saved.value = key
    }

    fun forget() {
        saved.value = null
    }

    /** Where focus goes when the page opens: the remembered control, else [fallback]. */
    fun target(fallback: String): FocusRequester = requester(saved.value ?: fallback)
}

@Composable
fun rememberFocusMemory(): FocusMemory {
    val saved = rememberSaveable { mutableStateOf<String?>(null) }
    return remember(saved) { FocusMemory(saved) }
}

/** Registers a control in [memory] under [key]. */
fun Modifier.remembered(memory: FocusMemory, key: String): Modifier =
    focusRequester(memory.requester(key)).onFocusChanged { if (it.isFocused) memory.remember(key) }

/** Gives the focus to the remembered control (or [fallback]) when navigating with keys. */
@Composable
fun RestoreFocus(memory: FocusMemory, fallback: String) {
    // While the player browses the page tabs, new content never pulls the focus away from them.
    AutoFocus(memory.target(fallback), enabled = !LocalTabsFocused.current.value)
}

/** Whether the tabs of the current [ConsolePage] hold the focus. */
val LocalTabsFocused = androidx.compose.runtime.staticCompositionLocalOf<androidx.compose.runtime.State<Boolean>> { mutableStateOf(false) }

/** The current tab of the [ConsolePage], for content whose own navigation needs to go up to it (grids). */
val LocalCurrentTab = androidx.compose.runtime.staticCompositionLocalOf<FocusRequester?> { null }
