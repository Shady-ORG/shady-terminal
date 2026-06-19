package cli.shady.install

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class InstallerScriptTest {
    private val installer = Path.of(System.getProperty("user.dir"), "scripts", "install.sh")

    @Test
    fun `installer has valid bash syntax`() {
        val process = ProcessBuilder("/bin/bash", "-n", installer.toString())
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().use { it.readText() }

        assertEquals(0, process.waitFor(), output)
    }

    @Test
    fun `installer verifies and atomically links a release`() {
        val root = Files.createTempDirectory("shady-installer-test")
        val distribution = root.resolve("source/shady")
        val launcher = distribution.resolve("bin/shady")
        Files.createDirectories(launcher.parent)
        Files.writeString(launcher, "#!/usr/bin/env bash\nprintf 'installed\\n'\n")
        launcher.toFile().setExecutable(true)
        val archive = root.resolve("shady-macos-aarch64.tar.gz")
        run("/usr/bin/tar", "-C", distribution.parent.toString(), "-czf", archive.toString(), "shady")
        val checksum = root.resolve("shady-macos-aarch64.tar.gz.sha256")
        val digest = commandOutput("/usr/bin/shasum", "-a", "256", archive.toString()).substringBefore(' ')
        Files.writeString(checksum, "$digest  ${archive.fileName}\n")

        val fakeBin = Files.createDirectories(root.resolve("fake-bin"))
        val fakeCurl = fakeBin.resolve("curl")
        Files.writeString(
            fakeCurl,
            """#!/usr/bin/env bash
set -euo pipefail
url="${'$'}2"
output="${'$'}4"
if [[ "${'$'}url" == *.sha256 ]]; then
  cp "${checksum}" "${'$'}output"
else
  cp "${archive}" "${'$'}output"
fi
""",
        )
        fakeCurl.toFile().setExecutable(true)

        val installRoot = root.resolve("install")
        val bin = root.resolve("bin")
        val process = ProcessBuilder("/bin/bash", installer.toString())
            .redirectErrorStream(true)
            .apply {
                environment()["PATH"] = "$fakeBin:${System.getenv("PATH")}"
                environment()["SHADY_REPO_URL"] = "https://example.invalid/shady"
                environment()["SHADY_VERSION"] = "v1.0.0"
                environment()["SHADY_INSTALL_ROOT"] = installRoot.toString()
                environment()["SHADY_BIN_DIR"] = bin.toString()
            }
            .start()
        val output = process.inputStream.bufferedReader().use { it.readText() }

        assertEquals(0, process.waitFor(), output)
        assertTrue(Files.isSymbolicLink(bin.resolve("shady")))
        assertTrue(Files.isExecutable(bin.resolve("shady")))
    }

    private fun run(vararg command: String) {
        val process = ProcessBuilder(*command).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().use { it.readText() }
        assertEquals(0, process.waitFor(), output)
    }

    private fun commandOutput(vararg command: String): String {
        val process = ProcessBuilder(*command).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().use { it.readText() }
        assertEquals(0, process.waitFor(), output)
        return output.trim()
    }
}
