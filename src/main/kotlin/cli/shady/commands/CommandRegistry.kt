package cli.shady.commands

import cli.shady.cli.CliInput

class CommandRegistry(
    private val commands: List<ShadyCommand>,
    private val aliasRepository: AliasRepository,
) {
    fun resolve(input: CliInput): CommandRequest {
        commands.firstNotNullOfOrNull { command -> command.resolve(input) }?.let { return it }

        val alias = input.tokens.singleOrNull()?.let(aliasRepository::find)
        return if (alias != null) {
            CommandRequest.RunShell(alias.command)
        } else {
            CommandRequest.RunShell(input.rawInput)
        }
    }
}
