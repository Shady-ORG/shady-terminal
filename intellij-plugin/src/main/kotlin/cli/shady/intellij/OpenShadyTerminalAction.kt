package cli.shady.intellij

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ui.Messages
import com.intellij.terminal.frontend.toolwindow.TerminalToolWindowTabsManager
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit

class OpenShadyTerminalAction : AnAction() {
    private val executableFinder = ShadyExecutableFinder()

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val shadyExecutable = executableFinder.find()
        if (shadyExecutable == null) {
            Messages.showErrorDialog(
                project,
                "Shady was not found. Install it in ~/.local/bin or add it to PATH.",
                "Shady Terminal",
            )
            return
        }

        val workingDirectory = project.basePath ?: System.getProperty("user.home")
        TerminalToolWindowTabsManager.getInstance(project)
            .createTabBuilder()
            .workingDirectory(workingDirectory)
            .shellCommand(listOf(shadyExecutable.toString(), "--ide-shell"))
            .tabName("Shady")
            .requestFocus(true)
            .createTab()
    }
}

internal class ShadyExecutableFinder(
    private val home: Path = Path.of(System.getProperty("user.home")),
    private val pathLookup: () -> Path? = ::lookupOnPath,
) {
    fun find(): Path? {
        val local = home.resolve(".local/bin/shady")
        if (Files.isExecutable(local)) return local
        return pathLookup()?.takeIf(Files::isExecutable)
    }

    private companion object {
        fun lookupOnPath(): Path? {
            val process = runCatching {
                ProcessBuilder("/bin/zsh", "-lc", "command -v shady")
                    .redirectErrorStream(true)
                    .start()
            }.getOrNull() ?: return null
            if (!process.waitFor(2, TimeUnit.SECONDS) || process.exitValue() != 0) return null
            val output = process.inputStream.readAllBytes().toString(StandardCharsets.UTF_8).trim()
            return output.takeIf(String::isNotBlank)?.let(Path::of)?.takeIf(Files::isExecutable)
        }
    }
}
