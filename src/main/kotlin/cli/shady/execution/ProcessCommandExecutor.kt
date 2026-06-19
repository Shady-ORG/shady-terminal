package cli.shady.execution

import java.io.File
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ProcessCommandExecutor(
    private val shell: ShellInvocationFactory = PlatformShell(),
    private val workingDirectory: File = File(System.getProperty("user.dir")),
    private val stopGracePeriod: Duration = 750.milliseconds,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : CommandExecutor {
    private val activeProcess = AtomicReference<Process?>(null)
    private val stopRequested = AtomicBoolean(false)

    override suspend fun execute(command: String, callbacks: CommandCallbacks): CommandOutcome =
        withContext(ioDispatcher) {
            var process: Process? = null
            try {
                process = ProcessBuilder(shell.invocationFor(command))
                    .directory(workingDirectory)
                    .redirectErrorStream(true)
                    .start()

                check(activeProcess.compareAndSet(null, process)) {
                    "This executor already has a running command."
                }

                callbacks.onStarted()
                if (stopRequested.get()) {
                    terminate(process)
                }

                process.inputStream.bufferedReader(StandardCharsets.UTF_8).use { reader ->
                    val buffer = CharArray(OUTPUT_CHUNK_SIZE)
                    while (true) {
                        val count = reader.read(buffer)
                        if (count < 0) {
                            break
                        }
                        if (count > 0) {
                            callbacks.onOutput(String(buffer, 0, count))
                        }
                    }
                }

                val exitCode = process.waitFor()
                if (stopRequested.get()) {
                    CommandOutcome.Stopped(exitCode)
                } else {
                    CommandOutcome.Finished(exitCode)
                }
            } catch (error: Exception) {
                if (stopRequested.get()) {
                    CommandOutcome.Stopped(process.exitCodeIfFinished())
                } else {
                    CommandOutcome.LaunchFailed(error.message ?: "Unable to start command.")
                }
            } finally {
                process?.let { activeProcess.compareAndSet(it, null) }
            }
        }

    override suspend fun stop() {
        stopRequested.set(true)
        withContext(ioDispatcher) {
            activeProcess.get()?.let(::terminate)
        }
    }

    private fun terminate(process: Process) {
        val descendants = runCatching {
            process.toHandle().descendants().toList().asReversed()
        }.getOrDefault(emptyList())
        if (descendants.isEmpty()) {
            signalDirectChildren(process.pid(), force = false)
        } else {
            descendants.forEach { handle -> handle.destroy() }
        }
        process.destroy()

        if (!process.waitFor(stopGracePeriod.inWholeMilliseconds, TimeUnit.MILLISECONDS)) {
            if (descendants.isEmpty()) {
                signalDirectChildren(process.pid(), force = true)
            } else {
                descendants.filter(ProcessHandle::isAlive).forEach { handle -> handle.destroyForcibly() }
            }
            if (process.isAlive) {
                process.destroyForcibly()
            }
            process.waitFor(stopGracePeriod.inWholeMilliseconds, TimeUnit.MILLISECONDS)
        }
    }

    private fun signalDirectChildren(parentPid: Long, force: Boolean) {
        runCatching {
            ProcessBuilder(
                "/usr/bin/pkill",
                if (force) "-KILL" else "-TERM",
                "-P",
                parentPid.toString(),
            ).start().waitFor(stopGracePeriod.inWholeMilliseconds, TimeUnit.MILLISECONDS)
        }
    }

    private fun Process?.exitCodeIfFinished(): Int? =
        this?.takeUnless(Process::isAlive)?.exitValue()

    private companion object {
        const val OUTPUT_CHUNK_SIZE = 4_096
    }
}
