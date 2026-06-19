package cli.shady.commands

sealed interface CommandRequest {
    data class RunShell(val command: String) : CommandRequest

    data class BuiltInOutput(
        val title: String,
        val output: String,
        val exitCode: Int = 0,
    ) : CommandRequest
}
