package ovh.verbois.retrohub.ui.console.kit

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import ovh.verbois.retrohub.ui.components.ControllerTextField

/** The console text field: A starts typing, B stops; focusing it never opens the keyboard. */
@Composable
fun ConsoleTextField(
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
) = ControllerTextField(value, onValueChange, label, modifier, icon, password, keyboardType, imeAction, onDone, enabled, tag, editRequest)
