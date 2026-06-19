package cli.shady.app

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class RestoredTab(
    val title: String,
    val workingDirectory: String,
)

@Serializable
data class RestoredWorkspace(
    val tabs: List<RestoredTab> = emptyList(),
    val activeIndex: Int = 0,
)

class WorkspaceStateRepository(
    private val path: Path,
    private val json: Json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
    },
) {
    fun load(): RestoredWorkspace {
        if (!Files.exists(path)) return RestoredWorkspace()
        return try {
            json.decodeFromString<RestoredWorkspace>(Files.readString(path))
        } catch (_: Exception) {
            RestoredWorkspace()
        }
    }

    fun save(state: TerminalWorkspaceState) {
        path.parent?.createDirectories()
        val activeIndex = state.tabs.indexOfFirst { it.id == state.activeTabId }.coerceAtLeast(0)
        val persisted = RestoredWorkspace(
            tabs = state.tabs.map { RestoredTab(it.title, it.workingDirectory) },
            activeIndex = activeIndex,
        )
        Files.writeString(path, json.encodeToString(persisted))
    }
}
