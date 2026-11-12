package ovh.verbois.retrohub.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ovh.verbois.retrohub.emulation.DsLayout
import ovh.verbois.retrohub.emulation.GameLayouts
import ovh.verbois.retrohub.emulation.PadKey
import ovh.verbois.retrohub.emulation.PadLayout
import ovh.verbois.retrohub.emulation.TouchPreset
import ovh.verbois.retrohub.ui.play.TouchControls
import ovh.verbois.retrohub.ui.play.padSpec
import ovh.verbois.retrohub.ui.theme.Palette
import ovh.verbois.retrohub.ui.theme.RetroHubTheme

@RunWith(AndroidJUnit4::class)
class TouchControlsTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun touchPresetsKeepLandscapeButtonsVisibleAndSupportTwoFingers() = checkPresets(false, allPads = true)

    @Test fun touchPresetsKeepPortraitButtonsVisibleAndSupportTwoFingers() = checkPresets(true)

    private fun checkPresets(portrait: Boolean, allPads: Boolean = false) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(instrumentation)
        if (portrait) device.setOrientationNatural() else device.setOrientationLandscape()
        var preset by mutableStateOf(TouchPreset.COMFORT)
        var padLayout by mutableStateOf(PadLayout.NINTENDO_DS)
        var scale by mutableStateOf(1f)
        val events = mutableListOf<Pair<PadKey, Boolean>>()
        var stylusTouches = 0
        rule.setContent {
            RetroHubTheme {
                BoxWithConstraints(Modifier.fillMaxSize().background(Palette.SkyLow).testTag("touch-host")) {
                    val density = LocalDensity.current
                    val layout = with(density) {
                        GameLayouts.compute(
                            maxWidth.toPx(),
                            maxHeight.toPx(),
                            GameLayouts.dsAspect(if (portrait) DsLayout.STACKED else DsLayout.SIDE_BY_SIDE),
                            true,
                            controlsUnit = (
                                48.dp *
                                    scale
                                ).toPx(),
                            preset = preset,
                        )
                    }
                    key(preset, scale, padLayout) {
                        TouchControls(
                            padSpec(padLayout).let { if (padLayout == PadLayout.PLAYSTATION) it.copy(leftStick = true) else it }, layout.controls, 1f, scale,
                            onKey = { key, down -> events += key to down }, onStick = { _, _, _ -> },
                            onScreenTouch = { if (it != null) stylusTouches++ }, onHaptic = {},
                            gameBottom = layout.game.bottom, gameLeft = layout.game.left,
                        )
                    }
                }
            }
        }
        for (pad in if (allPads) PadLayout.entries else listOf(PadLayout.NINTENDO_DS)) {
            val spec = padSpec(pad)
            for (choice in TouchPreset.entries) {
                for (size in listOf(1f, 1.35f)) {
                    rule.runOnIdle {
                        preset = choice
                        padLayout = pad
                        scale = size
                        events.clear()
                        stylusTouches = 0
                    }
                    rule.waitForIdle()
                    val host = rule.onNodeWithTag("touch-host").fetchSemanticsNode().boundsInRoot
                    val labels = buildList {
                        addAll(spec.face.map { it.label })
                        addAll(spec.stickButtons.map { it.label })
                        addAll(spec.shoulders.map { it.label })
                        addAll(listOfNotNull(spec.select, spec.start))
                    }
                    val controls = labels.map { label ->
                        rule.onNodeWithTag("touch-key-${label.lowercase()}").assertIsDisplayed().fetchSemanticsNode().boundsInRoot.also { bounds ->
                            val insideX = bounds.left >= host.left - 1 && bounds.right <= host.right + 1
                            val insideY = bounds.top >= host.top - 1 && bounds.bottom <= host.bottom + 1
                            assertTrue("$choice $size $label outside $host: $bounds", insideX && insideY)
                        }
                    }
                    val a = controls[0].center - host.topLeft
                    if (spec.face.size < 2) continue
                    val b = controls[1].center - host.topLeft
                    rule.onNodeWithTag("touch-host").performTouchInput {
                        down(0, a)
                        down(1, b)
                        up(0)
                        up(1)
                    }
                    assertEquals(1, events.count { it == spec.face[0].key to true })
                    assertEquals(1, events.count { it == spec.face[1].key to true })
                    assertEquals(1, events.count { it == spec.face[0].key to false })
                    assertEquals(1, events.count { it == spec.face[1].key to false })
                    assertEquals(0, stylusTouches)
                }
            }
        }
        device.setOrientationLandscape()
    }
}
