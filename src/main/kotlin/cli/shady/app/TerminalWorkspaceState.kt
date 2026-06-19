package cli.shady.app

import cli.shady.terminal.TerminalSessionStatus
import cli.shady.update.AvailableUpdate

data class TerminalTabState(
    val id: String,
    val title: String,
    val workingDirectory: String,
    val status: TerminalSessionStatus = TerminalSessionStatus.Starting,
    val activeCommand: String? = null,
    val lastExitCode: Int? = null,
    val isAtPrompt: Boolean = false,
)

data class TerminalWorkspaceState(
    val tabs: List<TerminalTabState> = emptyList(),
    val activeTabId: String? = null,
    val toolWindows: List<EmulatorToolWindow> = emptyList(),
    val fuzzyPaletteOpen: Boolean = false,
    val fuzzySearchEnabled: Boolean = true,
    val notification: String? = null,
    val availableUpdate: AvailableUpdate? = null,
)
