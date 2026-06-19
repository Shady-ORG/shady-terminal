package cli.shady.commands.builtins

import cli.shady.cli.CliInput
import cli.shady.commands.AcceptResult
import cli.shady.commands.AliasEntry
import cli.shady.commands.AliasRepository
import cli.shady.commands.AliasSuggestion
import cli.shady.commands.CommandRequest
import cli.shady.commands.ShadyCommand
import cli.shady.commands.SuggestionEngine

class AliasCommand(
    private val aliasRepository: AliasRepository,
    private val suggestionEngine: SuggestionEngine,
) : ShadyCommand {
    override val name: String = "alias"

    override fun resolve(input: CliInput): CommandRequest? {
        if (input.tokens.firstOrNull() != name) {
            return null
        }

        return when (input.tokens.getOrNull(1)) {
            "add" -> addAlias(input)
            "list" -> listAliases(input)
            "suggest" -> suggestAliases()
            "accept" -> acceptSuggestion(input)
            null -> error("Missing alias subcommand.\n\n${usage()}")
            else -> error("Unknown alias subcommand: ${input.tokens[1]}\n\n${usage()}")
        }
    }

    private fun addAlias(input: CliInput): CommandRequest.BuiltInOutput {
        val aliasName = input.tokens.getOrNull(2)
            ?: return error("Missing alias name.\n\n${usage()}")
        val command = input.textAfterDoubleDash()
            ?: return error("Missing alias command after --.\n\n${usage()}")

        val validationError = validateAliasName(aliasName)
        if (validationError != null) {
            return error(validationError)
        }

        return try {
            aliasRepository.upsert(AliasEntry(aliasName, command))
            output("Alias '$aliasName' -> $command\n")
        } catch (error: IllegalStateException) {
            error(error.message ?: "Unable to update alias registry.")
        }
    }

    private fun listAliases(input: CliInput): CommandRequest.BuiltInOutput {
        if (input.tokens.size != 2) {
            return error("alias list does not accept additional arguments.\n\n${usage()}")
        }

        return try {
            output(formatAliases(aliasRepository.all()))
        } catch (error: IllegalStateException) {
            error(error.message ?: "Unable to read alias registry.")
        }
    }

    private fun suggestAliases(): CommandRequest.BuiltInOutput {
        val suggestions = suggestionEngine.listSuggestions()

        if (suggestions.isEmpty()) {
            return CommandRequest.BuiltInOutput(
                title = "alias suggest",
                output = "No alias suggestions available.",
            )
        }

        return CommandRequest.BuiltInOutput(
            title = "alias suggest",
            output = formatSuggestions(suggestions),
        )
    }

    private fun acceptSuggestion(input: CliInput): CommandRequest.BuiltInOutput {
        val indexArg = input.tokens.getOrNull(2)
        val aliasName = input.tokens.getOrNull(3)

        if (indexArg == null || aliasName == null) {
            return CommandRequest.BuiltInOutput(
                title = "alias accept",
                output = "Usage: shady alias accept <number> <alias-name>\n",
                exitCode = 2,
            )
        }

        val index = indexArg.toIntOrNull()
            ?: return CommandRequest.BuiltInOutput(
                title = "alias accept",
                output = "Invalid suggestion number: '$indexArg'. Must be an integer.\n",
                exitCode = 2,
            )

        return when (val result = suggestionEngine.acceptSuggestion(index, aliasName)) {
            is AcceptResult.Success -> CommandRequest.BuiltInOutput(
                title = "alias accept",
                output = "Alias '${result.aliasName}' saved for command: ${result.command}\n",
            )
            is AcceptResult.IndexOutOfRange -> CommandRequest.BuiltInOutput(
                title = "alias accept",
                output = "Invalid suggestion number. Valid range: ${result.validRange.first}-${result.validRange.last}.\n",
                exitCode = 2,
            )
            is AcceptResult.InvalidName -> CommandRequest.BuiltInOutput(
                title = "alias accept",
                output = "${result.reason}\n",
                exitCode = 2,
            )
        }
    }

    private fun formatSuggestions(suggestions: List<AliasSuggestion>): String {
        val sb = StringBuilder("Alias suggestions\n")
        suggestions.forEachIndexed { index, suggestion ->
            if (index > 0) sb.append("\n")
            sb.append("  ${index + 1}. Command used ${suggestion.executionCount} times in the last hour:\n")
            sb.append("     ${suggestion.command}\n")
            sb.append("     Suggested alias:\n")
            sb.append("     ${suggestion.aliasAddInstruction}\n")
        }
        return sb.toString()
    }

    private fun formatAliases(aliases: List<AliasEntry>): String {
        if (aliases.isEmpty()) {
            return "No aliases configured yet.\n\nAdd one with:\n  shady alias add lint -- npm run lint\n"
        }

        val nameWidth = maxOf("Alias".length, aliases.maxOf { it.name.length })
        val header = "${"Alias".padEnd(nameWidth)}  Command"
        val divider = "${"-".repeat(nameWidth)}  ${"-".repeat(48)}"
        val rows = aliases.joinToString(separator = "\n") { alias ->
            "${alias.name.padEnd(nameWidth)}  ${alias.command}"
        }
        return "$header\n$divider\n$rows\n"
    }

    private fun validateAliasName(aliasName: String): String? = when {
        aliasName in RESERVED_NAMES -> "Alias name '$aliasName' is reserved."
        !ALIAS_NAME_PATTERN.matches(aliasName) -> {
            "Invalid alias name '$aliasName'. Use letters, numbers, '_', '-' or '.'."
        }
        else -> null
    }

    private fun output(text: String): CommandRequest.BuiltInOutput =
        CommandRequest.BuiltInOutput(title = "alias", output = text)

    private fun error(text: String): CommandRequest.BuiltInOutput =
        CommandRequest.BuiltInOutput(title = "alias", output = text, exitCode = 2)

    private fun usage(): String =
        USAGE

    companion object {
        const val USAGE = "Alias commands:\n" +
            "  shady alias add <name> -- <command>\n" +
            "      Add or update a project alias.\n" +
            "      Example: shady \"alias add fix -- npm run lint && npm run prettier-write\"\n" +
            "  shady alias list\n" +
            "      Show all configured aliases.\n" +
            "  shady alias suggest\n" +
            "      Show alias suggestions based on command history.\n" +
            "  shady alias accept <number> <alias-name>\n" +
            "      Accept a suggestion by its number and save it with the given name."

        private val ALIAS_NAME_PATTERN = Regex("""[A-Za-z0-9_.-]+""")
        private val RESERVED_NAMES = setOf("alias", "suggest", "accept")
    }
}
