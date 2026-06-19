package cli.shady.commands.config

import java.nio.file.Path

data class ShadyPaths(
    val configDirectory: Path,
    val globalConfig: Path,
    val colorRules: Path,
    val stylesDirectory: Path,
    val stateDirectory: Path,
    val workspaceState: Path,
    val updateState: Path,
) {
    companion object {
        fun system(
            home: Path = Path.of(System.getProperty("user.home")),
            environment: Map<String, String> = System.getenv(),
        ): ShadyPaths {
            val configRoot = environment["XDG_CONFIG_HOME"]
                ?.takeIf(String::isNotBlank)
                ?.let(Path::of)
                ?: home.resolve(".config")
            val stateRoot = environment["XDG_STATE_HOME"]
                ?.takeIf(String::isNotBlank)
                ?.let(Path::of)
                ?: home.resolve(".local/state")
            val configDirectory = configRoot.resolve("shady")
            val stateDirectory = stateRoot.resolve("shady")
            return ShadyPaths(
                configDirectory = configDirectory,
                globalConfig = configDirectory.resolve("config.json"),
                colorRules = configDirectory.resolve("colors.json"),
                stylesDirectory = configDirectory.resolve("styles"),
                stateDirectory = stateDirectory,
                workspaceState = stateDirectory.resolve("workspace.json"),
                updateState = stateDirectory.resolve("updates.json"),
            )
        }
    }
}
