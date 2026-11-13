package ovh.verbois.retrohub.ui.icons

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import org.jetbrains.compose.resources.vectorResource
import ovh.verbois.retrohub.resources.Res
import ovh.verbois.retrohub.resources.console_3ds
import ovh.verbois.retrohub.resources.console_atari2600
import ovh.verbois.retrohub.resources.console_dreamcast
import ovh.verbois.retrohub.resources.console_gamecube
import ovh.verbois.retrohub.resources.console_gb
import ovh.verbois.retrohub.resources.console_gba
import ovh.verbois.retrohub.resources.console_gbc
import ovh.verbois.retrohub.resources.console_mastersystem
import ovh.verbois.retrohub.resources.console_megadrive
import ovh.verbois.retrohub.resources.console_n64
import ovh.verbois.retrohub.resources.console_nds
import ovh.verbois.retrohub.resources.console_nes
import ovh.verbois.retrohub.resources.console_ps2
import ovh.verbois.retrohub.resources.console_psx
import ovh.verbois.retrohub.resources.console_snes

/** Console silhouettes from RetroArch Monochrome (CC BY 4.0); attribution in docs/licenses.md. */
object ConsoleIcons {
    @Composable
    fun forConsole(id: String): ImageVector {
        val resource = when (id) {
            "atari2600" -> Res.drawable.console_atari2600
            "nes" -> Res.drawable.console_nes
            "snes" -> Res.drawable.console_snes
            "gb" -> Res.drawable.console_gb
            "gbc" -> Res.drawable.console_gbc
            "gba" -> Res.drawable.console_gba
            "mastersystem" -> Res.drawable.console_mastersystem
            "megadrive" -> Res.drawable.console_megadrive
            "psx" -> Res.drawable.console_psx
            "ps2" -> Res.drawable.console_ps2
            "nds" -> Res.drawable.console_nds
            "3ds" -> Res.drawable.console_3ds
            "n64" -> Res.drawable.console_n64
            "gamecube" -> Res.drawable.console_gamecube
            "dreamcast" -> Res.drawable.console_dreamcast
            else -> return Lucide.Gamepad2
        }
        return vectorResource(resource)
    }
}
