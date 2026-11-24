package ovh.verbois.retrohub.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import ovh.verbois.retrohub.core.formatBytes
import ovh.verbois.retrohub.library.StorageUsage
import ovh.verbois.retrohub.ui.icons.Lucide
import ovh.verbois.retrohub.ui.theme.Palette

@Composable
fun StorageSummary(usage: StorageUsage) {
    GlassPanel(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Lucide.HardDrive, null, Modifier.size(22.dp), tint = Palette.Text)
                Text("${formatBytes(usage.total)} utilisés par RetroHub", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                Text("${formatBytes(usage.free)} libres", style = MaterialTheme.typography.bodyMedium, color = Palette.TextDim)
            }
            val all = (usage.total + usage.free).coerceAtLeast(1)
            Row(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(50)).background(Palette.Line)) {
                if (usage.roms > 0) Box(Modifier.weight(usage.roms.toFloat() / all).height(10.dp).background(Palette.PadX))
                if (usage.saves > 0) Box(Modifier.weight(usage.saves.toFloat() / all).height(10.dp).background(Palette.PadY))
                if (usage.system > 0) Box(Modifier.weight(usage.system.toFloat() / all).height(10.dp).background(Palette.Success))
                Box(Modifier.weight((usage.free.toFloat() / all).coerceAtLeast(0.0001f)).height(10.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                Legend(Palette.PadX, "Jeux ${formatBytes(usage.roms)}")
                Legend(Palette.PadY, "Sauvegardes ${formatBytes(usage.saves)}")
                Legend(Palette.Success, "Fichiers système ${formatBytes(usage.system)}")
            }
        }
    }
}

@Composable
private fun Legend(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(8.dp).clip(RoundedCornerShape(50)).background(color))
        Text(text, style = MaterialTheme.typography.bodySmall)
    }
}
