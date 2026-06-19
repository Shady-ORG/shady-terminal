package cli.shady.commands.builtins

import cli.shady.cli.CliInput
import cli.shady.commands.CommandRequest
import cli.shady.commands.ShadyCommand
import cli.shady.commands.config.ColorRules
import cli.shady.commands.config.ColorRulesRepository

class ColorCommandsCommand(
    private val repository: ColorRulesRepository,
) : ShadyCommand {
    override val name: String = "cc"

    override fun resolve(input: CliInput): CommandRequest? {
        if (input.tokens.firstOrNull() !in NAMES) return null
        return try {
            when (input.tokens.getOrNull(1)) {
                "list", null -> output(format(repository.load()))
                "command" -> setCommand(input)
                "filetype" -> setFileType(input)
                "remove" -> remove(input)
                "reset" -> {
                    repository.reset()
                    output("Color rules reset.\n")
                }
                else -> error("Unknown color command.\n\n$USAGE")
            }
        } catch (error: IllegalArgumentException) {
            error(error.message ?: "Invalid color rule.")
        } catch (error: IllegalStateException) {
            error(error.message ?: "Unable to update color rules.")
        }
    }

    private fun setCommand(input: CliInput): CommandRequest.BuiltInOutput {
        val command = input.tokens.getOrNull(2) ?: return error("Missing command name.\n\n$USAGE")
        val color = input.tokens.getOrNull(3) ?: return error("Missing color.\n\n$USAGE")
        repository.setCommand(command, color)
        return output("Command color saved: $command -> $color\n")
    }

    private fun setFileType(input: CliInput): CommandRequest.BuiltInOutput {
        val extension = input.tokens.getOrNull(2) ?: return error("Missing file extension.\n\n$USAGE")
        val color = input.tokens.getOrNull(3) ?: return error("Missing color.\n\n$USAGE")
        repository.setFileType(extension, color)
        return output("Filetype color saved: .${extension.removePrefix(".")} -> $color\n")
    }

    private fun remove(input: CliInput): CommandRequest.BuiltInOutput {
        val type = input.tokens.getOrNull(2) ?: return error("Missing rule type.\n\n$USAGE")
        val key = input.tokens.getOrNull(3) ?: return error("Missing rule key.\n\n$USAGE")
        when (type) {
            "command" -> repository.removeCommand(key)
            "filetype" -> repository.removeFileType(key)
            else -> return error("Rule type must be 'command' or 'filetype'.")
        }
        return output("Color rule removed: $type $key\n")
    }

    private fun format(rules: ColorRules): String = buildString {
        appendLine("Command colors")
        if (rules.commands.isEmpty()) appendLine("  (none)")
        rules.commands.toSortedMap().forEach { (command, color) -> appendLine("  $command -> $color") }
        appendLine("Filetype colors")
        if (rules.fileTypes.isEmpty()) appendLine("  (none)")
        rules.fileTypes.toSortedMap().forEach { (extension, color) -> appendLine("  .$extension -> $color") }
    }

    private fun output(text: String) = CommandRequest.BuiltInOutput("color-commands", text)

    private fun error(text: String) = CommandRequest.BuiltInOutput("color-commands", "$text\n", exitCode = 2)

    companion object {
        private val NAMES = setOf("cc", "color-commands")
        const val USAGE = "Color commands:\n" +
            "  shady cc list\n" +
            "  shady cc command <command> <color>\n" +
            "  shady cc filetype <extension> <color>\n" +
            "  shady cc remove command <command>\n" +
            "  shady cc remove filetype <extension>\n" +
            "  shady cc reset"
    }
}
