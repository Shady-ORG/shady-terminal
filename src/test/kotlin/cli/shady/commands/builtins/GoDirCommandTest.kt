package cli.shady.commands.builtins

import cli.shady.cli.CliInput
import cli.shady.commands.CommandRequest
import cli.shady.commands.GoDirRepository
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertIs

class GoDirCommandTest {
    @Test
    fun `adds navigates and removes a manual directory`() {
        val root = Files.createTempDirectory("shady-gdir-command")
        val directory = Files.createDirectory(root.resolve("work"))
        val repository = GoDirRepository(root.resolve("gdirs.json"))
        val command = GoDirCommand(repository)

        val add = command.resolve(CliInput.from(arrayOf("gdir add work \"${directory}\"")))
        assertEquals(0, assertIs<CommandRequest.BuiltInOutput>(add).exitCode)

        val navigate = assertIs<CommandRequest.BuiltInOutput>(
            command.resolve(CliInput.from(arrayOf("gdir work"))),
        )
        assertEquals("${directory.toAbsolutePath().normalize()}\n", navigate.output)

        val remove = command.resolve(CliInput.from(arrayOf("gdir remove work")))
        assertEquals(0, assertIs<CommandRequest.BuiltInOutput>(remove).exitCode)
        assertEquals(1, assertIs<CommandRequest.BuiltInOutput>(
            command.resolve(CliInput.from(arrayOf("gdir work"))),
        ).exitCode)
    }

    @Test
    fun `lists manual and automatic entries with colored source dots and shared parent colors`() {
        val root = Files.createTempDirectory("shady-gdir-command")
        val rca = Files.createDirectory(root.resolve("rca"))
        val revs = Files.createDirectory(root.resolve("revs"))
        val repository = GoDirRepository(root.resolve("gdirs.json"))
        val command = GoDirCommand(repository)

        repository.addManual("rca", rca.toString())
        repeat(10) { repository.recordVisit(revs) }

        val request = assertIs<CommandRequest.BuiltInOutput>(
            command.resolve(CliInput.from(arrayOf("gdir list"))),
        )

        assertContains(request.output, "\u001B[32m\u25CF\u001B[0m")
        assertContains(request.output, "\u001B[34m\u25CF\u001B[0m")
        assertEquals(colorBeforePath(request.output, rca.toString()), colorBeforePath(request.output, revs.toString()))
    }

    private fun colorBeforePath(output: String, path: String): String {
        val index = output.indexOf(path)
        val before = output.substring(0, index)
        return Regex("""\u001B\[[0-9;]+m""").findAll(before).last().value
    }
}
