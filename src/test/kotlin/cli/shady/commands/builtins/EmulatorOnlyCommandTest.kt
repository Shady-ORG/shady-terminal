package cli.shady.commands.builtins

import cli.shady.cli.CliInput
import cli.shady.commands.CommandRequest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class EmulatorOnlyCommandTest {
    @Test
    fun `explains that tool windows require the desktop terminal`() {
        val result = EmulatorOnlyCommand().resolve(CliInput.from(arrayOf("sys")))

        val output = assertIs<CommandRequest.BuiltInOutput>(result)
        assertEquals(2, output.exitCode)
        assertContains(output.output, "only inside")
    }

    @Test
    fun `ignores regular shell commands`() {
        assertNull(EmulatorOnlyCommand().resolve(CliInput.from(arrayOf("git", "status"))))
    }
}
