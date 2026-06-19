package cli.shady.execution

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

class ProcessCommandExecutorTest {
    @Test
    fun `streams standard output and errors from a command`() = runBlocking {
        val chunks = StringBuilder()
        val executor = unixExecutor()

        val outcome = executor.execute(
            "printf 'out'; printf 'err' >&2",
            CommandCallbacks(onStarted = {}, onOutput = chunks::append),
        )

        assertEquals("outerr", chunks.toString())
        assertEquals(0, assertIs<CommandOutcome.Finished>(outcome).exitCode)
    }

    @Test
    fun `stops a running process`() {
        runBlocking {
            val started = CompletableDeferred<Unit>()
            val executor = unixExecutor()
            val execution = async {
                executor.execute(
                    "sleep 30",
                    CommandCallbacks(onStarted = { started.complete(Unit) }, onOutput = {}),
                )
            }

            withTimeout(5.seconds) {
                started.await()
                executor.stop()
            }

            assertIs<CommandOutcome.Stopped>(execution.await())
        }
    }

    @Test
    fun `reports a process launch failure`() = runBlocking {
        val executor = ProcessCommandExecutor(
            shell = ShellInvocationFactory { listOf("/path/that/does/not/exist") },
            workingDirectory = File("."),
        )

        val outcome = executor.execute("ignored", CommandCallbacks({}, {}))

        assertTrue(assertIs<CommandOutcome.LaunchFailed>(outcome).message.isNotEmpty())
    }

    private fun unixExecutor() = ProcessCommandExecutor(
        shell = PlatformShell("Mac OS X"),
        workingDirectory = File("."),
    )
}
