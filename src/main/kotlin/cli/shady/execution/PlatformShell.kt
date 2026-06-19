package cli.shady.execution

import java.util.Locale

fun interface ShellInvocationFactory {
    fun invocationFor(command: String): List<String>
}

class PlatformShell(
    private val osName: String = System.getProperty("os.name"),
) : ShellInvocationFactory {
    override fun invocationFor(command: String): List<String> =
        if (osName.lowercase(Locale.ROOT).startsWith("windows")) {
            listOf("cmd.exe", "/d", "/s", "/c", command)
        } else {
            listOf("/bin/sh", "-lc", command)
        }
}
