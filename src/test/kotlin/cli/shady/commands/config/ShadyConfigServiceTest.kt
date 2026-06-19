package cli.shady.commands.config

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ShadyConfigServiceTest {
    @Test
    fun `defaults match terminal product configuration`() {
        val config = service().load()

        assertFalse(config.security.alwaysRequestSudo)
        assertTrue(config.terminal.coloredOutput)
        assertFalse(config.terminal.usernameOverrideEnabled)
        assertFalse(config.features.stylesCommandEnabled)
        assertTrue(config.features.fuzzySearchEnabled)
        assertEquals("/bin/zsh", config.terminal.shell)
    }

    @Test
    fun `project config overrides terminal features but not global security`() {
        val root = Files.createTempDirectory("shady-config-project")
        val home = Files.createTempDirectory("shady-config-home")
        val paths = ShadyPaths.system(home, emptyMap())
        Files.createDirectories(paths.globalConfig.parent)
        Files.writeString(
            paths.globalConfig,
            """{"security":{"alwaysRequestSudo":true},"terminal":{"coloredOutput":true}}""",
        )
        Files.createDirectories(root.resolve(".shady"))
        Files.writeString(
            root.resolve(".shady/config.json"),
            """{"ownCommandsInWindow":true,"security":{"alwaysRequestSudo":false},"terminal":{"coloredOutput":false}}""",
        )

        val config = ShadyConfigService(paths, root).load()

        assertTrue(config.security.alwaysRequestSudo)
        assertFalse(config.terminal.coloredOutput)
    }

    private fun service(): ShadyConfigService {
        val root = Files.createTempDirectory("shady-config-project")
        val home = Files.createTempDirectory("shady-config-home")
        return ShadyConfigService(ShadyPaths.system(home, emptyMap()), root)
    }
}
