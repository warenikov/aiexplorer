package ru.aiforopenide.explorer

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.LocalFileSystem
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
import javax.swing.Timer

class KimiFileGraphPanel(
    private val project: Project,
    arguments: List<String>,
) : JPanel(BorderLayout()), Disposable {
    private val log = Logger.getInstance(KimiFileGraphPanel::class.java)
    private val gson = Gson()
    private val reader = KimiSessionGraphReader(
        project.basePath?.let(Path::of) ?: Path.of(System.getProperty("user.home")),
        arguments.indexOf("--session").takeIf { it >= 0 }?.let { arguments.getOrNull(it + 1) },
    )
    private val refreshTimer = Timer(1200) { refreshEvents() }
    private val refreshing = AtomicBoolean(false)
    private var browser: JBCefBrowser? = null
    private var query: JBCefJSQuery? = null
    private var ready = false
    private val pendingEvents = mutableListOf<JsonObject>()

    init {
        Disposer.register(this, Disposable {
            refreshTimer.stop()
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
            add(JBLabel("<html><center>Для графа файлов необходим JCEF.<br>Включите плагин Web Browser (JCEF) в OpenIDE.</center></html>", JBLabel.CENTER), BorderLayout.CENTER)
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
        refreshTimer.start()
    }

    private fun createWebFile(): Path {
        val html = javaClass.getResourceAsStream("/web/graph.html")?.bufferedReader()?.use { it.readText() }
            ?: error("AI Explorer graph resource is missing")
        val script = """
            <script>
              window.AI_EXPLORER_GRAPH_ONLY = true;
              window.AiExplorerHost = {
                send: function(message) { ${query?.inject("message").orEmpty()} }
              };
            </script>
        """.trimIndent()
        val insertionPoint = html.lastIndexOf("</head>").also { require(it >= 0) { "AI Explorer HTML head is missing" } }
        val document = html.substring(0, insertionPoint) + script + html.substring(insertionPoint)
        return Files.createTempFile("ai-explorer-graph-", ".html").also { file ->
            Files.writeString(file, document)
            file.toFile().deleteOnExit()
        }
    }

    private fun handleCommand(message: String) {
        onEdt {
            try {
                val command = JsonParser.parseString(message).asJsonObject
                when (command.get("type")?.asString) {
                    "ui.ready" -> {
                        ready = true
                        post("theme") { addProperty("dark", UIUtil.isUnderDarcula()) }
                        pendingEvents.forEach(::postNow)
                        pendingEvents.clear()
                        refreshEvents()
                    }
                    "file.open" -> openFile(command.get("path").asString)
                    "link.open" -> BrowserUtil.browse(command.get("url").asString)
                }
            } catch (e: Exception) {
                log.warn("Invalid AI Explorer graph command", e)
            }
        }
    }

    private fun refreshEvents() {
        if (!refreshing.compareAndSet(false, true)) return
        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                val result = reader.readNewEvents()
                onEdt {
                    if (result.reset) post("history.loading")
                    result.events.forEach(::postNowOrLater)
                    if (result.reset) post("history.loaded")
                }
            } catch (e: Exception) {
                log.warn("Cannot read Kimi Code session graph", e)
            } finally {
                refreshing.set(false)
            }
        }
    }

    private fun postNowOrLater(event: JsonObject) {
        if (ready) postNow(event) else pendingEvents.add(event)
    }

    private fun post(type: String, payload: JsonObject.() -> Unit = {}) {
        val event = JsonObject().apply {
            addProperty("type", type)
            payload()
        }
        postNowOrLater(event)
    }

    private fun postNow(event: JsonObject) {
        val currentBrowser = browser ?: return
        currentBrowser.cefBrowser.executeJavaScript(
            "window.AiExplorer?.receive(${gson.toJson(event)});",
            currentBrowser.cefBrowser.url,
            0,
        )
    }

    private fun openFile(rawPath: String) {
        val path = Path.of(rawPath)
        val resolved = if (path.isAbsolute) path else project.basePath?.let { Path.of(it).resolve(path) } ?: path
        val file = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(resolved.toFile()) ?: return
        FileEditorManager.getInstance(project).openFile(file, true)
    }

    private fun onEdt(action: () -> Unit) {
        if (SwingUtilities.isEventDispatchThread()) action()
        else ApplicationManager.getApplication().invokeLater(action, ModalityState.any())
    }

    override fun dispose() = Unit
}
