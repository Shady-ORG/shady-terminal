package cli.shady.app

import cli.shady.commands.AliasNameDeriver
import cli.shady.commands.AliasRepository
import cli.shady.commands.CommandHistoryRepository
import cli.shady.commands.GoDirRepository
import cli.shady.commands.SuggestionEngine
import cli.shady.commands.config.FeatureConfig
import cli.shady.commands.config.SecurityConfig
import cli.shady.commands.config.ShadyConfig
import cli.shady.terminal.TerminalSession
import cli.shady.terminal.TerminalSessionEvent
import cli.shady.terminal.TerminalSessionFactory
import cli.shady.terminal.TerminalSessionSpec
import cli.shady.ui.theme.ShadyTerminalSettings
import java.awt.event.KeyListener
import java.nio.file.Files
import javax.swing.JComponent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class TerminalWorkspaceControllerTest {
    @Test
    fun `each tab owns an independent terminal session`() = runTest {
        val fixture = fixture(ShadyConfig())
        val firstId = fixture.controller.state.value.activeTabId

        val secondId = fixture.controller.newTab()

        assertNotEquals(firstId, secondId)
        assertEquals(2, fixture.factory.sessions.size)
        fixture.controller.activateTab(firstId!!)
        assertEquals(1, fixture.factory.sessions.first().focusRequests)

        fixture.controller.closeTab(firstId)
        assertTrue(fixture.factory.sessions.first().closed)
        assertEquals(secondId, fixture.controller.state.value.activeTabId)
        fixture.controller.close()
    }

    @Test
    fun `sudo validation is requested once per application start`() = runTest {
        val fixture = fixture(
            ShadyConfig(security = SecurityConfig(alwaysRequestSudo = true)),
        )
        val first = fixture.factory.sessions.first()
        first.emit(TerminalSessionEvent.PromptReady(fixture.directory.toString()))
        fixture.controller.newTab()
        val second = fixture.factory.sessions.last()
        second.emit(TerminalSessionEvent.PromptReady(fixture.directory.toString()))

        assertEquals(listOf("sudo -k; sudo -v\r"), first.writes)
        assertTrue(second.writes.isEmpty())
        fixture.controller.close()
    }

    @Test
    fun `fuzzy search obeys its feature flag`() = runTest {
        val fixture = fixture(
            ShadyConfig(features = FeatureConfig(fuzzySearchEnabled = false)),
        )

        fixture.controller.openFuzzyPalette()

        assertFalse(fixture.controller.state.value.fuzzySearchEnabled)
        assertFalse(fixture.controller.state.value.fuzzyPaletteOpen)
        fixture.controller.close()
    }

    @Test
    fun `interrupt and clear are sent to the active session`() = runTest {
        val fixture = fixture(ShadyConfig())
        val session = fixture.factory.sessions.single()

        fixture.controller.interruptActive()
        fixture.controller.clearActive()

        assertEquals(1, session.interrupts)
        assertEquals(listOf("clear\r"), session.writes)
        fixture.controller.close()
    }

    @Test
    fun `records directory visits only when a tab enters a directory`() = runTest {
        val fixture = fixture(ShadyConfig())
        val session = fixture.factory.sessions.single()
        val child = Files.createDirectory(fixture.directory.resolve("project"))

        session.emit(TerminalSessionEvent.PromptReady(fixture.directory.toString()))
        session.emit(TerminalSessionEvent.PromptReady(fixture.directory.toString()))
        session.emit(TerminalSessionEvent.PromptReady(child.toString()))

        val entries = fixture.goDirs.allEntries().associateBy { it.path }
        assertEquals(1, entries[fixture.directory.toAbsolutePath().normalize().toString()]?.count)
        assertEquals(1, entries[child.toAbsolutePath().normalize().toString()]?.count)
        fixture.controller.close()
    }

    private fun kotlinx.coroutines.test.TestScope.fixture(config: ShadyConfig): Fixture {
        val directory = Files.createTempDirectory("shady-workspace")
        val aliases = AliasRepository(directory.resolve("aliases.json"))
        val history = CommandHistoryRepository()
        val goDirs = GoDirRepository(directory.resolve("gdirs.json"))
        val factory = FakeTerminalSessionFactory()
        val controller = TerminalWorkspaceController(
            configProvider = { config },
            colorRulesProvider = { cli.shady.commands.config.ColorRules() },
            aliases = aliases,
            goDirRepository = goDirs,
            historyRepository = history,
            suggestionEngine = SuggestionEngine(history, aliases, AliasNameDeriver()),
            workspaceRepository = WorkspaceStateRepository(directory.resolve("workspace.json")),
            settingsProviderFactory = ::ShadyTerminalSettings,
            scope = this,
            initialDirectory = directory,
            sessionFactory = factory,
        )
        return Fixture(controller, factory, directory, goDirs)
    }

    private data class Fixture(
        val controller: TerminalWorkspaceController,
        val factory: FakeTerminalSessionFactory,
        val directory: java.nio.file.Path,
        val goDirs: GoDirRepository,
    )

    private class FakeTerminalSessionFactory : TerminalSessionFactory {
        val sessions = mutableListOf<FakeTerminalSession>()

        override fun create(spec: TerminalSessionSpec): TerminalSession =
            FakeTerminalSession(spec).also(sessions::add)
    }

    private class FakeTerminalSession(
        private val spec: TerminalSessionSpec,
    ) : TerminalSession {
        override val id: String = spec.id
        override val title: String = spec.title
        override val component: JComponent
            get() = error("The fake session has no UI component.")
        val writes = mutableListOf<String>()
        var interrupts = 0
        var focusRequests = 0
        var closed = false

        fun emit(event: TerminalSessionEvent) = spec.onEvent(event)
        override fun write(text: String) { writes += text }
        override fun interrupt() { interrupts++ }
        override fun clearBuffer() = Unit
        override fun resize(columns: Int, rows: Int) = Unit
        override fun requestFocus() { focusRequests++ }
        override fun addKeyListener(listener: KeyListener) = Unit
        override fun removeKeyListener(listener: KeyListener) = Unit
        override fun close() { closed = true }
    }
}
