package cli.shady.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import cli.shady.app.TerminalWorkspaceState
import cli.shady.app.FuzzyCandidate
import cli.shady.terminal.TerminalSession
import cli.shady.ui.components.TerminalTabStrip
import cli.shady.ui.theme.ShadyPalette
import cli.shady.ui.theme.ShadySizes
import cli.shady.ui.theme.ShadySpacing
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent

@Composable
fun TerminalWorkspaceWindow(
    state: TerminalWorkspaceState,
    runtimeFor: (String) -> TerminalSession?,
    onActivateTab: (String) -> Unit,
    onCloseTab: (String) -> Unit,
    onNewTab: () -> Unit,
    onOpenFuzzy: () -> Unit,
    onSearchFuzzy: (String) -> List<FuzzyCandidate>,
    onSelectFuzzy: (FuzzyCandidate) -> Unit,
    onCloseFuzzy: () -> Unit,
    onDismissNotification: () -> Unit,
    onInstallUpdate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize(), color = ShadyPalette.TerminalBackground) {
        Column(Modifier.fillMaxSize()) {
            TerminalTabStrip(
                tabs = state.tabs,
                activeTabId = state.activeTabId,
                onActivate = onActivateTab,
                onClose = onCloseTab,
                onNewTab = onNewTab,
            )

            if (state.fuzzyPaletteOpen) {
                FuzzyPalette(
                    search = onSearchFuzzy,
                    onSelect = onSelectFuzzy,
                    onClose = onCloseFuzzy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(ShadySizes.FuzzyPanelHeight),
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(ShadyPalette.TerminalBackground),
            ) {
                state.tabs.forEach { tab ->
                    val runtime = runtimeFor(tab.id)
                    val isActive = tab.id == state.activeTabId
                    if (runtime != null) {
                        key(tab.id) {
                            if (isActive) {
                                val fuzzyKeyListener = object : KeyAdapter() {
                                    override fun keyPressed(event: KeyEvent) {
                                        if (state.fuzzySearchEnabled && tab.isAtPrompt && event.isControlDown && event.keyCode == KeyEvent.VK_R) {
                                            event.consume()
                                            onOpenFuzzy()
                                        }
                                    }
                                }
                                DisposableEffect(runtime, tab.isAtPrompt) {
                                    runtime.addKeyListener(fuzzyKeyListener)
                                    onDispose {
                                        runtime.removeKeyListener(fuzzyKeyListener)
                                    }
                                }
                                LaunchedEffect(Unit) {
                                    runtime.requestFocus()
                                }
                            }
                            SwingPanel(
                                factory = { runtime.component },
                                modifier = if (isActive) Modifier.fillMaxSize() else Modifier.size(0.dp),
                            )
                        }
                    }
                }
            }

            state.notification?.let { message ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(ShadyPalette.TerminalPanelElevated)
                        .padding(horizontal = ShadySpacing.Medium, vertical = ShadySpacing.Small),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = message,
                        style = MaterialTheme.typography.labelMedium,
                        color = ShadyPalette.Warning,
                        modifier = Modifier.weight(1f),
                    )
                    if (state.availableUpdate != null) {
                        Text(
                            text = "Update",
                            color = ShadyPalette.Success,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier
                                .clickable(onClick = onInstallUpdate)
                                .padding(ShadySpacing.Small),
                        )
                    }
                    Text(
                        text = "×",
                        color = ShadyPalette.MutedText,
                        modifier = Modifier
                            .clickable(onClick = onDismissNotification)
                            .padding(ShadySpacing.Small),
                    )
                }
            }
        }
    }
}
