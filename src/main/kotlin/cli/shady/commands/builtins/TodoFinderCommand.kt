/*
package cli.shady.commands.builtins

import cli.shady.cli.CliInput
import cli.shady.commands.AliasEntry
import cli.shady.commands.CommandRequest
import cli.shady.commands.ShadyCommand

class TodoFinderCommand : ShadyCommand {
    override val name: String = "alias"

    override fun resolve(input: CliInput): CommandRequest? {

        return when (input.tokens.getOrNull(1)) {
            "link", "", null -> searchTodos(input)
            "count" -> countTodos(input)
            else -> error("Unknown subcommand: ${input.tokens[1]}\n\n${usage()}")
        }
    }

    private fun searchTodos(input: CliInput): CommandRequest.BuiltInOutput {

    }

    private fun usage(): String =
        USAGE

    companion object {
        const val USAGE = "TodoFinder commands:\n" +
                "  shady TodoFinder link\n" +
                "      print all todos with a file-link to them" +
                "      Example: shady \"TodoFinder link" +
                "  shady TodoFinder count\n" +
                "      count all todos in project."

        private val TODO_NAME_PATTERN = Regex("""[A-Za-z0-9_.-]+""")
        private val RESERVED_NAMES = setOf("TodoFinder")
    }
}
*/
