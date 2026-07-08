package cli.shady.terminal

import cli.shady.commands.AliasEntry
import cli.shady.commands.config.ShadyConfig
import cli.shady.commands.config.TerminalConfig
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertEquals
import java.nio.file.Files

class ZshIntegrationScriptTest {
    @Test
    fun `observes every command and removes redundant shady prefixes`() {
        val script = ZshIntegrationScript(ShadyConfig(), emptyList()).render()

        assertContains(script, "_shady_preexec")
        assertContains(script, "while [[ \"${'$'}input\" == 'shady shady '* ]]")
        assertContains(script, "BUFFER=\"${'$'}without_prefix\"")
        assertContains(script, "start|help|alias|gdir|prehook")
        assertContains(script, "config|update)")
        assertContains(script, "gdir()")
        assertContains(script, "a terminal session is already running")
    }

    @Test
    fun `provides native and shady completion`() {
        val script = ZshIntegrationScript(ShadyConfig(), emptyList()).render()

        assertContains(script, "autoload -Uz compinit")
        assertContains(script, "compdef _shady shady")
        assertContains(script, "compdef _shady_gdir gdir")
        assertContains(script, "security.alwaysRequestSudo")
        assertContains(script, "_shady_cc")
        assertContains(script, "_arguments '*:argument:_files'")
    }

    @Test
    fun `uses native username unless override is enabled`() {
        val native = ZshIntegrationScript(ShadyConfig(), emptyList()).render()
        val overridden = ZshIntegrationScript(
            ShadyConfig(
                terminal = TerminalConfig(usernameOverrideEnabled = true, username = "neo"),
            ),
            emptyList(),
        ).render()

        assertContains(native, "%n@%~")
        assertFalse(native.contains("neo@%~"))
        assertContains(overridden, "neo@%~")
    }

    @Test
    fun `installs configured aliases with shell safe quoting`() {
        val script = ZshIntegrationScript(
            ShadyConfig(),
            listOf(AliasEntry("say", "printf 'hello world'")),
        ).render()

        assertContains(script, "alias say='printf '\\''hello world'\\'''")
    }

    @Test
    fun `generated integration is valid zsh syntax`() {
        val file = Files.createTempFile("shady-zsh-integration", ".zsh")
        Files.writeString(file, ZshIntegrationScript(ShadyConfig(), emptyList(), eventChannelEnabled = false).render())

        val process = ProcessBuilder("/bin/zsh", "-n", file.toString()).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().use { it.readText() }

        assertEquals(0, process.waitFor(), output)
    }
}
