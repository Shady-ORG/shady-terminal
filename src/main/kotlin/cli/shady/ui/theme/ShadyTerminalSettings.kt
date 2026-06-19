package cli.shady.ui.theme

import com.jediterm.terminal.HyperlinkStyle
import com.jediterm.terminal.TerminalColor
import com.jediterm.terminal.TextStyle
import com.jediterm.terminal.ui.settings.DefaultSettingsProvider
import java.awt.Font

class ShadyTerminalSettings : DefaultSettingsProvider() {
    override fun getTerminalFont(): Font = Font(Font.MONOSPACED, Font.PLAIN, TERMINAL_FONT_SIZE.toInt())

    override fun getTerminalFontSize(): Float = TERMINAL_FONT_SIZE

    override fun getDefaultForeground(): TerminalColor = ShadyTerminalAwtPalette.Foreground.terminalColor()

    override fun getDefaultBackground(): TerminalColor = ShadyTerminalAwtPalette.Background.terminalColor()

    override fun getSelectionColor(): TextStyle = TextStyle(
        ShadyTerminalAwtPalette.SelectionForeground.terminalColor(),
        ShadyTerminalAwtPalette.SelectionBackground.terminalColor(),
    )

    override fun getFoundPatternColor(): TextStyle = TextStyle(
        ShadyTerminalAwtPalette.FoundForeground.terminalColor(),
        ShadyTerminalAwtPalette.FoundBackground.terminalColor(),
    )

    override fun getHyperlinkColor(): TextStyle = TextStyle(
        ShadyTerminalAwtPalette.Hyperlink.terminalColor(),
        ShadyTerminalAwtPalette.Background.terminalColor(),
    )

    override fun getHyperlinkHighlightingMode(): HyperlinkStyle.HighlightMode =
        HyperlinkStyle.HighlightMode.HOVER

    override fun copyOnSelect(): Boolean = false

    override fun useAntialiasing(): Boolean = true

    override fun audibleBell(): Boolean = false

    override fun enableMouseReporting(): Boolean = true

    override fun scrollToBottomOnTyping(): Boolean = true

    override fun getBufferMaxLinesCount(): Int = TERMINAL_SCROLLBACK_LINES

    override fun caretBlinkingMs(): Int = CARET_BLINK_MS

    private fun java.awt.Color.terminalColor(): TerminalColor = TerminalColor.rgb(red, green, blue)

    private companion object {
        const val TERMINAL_FONT_SIZE = 14f
        const val TERMINAL_SCROLLBACK_LINES = 20_000
        const val CARET_BLINK_MS = 530
    }
}

object ShadyTerminalAwtPalette {
    val Background = java.awt.Color(0x07, 0x11, 0x0C)
    val Foreground = java.awt.Color(0xD8, 0xE3, 0xE8)
    val SelectionBackground = java.awt.Color(0x31, 0x54, 0x49)
    val SelectionForeground = java.awt.Color(0xFF, 0xFF, 0xFF)
    val FoundBackground = java.awt.Color(0xD6, 0xB1, 0x5A)
    val FoundForeground = java.awt.Color(0x07, 0x11, 0x0C)
    val Hyperlink = java.awt.Color(0x5C, 0xB7, 0xFF)
}
