package ru.aiforopenide.explorer

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.ui.components.JBLabel
import com.intellij.ui.jcef.JBCefApp
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.ui.jcef.JBCefBrowserBase
import com.intellij.ui.jcef.JBCefJSQuery
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.JPanel
import javax.swing.SwingUtilities

internal class KimiResourcePanel(
    private val project: Project,
    private val kind: String,
) : JPanel(BorderLayout()), Disposable {
    private val log = Logger.getInstance(KimiResourcePanel::class.java)
    private val store = KimiResourceStore(project.basePath?.let(Path::of) ?: Path.of(System.getProperty("user.home")))
    private val gson = Gson()
    private var browser: JBCefBrowser? = null
    private var query: JBCefJSQuery? = null
    private var ready = false
    private val refreshing = AtomicBoolean(false)
    private var refreshAgain = false

    init {
        project.messageBus.connect(this).subscribe(AiExplorerLocaleListener.TOPIC, AiExplorerLocaleListener { reloadWeb() })
        project.messageBus.connect(this).subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
            override fun after(events: List<VFileEvent>) {
                if (events.any { it.path.endsWith(".md") || it.path.endsWith(".json") || it.path.endsWith(".toml") }) {
                    refreshResources()
                }
            }
        })
        Disposer.register(this, Disposable {
            removeAll()
            query?.dispose()
            browser?.dispose()
            query = null
            browser = null
        })
        ApplicationManager.getApplication().invokeLater({ initializeBrowser() }, ModalityState.any())
    }

    private fun initializeBrowser() {
        if (Disposer.isDisposed(this) || browser != null) return
        if (!JBCefApp.isSupported()) {
            add(JBLabel(AiExplorerBundle.message("jcef.required"), JBLabel.CENTER), BorderLayout.CENTER)
            return
        }
        val newBrowser = JBCefBrowser.createBuilder().setOffScreenRendering(false).build()
        browser = newBrowser
        query = JBCefJSQuery.create(newBrowser as JBCefBrowserBase).also { jsQuery ->
            jsQuery.addHandler { message ->
                handleCommand(message)
                null
            }
        }
        add(newBrowser.component, BorderLayout.CENTER)
        newBrowser.loadURL(createWebFile().toUri().toString())
    }

    private fun createWebFile(): Path {
        val resource = when (kind) {
            "skills" -> "/web/skills.html"
            "mcp" -> "/web/mcp.html"
            else -> "/web/resources.html"
        }
        val html = javaClass.getResourceAsStream(resource)?.bufferedReader()?.use { it.readText() }
            ?: error("AI Explorer resource browser is missing")
        val script = """
            <script>
              window.AI_EXPLORER_RESOURCE_KIND = ${gson.toJson(kind)};
              window.AiExplorerHost = { send: function(message) { ${query?.inject("message").orEmpty()} } };
            </script>
        """.trimIndent()
        val insertionPoint = html.lastIndexOf("</head>").also { require(it >= 0) }
        val document = html.substring(0, insertionPoint) + AiExplorerWebLocalization.script() + script + html.substring(insertionPoint)
        return Files.createTempFile("ai-explorer-resources-", ".html").also { file ->
            Files.writeString(file, document)
            file.toFile().deleteOnExit()
        }
    }

    private fun reloadWeb() {
        ready = false
        browser?.loadURL(createWebFile().toUri().toString())
    }

    private fun handleCommand(message: String) {
        onEdt {
            try {
                val command = JsonParser.parseString(message).asJsonObject
                when (command.get("type")?.asString) {
                    "ui.ready" -> {
                        ready = true
                        post("theme") { addProperty("dark", UIUtil.isUnderDarcula()) }
                        refreshResources()
                    }
                    "resources.refresh" -> refreshResources()
                    "file.open" -> openFile(command.get("path").asString)
                    "mcp.save" -> mutateMcp(command.get("oldName")?.asString, AiExplorerBundle.message("mcp.saved")) {
                        store.saveMcp(command)
                    }
                    "mcp.delete" -> mutateMcp(null, AiExplorerBundle.message("mcp.deleted")) {
                        store.deleteMcp(command.get("scope").asString, command.get("name").asString)
                    }
                }
            } catch (e: Exception) {
                log.warn("Invalid AI Explorer resource command", e)
            }
        }
    }

    private fun refreshResources() {
        if (!refreshing.compareAndSet(false, true)) {
            refreshAgain = true
            return
        }
        refreshAgain = false
        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                val nodes = store.load(kind)
                onEdt { post("resources") { add("nodes", nodes) } }
            } catch (e: Exception) {
                log.warn("Cannot load AI Explorer resources", e)
            } finally {
                refreshing.set(false)
                if (refreshAgain) onEdt { refreshResources() }
            }
        }
    }

    private fun mutateMcp(serverId: String?, success: String, action: () -> Unit) {
        ApplicationManager.getApplication().executeOnPooledThread {
            val message = try {
                action()
                success
            } catch (e: Exception) {
                log.warn("Cannot update MCP configuration", e)
                AiExplorerBundle.message("mcp.error", e.message ?: AiExplorerBundle.message("mcp.save.failed"))
            }
            onEdt {
                post("mcp.result") {
                    serverId?.let { addProperty("serverId", it) }
                    addProperty("message", message)
                }
            }
        }
    }

    private fun openFile(rawPath: String) {
        val path = Path.of(rawPath)
        val file = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(path.toFile()) ?: return
        FileEditorManager.getInstance(project).openFile(file, true)
    }

    private fun post(type: String, payload: JsonObject.() -> Unit = {}) {
        if (!ready) return
        val event = JsonObject().apply {
            addProperty("type", type)
            payload()
        }
        val currentBrowser = browser ?: return
        currentBrowser.cefBrowser.executeJavaScript(
            "window.AiExplorer?.receive(${gson.toJson(event)});",
            currentBrowser.cefBrowser.url,
            0,
        )
    }

    private fun onEdt(action: () -> Unit) {
        if (SwingUtilities.isEventDispatchThread()) action()
        else ApplicationManager.getApplication().invokeLater(action, ModalityState.any())
    }

    override fun dispose() = Unit
}
