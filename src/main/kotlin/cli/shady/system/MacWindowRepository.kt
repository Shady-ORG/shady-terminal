package cli.shady.system

import java.util.Locale
import java.util.concurrent.TimeUnit

data class MacWindowInfo(
    val appName: String,
    val processId: Int,
    val windowIndex: Int,
    val title: String,
) {
    val displayTitle: String = title.ifBlank { appName }
}

data class MacWindowListResult(
    val windows: List<MacWindowInfo>,
    val error: String? = null,
)

class MacWindowRepository {
    fun listOpenWindows(): MacWindowListResult {
        if (!isMacOs()) {
            return MacWindowListResult(
                windows = emptyList(),
                error = "Window navigation is available on macOS only.",
            )
        }

        val result = runAppleScript(LIST_WINDOWS_SCRIPT)
        if (result.exitCode != 0) {
            return MacWindowListResult(
                windows = emptyList(),
                error = result.output.ifBlank {
                    "Unable to read macOS windows. Enable Accessibility access for the terminal app."
                },
            )
        }

        return MacWindowListResult(
            windows = result.output
                .lineSequence()
                .mapNotNull(::parseWindowLine)
                .distinctBy { "${it.processId}:${it.windowIndex}:${it.title}" }
                .toList(),
        )
    }

    fun focus(window: MacWindowInfo): String? {
        if (!isMacOs()) {
            return "Window navigation is available on macOS only."
        }

        val script = """
            tell application "System Events"
              set targetProcess to first application process whose unix id is ${window.processId}
              set frontmost of targetProcess to true
              if (count of windows of targetProcess) >= ${window.windowIndex} then
                perform action "AXRaise" of window ${window.windowIndex} of targetProcess
              end if
            end tell
        """.trimIndent()

        val result = runAppleScript(script)
        return if (result.exitCode == 0) {
            null
        } else {
            result.output.ifBlank { "Unable to focus ${window.displayTitle}." }
        }
    }

    private fun parseWindowLine(line: String): MacWindowInfo? {
        val parts = line.split("\t", limit = 4)
        if (parts.size != 4) return null

        val processId = parts[1].toIntOrNull() ?: return null
        val windowIndex = parts[2].toIntOrNull() ?: return null
        val title = parts[3].trim()
        if (title.isBlank()) return null

        return MacWindowInfo(
            appName = parts[0].trim(),
            processId = processId,
            windowIndex = windowIndex,
            title = title,
        )
    }

    private fun runAppleScript(script: String): ScriptResult {
        val process = try {
            ProcessBuilder("osascript", "-e", script)
                .redirectErrorStream(true)
                .start()
        } catch (error: Exception) {
            return ScriptResult(exitCode = 1, output = error.message.orEmpty())
        }

        if (!process.waitFor(COMMAND_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
            process.destroyForcibly()
            return ScriptResult(exitCode = 1, output = "macOS window query timed out.")
        }

        val output = process.inputStream.bufferedReader().use { it.readText() }
        return ScriptResult(exitCode = process.exitValue(), output = output.trim())
    }

    private fun isMacOs(): Boolean =
        System.getProperty("os.name").lowercase(Locale.ROOT).contains("mac")

    private data class ScriptResult(
        val exitCode: Int,
        val output: String,
    )

    private companion object {
        const val COMMAND_TIMEOUT_MS = 2_500L

        val LIST_WINDOWS_SCRIPT = """
            tell application "System Events"
              set output to ""
              repeat with p in application processes
                if visible of p is true then
                  set appName to name of p as text
                  set pidValue to unix id of p as text
                  set windowCount to count of windows of p
                  repeat with windowIndex from 1 to windowCount
                    set windowTitle to name of window windowIndex of p as text
                    if windowTitle is not "" then
                      set output to output & appName & tab & pidValue & tab & (windowIndex as text) & tab & windowTitle & linefeed
                    end if
                  end repeat
                end if
              end repeat
            end tell
            return output
        """.trimIndent()
    }
}
