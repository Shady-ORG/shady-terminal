package cli.shady.commands

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.file.Path
import java.time.Instant
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.io.path.createDirectories

data class CommandHistoryEntry(
    val command: String,
    val timestamp: Instant,
)

@Serializable
private data class PersistedEntry(
    val command: String,
    val epochSecond: Long,
)

@Serializable
private data class PersistedHistory(
    val entries: List<PersistedEntry> = emptyList(),
)

class CommandHistoryRepository(
    private val historyFile: Path? = null,
    private val maxEntries: Int = 500,
    private val windowSeconds: Long = 3600,
    private val clock: () -> Instant = Instant::now,
) {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val entries = mutableListOf<CommandHistoryEntry>()
    private var loaded = false

    /** Record a successful command execution. Evicts expired and overflow entries. */
    fun record(command: String) {
        ensureLoaded()
        evictExpired()
        entries.add(CommandHistoryEntry(command, clock()))
        if (entries.size > maxEntries) {
            entries.removeFirst()
        }
        persist()
    }

    /** Return all non-expired entries. Evicts expired entries as a side effect. */
    fun all(): List<CommandHistoryEntry> {
        ensureLoaded()
        evictExpired()
        return entries.toList()
    }

    /** Return non-expired entries matching the given command text. */
    fun entriesFor(command: String): List<CommandHistoryEntry> {
        ensureLoaded()
        evictExpired()
        return entries.filter { it.command == command }
    }

    /** Count non-expired executions of a specific command. */
    fun countFor(command: String): Int {
        ensureLoaded()
        evictExpired()
        return entries.count { it.command == command }
    }

    /** Return all distinct commands with their execution counts, sorted descending by count. */
    fun commandFrequencies(): List<Pair<String, Int>> {
        ensureLoaded()
        evictExpired()
        return entries
            .groupingBy { it.command }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .map { it.key to it.value }
    }

    /** Remove all entries for a given command. */
    fun removeCommand(command: String) {
        ensureLoaded()
        entries.removeAll { it.command == command }
        persist()
    }

    /** Evict entries older than the time window. */
    private fun evictExpired() {
        val now = clock()
        entries.removeAll { entry ->
            java.time.Duration.between(entry.timestamp, now).seconds > windowSeconds
        }
    }

    private fun ensureLoaded() {
        if (loaded) return
        loaded = true
        val file = historyFile ?: return
        if (!file.exists()) return
        try {
            val persisted = json.decodeFromString<PersistedHistory>(file.readText())
            entries.clear()
            entries.addAll(
                persisted.entries.map {
                    CommandHistoryEntry(it.command, Instant.ofEpochSecond(it.epochSecond))
                }
            )
        } catch (_: Exception) {
            // Corrupted file — start fresh
        }
    }

    private fun persist() {
        val file = historyFile ?: return
        try {
            file.parent?.createDirectories()
            val persisted = PersistedHistory(
                entries.map { PersistedEntry(it.command, it.timestamp.epochSecond) }
            )
            file.writeText(json.encodeToString(persisted))
        } catch (_: Exception) {
            // Best effort — don't crash if write fails
        }
    }
}
