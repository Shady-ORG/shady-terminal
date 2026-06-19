package cli.shady.update

import cli.shady.commands.config.ShadyConfig
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import kotlin.io.path.createDirectories
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

data class AvailableUpdate(
    val version: String,
    val repositoryUrl: String,
)

sealed interface UpdateCheckResult {
    data object Disabled : UpdateCheckResult
    data object NotDue : UpdateCheckResult
    data object UpToDate : UpdateCheckResult
    data class Available(val update: AvailableUpdate) : UpdateCheckResult
    data class Failed(val message: String) : UpdateCheckResult
}

@Serializable
private data class PersistedUpdateState(
    val lastCheckedEpochSecond: Long = 0L,
    val latestVersion: String? = null,
)

class UpdateChecker(
    private val statePath: Path,
    private val client: HttpClient = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.ALWAYS)
        .connectTimeout(Duration.ofSeconds(4))
        .build(),
    private val json: Json = Json { prettyPrint = true; encodeDefaults = true },
    private val clock: () -> Instant = Instant::now,
) {
    fun check(config: ShadyConfig, force: Boolean = false): UpdateCheckResult {
        if (!config.features.startupUpdateCheckEnabled) return UpdateCheckResult.Disabled
        val repositoryUrl = config.updates.repositoryUrl?.trim()?.trimEnd('/')
            ?.takeIf(String::isNotBlank)
            ?: System.getenv("SHADY_REPO_URL")?.trim()?.trimEnd('/')?.takeIf(String::isNotBlank)
            ?: return UpdateCheckResult.Disabled

        val persisted = loadState()
        val elapsedHours = Duration.between(
            Instant.ofEpochSecond(persisted.lastCheckedEpochSecond),
            clock(),
        ).toHours()
        if (!force && elapsedHours < config.updates.checkIntervalHours) return UpdateCheckResult.NotDue

        return try {
            val request = HttpRequest.newBuilder(URI.create("$repositoryUrl/releases/latest"))
                .timeout(Duration.ofSeconds(8))
                .GET()
                .build()
            val response = client.send(request, HttpResponse.BodyHandlers.discarding())
            val latest = response.uri().path.substringAfterLast('/').removePrefix("v")
            saveState(PersistedUpdateState(clock().epochSecond, latest))
            if (isNewer(latest, BuildInfo.VERSION)) {
                UpdateCheckResult.Available(AvailableUpdate(latest, repositoryUrl))
            } else {
                UpdateCheckResult.UpToDate
            }
        } catch (error: Exception) {
            saveState(PersistedUpdateState(clock().epochSecond, persisted.latestVersion))
            UpdateCheckResult.Failed(error.message ?: "Unable to check for updates.")
        }
    }

    private fun isNewer(candidate: String, current: String): Boolean {
        val candidateParts = candidate.split('.').map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
        val currentParts = current.split('.').map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
        val size = maxOf(candidateParts.size, currentParts.size)
        repeat(size) { index ->
            val comparison = candidateParts.getOrElse(index) { 0 }.compareTo(currentParts.getOrElse(index) { 0 })
            if (comparison != 0) return comparison > 0
        }
        return false
    }

    private fun loadState(): PersistedUpdateState {
        if (!Files.exists(statePath)) return PersistedUpdateState()
        return runCatching {
            json.decodeFromString<PersistedUpdateState>(Files.readString(statePath))
        }.getOrDefault(PersistedUpdateState())
    }

    private fun saveState(state: PersistedUpdateState) {
        runCatching {
            statePath.parent?.createDirectories()
            Files.writeString(statePath, json.encodeToString(state))
        }
    }
}
