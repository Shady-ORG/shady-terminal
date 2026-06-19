package cli.shady.ui.toolwindows

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import cli.shady.system.MacWindowInfo
import cli.shady.system.MacWindowRepository
import cli.shady.ui.theme.ShadyPalette
import cli.shady.ui.theme.ShadySizes
import cli.shady.ui.theme.ShadySpacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun MacWindowsToolWindow(
    repository: MacWindowRepository,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var windows by remember { mutableStateOf(emptyList<MacWindowInfo>()) }
    var error by remember { mutableStateOf<String?>(null) }
    var focusError by remember { mutableStateOf<String?>(null) }

    fun refresh() {
        scope.launch {
            val result = withContext(Dispatchers.IO) { repository.listOpenWindows() }
            windows = result.windows
            error = result.error
        }
    }

    LaunchedEffect(repository) {
        while (true) {
            val result = withContext(Dispatchers.IO) { repository.listOpenWindows() }
            windows = result.windows
            error = result.error
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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ShadyPalette.TerminalPanel, MaterialTheme.shapes.medium)
                    .border(ShadySizes.BorderWidth, ShadyPalette.TerminalBorder, MaterialTheme.shapes.medium)
                    .padding(ShadySpacing.Medium),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(ShadySpacing.Medium),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "windows • ${windows.size} open",
                        style = MaterialTheme.typography.titleMedium,
                        color = ShadyPalette.Text,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "click a row to jump to that macOS window",
                        style = MaterialTheme.typography.labelMedium,
                        color = ShadyPalette.MutedText,
                    )
                }
                Button(onClick = ::refresh) {
                    Text("Refresh")
                }
            }

            val message = focusError ?: error
            if (message != null) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (error != null) ShadyPalette.Warning else ShadyPalette.Danger,
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(ShadyPalette.TerminalPanel, MaterialTheme.shapes.medium)
                    .border(ShadySizes.BorderWidth, ShadyPalette.TerminalBorder, MaterialTheme.shapes.medium)
                    .verticalScroll(rememberScrollState()),
            ) {
                if (windows.isEmpty() && error == null) {
                    Text(
                        text = "No visible macOS windows found.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = ShadyPalette.MutedText,
                        modifier = Modifier.padding(ShadySpacing.Large),
                    )
                }

                windows.forEachIndexed { index, window ->
                    WindowRow(
                        number = index + 1,
                        window = window,
                        highlighted = index == 0,
                        onClick = {
                            scope.launch {
                                focusError = withContext(Dispatchers.IO) { repository.focus(window) }
                            }
                        },
                    )
                    if (index < windows.lastIndex) {
                        HorizontalDivider(color = ShadyPalette.TerminalBorder)
                    }
                }
            }
        }
    }
}

@Composable
private fun WindowRow(
    number: Int,
    window: MacWindowInfo,
    highlighted: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (highlighted) ShadyPalette.RowActive else ShadyPalette.TerminalPanel)
            .clickable(onClick = onClick)
            .padding(horizontal = ShadySpacing.Large, vertical = ShadySpacing.Medium),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ShadySpacing.Medium),
    ) {
        Text(
            text = number.toString().padStart(2, '0'),
            style = MaterialTheme.typography.labelMedium,
            color = ShadyPalette.DimText,
            fontFamily = FontFamily.Monospace,
        )
        Column(modifier = Modifier.weight(1.15f)) {
            Text(
                text = window.displayTitle,
                style = MaterialTheme.typography.bodyMedium,
                color = ShadyPalette.Text,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = window.appName,
                style = MaterialTheme.typography.labelMedium,
                color = ShadyPalette.MutedText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = "pid ${window.processId} • window ${window.windowIndex}",
            style = MaterialTheme.typography.labelMedium,
            color = ShadyPalette.DimText,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(0.85f),
        )
    }
}

private const val REFRESH_INTERVAL_MS = 3_000L
