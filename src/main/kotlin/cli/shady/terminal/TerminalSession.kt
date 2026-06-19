package cli.shady.terminal

import cli.shady.commands.AliasEntry
import cli.shady.commands.config.ColorRules
import cli.shady.commands.config.ShadyConfig
import com.jediterm.terminal.ui.settings.SettingsProvider
import java.awt.event.KeyListener
import java.io.Closeable
import java.nio.file.Path
import javax.swing.JComponent
import kotlinx.coroutines.CoroutineScope

interface TerminalSession : Closeable {
    val id: String
    val title: String
    val component: JComponent

    fun write(text: String)
    fun interrupt()
    fun clearBuffer()
    fun resize(columns: Int, rows: Int)
    fun requestFocus()
    fun addKeyListener(listener: KeyListener)
    fun removeKeyListener(listener: KeyListener)
}

data class TerminalSessionSpec(
    val id: String,
    val initialDirectory: Path,
    val title: String,
    val config: ShadyConfig,
    val colorRulesProvider: () -> ColorRules,
    val aliases: List<AliasEntry>,
    val settingsProvider: SettingsProvider,
    val scope: CoroutineScope,
    val onEvent: (TerminalSessionEvent) -> Unit,
)

fun interface TerminalSessionFactory {
    fun create(spec: TerminalSessionSpec): TerminalSession
}

object PtyTerminalSessionFactory : TerminalSessionFactory {
    override fun create(spec: TerminalSessionSpec): TerminalSession = PtyTerminalSession(
        id = spec.id,
        initialDirectory = spec.initialDirectory,
        title = spec.title,
        config = spec.config,
        colorRulesProvider = spec.colorRulesProvider,
        aliases = spec.aliases,
        settingsProvider = spec.settingsProvider,
        scope = spec.scope,
        onEvent = spec.onEvent,
    )
}
