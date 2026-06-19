package cli.shady.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import cli.shady.app.FuzzyCandidate
import cli.shady.app.FuzzyCandidateSource
import cli.shady.ui.theme.ShadyPalette
import cli.shady.ui.theme.ShadySpacing

@Composable
fun FuzzyPalette(
    search: (String) -> List<FuzzyCandidate>,
    onSelect: (FuzzyCandidate) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by remember { mutableStateOf("") }
    var selectedIndex by remember { mutableIntStateOf(0) }
    val focusRequester = remember { FocusRequester() }
    val results = remember(query) { search(query) }
    if (selectedIndex > results.lastIndex) selectedIndex = results.lastIndex.coerceAtLeast(0)

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ShadyPalette.TerminalBackground)
            .padding(ShadySpacing.Medium),
    ) {
        TextField(
            value = query,
            onValueChange = {
                query = it
                selectedIndex = 0
            },
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (event.key) {
                        Key.DirectionDown -> {
                            selectedIndex = (selectedIndex + 1).coerceAtMost(results.lastIndex.coerceAtLeast(0))
                            true
                        }
                        Key.DirectionUp -> {
                            selectedIndex = (selectedIndex - 1).coerceAtLeast(0)
                            true
                        }
                        Key.Enter -> {
                            results.getOrNull(selectedIndex)?.let(onSelect)
                            true
                        }
                        Key.Escape -> {
                            onClose()
                            true
                        }
                        else -> false
                    }
                },
            placeholder = { Text("Fuzzy search history, aliases, commands and files…") },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = ShadyPalette.TerminalPanel,
                unfocusedContainerColor = ShadyPalette.TerminalPanel,
                focusedTextColor = ShadyPalette.Text,
                cursorColor = ShadyPalette.Success,
                focusedIndicatorColor = ShadyPalette.TerminalBorderStrong,
            ),
        )

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            itemsIndexed(results, key = { _, item -> "${item.source}:${item.value}" }) { index, item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (index == selectedIndex) ShadyPalette.RowActive else ShadyPalette.TerminalBackground)
                        .clickable { onSelect(item) }
                        .padding(horizontal = ShadySpacing.Medium, vertical = ShadySpacing.Small),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = sourceLabel(item.source),
                        style = MaterialTheme.typography.labelMedium,
                        color = ShadyPalette.Info,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(end = ShadySpacing.Medium),
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.value,
                            style = MaterialTheme.typography.bodyMedium,
                            color = ShadyPalette.Text,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        item.detail?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.labelMedium,
                                color = ShadyPalette.MutedText,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun sourceLabel(source: FuzzyCandidateSource): String = when (source) {
    FuzzyCandidateSource.History -> "HIST"
    FuzzyCandidateSource.Alias -> "ALIAS"
    FuzzyCandidateSource.Shady -> "SHADY"
    FuzzyCandidateSource.Executable -> "CMD"
    FuzzyCandidateSource.File -> "FILE"
}
