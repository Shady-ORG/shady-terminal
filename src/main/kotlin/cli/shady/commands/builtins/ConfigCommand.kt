package cli.shady.commands.builtins

import cli.shady.cli.CliInput
import cli.shady.commands.CommandRequest
import cli.shady.commands.ShadyCommand
import cli.shady.commands.config.ShadyConfig
import cli.shady.commands.config.ShadyConfigService
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class ConfigCommand(
    private val service: ShadyConfigService,
    private val json: Json = Json { prettyPrint = true; encodeDefaults = true },
) : ShadyCommand {
    override val name: String = "config"

    override fun resolve(input: CliInput): CommandRequest? {
        if (input.tokens.firstOrNull() != name) return null
        return when (input.tokens.getOrNull(1)) {
            null, "show" -> output(json.encodeToString(service.load()) + "\n")
            "set" -> set(input)
            else -> error("Unknown config command.\n\n$USAGE")
        }
    }

    private fun set(input: CliInput): CommandRequest.BuiltInOutput {
        val key = input.tokens.getOrNull(2) ?: return error("Missing config key.\n\n$USAGE")
        val value = input.tokens.getOrNull(3) ?: return error("Missing config value.\n\n$USAGE")
        val current = service.loadGlobal()
        val updated = try {
            update(current, key, value)
        } catch (error: IllegalArgumentException) {
            return error(error.message ?: "Invalid config value.")
        }
        service.saveGlobal(updated)
        return output("Updated $key. New tabs reload terminal settings; restart Shady for workspace settings.\n")
    }

    private fun update(config: ShadyConfig, key: String, value: String): ShadyConfig = when (key) {
        "security.alwaysRequestSudo" -> config.copy(
            security = config.security.copy(alwaysRequestSudo = value.boolean()),
        )
        "terminal.coloredOutput" -> config.copy(
            terminal = config.terminal.copy(coloredOutput = value.boolean()),
        )
        "terminal.usernameOverrideEnabled" -> config.copy(
            terminal = config.terminal.copy(usernameOverrideEnabled = value.boolean()),
        )
        "terminal.username" -> config.copy(
            terminal = config.terminal.copy(username = value.validUsername()),
        )
        "terminal.restoreTabs" -> config.copy(
            terminal = config.terminal.copy(restoreTabs = value.boolean()),
        )
        "features.stylesCommandEnabled" -> config.copy(
            features = config.features.copy(stylesCommandEnabled = value.boolean()),
        )
        "features.fuzzySearchEnabled" -> config.copy(
            features = config.features.copy(fuzzySearchEnabled = value.boolean()),
        )
        "features.startupUpdateCheckEnabled" -> config.copy(
            features = config.features.copy(startupUpdateCheckEnabled = value.boolean()),
        )
        "updates.repositoryUrl" -> config.copy(
            updates = config.updates.copy(repositoryUrl = value.takeUnless { it == "null" }),
        )
        "updates.checkIntervalHours" -> config.copy(
            updates = config.updates.copy(checkIntervalHours = value.positiveInt()),
        )
        else -> throw IllegalArgumentException("Unknown config key '$key'.")
    }

    private fun String.boolean(): Boolean = when (lowercase()) {
        "true" -> true
        "false" -> false
        else -> throw IllegalArgumentException("Expected true or false, got '$this'.")
    }

    private fun String.validUsername(): String {
        require(matches(Regex("[A-Za-z0-9_.-]+"))) {
            "Username may contain letters, numbers, '.', '_' and '-'."
        }
        return this
    }

    private fun String.positiveInt(): Int = toIntOrNull()
        ?.takeIf { it > 0 }
        ?: throw IllegalArgumentException("Expected a positive integer, got '$this'.")

    private fun output(text: String) = CommandRequest.BuiltInOutput(name, text)

    private fun error(text: String) = CommandRequest.BuiltInOutput(name, "$text\n", exitCode = 2)

    companion object {
        const val USAGE = "Config commands:\n" +
            "  shady config show\n" +
            "  shady config set <key> <value>"
    }
}
