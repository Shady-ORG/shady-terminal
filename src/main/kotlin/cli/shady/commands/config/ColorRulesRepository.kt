package cli.shady.commands.config

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class ColorRules(
    val commands: Map<String, String> = emptyMap(),
    val fileTypes: Map<String, String> = emptyMap(),
) {
    fun mergedWith(override: ColorRules): ColorRules = ColorRules(
        commands = commands + override.commands,
        fileTypes = fileTypes + override.fileTypes,
    )
}

class ColorRulesRepository(
    private val globalPath: Path,
    private val projectPath: Path? = null,
    private val json: Json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
    },
) {
    fun load(): ColorRules = read(globalPath).mergedWith(projectPath?.let(::read) ?: ColorRules())

    fun saveGlobal(rules: ColorRules) {
        globalPath.parent?.createDirectories()
        Files.writeString(globalPath, json.encodeToString(rules))
    }

    fun setCommand(command: String, color: String): ColorRules {
        validateKey(command, "command")
        validateColor(color)
        val updated = read(globalPath).let { it.copy(commands = it.commands + (command to color)) }
        saveGlobal(updated)
        return load()
    }

    fun setFileType(extension: String, color: String): ColorRules {
        val normalized = extension.trim().removePrefix(".").lowercase()
        validateKey(normalized, "file type")
        validateColor(color)
        val updated = read(globalPath).let { it.copy(fileTypes = it.fileTypes + (normalized to color)) }
        saveGlobal(updated)
        return load()
    }

    fun removeCommand(command: String): ColorRules {
        val updated = read(globalPath).let { it.copy(commands = it.commands - command) }
        saveGlobal(updated)
        return load()
    }

    fun removeFileType(extension: String): ColorRules {
        val normalized = extension.trim().removePrefix(".").lowercase()
        val updated = read(globalPath).let { it.copy(fileTypes = it.fileTypes - normalized) }
        saveGlobal(updated)
        return load()
    }

    fun reset(): ColorRules {
        saveGlobal(ColorRules())
        return load()
    }

    private fun read(path: Path): ColorRules {
        if (!Files.exists(path)) return ColorRules()
        return try {
            json.decodeFromString<ColorRules>(Files.readString(path))
        } catch (error: Exception) {
            throw IllegalStateException("Unable to read Shady color rules: $path", error)
        }
    }

    private fun validateKey(value: String, label: String) {
        require(value.isNotBlank()) { "$label must not be blank" }
        require(!value.any(Char::isWhitespace)) { "$label must not contain whitespace" }
    }

    private fun validateColor(value: String) {
        require(value.lowercase() in NAMED_COLORS || HEX_COLOR.matches(value)) {
            "Unsupported color '$value'. Use a named ANSI color or #RRGGBB."
        }
    }

    companion object {
        val NAMED_COLORS = setOf(
            "black", "red", "green", "yellow", "blue", "magenta", "cyan", "white",
            "bright-black", "bright-red", "bright-green", "bright-yellow", "bright-blue",
            "bright-magenta", "bright-cyan", "bright-white",
        )
        val HEX_COLOR = Regex("#[0-9a-fA-F]{6}")
    }
}
