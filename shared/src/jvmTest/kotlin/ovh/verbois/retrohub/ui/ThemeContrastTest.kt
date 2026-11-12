package ovh.verbois.retrohub.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import ovh.verbois.retrohub.ui.theme.DarkSky
import ovh.verbois.retrohub.ui.theme.LightSky
import kotlin.test.Test
import kotlin.test.assertTrue

class ThemeContrastTest {
    @Test
    fun normalAndSecondaryTextStayReadableOnBothColoredSkiesAndPanels() {
        for (scheme in listOf(LightSky, DarkSky)) {
            val peakGlow = scheme.glowB.compositeOver(scheme.glowA.compositeOver(scheme.glowTop))
            val backgrounds = listOf(scheme.skyTop, scheme.skyMid, scheme.skyLow, peakGlow, scheme.deep, scheme.field.compositeOver(peakGlow))
            for (text in listOf(scheme.text, scheme.textDim, scheme.textFaint)) {
                assertTrue(text.alpha == 1f, "Secondary text must remain opaque")
                backgrounds.forEach { assertContrast(text, it, 4.5f) }
            }
        }
    }

    @Test
    fun glossyButtonsRetainContrastThroughFocusHighlightsAndPrimaryGradients() {
        for (scheme in listOf(LightSky, DarkSky)) {
            val colors = listOf(scheme.buttonTop, scheme.buttonBottom, scheme.buttonFocusTop, scheme.buttonFocusBottom)
            for (background in colors) {
                val reflection = Color.White.copy(alpha = 0.08f).compositeOver(background)
                val textured = Color.White.copy(alpha = 0.012f).compositeOver(reflection)
                val focused = scheme.buttonFocusTop.copy(alpha = 0.22f).compositeOver(textured)
                assertContrast(scheme.text, textured, 4.5f)
                assertContrast(scheme.text, focused, 4.5f)
            }
            listOf(scheme.primaryTop, scheme.primaryBottom).forEach { assertContrast(scheme.onAccent, it, 7f) }
        }
    }

    private fun assertContrast(text: Color, background: Color, minimum: Float) {
        val a = text.luminance()
        val b = background.luminance()
        val ratio = (maxOf(a, b) + 0.05f) / (minOf(a, b) + 0.05f)
        assertTrue(ratio >= minimum, "$text on $background: $ratio must reach $minimum")
    }
}
