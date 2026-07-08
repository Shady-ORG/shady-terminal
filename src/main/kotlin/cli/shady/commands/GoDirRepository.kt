package cli.shady.commands

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

class GoDirRepository(
    private val path: Path,
    private val home: Path = Path.of(System.getProperty("user.home")),
    private val workingDirectory: () -> Path = { Path.of(System.getProperty("user.dir")) },
    private val autoSaveThreshold: Int = DEFAULT_AUTO_SAVE_THRESHOLD,
    private val json: Json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
    },
) {
    fun allEntries(): List<GoDirEntry> = load().entries.sorted()

    fun savedEntries(): List<GoDirEntry> {
        val entries = load().entries
        val manualPaths = entries
            .filter { it.source == GoDirEntrySource.Manual }
            .mapTo(mutableSetOf()) { it.path }

        return entries
            .filter { entry ->
                entry.source == GoDirEntrySource.Manual ||
                    (entry.source == GoDirEntrySource.Automatic && entry.saved && entry.path !in manualPaths)
            }
            .sorted()
    }

    fun resolve(name: String): GoDirEntry? =
        savedEntries()
            .filter { it.name == name }
            .maxWithOrNull(
                compareBy<GoDirEntry> { it.count }
                    .thenBy { if (it.source == GoDirEntrySource.Manual) 1 else 0 }
                    .thenBy { it.path },
            )

    fun addManual(name: String, rawPath: String): GoDirEntry {
        val directory = resolveInputPath(rawPath)
        require(Files.isDirectory(directory)) { "Directory does not exist: $rawPath" }

        val storedPath = storePath(directory)
        val registry = load()
        val count = registry.entries
            .filter { it.path == storedPath }
            .maxOfOrNull { it.count }
            ?: 0
        val entry = GoDirEntry(
            name = name,
            path = storedPath,
            count = count,
            source = GoDirEntrySource.Manual,
            saved = true,
        )
        val entries = registry.entries
            .filterNot { it.source == GoDirEntrySource.Manual && it.name == name }
            .plus(entry)
            .sorted()
        save(GoDirRegistryData(entries))
        return entry
    }

    fun removeManual(name: String): Boolean {
        val registry = load()
        val entries = registry.entries.filterNot { it.source == GoDirEntrySource.Manual && it.name == name }
        if (entries.size == registry.entries.size) return false
        save(GoDirRegistryData(entries.sorted()))
        return true
    }

    fun recordVisit(directory: Path): GoDirEntry? {
        val normalized = directory.toAbsolutePath().normalize()
        if (!Files.isDirectory(normalized)) return null

        val storedPath = storePath(normalized)
        val registry = load()
        val entries = registry.entries.map { entry ->
            if (entry.path != storedPath) {
                entry
            } else {
                val nextCount = entry.count + 1
                entry.copy(
                    count = nextCount,
                    saved = entry.saved || (entry.source == GoDirEntrySource.Automatic && nextCount >= autoSaveThreshold),
                )
            }
        }.toMutableList()

        if (entries.none { it.source == GoDirEntrySource.Automatic && it.path == storedPath }) {
            val count = entries
                .filter { it.path == storedPath }
                .maxOfOrNull { it.count }
                ?: 1
            entries += GoDirEntry(
                name = directoryName(normalized),
                path = storedPath,
                count = count,
                source = GoDirEntrySource.Automatic,
                saved = count >= autoSaveThreshold,
            )
        }

        val saved = GoDirRegistryData(entries.sorted())
        save(saved)
        return saved.entries.firstOrNull { it.source == GoDirEntrySource.Automatic && it.path == storedPath }
    }

    private fun resolveInputPath(rawPath: String): Path {
        val expanded = when {
            rawPath == "~" -> home
            rawPath.startsWith("~/") -> home.resolve(rawPath.removePrefix("~/"))
            else -> Path.of(rawPath)
        }
        return if (expanded.isAbsolute) {
            expanded.normalize()
        } else {
            workingDirectory().resolve(expanded).toAbsolutePath().normalize()
        }
    }

    private fun storePath(directory: Path): String = directory.toAbsolutePath().normalize().toString()

    private fun directoryName(directory: Path): String =
        directory.fileName?.toString()?.takeIf(String::isNotBlank) ?: "root"

    private fun load(): GoDirRegistryData {
        if (!Files.exists(path)) {
            return GoDirRegistryData()
        }

        return try {
            json.decodeFromString<GoDirRegistryData>(Files.readString(path))
        } catch (error: SerializationException) {
            throw IllegalStateException("GoDir registry is not valid JSON: $path", error)
        } catch (error: IOException) {
            throw IllegalStateException("Unable to read GoDir registry: $path", error)
        }
    }

    private fun save(registry: GoDirRegistryData) {
        try {
            Files.createDirectories(path.parent)
            val content = json.encodeToString(GoDirRegistryData.serializer(), registry).trimEnd() + "\n"
            Files.writeString(path, content)
        } catch (error: IOException) {
            throw IllegalStateException("Unable to write GoDir registry: $path", error)
        }
    }

    private fun List<GoDirEntry>.sorted(): List<GoDirEntry> =
        sortedWith(compareBy<GoDirEntry> { it.name }.thenByDescending { it.count }.thenBy { it.path })

    companion object {
        const val DEFAULT_AUTO_SAVE_THRESHOLD = 10
    }
}

@Serializable
data class GoDirRegistryData(
    val entries: List<GoDirEntry> = emptyList(),
)

@Serializable
data class GoDirEntry(
    val name: String,
    val path: String,
    val count: Int = 0,
    val source: GoDirEntrySource = GoDirEntrySource.Automatic,
    val saved: Boolean = false,
)

@Serializable
enum class GoDirEntrySource {
    Manual,
    Automatic,
}
