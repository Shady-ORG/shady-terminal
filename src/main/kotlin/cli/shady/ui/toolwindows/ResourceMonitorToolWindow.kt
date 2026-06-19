package cli.shady.ui.toolwindows

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import cli.shady.system.SystemResourceMonitor
import cli.shady.system.SystemResourceSnapshot
import cli.shady.ui.theme.ShadyAlpha
import cli.shady.ui.theme.ShadyPalette
import cli.shady.ui.theme.ShadySizes
import cli.shady.ui.theme.ShadySpacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@Composable
fun ResourceMonitorToolWindow(
    monitor: SystemResourceMonitor,
    modifier: Modifier = Modifier,
) {
    var snapshot by remember { mutableStateOf(SystemResourceSnapshot.Empty) }
    var previousSample by remember { mutableStateOf<ResourceSample?>(null) }
    var networkInRate by remember { mutableStateOf(0.0) }
    var networkOutRate by remember { mutableStateOf(0.0) }
    var cpuHistory by remember { mutableStateOf(emptyList<Float>()) }

    LaunchedEffect(monitor) {
        while (true) {
            val next = withContext(Dispatchers.IO) { monitor.snapshot() }
            val now = System.nanoTime()
            previousSample?.let { previous ->
                val elapsedSeconds = (now - previous.timestampNanos) / 1_000_000_000.0
                if (elapsedSeconds > 0.0) {
                    networkInRate = ((next.networkReceivedBytes - previous.snapshot.networkReceivedBytes)
                        .coerceAtLeast(0L)) / elapsedSeconds
                    networkOutRate = ((next.networkSentBytes - previous.snapshot.networkSentBytes)
                        .coerceAtLeast(0L)) / elapsedSeconds
                }
            }
            previousSample = ResourceSample(next, now)
            snapshot = next
            cpuHistory = (cpuHistory + next.cpuPercent.toFloat()).takeLast(MAX_HISTORY_POINTS)
            delay(REFRESH_INTERVAL_MS)
        }
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = ShadyPalette.TerminalBackground,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(ShadySpacing.Large),
            verticalArrangement = Arrangement.spacedBy(ShadySpacing.Medium),
        ) {
            ToolWindowHeader(
                title = "system",
                subtitle = "live",
                trailing = "${snapshot.systemLabel} • ${snapshot.coreCount} cores • up ${formatDuration(snapshot.uptimeSeconds)}",
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ShadySpacing.Medium),
            ) {
                MetricCard(
                    title = "CPU",
                    value = "${snapshot.cpuPercent.roundToInt()}%",
                    accent = ShadyPalette.Info,
                    progress = snapshot.cpuPercent / 100.0,
                    modifier = Modifier.weight(1f),
                ) {
                    Sparkline(values = cpuHistory, color = ShadyPalette.Info)
                }

                MetricCard(
                    title = "MEM",
                    value = formatPercent(snapshot.memoryUsedBytes, snapshot.memoryTotalBytes),
                    detail = "${formatBytes(snapshot.memoryUsedBytes)} / ${formatBytes(snapshot.memoryTotalBytes)}",
                    accent = ShadyPalette.Success,
                    progress = ratio(snapshot.memoryUsedBytes, snapshot.memoryTotalBytes),
                    modifier = Modifier.weight(1f),
                ) {
                    Donut(progress = ratio(snapshot.memoryUsedBytes, snapshot.memoryTotalBytes), color = ShadyPalette.Success)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ShadySpacing.Medium),
            ) {
                MetricCard(
                    title = "NET",
                    value = formatRate(networkInRate + networkOutRate),
                    detail = "in ${formatRate(networkInRate)} • out ${formatRate(networkOutRate)}",
                    accent = ShadyPalette.Net,
                    progress = 0.0,
                    modifier = Modifier.weight(1f),
                )

                MetricCard(
                    title = "DISK",
                    value = formatPercent(snapshot.diskUsedBytes, snapshot.diskTotalBytes),
                    detail = "${formatBytes(snapshot.diskUsedBytes)} / ${formatBytes(snapshot.diskTotalBytes)}",
                    accent = ShadyPalette.Disk,
                    progress = ratio(snapshot.diskUsedBytes, snapshot.diskTotalBytes),
                    modifier = Modifier.weight(1f),
                ) {
                    Donut(progress = ratio(snapshot.diskUsedBytes, snapshot.diskTotalBytes), color = ShadyPalette.Disk)
                }
            }
        }
    }
}

@Composable
private fun ToolWindowHeader(
    title: String,
    subtitle: String,
    trailing: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(ShadyPalette.TerminalPanel, MaterialTheme.shapes.medium)
            .border(ShadySizes.BorderWidth, ShadyPalette.TerminalBorder, MaterialTheme.shapes.medium)
            .padding(ShadySpacing.Medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "$title • $subtitle",
            style = MaterialTheme.typography.titleMedium,
            color = ShadyPalette.Text,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = trailing,
            style = MaterialTheme.typography.labelMedium,
            color = ShadyPalette.MutedText,
        )
    }
}

@Composable
private fun MetricCard(
    title: String,
    value: String,
    accent: Color,
    progress: Double,
    modifier: Modifier = Modifier,
    detail: String? = null,
    visual: @Composable (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .height(ShadySizes.ResourceMetricCardHeight)
            .background(ShadyPalette.TerminalPanel, MaterialTheme.shapes.medium)
            .border(ShadySizes.BorderWidth, ShadyPalette.TerminalBorder, MaterialTheme.shapes.medium)
            .padding(ShadySpacing.Medium),
        verticalArrangement = Arrangement.spacedBy(ShadySpacing.Small),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = ShadyPalette.MutedText,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                color = accent,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold,
            )
        }

        if (detail != null) {
            Text(
                text = detail,
                style = MaterialTheme.typography.bodyMedium,
                color = ShadyPalette.MutedText,
                maxLines = 1,
            )
        }

        visual?.invoke()
        Spacer(Modifier.weight(1f))
        LinearProgressIndicator(
            progress = { progress.toFloat().coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth(),
            color = accent,
            trackColor = accent.copy(alpha = ShadyAlpha.Track),
        )
    }
}

@Composable
private fun Sparkline(
    values: List<Float>,
    color: Color,
) {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(ShadySizes.ResourceSparklineHeight),
    ) {
        if (values.size < 2) return@Canvas

        val step = size.width / (values.size - 1)
        val points = values.mapIndexed { index, value ->
            Offset(
                x = index * step,
                y = size.height - (value.coerceIn(0f, 100f) / 100f * size.height),
            )
        }
        points.zipWithNext().forEach { (start, end) ->
            drawLine(
                color = color,
                start = start,
                end = end,
                strokeWidth = ShadySizes.ResourceSparklineStroke.toPx(),
                cap = StrokeCap.Round,
            )
        }
    }
}

@Composable
private fun Donut(
    progress: Double,
    color: Color,
) {
    Canvas(modifier = Modifier.size(ShadySizes.ResourceDonutSize)) {
        val stroke = Stroke(width = ShadySizes.ResourceDonutStroke.toPx(), cap = StrokeCap.Round)
        drawArc(
            color = color.copy(alpha = ShadyAlpha.Track),
            startAngle = -90f,
            sweepAngle = 360f,
            useCenter = false,
            style = stroke,
            size = Size(size.width, size.height),
        )
        drawArc(
            color = color,
            startAngle = -90f,
            sweepAngle = (progress.coerceIn(0.0, 1.0) * 360.0).toFloat(),
            useCenter = false,
            style = stroke,
            size = Size(size.width, size.height),
        )
    }
}

private data class ResourceSample(
    val snapshot: SystemResourceSnapshot,
    val timestampNanos: Long,
)

private fun ratio(used: Long, total: Long): Double =
    if (total <= 0L) 0.0 else used.toDouble() / total.toDouble()

private fun formatPercent(used: Long, total: Long): String =
    "${(ratio(used, total) * 100).roundToInt()}%"

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val units = listOf("B", "KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var index = 0
    while (value >= 1024.0 && index < units.lastIndex) {
        value /= 1024.0
        index++
    }
    return if (index == 0) "${value.roundToInt()} ${units[index]}" else "%.1f %s".format(value, units[index])
}

private fun formatRate(bytesPerSecond: Double): String =
    "${formatBytes(bytesPerSecond.roundToInt().toLong())}/s"

private fun formatDuration(seconds: Long): String {
    val days = seconds / 86_400
    val hours = (seconds % 86_400) / 3_600
    val minutes = (seconds % 3_600) / 60
    return if (days > 0) "${days}d ${hours}h" else "${hours}h ${minutes}m"
}

private const val REFRESH_INTERVAL_MS = 2_000L
private const val MAX_HISTORY_POINTS = 32
