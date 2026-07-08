package cli.shady

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import cli.shady.app.EmulatorToolWindowType
import cli.shady.app.FuzzySearchEngine
import cli.shady.app.TerminalWorkspaceController
import cli.shady.app.WorkspaceStateRepository
import cli.shady.cli.CliInput
import cli.shady.cli.CommandLine
import cli.shady.cli.CommandLineRequest
import cli.shady.commands.AliasNameDeriver
import cli.shady.commands.AliasRepository
import cli.shady.commands.CommandHistoryRepository
import cli.shady.commands.CommandRegistry
import cli.shady.commands.CommandRequest
import cli.shady.commands.GitPrehookScriptsRepository
import cli.shady.commands.GoDirRepository
import cli.shady.commands.SuggestionEngine
import cli.shady.commands.builtins.AliasCommand
import cli.shady.commands.builtins.ColorCommandsCommand
import cli.shady.commands.builtins.ConfigCommand
import cli.shady.commands.builtins.EmulatorOnlyCommand
import cli.shady.commands.builtins.GitPrehookScripts
import cli.shady.commands.builtins.GoDirCommand
import cli.shady.commands.builtins.HelpCommand
import cli.shady.commands.builtins.StylesCommand
import cli.shady.commands.builtins.UpdateCommand
import cli.shady.commands.config.ColorRulesRepository
import cli.shady.commands.config.ShadyConfigService
import cli.shady.commands.config.ShadyPaths
import cli.shady.execution.CommandCallbacks
import cli.shady.execution.CommandOutcome
import cli.shady.execution.ProcessCommandExecutor
import cli.shady.system.MacWindowRepository
import cli.shady.system.SystemResourceMonitor
import cli.shady.ui.TerminalWorkspaceWindow
import cli.shady.ui.theme.ShadySizes
import cli.shady.ui.theme.ShadyTerminalSettings
import cli.shady.ui.theme.ShadyTheme
import cli.shady.ui.toolwindows.MacWindowsToolWindow
import cli.shady.ui.toolwindows.ResourceMonitorToolWindow
import cli.shady.update.UpdateCheckResult
import cli.shady.update.UpdateChecker
import java.awt.Dimension
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.Comparator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    if (args.contentEquals(arrayOf(IDE_SHELL_FLAG))) {
        runIdeShell()
        return
    }

    if (tryHandleEmbeddedCommand(args)) {
        return
    }

    val projectRoot = Path.of(System.getProperty("user.dir"))
    val services = shadyServices(projectRoot)

    when (val request = CommandLine.parse(args)) {
        is CommandLineRequest.Start -> runTerminalMode(services)
        is CommandLineRequest.Execute -> exitProcess(
            runCliCommand(
                input = request.input,
                registry = services.registry,
                workingDirectory = File(System.getProperty("user.dir")),
            ),
        )
        is CommandLineRequest.Invalid -> {
            System.err.println(request.message)
            exitProcess(CommandLine.INVALID_ARGUMENT_EXIT_CODE)
        }
    }
}

private fun runIdeShell() {
    val projectRoot = Path.of(System.getProperty("user.dir"))
    val services = shadyServices(projectRoot)
    val config = services.configService.load()
    val sessionDirectory = Files.createTempDirectory("shady-ide-shell-")
    val exitCode = try {
        Files.writeString(
            sessionDirectory.resolve(".zshrc"),
            cli.shady.terminal.ZshIntegrationScript(
                config = config,
                aliases = services.aliasRepository.all(),
                eventChannelEnabled = false,
            ).render(),
        )
        val environment = HashMap(System.getenv()).apply {
            put("ZDOTDIR", sessionDirectory.toString())
            put("SHADY_ORIGINAL_ZDOTDIR", System.getenv("ZDOTDIR") ?: System.getProperty("user.home"))
            put("SHADY_JAVA_BIN", resolveJavaExecutable())
            put("SHADY_CLASSPATH", System.getProperty("java.class.path"))
            put("TERM", "xterm-256color")
        }
        val process = ProcessBuilder(config.terminal.shell, "-l")
            .directory(projectRoot.toFile())
            .environmentWith(environment)
            .inheritIO()
            .start()
        process.waitFor()
    } finally {
        runCatching {
            Files.walk(sessionDirectory).use { paths ->
                paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
            }
        }
    }
    exitProcess(exitCode)
}

private fun ProcessBuilder.environmentWith(values: Map<String, String>): ProcessBuilder = apply {
    environment().clear()
    environment().putAll(values)
}

private fun tryHandleEmbeddedCommand(args: Array<String>): Boolean {
    val fifo = System.getenv("SHADY_EMBEDDED_FIFO")?.takeIf(String::isNotBlank) ?: return false
    val command = args.joinToString(" ").trim()
    val first = args.firstOrNull() ?: return false
    if (first !in EMBEDDED_TOOL_COMMANDS) return false

    return try {
        Files.newOutputStream(Path.of(fifo)).use { output ->
            output.write("INTERNAL_COMMAND\u0000$command\u0000".toByteArray(StandardCharsets.UTF_8))
        }
        true
    } catch (error: Exception) {
        System.err.println("Unable to contact the Shady emulator: ${error.message}")
        true
    }
}

private fun shadyServices(projectRoot: Path): ShadyServices {
    val paths = ShadyPaths.system()
    val configService = ShadyConfigService(paths, projectRoot)
    val aliasRepository = AliasRepository(projectRoot.resolve("aliases/shady-aliases.json"))
    val historyRepository = CommandHistoryRepository(
        historyFile = projectRoot.resolve(".shady-history.json"),
    )
    val goDirRepository = GoDirRepository(paths.goDirs)
    val nameDeriver = AliasNameDeriver()
    val suggestionEngine = SuggestionEngine(
        historyRepository = historyRepository,
        aliasRepository = aliasRepository,
        nameDeriver = nameDeriver,
    )
    val colorRules = ColorRulesRepository(
        globalPath = paths.colorRules,
        projectPath = projectRoot.resolve(".shady/colors.json"),
    )
    val registry = CommandRegistry(
        commands = listOf(
            HelpCommand(),
            EmulatorOnlyCommand(),
            AliasCommand(aliasRepository, suggestionEngine),
            GoDirCommand(goDirRepository),
            GitPrehookScripts(
                GitPrehookScriptsRepository(projectRoot.resolve("prehooks")),
            ),
            ColorCommandsCommand(colorRules),
            ConfigCommand(configService),
            StylesCommand(configService, paths),
            UpdateCommand(configService),
        ),
        aliasRepository = aliasRepository,
    )
    return ShadyServices(
        registry = registry,
        aliasRepository = aliasRepository,
        goDirRepository = goDirRepository,
        historyRepository = historyRepository,
        suggestionEngine = suggestionEngine,
        configService = configService,
        paths = paths,
        colorRules = colorRules,
    )
}

private fun runTerminalMode(services: ShadyServices) {
    val workingDirectory = File(System.getProperty("user.dir"))
    val config = services.configService.load()
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val workspace = TerminalWorkspaceController(
        configProvider = services.configService::load,
        colorRulesProvider = services.colorRules::load,
        aliases = services.aliasRepository,
        goDirRepository = services.goDirRepository,
        historyRepository = services.historyRepository,
        suggestionEngine = services.suggestionEngine,
        workspaceRepository = WorkspaceStateRepository(services.paths.workspaceState),
        settingsProviderFactory = ::ShadyTerminalSettings,
        scope = scope,
        initialDirectory = workingDirectory.toPath(),
    )
    val fuzzySearch = FuzzySearchEngine(services.historyRepository, services.aliasRepository)
    val updateChecker = UpdateChecker(services.paths.updateState)

    application {
        var closeRequested by remember { mutableStateOf(false) }
        val state by workspace.state.collectAsState()
        val resourceMonitor = remember { SystemResourceMonitor() }
        val macWindowRepository = remember { MacWindowRepository() }

        LaunchedEffect(closeRequested) {
            if (closeRequested) {
                workspace.close()
                scope.cancel()
                exitApplication()
            }
        }

        LaunchedEffect(Unit) {
            val result = withContext(Dispatchers.IO) { updateChecker.check(config) }
            if (result is UpdateCheckResult.Available) {
                workspace.setAvailableUpdate(result.update)
            }
        }

        Window(
            onCloseRequest = { closeRequested = true },
            title = "shady",
            icon = painterResource("shady-icon.svg"),
            state = rememberWindowState(width = ShadySizes.MainWindowWidth, height = ShadySizes.MainWindowHeight),
            resizable = true,
        ) {
            LaunchedEffect(Unit) {
                window.minimumSize = Dimension(ShadySizes.MainWindowMinWidth, ShadySizes.MainWindowMinHeight)
            }
            ShadyTheme {
                TerminalWorkspaceWindow(
                    state = state,
                    runtimeFor = workspace::runtime,
                    onActivateTab = workspace::activateTab,
                    onCloseTab = workspace::closeTab,
                    onNewTab = { workspace.newTab() },
                    onOpenFuzzy = workspace::openFuzzyPalette,
                    onSearchFuzzy = { query ->
                        val directory = state.tabs
                            .firstOrNull { it.id == state.activeTabId }
                            ?.workingDirectory
                            ?.let(Path::of)
                            ?: workingDirectory.toPath()
                        fuzzySearch.search(query, directory)
                    },
                    onSelectFuzzy = { candidate ->
                        workspace.sendToActive(candidate.value)
                        workspace.closeFuzzyPalette()
                    },
                    onCloseFuzzy = workspace::closeFuzzyPalette,
                    onDismissNotification = workspace::dismissNotification,
                    onInstallUpdate = workspace::installAvailableUpdate,
                )
            }
        }

        state.toolWindows.forEach { toolWindow ->
            key(toolWindow.id) {
                Window(
                    onCloseRequest = { workspace.closeToolWindow(toolWindow.id) },
                    title = toolWindow.title,
                    icon = painterResource("shady-icon.svg"),
                    state = rememberWindowState(
                        width = ShadySizes.ToolWindowWidth,
                        height = ShadySizes.ToolWindowHeight,
                    ),
                    resizable = true,
                ) {
                    LaunchedEffect(Unit) {
                        window.minimumSize = Dimension(
                            ShadySizes.ToolWindowMinWidth,
                            ShadySizes.ToolWindowMinHeight,
                        )
                    }
                    ShadyTheme {
                        when (toolWindow.type) {
                            EmulatorToolWindowType.Resources -> ResourceMonitorToolWindow(
                                monitor = resourceMonitor,
                            )
                            EmulatorToolWindowType.MacWindows -> MacWindowsToolWindow(
                                repository = macWindowRepository,
                            )
                        }
                    }
                }
            }
        }
    }
}

private data class ShadyServices(
    val registry: CommandRegistry,
    val aliasRepository: AliasRepository,
    val goDirRepository: GoDirRepository,
    val historyRepository: CommandHistoryRepository,
    val suggestionEngine: SuggestionEngine,
    val configService: ShadyConfigService,
    val paths: ShadyPaths,
    val colorRules: ColorRulesRepository,
)

private fun runCliCommand(
    input: CliInput,
    registry: CommandRegistry,
    workingDirectory: File,
): Int {
    val request = try {
        registry.resolve(input)
    } catch (error: Exception) {
        System.err.println(error.message ?: "Command resolution failed.")
        return 1
    }

    return when (request) {
        is CommandRequest.BuiltInOutput -> {
            if (request.exitCode == 0) {
                print(request.output)
            } else {
                System.err.print(request.output)
            }
            request.exitCode
        }

        is CommandRequest.RunShell -> runBlocking {
            val executor = ProcessCommandExecutor(workingDirectory = workingDirectory)
            when (
                val outcome = executor.execute(
                    request.command,
                    CommandCallbacks(
                        onStarted = {},
                        onOutput = {
                            print(it)
                            System.out.flush()
                        },
                    ),
                )
            ) {
                is CommandOutcome.Finished -> outcome.exitCode
                is CommandOutcome.LaunchFailed -> {
                    System.err.println(outcome.message)
                    1
                }
                is CommandOutcome.Stopped -> outcome.exitCode ?: 130
            }
        }
    }
}

private val EMBEDDED_TOOL_COMMANDS = setOf(
    "sys",
    "system",
    "resource",
    "resources",
    "monitor",
    "dashboard",
    "windows",
    "window",
    "wins",
)

private const val IDE_SHELL_FLAG = "--ide-shell"

private fun resolveJavaExecutable(): String {
    val bundled = Path.of(System.getProperty("java.home"), "bin", "java")
    if (Files.isExecutable(bundled)) return bundled.toString()
    return "java"
}
