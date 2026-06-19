package cli.shady.commands

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

class AliasRepository(
    private val path: Path,
    private val json: Json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
    },
) {
    fun all(): List<AliasEntry> = load().aliases.sortedBy { it.name }

    fun find(name: String): AliasEntry? = all().firstOrNull { it.name == name }

    fun upsert(alias: AliasEntry) {
        val registry = load()
        val aliases = registry.aliases
            .filterNot { it.name == alias.name }
            .plus(alias)
            .sortedBy { it.name }
        save(AliasRegistryData(aliases))
    }

    private fun load(): AliasRegistryData {
        if (!Files.exists(path)) {
            return AliasRegistryData()
        }

        return try {
            json.decodeFromString<AliasRegistryData>(Files.readString(path))
        } catch (error: SerializationException) {
            throw IllegalStateException("Alias registry is not valid JSON: $path", error)
        } catch (error: IOException) {
            throw IllegalStateException("Unable to read alias registry: $path", error)
        }
    }

    private fun save(registry: AliasRegistryData) {
        try {
            Files.createDirectories(path.parent)
            val content = json.encodeToString(AliasRegistryData.serializer(), registry).trimEnd() + "\n"
            Files.writeString(path, content)
        } catch (error: IOException) {
            throw IllegalStateException("Unable to write alias registry: $path", error)
        }
    }
}

@Serializable
data class AliasRegistryData(
    val aliases: List<AliasEntry> = emptyList(),
)

@Serializable
data class AliasEntry(
    val name: String,
    val command: String,
)
