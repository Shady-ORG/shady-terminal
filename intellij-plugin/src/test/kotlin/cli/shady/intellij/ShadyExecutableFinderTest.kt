package cli.shady.intellij

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ShadyExecutableFinderTest {
    @Test
    fun `prefers local user installation`() {
        val home = Files.createTempDirectory("shady-plugin-home")
        val executable = home.resolve(".local/bin/shady")
        Files.createDirectories(executable.parent)
        Files.writeString(executable, "#!/bin/sh\n")
        executable.toFile().setExecutable(true)

        assertEquals(executable, ShadyExecutableFinder(home) { null }.find())
    }

    @Test
    fun `falls back to path lookup`() {
        val home = Files.createTempDirectory("shady-plugin-home")
        val executable = Files.writeString(home.resolve("shady"), "#!/bin/sh\n")
        executable.toFile().setExecutable(true)

        assertEquals(executable, ShadyExecutableFinder(home) { executable }.find())
    }

    @Test
    fun `rejects non executable path result`() {
        val home = Files.createTempDirectory("shady-plugin-home")
        val file = Files.writeString(home.resolve("shady"), "not executable")

        assertNull(ShadyExecutableFinder(home) { file }.find())
    }
}
