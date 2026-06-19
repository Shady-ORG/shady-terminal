package cli.shady.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import cli.shady.app.TerminalTabState
import cli.shady.ui.theme.ShadyPalette
import cli.shady.ui.theme.ShadySizes
import cli.shady.ui.theme.ShadySpacing

@Composable
fun TerminalTabStrip(
    tabs: List<TerminalTabState>,
    activeTabId: String?,
    onActivate: (String) -> Unit,
    onClose: (String) -> Unit,
    onNewTab: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(ShadySizes.TerminalTabHeight)
            .background(ShadyPalette.TerminalPanel)
            .horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ShadySpacing.Small),
    ) {
        tabs.forEach { tab ->
            val active = tab.id == activeTabId
            Row(
                modifier = Modifier
                    .height(ShadySizes.TerminalTabHeight)
                    .background(
                        if (active) ShadyPalette.RowActive else ShadyPalette.TerminalPanel,
                        MaterialTheme.shapes.small,
                    )
                    .clickable { onActivate(tab.id) }
                    .padding(start = ShadySpacing.Medium),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = tab.title,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (active) ShadyPalette.Text else ShadyPalette.MutedText,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                IconButton(onClick = { onClose(tab.id) }) {
                    Text("×", color = ShadyPalette.MutedText)
                }
            }
        }
        Box(
            modifier = Modifier
                .height(ShadySizes.TerminalTabHeight)
                .clickable(onClick = onNewTab)
                .padding(horizontal = ShadySpacing.Medium),
            contentAlignment = Alignment.Center,
        ) {
            Text("+", color = ShadyPalette.MutedText, style = MaterialTheme.typography.titleMedium)
        }
    }
}
