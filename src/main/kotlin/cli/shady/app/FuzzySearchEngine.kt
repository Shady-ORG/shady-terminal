package cli.shady.app

import cli.shady.commands.AliasRepository
import cli.shady.commands.CommandHistoryRepository
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isExecutable
import kotlin.io.path.isRegularFile

enum class FuzzyCandidateSource {
    History,
    Alias,
    Shady,
    Executable,
    File,
}

data class FuzzyCandidate(
    val value: String,
    val source: FuzzyCandidateSource,
    val detail: String? = null,
)

class FuzzySearchEngine(
    private val history: CommandHistoryRepository,
    private val aliases: AliasRepository,
    private val environment: Map<String, String> = System.getenv(),
) {
    fun search(query: String, workingDirectory: Path, limit: Int = 50): List<FuzzyCandidate> {
        val candidates = buildList {
            history.all().asReversed().forEach { add(FuzzyCandidate(it.command, FuzzyCandidateSource.History)) }
            aliases.all().forEach {
                add(FuzzyCandidate(it.name, FuzzyCandidateSource.Alias, it.command))
            }
            SHADY_COMMANDS.forEach { add(FuzzyCandidate(it, FuzzyCandidateSource.Shady)) }
            executables().forEach { add(FuzzyCandidate(it, FuzzyCandidateSource.Executable)) }
            files(workingDirectory).forEach { add(FuzzyCandidate(it, FuzzyCandidateSource.File)) }
        }.distinctBy { it.value }

        return candidates
            .mapNotNull { candidate -> fuzzyScore(query, candidate.value)?.let { it to candidate } }
            .sortedWith(compareBy<Pair<Int, FuzzyCandidate>> { it.first }.thenBy { it.second.value.length })
            .take(limit)
            .map(Pair<Int, FuzzyCandidate>::second)
    }

    private fun executables(): List<String> = environment["PATH"]
        .orEmpty()
        .split(':')
        .asSequence()
        .filter(String::isNotBlank)
        .map(Path::of)
        .filter(Files::isDirectory)
        .flatMap { directory ->
            runCatching {
                Files.list(directory).use { paths ->
                    paths.filter { it.isRegularFile() && it.isExecutable() }
                        .map { it.fileName.toString() }
                        .toList()
                        .asSequence()
                }
            }.getOrDefault(emptySequence())
        }
        .distinct()
        .take(MAX_EXECUTABLES)
        .toList()

    private fun files(directory: Path): List<String> = runCatching {
        Files.list(directory).use { paths ->
            paths.limit(MAX_FILES.toLong()).map { path ->
                if (Files.isDirectory(path)) "${path.fileName}/" else path.fileName.toString()
            }.toList()
        }
    }.getOrDefault(emptyList())

    private fun fuzzyScore(query: String, candidate: String): Int? {
        val needle = query.trim().lowercase()
        if (needle.isEmpty()) return 0
        val haystack = candidate.lowercase()
        val direct = haystack.indexOf(needle)
        if (direct >= 0) return direct

        var needleIndex = 0
        var score = 0
        var previousMatch = -2
        haystack.forEachIndexed { index, char ->
            if (needleIndex < needle.length && char == needle[needleIndex]) {
                score += if (index == previousMatch + 1) 0 else index + 2
                previousMatch = index
                needleIndex++
            }
        }
        return score.takeIf { needleIndex == needle.length }
    }

    private companion object {
        const val MAX_EXECUTABLES = 2_000
        const val MAX_FILES = 500
        val SHADY_COMMANDS = listOf(
            "shady help",
            "shady sys",
            "shady windows",
            "shady styles",
            "shady cc list",
            "shady config show",
            "shady alias list",
            "shady alias suggest",
            "shady prehook list",
        )
    }
}
