package cli.shady.app

import cli.shady.commands.AliasEntry
import cli.shady.commands.AliasRepository
import cli.shady.commands.CommandHistoryRepository
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FuzzySearchEngineTest {
    @Test
    fun `searches history aliases commands executables and files`() {
        val directory = Files.createTempDirectory("shady-fuzzy")
        Files.writeString(directory.resolve("README.md"), "docs")
        val executableDirectory = Files.createDirectory(directory.resolve("bin"))
        val executable = Files.writeString(executableDirectory.resolve("mytool"), "#!/bin/sh\n")
        executable.toFile().setExecutable(true)
        val aliases = AliasRepository(directory.resolve("aliases.json"))
        aliases.upsert(AliasEntry("lint", "npm run lint"))
        val history = CommandHistoryRepository()
        history.record("git status")
        val engine = FuzzySearchEngine(history, aliases, mapOf("PATH" to executableDirectory.toString()))

        assertEquals(FuzzyCandidateSource.History, engine.search("git", directory).first().source)
        assertTrue(engine.search("lint", directory).any { it.source == FuzzyCandidateSource.Alias })
        assertTrue(engine.search("mytool", directory).any { it.source == FuzzyCandidateSource.Executable })
        assertTrue(engine.search("readme", directory).any { it.source == FuzzyCandidateSource.File })
        assertTrue(engine.search("config", directory).any { it.source == FuzzyCandidateSource.Shady })
    }
}
