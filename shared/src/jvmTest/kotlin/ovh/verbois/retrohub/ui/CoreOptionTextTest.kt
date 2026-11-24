package ovh.verbois.retrohub.ui

import ovh.verbois.retrohub.ui.play.CoreOptionText
import kotlin.test.Test
import kotlin.test.assertEquals

class CoreOptionTextTest {
    @Test
    fun knownCoreLabelsAndValuesAreInFrench() {
        assertEquals("Format de l’image", CoreOptionText.label("fceumm_aspect", "Aspect Ratio"))
        assertEquals("Filtre NTSC", CoreOptionText.label("fceumm_ntsc_filter", "NTSC Filter"))
        assertEquals("Palette de couleurs", CoreOptionText.label("fceumm_palette", "Color Palette"))
        assertEquals("Désactivé", CoreOptionText.value("disabled"))
        assertEquals("Par défaut", CoreOptionText.value("default"))
        assertEquals("Activé", CoreOptionText.value("ON"))
    }

    @Test
    fun unknownTextIsMadeReadableWithoutBeingInvented() {
        assertEquals("Some new option", CoreOptionText.label("fceumm_some_new_option", ""))
        assertEquals("Custom thing", CoreOptionText.label("x", "custom_thing"))
        assertEquals("4:3", CoreOptionText.value("4:3"))
        assertEquals("2x native", CoreOptionText.value("2x_native").lowercase())
    }
}
