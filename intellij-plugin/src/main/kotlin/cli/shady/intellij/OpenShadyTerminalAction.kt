package cli.shady.intellij

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.Messages
import com.intellij.terminal.frontend.toolwindow.TerminalToolWindowTabsManager
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale
import java.util.concurrent.TimeUnit

class OpenShadyTerminalAction : DumbAwareAction("Shady") {
    private val executableFinder = ShadyExecutableFinder()

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val shadyCommand = executableFinder.find()
        if (shadyCommand == null) {
            Messages.showErrorDialog(
                project,
                "Shady was not found. Install it in ~/.local/bin, add it to PATH, or install Shady.app.",
                "Shady Terminal",
            )
            return
        }

        val workingDirectory = project.basePath ?: System.getProperty("user.home")
        TerminalToolWindowTabsManager.getInstance(project)
            .createTabBuilder()
            .workingDirectory(workingDirectory)
            .shellCommand(shadyCommand.withIdeShellFlag())
            .tabName("Shady")
            .requestFocus(true)
            .createTab()
    }
}

internal data class ShadyLaunchCommand(private val command: List<String>) {
    fun withIdeShellFlag(): List<String> = command + "--ide-shell"
}

internal class ShadyExecutableFinder(
    private val home: Path = Path.of(System.getProperty("user.home")),
    private val pathLookup: () -> Path? = ::lookupOnPath,
    private val appCandidates: List<Path> = defaultAppCandidates(home),
    private val javaLookup: () -> Path? = ::lookupJava,
) {
    fun find(): ShadyLaunchCommand? {
        val local = home.resolve(".local/bin/shady")
        if (Files.isExecutable(local)) return ShadyLaunchCommand(listOf(local.toString()))

        pathLookup()
            ?.takeIf(Files::isExecutable)
            ?.let { return ShadyLaunchCommand(listOf(it.toString())) }

        return appCandidates.firstNotNullOfOrNull(::appBundleCommand)
    }

    private fun appBundleCommand(appBundle: Path): ShadyLaunchCommand? {
        val appDirectory = appBundle.resolve("Contents/app")
        val config = ShadyAppBundleConfig.read(appDirectory.resolve("Shady.cfg"), appDirectory) ?: return null
        val java = javaLookup()?.takeIf(Files::isExecutable) ?: return null
        return ShadyLaunchCommand(
            listOf(java.toString()) +
                config.javaOptions +
                listOf("-cp", config.classpath.joinToString(File.pathSeparator) { it.toString() }, config.mainClass),
        )
    }

    private data class ShadyAppBundleConfig(
        val mainClass: String,
        val classpath: List<Path>,
        val javaOptions: List<String>,
    ) {
        companion object {
            fun read(configFile: Path, appDirectory: Path): ShadyAppBundleConfig? {
                if (!Files.isRegularFile(configFile)) return null

                var section = ""
                var mainClass: String? = null
                val classpath = mutableListOf<Path>()
                val javaOptions = mutableListOf<String>()
                Files.readAllLines(configFile).forEach { rawLine ->
                    val line = rawLine.trim()
                    when {
                        line.isBlank() -> return@forEach
                        line.startsWith("[") && line.endsWith("]") -> section = line.substring(1, line.length - 1)
                        section == "Application" && line.startsWith("app.mainclass=") -> {
                            mainClass = line.substringAfter("=").takeIf(String::isNotBlank)
                        }
                        section == "Application" && line.startsWith("app.classpath=") -> {
                            classpath.add(resolveAppPath(line.substringAfter("="), appDirectory))
                        }
                        section == "JavaOptions" && line.startsWith("java-options=") -> {
                            javaOptions.add(line.substringAfter("=").replace("\$APPDIR", appDirectory.toString()))
                        }
                    }
                }

                val usableClasspath = classpath.filter(Files::isRegularFile)
                return ShadyAppBundleConfig(
                    mainClass = mainClass ?: return null,
                    classpath = usableClasspath.takeIf { it.isNotEmpty() } ?: return null,
                    javaOptions = javaOptions,
                )
            }

            private fun resolveAppPath(rawPath: String, appDirectory: Path): Path {
                val expanded = rawPath.replace("\$APPDIR", appDirectory.toString())
                val path = Path.of(expanded)
                return if (path.isAbsolute) path else appDirectory.resolve(path).normalize()
            }
        }
    }

    private companion object {
        fun defaultAppCandidates(home: Path): List<Path> {
            if (!System.getProperty("os.name").lowercase(Locale.ROOT).contains("mac")) return emptyList()
            return listOf(
                Path.of("/Applications/Shady.app"),
                home.resolve("Applications/Shady.app"),
            )
        }

        fun lookupOnPath(): Path? {
            return lookupExecutable("shady")
        }

        fun lookupJava(): Path? {
            val currentJava = Path.of(System.getProperty("java.home")).resolve("bin/java")
            if (Files.isExecutable(currentJava)) return currentJava
            return lookupExecutable("java")
        }

        private fun lookupExecutable(name: String): Path? {
            val process = runCatching {
                ProcessBuilder("/bin/zsh", "-lc", "command -v $name")
                    .redirectErrorStream(true)
                    .start()
            }.getOrNull() ?: return null
            if (!process.waitFor(2, TimeUnit.SECONDS) || process.exitValue() != 0) return null
            val output = process.inputStream.readAllBytes().toString(StandardCharsets.UTF_8).trim()
            return output.takeIf(String::isNotBlank)?.let(Path::of)?.takeIf(Files::isExecutable)
        }
    }
}
