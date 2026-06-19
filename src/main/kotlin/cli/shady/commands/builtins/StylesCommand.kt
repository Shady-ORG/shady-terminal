package cli.shady.commands.builtins

import cli.shady.cli.CliInput
import cli.shady.commands.CommandRequest
import cli.shady.commands.ShadyCommand
import cli.shady.commands.config.ShadyConfigService
import cli.shady.commands.config.ShadyPaths
import java.nio.file.Files

class StylesCommand(
    private val configService: ShadyConfigService,
    private val paths: ShadyPaths,
) : ShadyCommand {
    override val name: String = "styles"

    override fun resolve(input: CliInput): CommandRequest? {
        if (input.tokens.firstOrNull() != name) return null
        if (input.tokens.size != 1) return error("styles does not accept arguments.")
        if (!configService.load().features.stylesCommandEnabled) {
            return error(
                "The styles command is disabled. Enable it with:\n" +
                    "  shady config set features.stylesCommandEnabled true",
            )
        }

        return try {
            Files.createDirectories(paths.stylesDirectory)
            ProcessBuilder("open", paths.stylesDirectory.toString()).start()
            CommandRequest.BuiltInOutput(name, "Opened ${paths.stylesDirectory}\n")
        } catch (error: Exception) {
            error("Unable to open styles directory: ${error.message}")
        }
    }

    private fun error(text: String) = CommandRequest.BuiltInOutput(name, "$text\n", exitCode = 2)
}
