package cli.shady.terminal

import cli.shady.commands.config.ColorRules
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

class AnsiOutputColorizerTest {
    @Test
    fun `filetype color overrides command color`() {
        val colorizer = colorizer(
            ColorRules(commands = mapOf("ls" to "magenta"), fileTypes = mapOf("kt" to "cyan")),
        )
        colorizer.setActiveCommand("ls -la")

        val output = colorizer.transform("plain README.kt end")

        assertContains(output, "\u001B[35mplain ")
        assertContains(output, "\u001B[36mREADME.kt\u001B[0m")
    }

    @Test
    fun `reloads rules when a command starts`() {
        var rules = ColorRules(commands = mapOf("echo" to "red"))
        val colorizer = AnsiOutputColorizer({ rules }, enabled = true)
        colorizer.setActiveCommand("echo one")
        assertContains(colorizer.transform("one"), "\u001B[31m")

        rules = ColorRules(commands = mapOf("echo" to "green"))
        colorizer.setActiveCommand("echo two")
        assertContains(colorizer.transform("two"), "\u001B[32m")
    }

    @Test
    fun `preserves native ansi and alternate screen output`() {
        val colorizer = colorizer(ColorRules(commands = mapOf("top" to "red")))
        colorizer.setActiveCommand("top")

        assertEquals("\u001B[32mnative\u001B[0m", colorizer.transform("\u001B[32mnative\u001B[0m"))
        assertEquals("\u001B[?1049hfull screen", colorizer.transform("\u001B[?1049hfull screen"))
    }

    @Test
    fun `handles ansi sequences split between chunks`() {
        val colorizer = colorizer(ColorRules(commands = mapOf("echo" to "red")))
        colorizer.setActiveCommand("echo")

        assertEquals("", colorizer.transform("\u001B[3"))
        assertEquals("\u001B[32mgreen", colorizer.transform("2mgreen"))
    }

    @Test
    fun `uses command behind sudo`() {
        val colorizer = colorizer(ColorRules(commands = mapOf("ls" to "yellow")))
        colorizer.setActiveCommand("sudo -n ls")

        assertContains(colorizer.transform("file"), "\u001B[33mfile")
    }

    private fun colorizer(rules: ColorRules) = AnsiOutputColorizer({ rules }, enabled = true)
}
