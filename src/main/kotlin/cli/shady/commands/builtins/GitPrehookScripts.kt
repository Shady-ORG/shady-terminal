package cli.shady.commands.builtins

import cli.shady.cli.CliInput
import cli.shady.commands.CommandRequest
import cli.shady.commands.GitPrehookScriptsRepository
import cli.shady.commands.ShadyCommand
import java.io.IOException
import java.nio.file.Path

class GitPrehookScripts(
    private val gitPrehookScriptsRepository: GitPrehookScriptsRepository,
    private val editor: PrehookEditor = VimPrehookEditor,
) : ShadyCommand {
    override val name: String = "prehook"

    override fun resolve(input: CliInput): CommandRequest? {
        if (input.tokens.firstOrNull() != name) {
            return null
        }

        return when (input.tokens.getOrNull(1)) {
            "add" -> addPrehook(input)
            "list" -> listPrehook(input)
            "run" -> runPrehookWithGitCommands(input)
            null -> error("Missing prehook subcommand.\n\n${usage()}")
            else -> error("Unknown prehook subcommand: ${input.tokens[1]}\n\n${usage()}")
        }
    }

    private fun addPrehook(input: CliInput): CommandRequest.BuiltInOutput {
        val prehookName = input.tokens.getOrNull(2)
            ?: return error("Missing prehook name.\n\n${usage()}")
        val sourcePath = input.textAfterDoubleDash()

        if (sourcePath != null) {
            return copyPrehook(prehookName, Path.of(sourcePath))
        }
        if (input.tokens.size != 3) {
            return error("Unexpected arguments for prehook add.\n\n${usage()}")
        }

        return createPrehookWithEditor(prehookName)
    }

    private fun copyPrehook(prehookName: String, sourcePath: Path): CommandRequest.BuiltInOutput =
        try {
            val script = gitPrehookScriptsRepository.addFromPath(prehookName, sourcePath)
            output("Prehook '${script.name}' copied to ${script.path}\n")
        } catch (error: IllegalArgumentException) {
            error(error.message ?: "Unable to add prehook.")
        } catch (error: IOException) {
            error(error.message ?: "Unable to copy prehook.")
        }

    private fun createPrehookWithEditor(prehookName: String): CommandRequest.BuiltInOutput {
        val script = try {
            gitPrehookScriptsRepository.createForEditing(prehookName)
        } catch (error: IllegalArgumentException) {
            return error(error.message ?: "Unable to create prehook.")
        } catch (error: IOException) {
            return error(error.message ?: "Unable to create prehook.")
        }

        val exitCode = try {
            editor.edit(script.path)
        } catch (error: IOException) {
            gitPrehookScriptsRepository.delete(script)
            return error("Unable to open vim: ${error.message}")
        } catch (error: InterruptedException) {
            Thread.currentThread().interrupt()
            gitPrehookScriptsRepository.delete(script)
            return error("Editing prehook was interrupted.")
        }

        if (exitCode != 0) {
            gitPrehookScriptsRepository.delete(script)
            return error("vim exited with code $exitCode. Prehook was not saved.")
        }

        return try {
            gitPrehookScriptsRepository.validateBashScript(script.path)
            output("Prehook '${script.name}' created at ${script.path}\n")
        } catch (error: IllegalArgumentException) {
            gitPrehookScriptsRepository.delete(script)
            error(error.message ?: "Prehook is not a valid bash script.")
        }
    }

    private fun listPrehook(input: CliInput): CommandRequest.BuiltInOutput {
        if (input.tokens.size != 2) {
            return error("prehook list does not accept additional arguments.\n\n${usage()}")
        }

        val scripts = gitPrehookScriptsRepository.all()
        if (scripts.isEmpty()) {
            return output("No prehooks configured yet.\n\nAdd one with:\n  shady prehook add my_pre -- path/to/script.sh\n")
        }

        val nameWidth = maxOf("Prehook".length, scripts.maxOf { it.name.length })
        val header = "${"Prehook".padEnd(nameWidth)}  File"
        val divider = "${"-".repeat(nameWidth)}  ${"-".repeat(48)}"
        val rows = scripts.joinToString("\n") { script ->
            "${script.name.padEnd(nameWidth)}  ${script.name} - ${script.path}\n"
        }
        return output("$header\n$divider\n$rows\n")
    }

    private fun runPrehookWithGitCommands(input: CliInput): CommandRequest {
        val prehookName = input.tokens.getOrNull(2)
            ?: return error("Missing prehook name.\n\n${usage()}")
        if (input.tokens.size < 3) {
            return error("prehook run requires a prehook name.\n\n${usage()}")
        }

        val script = gitPrehookScriptsRepository.find(prehookName)
            ?: return error("Unknown prehook '$prehookName'.")

        val commands = mutableListOf(prehookExecutionCommand(script.path))

        val gitCommands = executeGitCommands(input)
        if (gitCommands is CommandRequest.RunShell && gitCommands.command.isNotBlank()) {
            commands += gitCommands.command
        }

        return CommandRequest.RunShell(commands.joinToString(" && "))
    }

    private fun executeGitCommands(input: CliInput): CommandRequest {
        val tokens = input.tokens
        val commands = mutableListOf<String>()

        val hasCommit = tokens.any { it == "--commit" || it == "--c" }
        val hasPush = tokens.any { it == "--push" || it == "--p" }

        if (hasCommit || (!hasPush)) {
            val message = commitMessageFrom(tokens)

            commands += "git commit -m ${message.shellArgument()}"
        }

        if (hasPush) {
            commands += "git push"
        }

        return CommandRequest.RunShell(
            commands.joinToString(" && ")
        )
    }

    private fun commitMessageFrom(tokens: List<String>): String {
        val messageIndex = tokens.indexOf("-m")
        if (messageIndex == -1 || messageIndex == tokens.lastIndex) {
            return DEFAULT_COMMIT_MESSAGE
        }

        return tokens
            .drop(messageIndex + 1)
            .takeWhile { it !in GIT_OPTION_TOKENS }
            .joinToString(" ")
            .trimMatchingQuotes()
            .takeIf(String::isNotBlank)
            ?: DEFAULT_COMMIT_MESSAGE
    }

    private fun output(text: String): CommandRequest.BuiltInOutput =
        CommandRequest.BuiltInOutput(title = "prehook", output = text)

    private fun error(text: String): CommandRequest.BuiltInOutput =
        CommandRequest.BuiltInOutput(title = "prehook", output = text, exitCode = 2)

    private fun usage(): String =
        USAGE

    private fun prehookExecutionCommand(scriptPath: Path): String {
        val bashBootstrap = """
            shopt -s expand_aliases
            if [ -n "${'$'}{SHELL:-}" ] && [ -x "${'$'}SHELL" ]; then
              eval "$("${'$'}SHELL" -lic 'alias -p 2>/dev/null || alias -L 2>/dev/null || alias 2>/dev/null' 2>/dev/null | sed -n '/^alias /p')" 2>/dev/null || true
            fi
            source ${scriptPath.toShellArgument()}
        """.trimIndent()

        return "bash -lc ${bashBootstrap.shellArgument()}"
    }

    private fun Path.toShellArgument(): String =
        toString().shellArgument()

    private fun String.shellArgument(): String =
        "'${replace("'", "'\\''")}'"

    private fun String.trimMatchingQuotes(): String {
        val trimmed = trim()
        if (trimmed.length < 2) {
            return trimmed
        }

        val first = trimmed.first()
        val last = trimmed.last()
        val isQuoted = QUOTE_PAIRS.any { (open, close) -> first == open && last == close }
        return if (isQuoted) trimmed.substring(1, trimmed.lastIndex).trim() else trimmed
    }

    companion object {
        private const val DEFAULT_COMMIT_MESSAGE = "wip"
        private val GIT_OPTION_TOKENS = setOf("--commit", "--c", "--push", "--p")
        private val QUOTE_PAIRS = listOf(
            '\'' to '\'',
            '"' to '"',
            '“' to '”',
            '„' to '“',
            '«' to '»',
        )

        const val USAGE = "Prehook commands:\n" +
            "  shady prehook add <name> -- <path-to-bash-script>\n" +
            "      Copy an existing bash script into the local prehooks folder.\n" +
            "  shady prehook add <name>\n" +
            "      Create a new bash script and open it in vim.\n" +
            "  shady prehook list\n" +
            "      Show all configured prehooks.\n" +
            "  shady prehook run <name>\n" +
            "      Run a prehook and then commit with the default message \"wip\".\n" +
            "  shady prehook run <name> --commit -m <message> --push\n" +
            "      Run a prehook, commit with a message, and push."
    }
}

fun interface PrehookEditor {
    @Throws(IOException::class, InterruptedException::class)
    fun edit(path: Path): Int
}

object VimPrehookEditor : PrehookEditor {
    override fun edit(path: Path): Int =
        ProcessBuilder("vim", path.toString())
            .inheritIO()
            .start()
            .waitFor()
}
