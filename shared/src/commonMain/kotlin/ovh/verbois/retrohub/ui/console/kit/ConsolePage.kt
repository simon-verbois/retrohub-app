package ovh.verbois.retrohub.ui.console.kit

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ovh.verbois.retrohub.ui.components.LocalScreenActive

/** Height reserved at the bottom of every console screen for the hint bar. */
val HintBarHeight = 44.dp

/** Horizontal margin of console pages. */
val PageInset = 40.dp

/**
 * A full-screen console page: a title, optional LB/RB tabs, the content and room for the hint bar.
 * Back is the B button (and the « Retour » hint), so there is no back arrow.
 */
@Composable
fun ConsolePage(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    tabs: List<String> = emptyList(),
    tab: Int = 0,
    onTab: (Int) -> Unit = {},
    trailing: @Composable RowScope.() -> Unit = {},
    content: @Composable BoxScope.() -> Unit,
) {
    val active = LocalScreenActive.current
    val currentTab by rememberUpdatedState(tab)
    val change by rememberUpdatedState(onTab)
    Column(
        modifier.fillMaxSize().onPreviewKeyEvent { event ->
            if (!active || tabs.size < 2 || (event.key != Key.ButtonL1 && event.key != Key.ButtonR1)) return@onPreviewKeyEvent false
            if (event.type == KeyEventType.KeyUp) {
                val step = if (event.key == Key.ButtonR1) 1 else -1
                change((currentTab + step).coerceIn(0, tabs.lastIndex))
            }
            true
        },
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 54.dp).padding(horizontal = PageInset).padding(top = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    Modifier.semantics { heading() },
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = dim, maxLines = 1)
            }
            trailing()
        }
        val tabFocus = remember(tabs.size) { List(tabs.size) { FocusRequester() } }
        val tabsFocused = remember { mutableStateOf(false) }
        if (tabs.size > 1) {
            Tabs(tabs, tab, onTab, Modifier.padding(horizontal = PageInset - 12.dp).onFocusChanged { tabsFocused.value = it.hasFocus }, tabFocus)
        }
        Box(
            Modifier.weight(1f).fillMaxWidth().padding(bottom = HintBarHeight).then(
                if (tabs.size > 1) {
                    // Up from the content returns to the current tab, never to a neighbour (which would switch).
                    Modifier.focusProperties {
                        onExit = {
                            if (requestedFocusDirection == FocusDirection.Up) {
                                cancelFocusChange()
                                runCatching { tabFocus[currentTab].requestFocus() }
                            }
                        }
                    }.focusGroup()
                } else {
                    Modifier
                },
            ),
        ) {
            CompositionLocalProvider(LocalTabsFocused provides tabsFocused, LocalCurrentTab provides tabFocus.getOrNull(tab)) { content() }
        }
    }
}

/**
 * LB/RB tabs, PS5 style: the current tab is bold white; a focused tab turns white like any control and
 * switches the content at once, without a press.
 */
@Composable
fun Tabs(tabs: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier, focus: List<FocusRequester> = emptyList()) {
    val select by rememberUpdatedState(onSelect)
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        PadGlyph(Pad.LB_RB, Modifier.padding(end = 6.dp))
        tabs.forEachIndexed { index, label ->
            val chosen = index == selected
            FocusSurface(
                { select(index) },
                Modifier.then(focus.getOrNull(index)?.let { Modifier.focusRequester(it) } ?: Modifier).semantics { this.selected = chosen },
                shape = PillShape,
                tone = Tone.QUIET,
                role = Role.Tab,
                focusScale = 1.04f,
                onFocus = { if (it && index != selected) select(index) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = if (chosen) FontWeight.Bold else FontWeight.Normal),
                    color = if (chosen) LocalContentColor.current else LocalContentColor.current.copy(alpha = 0.62f),
                    maxLines = 1,
                )
            }
        }
    }
}
