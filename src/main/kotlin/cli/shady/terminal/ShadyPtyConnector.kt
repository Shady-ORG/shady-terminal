package cli.shady.terminal

import com.jediterm.core.util.TermSize
import com.jediterm.terminal.ProcessTtyConnector
import com.pty4j.PtyProcess
import com.pty4j.WinSize
import java.nio.charset.StandardCharsets

class ShadyPtyConnector(
    private val ptyProcess: PtyProcess,
    commandLine: List<String>,
    private val sessionName: String,
    private val colorizer: AnsiOutputColorizer,
) : ProcessTtyConnector(ptyProcess, StandardCharsets.UTF_8, commandLine) {
    private val pendingOutput = StringBuilder()

    override fun getName(): String = sessionName

    @Synchronized
    override fun read(buffer: CharArray, offset: Int, length: Int): Int {
        if (pendingOutput.isEmpty()) {
            val source = CharArray(length.coerceAtLeast(4_096))
            val count = super.read(source, 0, source.size)
            if (count <= 0) return count
            pendingOutput.append(colorizer.transform(String(source, 0, count)))
        }
        val count = minOf(length, pendingOutput.length)
        pendingOutput.getChars(0, count, buffer, offset)
        pendingOutput.delete(0, count)
        return count
    }

    fun setActiveCommand(command: String?) {
        colorizer.setActiveCommand(command)
    }

    override fun resize(termSize: TermSize) {
        ptyProcess.setWinSize(WinSize(termSize.columns, termSize.rows))
    }
}
