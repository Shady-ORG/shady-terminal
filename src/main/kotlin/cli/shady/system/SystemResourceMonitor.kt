package cli.shady.system

import java.io.File
import java.lang.management.ManagementFactory
import java.time.Instant
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.max

data class SystemResourceSnapshot(
    val cpuPercent: Double,
    val memoryUsedBytes: Long,
    val memoryTotalBytes: Long,
    val diskUsedBytes: Long,
    val diskTotalBytes: Long,
    val networkReceivedBytes: Long,
    val networkSentBytes: Long,
    val uptimeSeconds: Long,
    val coreCount: Int,
    val systemLabel: String,
) {
    companion object {
        val Empty = SystemResourceSnapshot(
            cpuPercent = 0.0,
            memoryUsedBytes = 0L,
            memoryTotalBytes = 0L,
            diskUsedBytes = 0L,
            diskTotalBytes = 0L,
            networkReceivedBytes = 0L,
            networkSentBytes = 0L,
            uptimeSeconds = 0L,
            coreCount = Runtime.getRuntime().availableProcessors(),
            systemLabel = System.getProperty("os.name"),
        )
    }
}

class SystemResourceMonitor {
    fun snapshot(): SystemResourceSnapshot {
        val memory = memoryUsage()
        val disk = diskUsage()
        val network = networkUsage()

        return SystemResourceSnapshot(
            cpuPercent = cpuPercent(),
            memoryUsedBytes = memory.usedBytes,
            memoryTotalBytes = memory.totalBytes,
            diskUsedBytes = disk.usedBytes,
            diskTotalBytes = disk.totalBytes,
            networkReceivedBytes = network.receivedBytes,
            networkSentBytes = network.sentBytes,
            uptimeSeconds = uptimeSeconds(),
            coreCount = Runtime.getRuntime().availableProcessors(),
            systemLabel = systemLabel(),
        )
    }

    private fun cpuPercent(): Double {
        val bean = ManagementFactory.getOperatingSystemMXBean()
            as? com.sun.management.OperatingSystemMXBean
        val cpuLoad = bean?.cpuLoad ?: -1.0
        if (cpuLoad >= 0.0) {
            return (cpuLoad * 100.0).coerceIn(0.0, 100.0)
        }

        val output = commandOutput("top", "-l", "1", "-n", "0") ?: return 0.0
        val idle = Regex("""([\d.]+)%\s+idle""")
            .find(output)
            ?.groupValues
            ?.getOrNull(1)
            ?.toDoubleOrNull()
            ?: return 0.0
        return (100.0 - idle).coerceIn(0.0, 100.0)
    }

    private fun memoryUsage(): ByteUsage {
        val bean = ManagementFactory.getOperatingSystemMXBean()
            as? com.sun.management.OperatingSystemMXBean
        val total = bean?.totalMemorySize?.takeIf { it > 0L }
            ?: sysctlLong("hw.memsize")
            ?: 0L
        val free = bean?.freeMemorySize?.takeIf { it >= 0L } ?: 0L
        return ByteUsage(
            usedBytes = max(0L, total - free),
            totalBytes = total,
        )
    }

    private fun diskUsage(): ByteUsage {
        val root = File("/")
        val total = root.totalSpace
        return ByteUsage(
            usedBytes = max(0L, total - root.usableSpace),
            totalBytes = total,
        )
    }

    private fun networkUsage(): NetworkUsage {
        val output = commandOutput("netstat", "-ibn") ?: return NetworkUsage()
        var received = 0L
        var sent = 0L

        output.lineSequence()
            .filter { it.isNotBlank() && !it.startsWith("Name") }
            .forEach { line ->
                val parts = line.trim().split(Regex("""\s+"""))
                if (parts.size < 10 || parts.first().startsWith("lo")) return@forEach
                val inputBytes = parts.getOrNull(6)?.toLongOrNull() ?: return@forEach
                val outputBytes = parts.getOrNull(9)?.toLongOrNull() ?: return@forEach
                received += inputBytes
                sent += outputBytes
            }

        return NetworkUsage(receivedBytes = received, sentBytes = sent)
    }

    private fun uptimeSeconds(): Long {
        val output = commandOutput("sysctl", "-n", "kern.boottime") ?: return 0L
        val bootEpoch = Regex("""sec\s*=\s*(\d+)""")
            .find(output)
            ?.groupValues
            ?.getOrNull(1)
            ?.toLongOrNull()
            ?: return 0L
        return max(0L, Instant.now().epochSecond - bootEpoch)
    }

    private fun systemLabel(): String {
        val osName = System.getProperty("os.name").lowercase(Locale.ROOT)
        if (!osName.contains("mac")) {
            return System.getProperty("os.name")
        }

        val productVersion = commandOutput("sw_vers", "-productVersion")
            ?.trim()
            ?.takeIf(String::isNotBlank)
        return if (productVersion != null) "macOS $productVersion" else "macOS"
    }

    private fun sysctlLong(name: String): Long? =
        commandOutput("sysctl", "-n", name)
            ?.trim()
            ?.toLongOrNull()

    private fun commandOutput(vararg command: String): String? {
        val process = try {
            ProcessBuilder(*command)
                .redirectErrorStream(true)
                .start()
        } catch (_: Exception) {
            return null
        }

        if (!process.waitFor(COMMAND_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
            process.destroyForcibly()
            return null
        }

        return process.inputStream.bufferedReader().use { it.readText() }
    }

    private data class ByteUsage(
        val usedBytes: Long,
        val totalBytes: Long,
    )

    private data class NetworkUsage(
        val receivedBytes: Long = 0L,
        val sentBytes: Long = 0L,
    )

    private companion object {
        const val COMMAND_TIMEOUT_MS = 1_500L
    }
}
