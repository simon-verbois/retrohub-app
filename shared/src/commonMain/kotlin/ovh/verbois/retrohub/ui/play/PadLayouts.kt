package ovh.verbois.retrohub.ui.play

import androidx.compose.ui.graphics.Color
import ovh.verbois.retrohub.emulation.PadKey
import ovh.verbois.retrohub.emulation.PadLayout
import ovh.verbois.retrohub.emulation.Stick
import ovh.verbois.retrohub.ui.theme.Palette

/** Un bouton de façade : touche libretro envoyée, libellé de la manette d’origine, position. */
data class FaceButton(
    val key: PadKey,
    val label: String,
    /** Position dans le groupe, de -1 à 1 (x vers la droite, y vers le bas). */
    val x: Float,
    val y: Float,
    val color: Color = Color.White,
    val big: Boolean = false,
)

/** Boutons C de la N64 et C-stick de la GameCube : directions du stick droit. */
data class StickButton(val stick: Stick, val dx: Float, val dy: Float, val label: String, val x: Float, val y: Float, val color: Color)

data class Shoulder(val key: PadKey, val label: String, val left: Boolean, val row: Int = 0)

/** Commandes tactiles d’une console, fidèles à sa manette (positions libretro, voir docs/cores.md). */
data class PadSpec(
    val dpad: Boolean = true,
    val leftStick: Boolean = false,
    val rightStick: Boolean = false,
    val face: List<FaceButton>,
    val stickButtons: List<StickButton> = emptyList(),
    val shoulders: List<Shoulder> = emptyList(),
    val select: String? = "Select",
    val start: String? = "Start",
)

private val Ps = Color(0xFFB9C3FF)

fun padSpec(layout: PadLayout): PadSpec = when (layout) {
    PadLayout.NES, PadLayout.GAME_BOY ->
        PadSpec(
            face = listOf(FaceButton(PadKey.B, "B", -0.55f, 0.3f, Palette.PadB), FaceButton(PadKey.A, "A", 0.55f, -0.3f, Palette.PadB)),
        )

    PadLayout.GBA ->
        PadSpec(
            face = listOf(FaceButton(PadKey.B, "B", -0.55f, 0.3f), FaceButton(PadKey.A, "A", 0.55f, -0.3f)),
            shoulders = listOf(Shoulder(PadKey.L, "L", true), Shoulder(PadKey.R, "R", false)),
        )

    PadLayout.SNES, PadLayout.NINTENDO_DS ->
        PadSpec(
            face =
            listOf(
                FaceButton(PadKey.X, "X", 0f, -0.7f, Palette.PadX),
                FaceButton(PadKey.A, "A", 0.7f, 0f, Palette.PadB),
                FaceButton(PadKey.B, "B", 0f, 0.7f, Palette.PadY),
                FaceButton(PadKey.Y, "Y", -0.7f, 0f, Palette.PadA),
            ),
            shoulders = listOf(Shoulder(PadKey.L, "L", true), Shoulder(PadKey.R, "R", false)),
        )

    PadLayout.NINTENDO_3DS -> padSpec(PadLayout.NINTENDO_DS).copy(
        leftStick = true,
        shoulders = listOf(
            Shoulder(PadKey.L, "L", true),
            Shoulder(PadKey.R, "R", false),
            Shoulder(PadKey.L2, "ZL", true, 1),
            Shoulder(PadKey.R2, "ZR", false, 1),
        ),
    )

    PadLayout.PLAYSTATION_2 -> padSpec(PadLayout.PLAYSTATION).copy(leftStick = true, rightStick = true)

    PadLayout.MASTER_SYSTEM ->
        PadSpec(
            face = listOf(FaceButton(PadKey.B, "1", -0.5f, 0.15f), FaceButton(PadKey.A, "2", 0.5f, -0.15f)),
            select = null,
            start = "Pause",
        )

    PadLayout.MEGA_DRIVE ->
        PadSpec(
            face = listOf(
                FaceButton(PadKey.Y, "A", -0.78f, 0.32f),
                FaceButton(PadKey.B, "B", 0f, 0.05f),
                FaceButton(PadKey.A, "C", 0.78f, -0.22f),
            ),
            select = "Mode",
        )

    PadLayout.PLAYSTATION ->
        PadSpec(
            face =
            listOf(
                FaceButton(PadKey.X, "△", 0f, -0.7f, Color(0xFF5FD6B4)),
                FaceButton(PadKey.A, "○", 0.7f, 0f, Color(0xFFFF7A8A)),
                FaceButton(PadKey.B, "✕", 0f, 0.7f, Ps),
                FaceButton(PadKey.Y, "□", -0.7f, 0f, Color(0xFFF2A6D8)),
            ),
            shoulders =
            listOf(
                Shoulder(PadKey.L, "L1", true),
                Shoulder(PadKey.L2, "L2", true, 1),
                Shoulder(PadKey.R, "R1", false),
                Shoulder(PadKey.R2, "R2", false, 1),
            ),
        )

    PadLayout.NINTENDO_64 ->
        PadSpec(
            dpad = false,
            leftStick = true,
            face = listOf(
                FaceButton(PadKey.B, "A", -0.35f, 0.55f, Color(0xFF6C8CFF), big = true),
                FaceButton(PadKey.Y, "B", -0.95f, 0.05f, Palette.Success),
            ),
            stickButtons =
            listOf(
                StickButton(Stick.RIGHT, 0f, -1f, "▲", 0.55f, -0.75f, Palette.Warning),
                StickButton(Stick.RIGHT, -1f, 0f, "◀", 0.05f, -0.25f, Palette.Warning),
                StickButton(Stick.RIGHT, 1f, 0f, "▶", 1.05f, -0.25f, Palette.Warning),
                StickButton(Stick.RIGHT, 0f, 1f, "▼", 0.55f, 0.25f, Palette.Warning),
            ),
            shoulders = listOf(Shoulder(PadKey.L2, "Z", true), Shoulder(PadKey.L, "L", true, 1), Shoulder(PadKey.R, "R", false)),
            select = null,
        )

    PadLayout.ATARI_2600 ->
        PadSpec(face = listOf(FaceButton(PadKey.B, "Feu", 0f, 0f, Palette.PadB, big = true)), select = "Select", start = "Reset")

    PadLayout.GAMECUBE ->
        PadSpec(
            dpad = false,
            leftStick = true,
            face =
            listOf(
                FaceButton(PadKey.A, "A", 0f, 0.1f, Color(0xFF3FD48A), big = true),
                FaceButton(PadKey.B, "B", -0.8f, 0.6f, Palette.PadB),
                FaceButton(PadKey.X, "X", 0.85f, -0.25f),
                FaceButton(PadKey.Y, "Y", -0.15f, -0.85f),
            ),
            stickButtons =
            listOf(
                StickButton(Stick.RIGHT, 0f, -1f, "C▲", -0.95f, -0.85f, Palette.Warning),
                StickButton(Stick.RIGHT, 0f, 1f, "C▼", -0.95f, -0.25f, Palette.Warning),
            ),
            shoulders = listOf(Shoulder(PadKey.L2, "L", true), Shoulder(PadKey.R2, "R", false), Shoulder(PadKey.R, "Z", false, 1)),
            select = null,
        )

    PadLayout.DREAMCAST ->
        PadSpec(
            dpad = false,
            leftStick = true,
            face =
            listOf(
                FaceButton(PadKey.X, "Y", 0f, -0.7f, Palette.PadY),
                FaceButton(PadKey.A, "B", 0.7f, 0f, Palette.PadB),
                FaceButton(PadKey.B, "A", 0f, 0.7f, Palette.PadA),
                FaceButton(PadKey.Y, "X", -0.7f, 0f, Palette.PadX),
            ),
            shoulders = listOf(Shoulder(PadKey.L2, "L", true), Shoulder(PadKey.R2, "R", false)),
            select = null,
        )
}
