package cli.shady.cli

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertIs

class CommandLineTest {
    @Test
    fun `accepts one command argument`() {
        val request = CommandLine.parse(arrayOf("python3 script.py --flag"))

        assertEquals(
            "python3 script.py --flag",
            assertIs<CommandLineRequest.Execute>(request).input.rawInput,
        )
    }

    @Test
    fun `accepts separate command parameters`() {
        val request = assertIs<CommandLineRequest.Execute>(
            CommandLine.parse(arrayOf("python3", "script.py")),
        )

        assertEquals("python3 script.py", request.input.rawInput)
        assertEquals(listOf("python3", "script.py"), request.input.tokens)
    }

    @Test
    fun `tokenizes a quoted built in command passed as one argument`() {
        val request = assertIs<CommandLineRequest.Execute>(
            CommandLine.parse(arrayOf("alias list")),
        )

        assertEquals(listOf("alias", "list"), request.input.tokens)
    }

    @Test
    fun `extracts long alias commands after double dash`() {
        val request = assertIs<CommandLineRequest.Execute>(
            CommandLine.parse(arrayOf("alias add lint -- npm run lint && npm run prettier-write")),
        )

        assertEquals("npm run lint && npm run prettier-write", request.input.textAfterDoubleDash())
    }

    @Test
    fun `extracts quoted paths after double dash without quotes`() {
        val request = assertIs<CommandLineRequest.Execute>(
            CommandLine.parse(arrayOf("""prehook add my_pre -- "/tmp/my script.sh"""")),
        )

        assertEquals("/tmp/my script.sh", request.input.textAfterDoubleDash())
    }

    @Test
    fun `returns invalid request for empty args`() {
        val request = CommandLine.parse(emptyArray())

        assertIs<CommandLineRequest.Invalid>(request)
        assertEquals(2, CommandLine.INVALID_ARGUMENT_EXIT_CODE)
    }

    @Test
    fun `returns start mode only for start command`() {
        val request = CommandLine.parse(arrayOf("start"))

        assertIs<CommandLineRequest.Start>(request)
    }

    @Test
    fun `global usage contains all command groups`() {
        assertContains(CommandLine.USAGE, "Emulator:")
        assertContains(CommandLine.USAGE, "shady start")
        assertContains(CommandLine.USAGE, "Emulator-only commands:")
        assertContains(CommandLine.USAGE, "shady sys")
        assertContains(CommandLine.USAGE, "shady windows")
        assertContains(CommandLine.USAGE, "Raw CLI commands:")
        assertContains(CommandLine.USAGE, "Help commands:")
        assertContains(CommandLine.USAGE, "Alias commands:")
        assertContains(CommandLine.USAGE, "Prehook commands:")
        assertContains(CommandLine.USAGE, "shady git status")
    }
}
