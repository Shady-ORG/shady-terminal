package cli.shady.execution

interface CommandExecutor {
    suspend fun execute(command: String, callbacks: CommandCallbacks): CommandOutcome

    suspend fun stop()
}

data class CommandCallbacks(
    val onStarted: () -> Unit,
    val onOutput: (String) -> Unit,
)

sealed interface CommandOutcome {
    data class Finished(val exitCode: Int) : CommandOutcome

    data class Stopped(val exitCode: Int?) : CommandOutcome

    data class LaunchFailed(val message: String) : CommandOutcome
}
