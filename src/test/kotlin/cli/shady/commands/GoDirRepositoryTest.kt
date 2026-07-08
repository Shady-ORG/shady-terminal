package cli.shady.commands

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GoDirRepositoryTest {
    @Test
    fun `automatic entries become saved after ten visits`() {
        val root = Files.createTempDirectory("shady-gdir")
        val directory = Files.createDirectory(root.resolve("project"))
        val repository = GoDirRepository(root.resolve("gdirs.json"))

        repeat(9) { repository.recordVisit(directory) }

        assertTrue(repository.savedEntries().isEmpty())

        repository.recordVisit(directory)

        val entry = repository.savedEntries().single()
        assertEquals("project", entry.name)
        assertEquals(stored(directory), entry.path)
        assertEquals(10, entry.count)
        assertEquals(GoDirEntrySource.Automatic, entry.source)
    }

    @Test
    fun `matching directory names resolve to the highest usage count`() {
        val root = Files.createTempDirectory("shady-gdir")
        val first = Files.createDirectories(root.resolve("a/service"))
        val second = Files.createDirectories(root.resolve("b/service"))
        val repository = GoDirRepository(root.resolve("gdirs.json"))

        repeat(10) { repository.recordVisit(first) }
        repeat(12) { repository.recordVisit(second) }

        assertEquals(stored(second), assertNotNull(repository.resolve("service")).path)
    }

    @Test
    fun `manual entries are not overwritten by automatic entries with the same name`() {
        val root = Files.createTempDirectory("shady-gdir")
        val manual = Files.createDirectory(root.resolve("manual-service"))
        val automatic = Files.createDirectory(root.resolve("service"))
        val repository = GoDirRepository(root.resolve("gdirs.json"))

        repository.addManual("service", manual.toString())
        repeat(11) { repository.recordVisit(automatic) }

        val matchingPaths = repository.savedEntries()
            .filter { it.name == "service" }
            .map { it.path }
            .toSet()
        assertEquals(setOf(stored(manual), stored(automatic)), matchingPaths)
        assertEquals(stored(automatic), assertNotNull(repository.resolve("service")).path)

        assertTrue(repository.removeManual("service"))
        assertEquals(listOf(stored(automatic)), repository.savedEntries().map { it.path })
    }

    private fun stored(path: Path): String = path.toAbsolutePath().normalize().toString()
}
