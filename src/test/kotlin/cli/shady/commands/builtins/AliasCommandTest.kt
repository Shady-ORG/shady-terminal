package cli.shady.commands.builtins

import cli.shady.cli.CliInput
import cli.shady.commands.AliasRepository
import cli.shady.commands.CommandHistoryRepository
import cli.shady.commands.CommandRequest
import cli.shady.commands.SuggestionEngine
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertIs

class AliasCommandTest {
    @Test
    fun `adds an alias to the registry`() {
        val repository = AliasRepository(tempRegistry())
        val command = AliasCommand(repository, stubSuggestionEngine(repository))

        val request = command.resolve(
            CliInput.from(arrayOf("alias add lint -- npm run lint && npm run prettier-write")),
        )

        assertEquals(0, assertIs<CommandRequest.BuiltInOutput>(request).exitCode)
        assertEquals("npm run lint && npm run prettier-write", repository.find("lint")?.command)
    }

    @Test
    fun `updates an existing alias`() {
        val repository = AliasRepository(tempRegistry())
        val command = AliasCommand(repository, stubSuggestionEngine(repository))

        command.resolve(CliInput.from(arrayOf("alias add lint -- npm run lint")))
        command.resolve(CliInput.from(arrayOf("alias add lint -- npm run prettier-write")))

        assertEquals("npm run prettier-write", repository.find("lint")?.command)
        assertEquals(1, repository.all().size)
    }

    @Test
    fun `lists aliases in a readable table`() {
        val repository = AliasRepository(tempRegistry())
        repository.upsert(cli.shady.commands.AliasEntry("lint", "npm run lint"))
        repository.upsert(cli.shady.commands.AliasEntry("format", "npm run prettier-write"))

        val request = assertIs<CommandRequest.BuiltInOutput>(
            AliasCommand(repository, stubSuggestionEngine(repository)).resolve(CliInput.from(arrayOf("alias list"))),
        )

        assertContains(request.output, "Alias")
        assertContains(request.output, "format")
        assertContains(request.output, "npm run prettier-write")
    }

    @Test
    fun `rejects reserved alias names`() {
        val repository = AliasRepository(tempRegistry())
        val request = assertIs<CommandRequest.BuiltInOutput>(
            AliasCommand(repository, stubSuggestionEngine(repository))
                .resolve(CliInput.from(arrayOf("alias add alias -- echo nope"))),
        )

        assertEquals(2, request.exitCode)
        assertContains(request.output, "reserved")
    }

    @Test
    fun `rejects invalid alias names`() {
        val repository = AliasRepository(tempRegistry())
        val request = assertIs<CommandRequest.BuiltInOutput>(
            AliasCommand(repository, stubSuggestionEngine(repository))
                .resolve(CliInput.from(arrayOf("alias add bad/name -- echo nope"))),
        )

        assertEquals(2, request.exitCode)
        assertContains(request.output, "Invalid alias name")
    }

    private fun tempRegistry() = Files.createTempDirectory("shady-alias-command").resolve("aliases.json")

    private fun stubSuggestionEngine(repository: AliasRepository) = SuggestionEngine(
        historyRepository = CommandHistoryRepository(),
        aliasRepository = repository,
    )
}
