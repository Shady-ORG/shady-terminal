package cli.shady.intellij

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ShadyExecutableFinderTest {
    @Test
    fun `prefers local user installation`() {
        val home = Files.createTempDirectory("shady-plugin-home")
        val executable = home.resolve(".local/bin/shady")
        Files.createDirectories(executable.parent)
        Files.writeString(executable, "#!/bin/sh\n")
        executable.toFile().setExecutable(true)

        assertEquals(
            listOf(executable.toString(), "--ide-shell"),
            ShadyExecutableFinder(home, appCandidates = emptyList()) { null }.find()?.withIdeShellFlag(),
        )
    }

    @Test
    fun `falls back to path lookup`() {
        val home = Files.createTempDirectory("shady-plugin-home")
        val executable = Files.writeString(home.resolve("shady"), "#!/bin/sh\n")
        executable.toFile().setExecutable(true)

        assertEquals(
            listOf(executable.toString(), "--ide-shell"),
            ShadyExecutableFinder(home, pathLookup = { executable }, appCandidates = emptyList()).find()?.withIdeShellFlag(),
        )
    }

    @Test
    fun `rejects non executable path result`() {
        val home = Files.createTempDirectory("shady-plugin-home")
        val file = Files.writeString(home.resolve("shady"), "not executable")

        assertNull(ShadyExecutableFinder(home, pathLookup = { file }, appCandidates = emptyList()).find())
    }

    @Test
    fun `discovers mac app bundle without hardcoding shady jar version`() {
        val home = Files.createTempDirectory("shady-plugin-home")
        val appBundle = home.resolve("Applications/Shady.app")
        val appDirectory = appBundle.resolve("Contents/app")
        Files.createDirectories(appDirectory)
        val shadyJar = Files.writeString(appDirectory.resolve("shady-2.4.6-any-hash.jar"), "")
        val dependencyJar = Files.writeString(appDirectory.resolve("kotlin-stdlib.jar"), "")
        val java = Files.writeString(home.resolve("java"), "#!/bin/sh\n")
        java.toFile().setExecutable(true)
        Files.writeString(
            appDirectory.resolve("Shady.cfg"),
            """
            |[Application]
            |app.classpath=${'$'}APPDIR/${shadyJar.fileName}
            |app.mainclass=cli.shady.MainKt
            |app.classpath=${'$'}APPDIR/${dependencyJar.fileName}
            |
            |[JavaOptions]
            |java-options=--enable-native-access=ALL-UNNAMED
            |java-options=-Dskiko.library.path=${'$'}APPDIR
            |
            |[ArgOptions]
            |arguments=start
            """.trimMargin(),
        )

        assertEquals(
            listOf(
                java.toString(),
                "--enable-native-access=ALL-UNNAMED",
                "-Dskiko.library.path=$appDirectory",
                "-cp",
                listOf(shadyJar, dependencyJar).joinToString(File.pathSeparator) { it.toString() },
                "cli.shady.MainKt",
                "--ide-shell",
            ),
            ShadyExecutableFinder(
                home = home,
                pathLookup = { null },
                appCandidates = listOf(appBundle),
                javaLookup = { java },
            ).find()?.withIdeShellFlag(),
        )
    }
}
