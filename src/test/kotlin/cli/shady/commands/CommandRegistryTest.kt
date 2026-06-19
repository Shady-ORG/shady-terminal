package cli.shady.commands

import cli.shady.cli.CliInput
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class CommandRegistryTest {
    @Test
    fun `runs a stored alias as shell command`() {
        val aliasRepository = AliasRepository(tempRegistry())
        aliasRepository.upsert(AliasEntry("lint", "npm run lint && npm run prettier-write"))
        val registry = CommandRegistry(commands = emptyList(), aliasRepository = aliasRepository)

        val request = registry.resolve(CliInput.from(arrayOf("lint")))

        assertEquals(
            "npm run lint && npm run prettier-write",
            assertIs<CommandRequest.RunShell>(request).command,
        )
    }

    @Test
    fun `falls back to raw shell commands`() {
        val registry = CommandRegistry(commands = emptyList(), aliasRepository = AliasRepository(tempRegistry()))

        val request = registry.resolve(CliInput.from(arrayOf("python3", "python.py")))

        assertEquals("python3 python.py", assertIs<CommandRequest.RunShell>(request).command)
    }

    private fun tempRegistry() = Files.createTempDirectory("shady-alias-registry").resolve("aliases.json")
}
