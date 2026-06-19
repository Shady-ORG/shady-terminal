package cli.shady.commands

import cli.shady.cli.CliInput

interface ShadyCommand {
    val name: String

    fun resolve(input: CliInput): CommandRequest?
}
