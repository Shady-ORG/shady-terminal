package cli.shady.commands.builtins

import cli.shady.cli.CliInput
import cli.shady.commands.CommandRequest
import cli.shady.commands.ShadyCommand

class EmulatorOnlyCommand : ShadyCommand {
    override val name: String = "emulator-tools"

    override fun resolve(input: CliInput): CommandRequest? {
        val command = input.tokens.firstOrNull() ?: return null
        if (command !in NAMES) return null
        if (input.tokens.size > 1) {
            return error("shady $command does not accept additional arguments.")
        }
        return error("shady $command is available only inside the Shady desktop terminal.")
    }

    private fun error(message: String) = CommandRequest.BuiltInOutput(commandTitle(), "$message\n", exitCode = 2)

    private fun commandTitle(): String = "shady tool"

    private companion object {
        val NAMES = setOf(
            "sys", "system", "resource", "resources", "monitor", "dashboard",
            "windows", "window", "wins",
        )
    }
}
