package ovh.verbois.retrohub.game

import ovh.verbois.retrohub.emulation.PadKey

/** Physical input after console remapping, also observed while the game is paused. */
data class ControllerInput(val keys: Set<PadKey> = emptySet(), val leftX: Float = 0f, val leftY: Float = 0f, val rightX: Float = 0f, val rightY: Float = 0f)
