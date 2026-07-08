package cli.shady.commands.builtins

import cli.shady.cli.CliInput
import cli.shady.commands.CommandRequest
import cli.shady.commands.GoDirEntry
import cli.shady.commands.GoDirEntrySource
import cli.shady.commands.GoDirRepository
import cli.shady.commands.ShadyCommand
import java.nio.file.Path

class GoDirCommand(
    private val repository: GoDirRepository,
) : ShadyCommand {
    override val name: String = "gdir"

    override fun resolve(input: CliInput): CommandRequest? {
        if (input.tokens.firstOrNull() != name) return null

        return try {
            when (input.tokens.getOrNull(1)) {
                "add" -> add(input)
                "list" -> list(input)
                "remove" -> remove(input)
                "help" -> output("$USAGE\n")
                null -> error("Missing GoDir name.\n\n$USAGE")
                else -> navigate(input)
            }
        } catch (error: IllegalArgumentException) {
            error(error.message ?: "Invalid GoDir entry.")
        } catch (error: IllegalStateException) {
            error(error.message ?: "Unable to update GoDir registry.")
        }
    }

    private fun add(input: CliInput): CommandRequest.BuiltInOutput {
        val dirName = input.tokens.getOrNull(2) ?: return error("Missing GoDir name.\n\n$USAGE")
        val path = input.tokens.getOrNull(3) ?: return error("Missing directory path.\n\n$USAGE")
        if (input.tokens.size != 4) {
            return error("gdir add expects exactly a name and a path.\n\n$USAGE")
        }

        validateName(dirName)?.let { return error(it) }

        val entry = repository.addManual(dirName, path)
        return output("GoDir saved: ${entry.name} -> ${entry.path}\n")
    }

    private fun list(input: CliInput): CommandRequest.BuiltInOutput {
        if (input.tokens.size != 2) {
            return error("gdir list does not accept additional arguments.\n\n$USAGE")
        }
        return output(format(repository.savedEntries()))
    }

    private fun remove(input: CliInput): CommandRequest.BuiltInOutput {
        val dirName = input.tokens.getOrNull(2) ?: return error("Missing GoDir name.\n\n$USAGE")
        if (input.tokens.size != 3) {
            return error("gdir remove expects exactly one name.\n\n$USAGE")
        }

        return if (repository.removeManual(dirName)) {
            output("Manual GoDir removed: $dirName\n")
        } else {
            error("No manual GoDir entry found for '$dirName'.", exitCode = 1)
        }
    }

    private fun navigate(input: CliInput): CommandRequest.BuiltInOutput {
        val dirName = input.tokens.getOrNull(1) ?: return error("Missing GoDir name.\n\n$USAGE")
        if (input.tokens.size != 2) {
            return error("gdir expects exactly one name.\n\n$USAGE")
        }

        val entry = repository.resolve(dirName)
            ?: return error("No GoDir entry found for '$dirName'.", exitCode = 1)
        return output("${entry.path}\n")
    }

    private fun format(entries: List<GoDirEntry>): String {
        if (entries.isEmpty()) {
            return "No GoDir entries saved yet.\n\n" +
                "Open a directory 10 times or add one with:\n" +
                "  gdir add work \"~/work/project\"\n"
        }

        val nameWidth = maxOf("Name".length, entries.maxOf { it.name.length })
        val countWidth = maxOf("Count".length, entries.maxOf { it.count.toString().length })
        val header = "${"Name".padEnd(nameWidth)}  ${"Count".padStart(countWidth)}  Path"
        val divider = "${"-".repeat(nameWidth)}  ${"-".repeat(countWidth)}  ${"-".repeat(48)}"
        val rows = entries.joinToString(separator = "\n") { entry ->
            val dot = when (entry.source) {
                GoDirEntrySource.Manual -> "$GREEN$DOT$RESET"
                GoDirEntrySource.Automatic -> "$BLUE$DOT$RESET"
            }
            val pathColor = parentColor(entry.path)
            "${entry.name.padEnd(nameWidth)}  ${entry.count.toString().padStart(countWidth)}  " +
                "$dot $pathColor${entry.path}$RESET"
        }
        return "$header\n$divider\n$rows\n"
    }

    private fun validateName(dirName: String): String? = when {
        dirName.isBlank() -> "GoDir name must not be blank."
        dirName in RESERVED_NAMES -> "GoDir name '$dirName' is reserved."
        "/" in dirName -> "GoDir name '$dirName' must not contain '/'."
        "\u0000" in dirName -> "GoDir name must not contain NUL."
        else -> null
    }

    private fun parentColor(path: String): String {
        val parent = runCatching { Path.of(path).parent?.toString() ?: path }.getOrDefault(path)
        return PARENT_COLORS[Math.floorMod(parent.hashCode(), PARENT_COLORS.size)]
    }

    private fun output(text: String) = CommandRequest.BuiltInOutput(name, text)

    private fun error(text: String, exitCode: Int = 2) =
        CommandRequest.BuiltInOutput(name, "$text\n", exitCode = exitCode)

    companion object {
        const val USAGE = "GoDir commands:\n" +
            "  gdir <name>\n" +
            "      Go to the saved directory with the highest usage count for that name.\n" +
            "  gdir add <name> \"<path>\"\n" +
            "      Save a manual directory. Manual entries are never overwritten automatically.\n" +
            "  gdir list\n" +
            "      Show saved directories with usage counts.\n" +
            "  gdir remove <name>\n" +
            "      Remove a manual directory entry."

        private val RESERVED_NAMES = setOf("add", "list", "remove", "help")
        private const val RESET = "\u001B[0m"
        private const val GREEN = "\u001B[32m"
        private const val BLUE = "\u001B[34m"
        private const val DOT = "\u25CF"
        private val PARENT_COLORS = listOf(
            "\u001B[35m",
            "\u001B[36m",
            "\u001B[33m",
            "\u001B[91m",
            "\u001B[95m",
            "\u001B[96m",
            "\u001B[97m",
            "\u001B[90m",
            "\u001B[93m",
        )
    }
}
