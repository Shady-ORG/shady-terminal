package cli.shady.terminal

sealed interface TerminalSessionEvent {
    data class PromptReady(val workingDirectory: String) : TerminalSessionEvent
    data class CommandStarted(val command: String) : TerminalSessionEvent
    data class CommandFinished(val exitCode: Int) : TerminalSessionEvent
    data class InternalCommand(val command: String) : TerminalSessionEvent
    data class Exited(val exitCode: Int) : TerminalSessionEvent
    data class Failed(val message: String) : TerminalSessionEvent
}

enum class TerminalSessionStatus {
    Starting,
    Running,
    Exited,
    Failed,
}
