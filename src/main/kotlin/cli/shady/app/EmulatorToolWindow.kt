package cli.shady.app

data class EmulatorToolWindow(
    val id: String,
    val title: String,
    val type: EmulatorToolWindowType,
)

enum class EmulatorToolWindowType {
    Resources,
    MacWindows,
}
