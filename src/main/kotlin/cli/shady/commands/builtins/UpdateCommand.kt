package cli.shady.commands.builtins

import cli.shady.cli.CliInput
import cli.shady.commands.CommandRequest
import cli.shady.commands.ShadyCommand
import cli.shady.commands.config.ShadyConfigService
import java.nio.file.Files
import java.nio.file.Path

class UpdateCommand(
    private val configService: ShadyConfigService,
) : ShadyCommand {
    override val name: String = "update"

    override fun resolve(input: CliInput): CommandRequest? {
        if (input.tokens.firstOrNull() != name) return null
        if (input.tokens.size != 1) return error("update does not accept arguments.")
        val repositoryUrl = configService.load().updates.repositoryUrl
            ?: System.getenv("SHADY_REPO_URL")
            ?: return error(
                "No update repository configured. Set updates.repositoryUrl or SHADY_REPO_URL.",
            )
        val script = installedScript()
            ?: return error("Update script was not found in this Shady distribution.")
        return CommandRequest.RunShell(
            "SHADY_REPO_URL=${repositoryUrl.shellQuote()} ${script.toString().shellQuote()}",
        )
    }

    private fun installedScript(): Path? {
        val location = runCatching {
            Path.of(UpdateCommand::class.java.protectionDomain.codeSource.location.toURI())
        }.getOrNull() ?: return null
        val root = if (Files.isDirectory(location)) {
            Path.of(System.getProperty("user.dir"))
        } else {
            location.parent?.parent ?: return null
        }
        return root.resolve("scripts/update.sh").takeIf(Files::isRegularFile)
    }

    private fun String.shellQuote(): String = "'${replace("'", "'\\''")}'"

    private fun error(message: String) = CommandRequest.BuiltInOutput(name, "$message\n", exitCode = 2)
}
