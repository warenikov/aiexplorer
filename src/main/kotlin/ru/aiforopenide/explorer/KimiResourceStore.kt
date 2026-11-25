package ru.aiforopenide.explorer

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.name
import kotlin.streams.asSequence

class KimiResourceStore(projectPath: Path) {
    private val home = Path.of(System.getProperty("user.home"))
    private val kimiHome = System.getenv("KIMI_CODE_HOME")?.takeIf(String::isNotBlank)?.let(Path::of)
        ?: home.resolve(".kimi-code")
    private val project = projectPath.toAbsolutePath().normalize()

    fun load(kind: String): JsonArray = when (kind) {
        "skills" -> skills()
        "settings" -> settings()
        "mcp" -> mcp()
        else -> JsonArray()
    }

    private fun skills(): JsonArray {
        val roots = mutableListOf<Triple<String, String, Path>>()
        roots += Triple("Проект", "project", project.resolve(".kimi-code/skills"))
        roots += Triple("Проект", "project", project.resolve(".agents/skills"))
        roots += Triple("Глобальные", "global", kimiHome.resolve("skills"))
        roots += Triple("Глобальные", "global", home.resolve(".agents/skills"))
        extraSkillDirectories().forEach { roots += Triple("Дополнительные", "extra", it) }
        return JsonArray().apply {
            roots.filter { Files.isDirectory(it.third) }.forEach { (label, scope, root) ->
                add(group("$label · ${root.displayName()}", root, scope))
            }
        }
    }

    private fun settings(): JsonArray {
        val global = JsonArray()
        addDocument(global, kimiHome.resolve("AGENTS.md"), "global", "Глобальные инструкции")
        addDocument(global, home.resolve(".claude/CLAUDE.md"), "global", "Глобальные инструкции Claude")
        addFile(global, kimiHome.resolve("config.toml"), "global", "Основные настройки")
        addFile(global, kimiHome.resolve("tui.toml"), "global", "Настройки терминала")

        val projectFiles = JsonArray()
        if (Files.isDirectory(project)) {
            Files.walk(project).use { paths ->
                paths.asSequence()
                    .filter { Files.isRegularFile(it) }
                    .filter { it.name == "AGENTS.md" || it.name == "CLAUDE.md" || it.name == "claude.md" }
                    .filterNot { it.any { part -> part.name in ignoredDirectories } }
                    .sorted()
                    .forEach { addDocument(projectFiles, it, "project", project.relativize(it.parent).toString().ifEmpty { "Корень проекта" }) }
            }
        }
        addFile(projectFiles, project.resolve(".kimi-code/config.toml"), "project", "Настройки проекта")
        addFile(projectFiles, project.resolve(".kimi-code/local.toml"), "project", "Локальные настройки проекта")
        return JsonArray().apply {
            add(group("Глобальные", null, "global", global))
            add(group("Проект", null, "project", projectFiles))
        }
    }

    private fun mcp(): JsonArray {
        val globalPath = kimiHome.resolve("mcp.json")
        val projectPath = project.resolve(".kimi-code/mcp.json")
        val globalServers = serverNames(globalPath)
        return JsonArray().apply {
            add(mcpGroup("Глобальные", globalPath, "global", emptySet()))
            add(mcpGroup("Проект", projectPath, "project", globalServers))
        }
    }

    private fun mcpGroup(title: String, path: Path, scope: String, overridden: Set<String>): JsonObject {
        val entries = JsonArray()
        if (Files.isRegularFile(path)) {
            try {
                val root = JsonParser.parseString(Files.readString(path)).asJsonObject
                val servers = root.getAsJsonObject("mcpServers")
                servers?.entrySet()?.sortedBy { it.key }?.forEach { (name, element) ->
                    val config = element.asJsonObject
                    val command = config.get("command")?.asString
                    val url = config.get("url")?.asString
                    val target = command ?: url?.let(::safeUrlHost) ?: "MCP"
                    val enabled = config.get("enabled")?.asBoolean != false
                    entries.add(node(name, path, scope, "$target · ${if (enabled) "включён" else "выключен"}${if (name in overridden) " · переопределяет глобальный" else ""}"))
                }
            } catch (_: Exception) {
                entries.add(node("Ошибка чтения конфигурации", path, scope, "Открыть файл"))
            }
        }
        return group(title, if (Files.isRegularFile(path)) path else null, scope, entries, if (entries.size() == 0) "Нет конфигурации" else null)
    }

    private fun safeUrlHost(value: String): String = try {
        val uri = URI(value)
        uri.host?.let { if (uri.port >= 0) "$it:${uri.port}" else it } ?: "HTTP MCP"
    } catch (_: Exception) {
        "HTTP MCP"
    }

    private fun serverNames(path: Path): Set<String> = try {
        JsonParser.parseString(Files.readString(path)).asJsonObject
            .getAsJsonObject("mcpServers")?.keySet().orEmpty()
    } catch (_: Exception) {
        emptySet()
    }

    private fun extraSkillDirectories(): List<Path> {
        val config = kimiHome.resolve("config.toml")
        if (!Files.isRegularFile(config)) return emptyList()
        return try {
            val text = Files.readString(config)
            Regex("""(?m)^extra_skill_dirs\s*=\s*\[(.*?)]""").find(text)?.groupValues?.get(1)
                ?.let { body -> Regex(""""((?:[^"\\]|\\.)*)"""").findAll(body).map { it.groupValues[1] } }
                ?.map { value -> if (value == "~" || value.startsWith("~/")) home.resolve(value.removePrefix("~/")) else Path.of(value).let { if (it.isAbsolute) it else project.resolve(it) } }
                ?.toList().orEmpty()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun group(title: String, root: Path?, scope: String): JsonObject =
        group(title, root, scope, root?.let { directoryTree(it, scope) } ?: JsonArray())

    private fun group(title: String, path: Path?, scope: String, children: JsonArray, subtitle: String? = null): JsonObject =
        JsonObject().apply {
            addProperty("title", title)
            addProperty("scope", scope)
            path?.let {
                addProperty("path", it.toString())
                if (Files.isDirectory(it)) addProperty("kind", "directory")
            }
            subtitle?.let { addProperty("subtitle", it) }
            add("children", children)
        }

    private fun directoryTree(directory: Path, scope: String): JsonArray {
        val result = JsonArray()
        Files.list(directory).use { paths ->
            paths.asSequence().sortedWith(compareBy({ !Files.isDirectory(it) }, { it.name.lowercase() })).forEach { path ->
                if (Files.isDirectory(path) && path.name !in ignoredDirectories) {
                    result.add(group(path.name, path, scope, directoryTree(path, scope)))
                } else if (Files.isRegularFile(path)) {
                    result.add(documentNode(path, scope))
                }
            }
        }
        return result
    }

    private fun addDocument(target: JsonArray, path: Path, scope: String, subtitle: String) {
        if (Files.isRegularFile(path)) target.add(documentNode(path, scope, subtitle))
    }

    private fun addFile(target: JsonArray, path: Path, scope: String, subtitle: String) {
        if (Files.isRegularFile(path)) target.add(node(path.name, path, scope, subtitle))
    }

    private fun documentNode(path: Path, scope: String, subtitle: String? = null): JsonObject {
        val isSkill = path.name.equals("SKILL.md", true) || path.parent == path.root?.resolve("skills")
        val text = if (path.name.endsWith(".md", true)) try { Files.readString(path) } catch (_: Exception) { null } else null
        val description = text?.let { readFrontmatter(it, "description") }
        val item = node(path.name, path, scope, subtitle ?: description)
        if (text != null) {
            val links = localMarkdownLinks(path, text)
            if (links.isNotEmpty()) item.add("children", JsonArray().apply {
                links.forEach { add(node("Ссылка · ${it.name}", it, scope)) }
            })
        }
        if (isSkill) item.addProperty("kind", "skill")
        return item
    }

    private fun readFrontmatter(text: String, key: String): String? =
        if (!text.startsWith("---")) null
        else Regex("""(?m)^$key\s*:\s*["']?(.+?)["']?$""").find(text.substringBefore("\n---", text))?.groupValues?.get(1)?.trim()

    private fun localMarkdownLinks(path: Path, text: String): List<Path> = try {
        val markdown = Regex("""\[[^]]*]\(([^)#\s]+)""").findAll(text).map { it.groupValues[1] }
        val wiki = Regex("""\[\[([^]|#]+)""").findAll(text).map { it.groupValues[1] }
        (markdown + wiki).mapNotNull { link ->
            if (link.startsWith("http") || link.startsWith("mailto:")) null
            else path.parent.resolve(link).normalize().takeIf { Files.isRegularFile(it) && it != path }
        }.distinct().toList()
    } catch (_: Exception) {
        emptyList()
    }

    private fun node(title: String, path: Path, scope: String, subtitle: String? = null) = JsonObject().apply {
        addProperty("title", title)
        addProperty("path", path.toString())
        addProperty("scope", scope)
        subtitle?.takeIf(String::isNotBlank)?.let { addProperty("subtitle", it) }
    }

    private fun Path.displayName() = fileName?.toString() ?: toString()

    private companion object {
        val ignoredDirectories = setOf(".git", ".idea", ".gradle", ".intellijPlatform", ".kotlin", "build", "node_modules")
    }
}
