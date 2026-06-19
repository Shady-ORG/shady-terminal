package cli.shady.commands.builtins

import cli.shady.cli.CliInput
import cli.shady.commands.CommandHelp
import cli.shady.commands.CommandRequest
import cli.shady.commands.ShadyCommand

class HelpCommand : ShadyCommand {
    override val name: String = "help"

    override fun resolve(input: CliInput): CommandRequest? {
        if (input.tokens.firstOrNull() != name) {
            return null
        }

        if (input.tokens.size != 1) {
            return CommandRequest.BuiltInOutput(
                title = name,
                output = "help does not accept additional arguments.\n\n${CommandHelp.USAGE}\n",
                exitCode = 2,
            )
        }

        return CommandRequest.BuiltInOutput(
            title = name,
            output = "${CommandHelp.USAGE}\n",
        )
    }
}
