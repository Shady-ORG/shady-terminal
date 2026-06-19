package cli.shady.commands.builtins

import cli.shady.cli.CliInput
import cli.shady.commands.CommandRequest
import cli.shady.commands.config.ColorRulesRepository
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ColorCommandsCommandTest {
    @Test
    fun `stores command and filetype colors`() {
        val path = Files.createTempDirectory("shady-colors").resolve("colors.json")
        val repository = ColorRulesRepository(path)
        val command = ColorCommandsCommand(repository)

        command.resolve(CliInput.from(arrayOf("cc", "command", "ls", "magenta")))
        command.resolve(CliInput.from(arrayOf("cc", "filetype", ".kt", "cyan")))

        assertEquals("magenta", repository.load().commands["ls"])
        assertEquals("cyan", repository.load().fileTypes["kt"])
    }

    @Test
    fun `rejects unsupported colors`() {
        val path = Files.createTempDirectory("shady-colors").resolve("colors.json")
        val result = ColorCommandsCommand(ColorRulesRepository(path)).resolve(
            CliInput.from(arrayOf("cc", "command", "ls", "invisible")),
        )

        val output = assertIs<CommandRequest.BuiltInOutput>(result)
        assertEquals(2, output.exitCode)
        assertContains(output.output, "Unsupported color")
    }
}
