package ovh.verbois.retrohub.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import ovh.verbois.retrohub.ui.icons.Lucide
import ovh.verbois.retrohub.ui.theme.Palette

/** Menus use the same A/Enter activation and focus feedback as the rest of the console. */
@Composable
fun ConsoleMenuItem(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    tint: Color = Palette.Text,
    selected: Boolean? = null,
) {
    Row(
        modifier.fillMaxWidth().heightIn(min = 48.dp).semantics { if (selected != null) this.selected = selected }
            .consoleFocus(RoundedCornerShape(12.dp), onClick, focusScale = 1f).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (icon != null) Icon(icon, null, Modifier.size(20.dp), tint = tint)
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, color = tint)
        if (selected == true) Icon(Lucide.Check, null, Modifier.size(18.dp), tint = tint)
    }
}
