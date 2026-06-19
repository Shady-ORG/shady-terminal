package cli.shady.commands

import java.time.Duration
import java.time.Instant

data class AliasSuggestion(
    val command: String,
    val executionCount: Int,
    val suggestedName: String,
    val aliasAddInstruction: String,
)

sealed interface AcceptResult {
    data class Success(val aliasName: String, val command: String) : AcceptResult
    data class IndexOutOfRange(val validRange: IntRange) : AcceptResult
    data class InvalidName(val reason: String) : AcceptResult
}

class SuggestionEngine(
    private val historyRepository: CommandHistoryRepository,
    private val aliasRepository: AliasRepository,
    private val nameDeriver: AliasNameDeriver = AliasNameDeriver(),
    private val minimumFrequency: Int = 2,
    private val minimumLength: Int = 2,
    private val cooldownSeconds: Long = 86400,
    private val clock: () -> Instant = Instant::now,
) {
    private val cooldowns = mutableMapOf<String, Instant>()

    /**
     * Evaluate whether a suggestion should be shown after a command execution.
     * Returns an AliasSuggestion if the command meets threshold and is not in cooldown, null otherwise.
     */
    fun evaluateAfterExecution(command: String): AliasSuggestion? {
        if (!meetsThreshold(command)) return null
        if (isInCooldown(command)) return null

        val count = historyRepository.countFor(command)
        val existingNames = aliasRepository.all().map { it.name }.toSet()
        val suggestedName = nameDeriver.derive(command, existingNames)
        val instruction = "shady alias add $suggestedName -- $command"

        cooldowns[command] = clock()

        return AliasSuggestion(
            command = command,
            executionCount = count,
            suggestedName = suggestedName,
            aliasAddInstruction = instruction,
        )
    }

    /**
     * List all current suggestions meeting criteria.
     * Sorted by execution count descending.
     */
    fun listSuggestions(): List<AliasSuggestion> {
        val frequencies = historyRepository.commandFrequencies()
        val existingAliasCommands = aliasRepository.all().map { it.command }.toSet()
        val existingAliasNames = aliasRepository.all().map { it.name }.toSet()

        return frequencies
            .filter { (command, count) ->
                count >= minimumFrequency &&
                    command !in existingAliasCommands
            }
            .map { (command, count) ->
                val suggestedName = nameDeriver.derive(command, existingAliasNames)
                AliasSuggestion(
                    command = command,
                    executionCount = count,
                    suggestedName = suggestedName,
                    aliasAddInstruction = "shady alias add $suggestedName -- $command",
                )
            }
            .sortedByDescending { it.executionCount }
    }

    /**
     * Accept the suggestion at the given 1-based index with the provided name.
     */
    fun acceptSuggestion(index: Int, aliasName: String): AcceptResult {
        val suggestions = listSuggestions()

        if (index < 1 || index > suggestions.size) {
            val validRange = if (suggestions.isEmpty()) IntRange(0, 0) else 1..suggestions.size
            return AcceptResult.IndexOutOfRange(validRange)
        }

        if (!AliasNameDeriver.NAME_PATTERN.matches(aliasName)) {
            return AcceptResult.InvalidName("Alias name must match [a-zA-Z0-9_\\-.]+")
        }

        if (aliasName in AliasNameDeriver.DEFAULT_RESERVED_NAMES) {
            return AcceptResult.InvalidName("Alias name '$aliasName' is reserved.")
        }

        val suggestion = suggestions[index - 1]
        aliasRepository.upsert(AliasEntry(name = aliasName, command = suggestion.command))
        historyRepository.removeCommand(suggestion.command)

        return AcceptResult.Success(aliasName = aliasName, command = suggestion.command)
    }

    private fun meetsThreshold(command: String): Boolean {
        if (historyRepository.countFor(command) < minimumFrequency) return false
        val existingAliasCommands = aliasRepository.all().map { it.command }.toSet()
        return command !in existingAliasCommands
    }

    private fun isInCooldown(command: String): Boolean {
        val lastDisplayed = cooldowns[command] ?: return false
        val elapsed = Duration.between(lastDisplayed, clock()).seconds
        return elapsed < cooldownSeconds
    }
}
