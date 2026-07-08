package cli.shady.app

import com.jediterm.terminal.ui.settings.SettingsProvider
import cli.shady.commands.AliasRepository
import cli.shady.commands.CommandHistoryRepository
import cli.shady.commands.GoDirRepository
import cli.shady.commands.SuggestionEngine
import cli.shady.commands.config.ShadyConfig
import cli.shady.commands.config.ColorRules
import cli.shady.terminal.PtyTerminalSessionFactory
import cli.shady.terminal.TerminalSession
import cli.shady.terminal.TerminalSessionFactory
import cli.shady.terminal.TerminalSessionSpec
import cli.shady.terminal.TerminalSessionEvent
import cli.shady.terminal.TerminalSessionStatus
import cli.shady.update.AvailableUpdate
import java.io.Closeable
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class TerminalWorkspaceController(
    private val configProvider: () -> ShadyConfig,
    private val colorRulesProvider: () -> ColorRules,
    private val aliases: AliasRepository,
    private val goDirRepository: GoDirRepository,
    private val historyRepository: CommandHistoryRepository,
    private val suggestionEngine: SuggestionEngine,
    private val workspaceRepository: WorkspaceStateRepository,
    private val settingsProviderFactory: () -> SettingsProvider,
    private val scope: CoroutineScope,
    private val initialDirectory: Path,
    private val sessionFactory: TerminalSessionFactory = PtyTerminalSessionFactory,
) : Closeable {
    private val appConfig = configProvider()
    private val runtimes = linkedMapOf<String, TerminalSession>()
    private val currentCommands = mutableMapOf<String, String>()
    private val recordedDirectories = mutableMapOf<String, String>()
    private val sudoRequested = AtomicBoolean(false)
    private val mutableState = MutableStateFlow(
        TerminalWorkspaceState(fuzzySearchEnabled = appConfig.features.fuzzySearchEnabled),
    )

    val state: StateFlow<TerminalWorkspaceState> = mutableState.asStateFlow()

    init {
        restoreOrCreateInitialTabs()
    }

    fun runtime(tabId: String): TerminalSession? = runtimes[tabId]

    fun activeRuntime(): TerminalSession? = mutableState.value.activeTabId?.let(runtimes::get)

    fun newTab(directory: Path = activeDirectory(), title: String? = null): String {
        val safeDirectory = directory.takeIf(Files::isDirectory) ?: initialDirectory
        val sessionId = UUID.randomUUID().toString()
        val session = sessionFactory.create(
            TerminalSessionSpec(
                id = sessionId,
                initialDirectory = safeDirectory,
                title = title ?: defaultTitle(safeDirectory),
                config = configProvider(),
                colorRulesProvider = colorRulesProvider,
                aliases = aliases.all(),
                settingsProvider = settingsProviderFactory(),
                scope = scope,
                onEvent = { event -> handleEvent(sessionId, event) },
            ),
        )
        runtimes[session.id] = session
        val tab = TerminalTabState(
            id = session.id,
            title = session.title,
            workingDirectory = safeDirectory.toAbsolutePath().normalize().toString(),
        )
        mutableState.update { it.copy(tabs = it.tabs + tab, activeTabId = tab.id) }
        persistWorkspace()
        return session.id
    }

    fun closeTab(tabId: String) {
        runtimes.remove(tabId)?.close()
        currentCommands.remove(tabId)
        recordedDirectories.remove(tabId)
        mutableState.update { state ->
            val index = state.tabs.indexOfFirst { it.id == tabId }
            val remaining = state.tabs.filterNot { it.id == tabId }
            val activeId = when {
                state.activeTabId != tabId -> state.activeTabId
                remaining.isEmpty() -> null
                else -> remaining[index.coerceAtMost(remaining.lastIndex)].id
            }
            state.copy(tabs = remaining, activeTabId = activeId)
        }
        if (mutableState.value.tabs.isEmpty()) {
            newTab(initialDirectory)
        } else {
            persistWorkspace()
        }
    }

    fun activateTab(tabId: String) {
        if (tabId !in runtimes) return
        mutableState.update { it.copy(activeTabId = tabId) }
        runtimes[tabId]?.requestFocus()
        persistWorkspace()
    }

    fun renameTab(tabId: String, title: String) {
        val normalized = title.trim().takeIf(String::isNotEmpty) ?: return
        mutableState.update { state ->
            state.copy(tabs = state.tabs.map { if (it.id == tabId) it.copy(title = normalized) else it })
        }
        persistWorkspace()
    }

    fun interruptActive() {
        activeRuntime()?.interrupt()
    }

    fun clearActive() {
        activeRuntime()?.write("clear\r")
    }

    fun sendToActive(text: String, execute: Boolean = false) {
        activeRuntime()?.write(if (execute) "$text\r" else text)
    }

    fun openFuzzyPalette() {
        if (!mutableState.value.fuzzySearchEnabled) return
        mutableState.update { it.copy(fuzzyPaletteOpen = true) }
    }

    fun closeFuzzyPalette() {
        mutableState.update { it.copy(fuzzyPaletteOpen = false) }
        activeRuntime()?.requestFocus()
    }

    fun dismissNotification() {
        mutableState.update { it.copy(notification = null) }
    }

    fun setAvailableUpdate(update: AvailableUpdate) {
        mutableState.update {
            it.copy(
                availableUpdate = update,
                notification = "Shady ${update.version} is available.",
            )
        }
    }

    fun installAvailableUpdate() {
        val update = mutableState.value.availableUpdate ?: return
        sendToActive("SHADY_REPO_URL=${update.repositoryUrl.shellQuote()} shady update", execute = true)
        mutableState.update { it.copy(availableUpdate = null, notification = null) }
    }

    fun closeToolWindow(id: String) {
        mutableState.update { state ->
            state.copy(toolWindows = state.toolWindows.filterNot { it.id == id })
        }
    }

    override fun close() {
        persistWorkspace()
        runtimes.values.toList().forEach(TerminalSession::close)
        runtimes.clear()
    }

    private fun handleEvent(tabId: String, event: TerminalSessionEvent) {
        when (event) {
            is TerminalSessionEvent.PromptReady -> {
                recordGoDirVisit(tabId, event.workingDirectory)
                updateTab(tabId) {
                    it.copy(
                        workingDirectory = event.workingDirectory,
                        status = TerminalSessionStatus.Running,
                        activeCommand = null,
                        isAtPrompt = true,
                    )
                }
                persistWorkspace()
                if (appConfig.security.alwaysRequestSudo && sudoRequested.compareAndSet(false, true)) {
                    runtimes[tabId]?.write("sudo -k; sudo -v\r")
                }
            }

            is TerminalSessionEvent.CommandStarted -> {
                currentCommands[tabId] = event.command
                updateTab(tabId) {
                    it.copy(activeCommand = event.command, isAtPrompt = false)
                }
            }

            is TerminalSessionEvent.CommandFinished -> {
                val command = currentCommands.remove(tabId)
                if (command != null && event.exitCode == 0 && shouldRecord(command)) {
                    historyRepository.record(command)
                    suggestionEngine.evaluateAfterExecution(command)?.let { suggestion ->
                        mutableState.update {
                            it.copy(notification = "Alias suggestion: ${suggestion.aliasAddInstruction}")
                        }
                    }
                }
                updateTab(tabId) { it.copy(lastExitCode = event.exitCode) }
            }

            is TerminalSessionEvent.InternalCommand -> handleInternalCommand(event.command)
            is TerminalSessionEvent.Exited -> updateTab(tabId) {
                it.copy(status = TerminalSessionStatus.Exited, lastExitCode = event.exitCode, isAtPrompt = false)
            }
            is TerminalSessionEvent.Failed -> {
                updateTab(tabId) { it.copy(status = TerminalSessionStatus.Failed, isAtPrompt = false) }
                mutableState.update { it.copy(notification = event.message) }
            }
        }
    }

    private fun handleInternalCommand(command: String) {
        val first = command.trim().split(Regex("\\s+")).firstOrNull() ?: return
        val window = when (first) {
            "sys", "system", "resource", "resources", "monitor", "dashboard" -> EmulatorToolWindow(
                id = "resources",
                title = "shady resources",
                type = EmulatorToolWindowType.Resources,
            )
            "windows", "window", "wins" -> EmulatorToolWindow(
                id = "mac-windows",
                title = "shady windows",
                type = EmulatorToolWindowType.MacWindows,
            )
            else -> return
        }
        mutableState.update { state ->
            state.copy(toolWindows = state.toolWindows.filterNot { it.id == window.id } + window)
        }
    }

    private fun recordGoDirVisit(tabId: String, workingDirectory: String) {
        val directory = runCatching { Path.of(workingDirectory).toAbsolutePath().normalize() }.getOrNull()
            ?: return
        val storedPath = directory.toString()
        if (recordedDirectories[tabId] == storedPath) return
        recordedDirectories[tabId] = storedPath
        runCatching { goDirRepository.recordVisit(directory) }
    }

    private fun updateTab(tabId: String, transform: (TerminalTabState) -> TerminalTabState) {
        mutableState.update { state ->
            state.copy(tabs = state.tabs.map { if (it.id == tabId) transform(it) else it })
        }
    }

    private fun restoreOrCreateInitialTabs() {
        val restored = if (appConfig.terminal.restoreTabs) workspaceRepository.load() else RestoredWorkspace()
        val candidates = restored.tabs.filter { Files.isDirectory(Path.of(it.workingDirectory)) }
        if (candidates.isEmpty()) {
            newTab(initialDirectory)
            return
        }
        val ids = candidates.map { newTab(Path.of(it.workingDirectory), it.title) }
        val activeId = ids.getOrNull(restored.activeIndex.coerceIn(0, ids.lastIndex)) ?: ids.first()
        activateTab(activeId)
    }

    private fun activeDirectory(): Path = mutableState.value.tabs
        .firstOrNull { it.id == mutableState.value.activeTabId }
        ?.workingDirectory
        ?.let(Path::of)
        ?: initialDirectory

    private fun defaultTitle(directory: Path): String =
        directory.fileName?.toString()?.takeIf(String::isNotBlank) ?: "shady"

    private fun shouldRecord(command: String): Boolean {
        val normalized = command.trim()
        val first = normalized.substringBefore(' ')
        return normalized.isNotEmpty() &&
            normalized != "sudo -k; sudo -v" &&
            first !in setOf("clear", "history", "shady")
    }

    private fun String.shellQuote(): String = "'${replace("'", "'\\''")}'"

    private fun persistWorkspace() {
        if (!appConfig.terminal.restoreTabs) return
        runCatching { workspaceRepository.save(mutableState.value) }
    }
}
