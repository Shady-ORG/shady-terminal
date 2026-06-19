package cli.shady.commands.builtins

import cli.shady.cli.CliInput
import cli.shady.commands.CommandRequest
import cli.shady.commands.GitPrehookScriptsRepository
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class GitPrehookScriptsTest {
    @Test
    fun `copies a bash script into the local prehooks folder`() {
        val source = bashSource()
        val scriptsDirectory = Files.createTempDirectory("shady-prehooks")
        val command = GitPrehookScripts(GitPrehookScriptsRepository(scriptsDirectory))

        val request = command.resolve(CliInput.from(arrayOf("prehook", "add", "my_pre", "--", source.toString())))

        assertEquals(0, assertIs<CommandRequest.BuiltInOutput>(request).exitCode)
        val copiedScript = scriptsDirectory.resolve("my_pre.sh")
        assertTrue(Files.exists(copiedScript))
        assertContains(Files.readString(copiedScript), "echo ok")
    }

    @Test
    fun `creates a bash script with vim when no path is provided`() {
        val scriptsDirectory = Files.createTempDirectory("shady-prehooks")
        val command = GitPrehookScripts(
            gitPrehookScriptsRepository = GitPrehookScriptsRepository(scriptsDirectory),
            editor = PrehookEditor { path ->
                Files.writeString(path, "#!/usr/bin/env bash\n\necho from vim\n")
                0
            },
        )

        val request = command.resolve(CliInput.from(arrayOf("prehook", "add", "my_pree")))

        assertEquals(0, assertIs<CommandRequest.BuiltInOutput>(request).exitCode)
        val createdScript = scriptsDirectory.resolve("my_pree.sh")
        assertTrue(Files.exists(createdScript))
        assertContains(Files.readString(createdScript), "echo from vim")
    }

    @Test
    fun `adds a random suffix when a prehook name is already used`() {
        val source = bashSource()
        val scriptsDirectory = Files.createTempDirectory("shady-prehooks")
        Files.writeString(scriptsDirectory.resolve("my_pre.sh"), "#!/usr/bin/env bash\n")
        val command = GitPrehookScripts(
            GitPrehookScriptsRepository(scriptsDirectory, suffixGenerator = { "abc123" }),
        )

        val request = command.resolve(CliInput.from(arrayOf("prehook", "add", "my_pre", "--", source.toString())))

        assertEquals(0, assertIs<CommandRequest.BuiltInOutput>(request).exitCode)
        assertTrue(Files.exists(scriptsDirectory.resolve("my_pre_abc123.sh")))
    }

    @Test
    fun `rejects non bash source files`() {
        val source = Files.createTempFile("zsh-prehook", ".sh")
        Files.writeString(source, "#!/usr/bin/env zsh\n\necho nope\n")
        val scriptsDirectory = Files.createTempDirectory("shady-prehooks")
        val command = GitPrehookScripts(GitPrehookScriptsRepository(scriptsDirectory))

        val request = assertIs<CommandRequest.BuiltInOutput>(
            command.resolve(CliInput.from(arrayOf("prehook", "add", "my_pre", "--", source.toString()))),
        )

        assertEquals(2, request.exitCode)
        assertContains(request.output, "Only bash scripts are allowed")
        assertFalse(Files.exists(scriptsDirectory.resolve("my_pre.sh")))
    }

    @Test
    fun `removes an edited file when vim exits with an error`() {
        val scriptsDirectory = Files.createTempDirectory("shady-prehooks")
        val command = GitPrehookScripts(
            gitPrehookScriptsRepository = GitPrehookScriptsRepository(scriptsDirectory),
            editor = PrehookEditor { 1 },
        )

        val request = command.resolve(CliInput.from(arrayOf("prehook", "add", "my_pre")))

        assertEquals(2, assertIs<CommandRequest.BuiltInOutput>(request).exitCode)
        assertFalse(Files.exists(scriptsDirectory.resolve("my_pre.sh")))
    }

    @Test
    fun `runs prehook through bash with interactive shell aliases imported`() {
        val source = bashSource()
        val scriptsDirectory = Files.createTempDirectory("shady-prehooks")
        val command = GitPrehookScripts(GitPrehookScriptsRepository(scriptsDirectory))
        command.resolve(CliInput.from(arrayOf("prehook", "add", "my_pre", "--", source.toString())))

        val request = command.resolve(CliInput.from(arrayOf("prehook", "run", "my_pre")))

        val shellCommand = assertIs<CommandRequest.RunShell>(request).command
        assertContains(shellCommand, "bash -lc")
        assertContains(shellCommand, "shopt -s expand_aliases")
        assertContains(shellCommand, "alias -p")
        assertContains(shellCommand, "sed -n")
        assertContains(shellCommand, "source")
    }

    @Test
    fun `quotes unicode commit messages`() {
        val source = bashSource()
        val scriptsDirectory = Files.createTempDirectory("shady-prehooks")
        val command = GitPrehookScripts(GitPrehookScriptsRepository(scriptsDirectory))
        command.resolve(CliInput.from(arrayOf("prehook", "add", "my_pre", "--", source.toString())))

        val request = command.resolve(
            CliInput.from(arrayOf("prehook run my_pre --commit -m „öljölkjé“ --push")),
        )

        val shellCommand = assertIs<CommandRequest.RunShell>(request).command
        assertContains(shellCommand, "git commit -m 'öljölkjé' && git push")
    }

    @Test
    fun `keeps multi word commit messages together`() {
        val source = bashSource()
        val scriptsDirectory = Files.createTempDirectory("shady-prehooks")
        val command = GitPrehookScripts(GitPrehookScriptsRepository(scriptsDirectory))
        command.resolve(CliInput.from(arrayOf("prehook", "add", "my_pre", "--", source.toString())))

        val request = command.resolve(
            CliInput.from(arrayOf("prehook run my_pre --commit -m „fix the schön path“ --push")),
        )

        val shellCommand = assertIs<CommandRequest.RunShell>(request).command
        assertContains(shellCommand, "git commit -m 'fix the schön path' && git push")
    }

    private fun bashSource() =
        Files.createTempFile("bash-prehook", ".sh").also { path ->
            Files.writeString(path, "#!/usr/bin/env bash\n\necho ok\n")
        }
}
