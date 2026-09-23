package cli.shady.ui.theme

import kotlin.test.Test
import kotlin.test.assertEquals

class ShadyTerminalSettingsTest {
    @Test
    fun `default style uses shady colors for cursor inversion`() {
        val style = ShadyTerminalSettings().defaultStyle

        assertEquals(0xFF, style.foreground?.toColor()?.red)
        assertEquals(0xFF, style.foreground?.toColor()?.green)
        assertEquals(0xFF, style.foreground?.toColor()?.blue)
        assertEquals(0x07, style.background?.toColor()?.red)
        assertEquals(0x11, style.background?.toColor()?.green)
        assertEquals(0x0C, style.background?.toColor()?.blue)
    }
}
