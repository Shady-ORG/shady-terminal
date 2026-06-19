package cli.shady.terminal

import com.pty4j.PtyProcess
import com.pty4j.PtyProcessBuilder
import com.pty4j.WinSize
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertTrue
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable

@EnabledIfEnvironmentVariable(named = "SHADY_PTY_INTEGRATION", matches = "true")
class PtyProcessIntegrationTest {
    @Test
    fun `pty accepts input and reports resized terminal dimensions`() {
        val process = zsh()
        process.setWinSize(WinSize(120, 40))

        process.writeLine("read answer; printf 'INPUT:%s\\n' \"${'$'}answer\"; stty size; exit")
        process.writeLine("hello")
        val output = process.finish()

        assertContains(output, "INPUT:hello")
        assertContains(output, "40 120")
    }

    @Test
    fun `ctrl c interrupts the foreground command`() {
        val process = zsh()
        process.writeLine("sleep 30")
        Thread.sleep(150)
        process.outputStream.write(byteArrayOf(3))
        process.outputStream.flush()
        process.writeLine("printf 'INTERRUPTED\\n'; exit")

        assertContains(process.finish(), "INTERRUPTED")
    }

    private fun zsh(): PtyProcess = PtyProcessBuilder(arrayOf("/bin/zsh", "-f", "-i"))
        .setEnvironment(HashMap(System.getenv()).apply { put("TERM", "xterm-256color") })
        .setDirectory(System.getProperty("user.dir"))
        .setRedirectErrorStream(true)
        .setInitialColumns(80)
        .setInitialRows(24)
        .start()

    private fun PtyProcess.writeLine(text: String) {
        outputStream.write(text.toByteArray(StandardCharsets.UTF_8))
        outputStream.write(byteArrayOf(enterKeyCode))
        outputStream.flush()
    }

    private fun PtyProcess.finish(): String {
        if (!waitFor(5, TimeUnit.SECONDS)) {
            val availableOutput = inputStream.readNBytes(inputStream.available()).toString(StandardCharsets.UTF_8)
            destroyForcibly()
            assertTrue(false, "PTY process did not finish. Output: $availableOutput")
        }
        return inputStream.readAllBytes().toString(StandardCharsets.UTF_8)
    }
}
