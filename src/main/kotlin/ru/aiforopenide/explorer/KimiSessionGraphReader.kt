package ru.aiforopenide.explorer

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

data class KimiGraphEvents(
    val reset: Boolean,
    val events: List<JsonObject>,
)

class KimiSessionGraphReader(
    workingDirectory: Path,
    private val requestedSessionId: String?,
) {
    private val store = KimiSessionStore(workingDirectory)
    private val offsets = mutableMapOf<Path, Long>()
    private var sessionDirectory: Path? = null

    fun readNewEvents(): KimiGraphEvents {
        val directory = requestedSessionId?.let(store::sessionDirectory) ?: store.latestSessionDirectory()
        if (directory == null) {
            val reset = sessionDirectory != null
            sessionDirectory = null
            offsets.clear()
            return KimiGraphEvents(reset, emptyList())
        }
        val reset = directory != sessionDirectory
        if (reset) {
            sessionDirectory = directory
            offsets.clear()
        }
        val agents = directory.resolve("agents")
        val files = if (Files.isDirectory(agents)) {
            Files.walk(agents).use { paths ->
                paths.filter { Files.isRegularFile(it) && it.fileName.toString() == "wire.jsonl" }.sorted().toList()
            }
        } else {
            emptyList()
        }
        return KimiGraphEvents(reset, files.flatMap(::readNewEvents))
    }

    private fun readNewEvents(path: Path): List<JsonObject> {
        val size = Files.size(path)
        val previousOffset = offsets[path] ?: 0
        val start = if (size < previousOffset) 0 else previousOffset
        if (size == start) return emptyList()
        return Files.newInputStream(path).use { input ->
            input.skipNBytes(start)
            val text = String(input.readBytes(), StandardCharsets.UTF_8)
            val lastLineBreak = text.lastIndexOf('\n')
            if (lastLineBreak < 0) return@use emptyList()
            val completeText = text.substring(0, lastLineBreak + 1)
            offsets[path] = start + completeText.toByteArray(StandardCharsets.UTF_8).size
            completeText.lineSequence()
                .filter(String::isNotBlank)
                .mapNotNull { line ->
                    try {
                        toGraphEvent(JsonParser.parseString(line).asJsonObject)
                    } catch (_: Exception) {
                        null
                    }
                }
                .toList()
        }
    }

    private fun toGraphEvent(record: JsonObject): JsonObject? {
        val event = if (record.get("type")?.asString == "context.append_loop_event") {
            record.getAsJsonObject("event") ?: return null
        } else {
            record
        }
        val update = when (event.get("type")?.asString) {
            "tool.call" -> JsonObject().apply {
                addProperty("toolCallId", event.get("toolCallId")?.asString ?: return null)
                addProperty("kind", toolKind(event))
                addProperty("title", event.get("name")?.asString ?: "Инструмент")
                addProperty("status", "in_progress")
                add("rawInput", event.get("args") ?: JsonObject())
            }
            "tool.result" -> JsonObject().apply {
                addProperty("toolCallId", event.get("toolCallId")?.asString ?: return null)
                val result = event.getAsJsonObject("result")
                addProperty("status", if (result?.get("isError")?.asBoolean == true) "failed" else "completed")
            }
            else -> return null
        }
        return JsonObject().apply {
            addProperty("type", "tool.updated")
            add("update", update)
        }
    }

    private fun toolKind(event: JsonObject): String {
        val display = event.getAsJsonObject("display")
        if (display?.get("kind")?.asString == "file_io") {
            return display.get("operation")?.asString ?: "read"
        }
        return when (event.get("name")?.asString) {
            "Read" -> "read"
            "Edit" -> "edit"
            "Write" -> "write"
            "FetchURL", "WebSearch" -> "fetch"
            "Agent", "AgentSwarm" -> "delegate"
            "Grep", "Glob" -> "search"
            else -> "other"
        }
    }
}
