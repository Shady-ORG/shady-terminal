package cli.shady.commands.config

import java.nio.file.Files
import java.nio.file.Path
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.encodeToString

class ShadyConfigService(
    private val paths: ShadyPaths,
    private val projectRoot: Path,
    private val json: Json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
    },
) {
    fun load(): ShadyConfig {
        val global = readObject(paths.globalConfig)
        val project = readObject(projectConfigPath())
            .filterKeys { it !in GLOBAL_ONLY_SECTIONS }
        return json.decodeFromJsonElement(deepMerge(global, JsonObject(project)))
    }

    fun loadGlobal(): ShadyConfig = json.decodeFromJsonElement(readObject(paths.globalConfig))

    fun saveGlobal(config: ShadyConfig) {
        paths.globalConfig.parent?.let(Files::createDirectories)
        Files.writeString(paths.globalConfig, json.encodeToString(config))
    }

    fun projectConfigPath(): Path {
        val preferred = projectRoot.resolve(".shady/config.json")
        val legacy = projectRoot.resolve("shady.config.json")
        return when {
            Files.exists(preferred) -> preferred
            Files.exists(legacy) -> legacy
            else -> preferred
        }
    }

    private fun readObject(path: Path): JsonObject {
        if (!Files.exists(path)) return JsonObject(emptyMap())
        return try {
            json.parseToJsonElement(Files.readString(path)) as? JsonObject
                ?: throw IllegalStateException("Shady config must contain a JSON object: $path")
        } catch (error: Exception) {
            throw IllegalStateException("Unable to read Shady config: $path", error)
        }
    }

    private fun deepMerge(base: JsonObject, override: JsonObject): JsonObject {
        val merged = base.toMutableMap()
        override.forEach { (key, value) ->
            val existing = merged[key]
            merged[key] = if (existing is JsonObject && value is JsonObject) {
                deepMerge(existing, value)
            } else {
                value
            }
        }
        return JsonObject(merged)
    }

    private companion object {
        val GLOBAL_ONLY_SECTIONS = setOf("security", "updates")
    }
}
