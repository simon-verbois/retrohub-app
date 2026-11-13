package ovh.verbois.retrohub.ui.components

import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** A shorter final row takes the closest available column instead of trapping focus above it. */
fun gridVerticalTarget(index: Int, count: Int, columns: Int, down: Boolean): Int? {
    if (count <= 0 || columns <= 0 || index !in 0 until count) return null
    val row = index / columns
    val targetRow = row + if (down) 1 else -1
    if (targetRow < 0 || targetRow * columns >= count) return null
    return (targetRow * columns + index % columns).coerceAtMost(count - 1)
}

@Composable
fun gridNavigation(state: LazyGridState, count: Int, columns: Int): (Int) -> Modifier {
    val scope = rememberCoroutineScope()
    val requesters = remember { mutableMapOf<Int, FocusRequester>() }
    return { index ->
        Modifier.focusRequester(requesters.getOrPut(index) { FocusRequester() }).onPreviewKeyEvent { event ->
            val down = when (event.key) {
                Key.DirectionDown -> true
                Key.DirectionUp -> false
                else -> return@onPreviewKeyEvent false
            }
            val next = gridVerticalTarget(index, count, columns, down) ?: return@onPreviewKeyEvent false
            if (event.type == KeyEventType.KeyDown) {
                scope.launch {
                    if (state.layoutInfo.visibleItemsInfo.none { it.index == next }) state.scrollToItem(next)
                    snapshotFlow { state.layoutInfo.visibleItemsInfo.any { it.index == next } }.first { it }
                    requesters[next]?.requestFocus()
                }
            }
            true
        }
    }
}
