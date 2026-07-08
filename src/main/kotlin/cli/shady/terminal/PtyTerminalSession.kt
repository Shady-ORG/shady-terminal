package cli.shady.terminal

import com.jediterm.terminal.CursorShape
import com.jediterm.terminal.ui.JediTermWidget
import com.jediterm.terminal.ui.settings.SettingsProvider
import com.jediterm.core.util.TermSize
import com.pty4j.PtyProcess
import com.pty4j.PtyProcessBuilder
import cli.shady.commands.AliasEntry
import cli.shady.commands.config.ColorRules
import cli.shady.commands.config.ShadyConfig
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.awt.event.KeyListener
import javax.swing.JComponent
import javax.swing.SwingUtilities
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch

class PtyTerminalSession(
    override val id: String = UUID.randomUUID().toString(),
    val initialDirectory: Path,
    override val title: String,
    private val config: ShadyConfig,
    colorRulesProvider: () -> ColorRules,
    aliases: List<AliasEntry>,
    settingsProvider: SettingsProvider,
    private val scope: CoroutineScope,
    private val onEvent: (TerminalSessionEvent) -> Unit,
) : TerminalSession {
    private val closed = AtomicBoolean(false)
    private val sessionDirectory = Files.createTempDirectory("shady-session-")
    private val eventFifo = sessionDirectory.resolve("events.fifo")
    private val eventStreamLock = Any()
    private var eventInput: InputStream? = null
    private val process: PtyProcess
    private val connector: ShadyPtyConnector
    val widget: JediTermWidget
    override val component: JComponent
        get() = widget
    private val eventJob: Job
    private val waitJob: Job

    init {
        createFifo(eventFifo)
        Files.writeString(
            sessionDirectory.resolve(".zshrc"),
            ZshIntegrationScript(config, aliases).render(),
        )

        val configuredShell = config.terminal.shell.takeIf(String::isNotBlank)?.let(Path::of)
        val shell = configuredShell
            ?.takeIf { it.fileName.toString() == "zsh" && Files.isExecutable(it) }
            ?.toString()
            ?: "/bin/zsh"
        val commandLine = listOf(shell, "-l")
        val environment = HashMap(System.getenv()).apply {
            put("ZDOTDIR", sessionDirectory.toString())
            put("SHADY_ORIGINAL_ZDOTDIR", System.getenv("ZDOTDIR") ?: System.getProperty("user.home"))
            put("SHADY_EVENT_FIFO", eventFifo.toString())
            put("SHADY_EMBEDDED_FIFO", eventFifo.toString())
            put("SHADY_JAVA_BIN", javaExecutable())
            put("SHADY_CLASSPATH", System.getProperty("java.class.path"))
            put("TERM", "xterm-256color")
        }
        process = PtyProcessBuilder(commandLine.toTypedArray())
            .setEnvironment(environment)
            .setDirectory(initialDirectory.toAbsolutePath().normalize().toString())
            .setRedirectErrorStream(true)
            .setInitialColumns(DEFAULT_COLUMNS)
            .setInitialRows(DEFAULT_ROWS)
            .start()
        eventJob = scope.launch(Dispatchers.IO) { readEvents() }
        connector = ShadyPtyConnector(
            ptyProcess = process,
            commandLine = commandLine,
            sessionName = title,
            colorizer = AnsiOutputColorizer(colorRulesProvider, config.terminal.coloredOutput),
        )
        widget = onEdt {
            JediTermWidget(DEFAULT_COLUMNS, DEFAULT_ROWS, settingsProvider).also {
                it.terminalPanel.setDefaultCursorShape(CursorShape.BLINK_BLOCK)
                it.setTtyConnector(connector)
                it.start()
            }
        }
        waitJob = scope.launch(Dispatchers.IO) {
            try {
                onEvent(TerminalSessionEvent.Exited(process.waitFor()))
            } catch (error: Exception) {
                if (!closed.get()) {
                    onEvent(TerminalSessionEvent.Failed(error.message ?: "Terminal session failed."))
                }
            }
        }
    }

    override fun write(text: String) {
        connector.write(text)
    }

    override fun interrupt() {
        connector.write(byteArrayOf(CTRL_C))
    }

    override fun clearBuffer() {
        onEdt { widget.terminalPanel.clearBuffer() }
    }

    override fun resize(columns: Int, rows: Int) {
        connector.resize(TermSize(columns, rows))
    }

    override fun requestFocus() {
        onEdt { widget.requestFocusInWindow() }
    }

    override fun addKeyListener(listener: KeyListener) {
        onEdt { widget.terminalPanel.addCustomKeyListener(listener) }
    }

    override fun removeKeyListener(listener: KeyListener) {
        onEdt { widget.terminalPanel.removeCustomKeyListener(listener) }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        synchronized(eventStreamLock) {
            eventInput?.close()
            eventInput = null
        }
        onEdt { widget.close() }
        connector.close()
        if (process.isAlive) {
            process.destroy()
            if (!process.waitFor(CLOSE_GRACE_MILLIS, TimeUnit.MILLISECONDS)) {
                process.destroyForcibly()
                process.waitFor(CLOSE_GRACE_MILLIS, TimeUnit.MILLISECONDS)
            }
        }
        eventJob.cancel()
        waitJob.cancel()
        deleteSessionDirectory()
    }

    private fun readEvents() {
        try {
            Files.newInputStream(eventFifo).use { input ->
                synchronized(eventStreamLock) { eventInput = input }
                while (!closed.get()) {
                    val type = readNullTerminated(input) ?: break
                    val payload = readNullTerminated(input) ?: break
                    parseEvent(type, payload)?.let { event ->
                        when (event) {
                            is TerminalSessionEvent.CommandStarted -> connector.setActiveCommand(event.command)
                            is TerminalSessionEvent.CommandFinished -> connector.setActiveCommand(null)
                            else -> Unit
                        }
                        onEvent(event)
                    }
                }
            }
        } catch (error: Exception) {
            if (!closed.get()) {
                onEvent(TerminalSessionEvent.Failed(error.message ?: "Session event channel failed."))
            }
        } finally {
            synchronized(eventStreamLock) { eventInput = null }
        }
    }

    private fun parseEvent(type: String, payload: String): TerminalSessionEvent? = when (type) {
        "PROMPT_READY" -> TerminalSessionEvent.PromptReady(payload)
        "COMMAND_STARTED" -> TerminalSessionEvent.CommandStarted(payload)
        "COMMAND_FINISHED" -> TerminalSessionEvent.CommandFinished(payload.toIntOrNull() ?: 1)
        "INTERNAL_COMMAND" -> TerminalSessionEvent.InternalCommand(payload)
        else -> null
    }

    private fun readNullTerminated(input: InputStream): String? {
        val output = ByteArrayOutputStream()
        while (true) {
            val value = input.read()
            if (value < 0) return null
            if (value == 0) return output.toString(StandardCharsets.UTF_8)
            output.write(value)
        }
    }

    private fun createFifo(path: Path) {
        val result = ProcessBuilder("/usr/bin/mkfifo", path.toString())
            .redirectErrorStream(true)
            .start()
        val output = result.inputStream.bufferedReader().use { it.readText() }
        check(result.waitFor() == 0) { "Unable to create session event channel: $output" }
    }

    private fun deleteSessionDirectory() {
        try {
            Files.walk(sessionDirectory).use { paths ->
                paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
            }
        } catch (_: Exception) {
            // Best-effort cleanup for temporary session files.
        }
    }

    private fun javaExecutable(): String {
        val bundled = Path.of(System.getProperty("java.home"), "bin", "java")
        if (Files.isExecutable(bundled)) return bundled.toString()
        return "java"
    }

    private fun <T> onEdt(block: () -> T): T {
        if (SwingUtilities.isEventDispatchThread()) return block()
        var result: Result<T>? = null
        SwingUtilities.invokeAndWait { result = runCatching(block) }
        return result!!.getOrThrow()
    }

    private companion object {
        const val DEFAULT_COLUMNS = 100
        const val DEFAULT_ROWS = 32
        const val CTRL_C: Byte = 3
        const val CLOSE_GRACE_MILLIS = 500L
    }
}
