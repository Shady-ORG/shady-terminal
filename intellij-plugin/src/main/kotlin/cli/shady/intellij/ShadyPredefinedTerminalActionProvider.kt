package cli.shady.intellij

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.terminal.ui.OpenPredefinedTerminalActionProvider

class ShadyPredefinedTerminalActionProvider : OpenPredefinedTerminalActionProvider {
    override fun listOpenPredefinedTerminalActions(project: Project): List<AnAction> =
        listOf(OpenShadyTerminalAction())
}
