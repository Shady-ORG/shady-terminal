package cli.shady.commands

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.SecureRandom
import kotlin.io.path.name
import kotlin.io.path.nameWithoutExtension

class GitPrehookScriptsRepository(
    private val scriptsDirectory: Path,
    private val suffixGenerator: () -> String = { RandomSuffix.next() },
) {
    fun addFromPath(name: String, sourcePath: Path): PrehookScript {
        validateName(name)
        validateBashScript(sourcePath)
        Files.createDirectories(scriptsDirectory)

        val destination = nextAvailablePath(name)
        Files.copy(sourcePath, destination, StandardCopyOption.COPY_ATTRIBUTES)
        makeExecutable(destination)
        return PrehookScript(name = destination.nameWithoutExtension, path = destination)
    }

    fun createForEditing(name: String): PrehookScript {
        validateName(name)
        Files.createDirectories(scriptsDirectory)

        val destination = nextAvailablePath(name)
        Files.writeString(destination, DEFAULT_BASH_SCRIPT)
        makeExecutable(destination)
        return PrehookScript(name = destination.nameWithoutExtension, path = destination)
    }

    fun all(): List<PrehookScript> {
        if (!Files.exists(scriptsDirectory)) {
            return emptyList()
        }

        return Files.list(scriptsDirectory).use { stream ->
            stream
                .filter { path -> Files.isRegularFile(path) }
                .filter { it.name.endsWith(SCRIPT_EXTENSION) }
                .map { PrehookScript(name = it.nameWithoutExtension, path = it) }
                .sorted(compareBy<PrehookScript> { it.name })
                .toList()
        }
    }

    fun find(name: String): PrehookScript? =
        all().firstOrNull { it.name == name || it.path.name == name }

    fun validateBashScript(path: Path) {
        if (!Files.isRegularFile(path)) {
            throw IllegalArgumentException("Prehook source is not a file: $path")
        }

        val firstLine = try {
            Files.newBufferedReader(path).use { it.readLine() }
        } catch (error: IOException) {
            throw IllegalArgumentException("Unable to read prehook source: $path", error)
        }

        if (firstLine == null || !BASH_SHEBANG.matches(firstLine.trim())) {
            throw IllegalArgumentException(
                "Only bash scripts are allowed. Start the file with '#!/usr/bin/env bash' or '#!/bin/bash'.",
            )
        }
    }

    fun delete(script: PrehookScript) {
        Files.deleteIfExists(script.path)
    }

    private fun validateName(name: String) {
        require(name.isNotBlank()) { "Prehook name is required." }
        require(NAME_PATTERN.matches(name)) {
            "Invalid prehook name '$name'. Use letters, numbers, '_', '-' or '.'."
        }
    }

    private fun nextAvailablePath(name: String): Path {
        val baseName = name.removeSuffix(SCRIPT_EXTENSION)
        var candidate = scriptsDirectory.resolve("$baseName$SCRIPT_EXTENSION")
        while (Files.exists(candidate)) {
            candidate = scriptsDirectory.resolve("${baseName}_${suffixGenerator()}$SCRIPT_EXTENSION")
        }
        return candidate
    }

    private fun makeExecutable(path: Path) {
        path.toFile().setExecutable(true, true)
    }

    private object RandomSuffix {
        private const val ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789"
        private val random = SecureRandom()

        fun next(length: Int = 8): String =
            buildString(length) {
                repeat(length) {
                    append(ALPHABET[random.nextInt(ALPHABET.length)])
                }
            }
    }

    companion object {
        private const val SCRIPT_EXTENSION = ".sh"
        private const val DEFAULT_BASH_SCRIPT = "#!/usr/bin/env bash\n\n"
        private val NAME_PATTERN = Regex("""[A-Za-z0-9_.-]+""")
        private val BASH_SHEBANG = Regex("""^#!\s*(/usr/bin/env\s+bash|/bin/bash)(\s.*)?$""")
    }
}

data class PrehookScript(
    val name: String,
    val path: Path,
)
