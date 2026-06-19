package cli.shady.commands.config

import kotlinx.serialization.Serializable

@Serializable
data class ShadyConfig(
    val security: SecurityConfig = SecurityConfig(),
    val terminal: TerminalConfig = TerminalConfig(),
    val features: FeatureConfig = FeatureConfig(),
    val updates: UpdateConfig = UpdateConfig(),
)

@Serializable
data class SecurityConfig(
    val alwaysRequestSudo: Boolean = false,
)

@Serializable
data class TerminalConfig(
    val coloredOutput: Boolean = true,
    val usernameOverrideEnabled: Boolean = false,
    val username: String = System.getProperty("user.name", "user"),
    val shell: String = "/bin/zsh",
    val restoreTabs: Boolean = true,
)

@Serializable
data class FeatureConfig(
    val stylesCommandEnabled: Boolean = false,
    val fuzzySearchEnabled: Boolean = true,
    val startupUpdateCheckEnabled: Boolean = true,
)

@Serializable
data class UpdateConfig(
    val repositoryUrl: String? = null,
    val checkIntervalHours: Int = 24,
)
