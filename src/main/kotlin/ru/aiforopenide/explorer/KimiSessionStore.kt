package ru.aiforopenide.explorer

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.nio.file.Files
import java.nio.file.Path

data class KimiSessionSummary(
    val id: String,
    val title: String,
    val updatedAt: Long,
)

class KimiSessionStore(private val workingDirectory: Path) {
    fun list(): List<KimiSessionSummary> {
        val index = kimiHome().resolve("session_index.jsonl")
        if (!Files.isRegularFile(index)) return emptyList()
        val expectedDirectory = workingDirectory.toAbsolutePath().normalize()
        return Files.newBufferedReader(index).useLines { lines ->
            lines.mapNotNull { line ->
                try {
                    val entry = JsonParser.parseString(line).asJsonObject
                    val workDir = entry.get("workDir")?.asString?.let(Path::of) ?: return@mapNotNull null
                    if (workDir.toAbsolutePath().normalize() != expectedDirectory) return@mapNotNull null
                    loadState(entry)
                } catch (_: Exception) {
                    null
                }
            }.sortedByDescending(KimiSessionSummary::updatedAt).toList()
        }
    }

    fun latestSessionDirectory(): Path? = list().firstOrNull()?.let { sessionDirectory(it.id) }

    fun sessionDirectory(sessionId: String): Path? {
        val index = kimiHome().resolve("session_index.jsonl")
        if (!Files.isRegularFile(index)) return null
        val expectedDirectory = workingDirectory.toAbsolutePath().normalize()
        return Files.newBufferedReader(index).useLines { lines ->
            lines.mapNotNull { line ->
                try {
                    val entry = JsonParser.parseString(line).asJsonObject
                    if (entry.get("sessionId")?.asString != sessionId) return@mapNotNull null
                    val workDir = entry.get("workDir")?.asString?.let(Path::of) ?: return@mapNotNull null
                    if (workDir.toAbsolutePath().normalize() != expectedDirectory) return@mapNotNull null
                    entry.get("sessionDir")?.asString?.let(Path::of)
                } catch (_: Exception) {
                    null
                }
            }.firstOrNull()
        }
    }

    fun delete(sessionId: String) {
        val index = kimiHome().resolve("session_index.jsonl")
        if (!Files.isRegularFile(index)) return
        val expectedDirectory = workingDirectory.toAbsolutePath().normalize()
        val sessionDirectory = Files.newBufferedReader(index).useLines { lines ->
            lines.mapNotNull { line ->
                try {
                    val entry = JsonParser.parseString(line).asJsonObject
                    if (entry.get("sessionId")?.asString != sessionId) return@mapNotNull null
                    val workDir = entry.get("workDir")?.asString?.let(Path::of) ?: return@mapNotNull null
                    if (workDir.toAbsolutePath().normalize() != expectedDirectory) return@mapNotNull null
                    entry.get("sessionDir")?.asString?.let(Path::of)
                } catch (_: Exception) {
                    null
                }
            }.firstOrNull()
        } ?: return
        Files.walk(sessionDirectory).use { paths ->
            paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
        }
    }

    private fun loadState(entry: JsonObject): KimiSessionSummary? {
        val id = entry.get("sessionId")?.asString ?: return null
        val stateFile = entry.get("sessionDir")?.asString?.let { Path.of(it, "state.json") }
            ?: return KimiSessionSummary(id, "Kimi Code", 0)
        if (!Files.isRegularFile(stateFile)) return null
        val state = JsonParser.parseString(Files.readString(stateFile)).asJsonObject
        if (state.get("archived")?.asBoolean == true) return null
        return KimiSessionSummary(
            id = id,
            title = state.get("title")?.asString?.takeIf(String::isNotBlank) ?: "Kimi Code",
            updatedAt = state.get("updatedAt")?.asLong ?: 0,
        )
    }

    private fun kimiHome(): Path = System.getenv("KIMI_CODE_HOME")?.takeIf(String::isNotBlank)?.let(Path::of)
        ?: Path.of(System.getProperty("user.home"), ".kimi-code")
}
