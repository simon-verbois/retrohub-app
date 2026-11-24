package ovh.verbois.retrohub.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ovh.verbois.retrohub.ui.theme.Palette

/**
 * A text field for a controller: focusing it never opens the keyboard. A (or a tap) starts typing;
 * B, Back or the keyboard action stops typing and leaves the field focused, with what was typed.
 */
@Composable
fun ControllerTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    password: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Done,
    onDone: () -> Unit = {},
    enabled: Boolean = true,
    tag: String? = null,
    editRequest: Int = 0,
    capitalization: androidx.compose.ui.text.input.KeyboardCapitalization = androidx.compose.ui.text.input.KeyboardCapitalization.None,
    textAlign: androidx.compose.ui.text.style.TextAlign = androidx.compose.ui.text.style.TextAlign.Start,
) {
    var editing by remember { mutableStateOf(false) }
    var typing by remember { mutableStateOf(false) }
    val activity = LocalFocusActivity.current
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    // The text being typed, with the cursor at the end when typing starts.
    var field by remember { mutableStateOf(TextFieldValue(value, TextRange(value.length))) }
    if (field.text != value) field = TextFieldValue(value, TextRange(value.length))
    val outer = remember { FocusRequester() }
    val inner = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val stop = {
        editing = false
        keyboard?.hide()
        runCatching { outer.requestFocus() }
    }
    val finish = {
        stop()
        onDone()
    }
    // « Next » on the keyboard goes on typing in the following field, like on a phone form.
    val next = {
        editing = false
        TypingChain.pending = true
        if (!focusManager.moveFocus(androidx.compose.ui.focus.FocusDirection.Down)) {
            TypingChain.pending = false
            keyboard?.hide()
            runCatching { outer.requestFocus() }
        }
    }
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { if (typing) activity.typing = false } }
    LaunchedEffect(editRequest) { if (editRequest > 0 && enabled) editing = true }
    // Closing the keyboard (Back, Escape) ends typing too, without leaving the screen.
    val density = androidx.compose.ui.platform.LocalDensity.current
    val imeShown = androidx.compose.foundation.layout.WindowInsets.Companion.ime.getBottom(density) > 0
    var imeSeen by remember { mutableStateOf(false) }
    LaunchedEffect(imeShown, editing) {
        if (!editing) {
            imeSeen = false
        } else if (imeShown) {
            imeSeen = true
        } else if (imeSeen) {
            // The keyboard blinks when typing moves to the next field: only a lasting close ends typing.
            kotlinx.coroutines.delay(350)
            stop()
        }
    }
    LaunchedEffect(editing) {
        if (editing) {
            field = field.copy(selection = TextRange(field.text.length))
            withFrameNanos { }
            runCatching { inner.requestFocus() }
            keyboard?.show()
        }
    }
    Box(modifier) {
        FocusFill(
            { editing = true },
            // The tag follows the part that holds the focus: the surface at rest, the text field while typing.
            Modifier.fillMaxWidth().heightIn(min = 56.dp).focusRequester(outer).then(if (tag != null && !editing) Modifier.testTag(tag) else Modifier)
                .onFocusChanged {
                    if (it.isFocused && TypingChain.pending) {
                        TypingChain.pending = false
                        if (enabled) editing = true
                    }
                }
                .semantics { contentDescription = label },
            rest = ConsoleTone.Rest,
            enabled = enabled,
            focusScale = 1.02f,
        ) { focused ->
            Row(
                Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                if (icon != null) Icon(icon, null, Modifier.size(20.dp))
                Column(Modifier.weight(1f)) {
                    if (value.isNotEmpty() ||
                        editing
                    ) {
                        Text(label, style = MaterialTheme.typography.labelSmall, color = LocalContentColor.current.copy(alpha = 0.72f))
                    }
                    if (editing) {
                        BasicTextField(
                            field,
                            {
                                field = it
                                if (it.text != value) onValueChange(it.text)
                            },
                            Modifier.fillMaxWidth().focusRequester(inner).then(if (tag != null) Modifier.testTag(tag) else Modifier)
                                .onFocusChanged {
                                    activity.typing = it.isFocused
                                    if (it.isFocused) {
                                        typing = true
                                    } else if (typing) {
                                        typing = false
                                        editing = false
                                    }
                                }
                                .onPreviewKeyEvent { event ->
                                    if (event.key == Key.Escape || event.key == Key.ButtonB || event.key == Key.Back) {
                                        if (event.type == KeyEventType.KeyUp) stop()
                                        true
                                    } else {
                                        false
                                    }
                                },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.titleMedium.copy(color = LocalContentColor.current, textAlign = textAlign),
                            cursorBrush = SolidColor(if (focused) ConsoleTone.OnFocus else Color.White),
                            visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
                            keyboardOptions = KeyboardOptions(
                                capitalization = capitalization,
                                keyboardType = if (password) KeyboardType.Password else keyboardType,
                                imeAction = imeAction,
                                autoCorrectEnabled = false,
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = { finish() },
                                onGo = { finish() },
                                onNext = { next() },
                                onSearch = { finish() },
                                onSend = { finish() },
                            ),
                        )
                    } else {
                        Text(
                            when {
                                value.isEmpty() -> label
                                password -> "•".repeat(value.length.coerceAtMost(24))
                                else -> value
                            },
                            style = MaterialTheme.typography.titleMedium,
                            color = if (value.isEmpty()) LocalContentColor.current.copy(alpha = 0.72f) else LocalContentColor.current,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (editing) {
                    Box(Modifier.size(8.dp).clip(androidx.compose.foundation.shape.CircleShape).background(Palette.Success))
                }
            }
        }
    }
}

/** Set by « Next » on the keyboard: the field that gets the focus next starts typing at once. */
private object TypingChain {
    var pending = false
}
