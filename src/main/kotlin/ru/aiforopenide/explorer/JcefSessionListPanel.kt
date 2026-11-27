package ru.aiforopenide.explorer

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindowManager
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
import javax.swing.Timer
import javax.swing.SwingUtilities

internal class JcefSessionListPanel(
    private val project: Project,
) : JPanel(BorderLayout()), Disposable {
    private val log = Logger.getInstance(JcefSessionListPanel::class.java)
    private val gson = Gson()
    private val store = KimiSessionStore(project.basePath?.let(Path::of) ?: Path.of(System.getProperty("user.home")))
    private var browser: JBCefBrowser? = null
    private var query: JBCefJSQuery? = null
    private var ready = false
    private val pendingEvents = mutableListOf<JsonObject>()
    private val refreshing = AtomicBoolean(false)
    private val refreshTimer = Timer(2000) { if (isShowing) refreshSessions() }

    init {
        project.messageBus.connect(this).subscribe(AiExplorerLocaleListener.TOPIC, AiExplorerLocaleListener { reloadWeb() })
        Disposer.register(this, Disposable {
            refreshTimer.stop()
            removeAll()
            query?.dispose()
            browser?.dispose()
            query = null
            browser = null
        })
        project.messageBus.connect(this).subscribe(ChatSelectionListener.TOPIC, ChatSelectionListener { sessionId ->
            post("session.current") { addProperty("sessionId", sessionId) }
        })
        project.messageBus.connect(this).subscribe(ChatMetadataListener.TOPIC, ChatMetadataListener { refreshSessions() })
        ApplicationManager.getApplication().invokeLater({ initializeBrowser() }, ModalityState.any())
    }

    private fun initializeBrowser() {
        if (Disposer.isDisposed(this) || browser != null) return
        if (!JBCefApp.isSupported()) {
            add(JBLabel("<html><center>${AiExplorerBundle.message("jcef.sidebar.required")}</center></html>", JBLabel.CENTER), BorderLayout.CENTER)
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
        val html = javaClass.getResourceAsStream("/web/index.html")?.bufferedReader()?.use { it.readText() }
            ?: error("AI Explorer web resources are missing")
        val script = """
            <script>
              window.AI_EXPLORER_SIDEBAR = true;
              window.AiExplorerHost = {
                send: function(message) { ${query?.inject("message").orEmpty()} }
              };
              window.addEventListener("load", function() {
                const footer = document.querySelector(".sidebar-footer");
                if (!footer) return;
                const login = document.createElement("button");
                login.className = "auth-login";
                login.textContent = ${gson.toJson(AiExplorerBundle.message("session.login"))};
                login.title = ${gson.toJson(AiExplorerBundle.message("session.login.hint"))};
                login.addEventListener("click", function() {
                  window.AiExplorerHost.send(JSON.stringify({ type: "auth.login" }));
                });
                footer.replaceChildren(login);
              });
            </script>
        """.trimIndent()
        val style = """
            <style>
              :root {
                font-family: "JetBrains Mono", "SFMono-Regular", Consolas, monospace !important;
                --bg: #1e1f22;
                --surface: #1e1f22;
                --surface-raised: #2b2d30;
                --surface-soft: #2b2d30;
                --surface-hover: #313438;
                --text: #bcbec4;
                --text-secondary: #9da0a8;
                --text-faint: #6f737a;
                --border: #393b40;
                --border-strong: #4b4d52;
                --accent: #c77d59;
                --accent-strong: #d68c67;
                --accent-soft: #35312e;
                --accent-text: #bcbec4;
                --code-bg: #1e1f22;
                --shadow: none;
              }
              :root[data-theme=dark] {
                --bg: #1e1f22;
                --surface: #1e1f22;
                --surface-raised: #2b2d30;
                --surface-soft: #2b2d30;
                --surface-hover: #313438;
                --text: #bcbec4;
                --text-secondary: #9da0a8;
                --text-faint: #6f737a;
                --border: #393b40;
                --border-strong: #4b4d52;
                --accent: #c77d59;
                --accent-strong: #d68c67;
                --accent-soft: #35312e;
                --accent-text: #bcbec4;
                --code-bg: #1e1f22;
                --shadow: none;
              }
              body, button, input, textarea, select {
                font-family: "JetBrains Mono", "SFMono-Regular", Consolas, monospace !important;
                font-size: 13px;
              }
              .brand, .session-item, .session-section-title, .search input, .new-chat {
                font-size: 13px !important;
              }
              .brand-mark, .new-chat, .session-item.active, .session-item:hover,
              .search, .icon-button, .session-action {
                border-radius: 3px !important;
                box-shadow: none !important;
              }
              .brand-mark { background: transparent !important; color: var(--text) !important; }
              .new-chat {
                background: transparent !important;
                color: var(--text) !important;
                border: 1px solid var(--border-strong) !important;
                font-weight: 400 !important;
              }
              .new-chat:hover { background: var(--surface-hover) !important; }
              .session-item.active {
                background: var(--surface-soft) !important;
                border-color: var(--border-strong) !important;
              }
              .sidebar, .sessions {
                scrollbar-width: thin;
                scrollbar-color: #55575c transparent;
              }
              .sidebar::-webkit-scrollbar, .sessions::-webkit-scrollbar {
                width: 10px;
                height: 10px;
              }
              .sidebar::-webkit-scrollbar-track, .sessions::-webkit-scrollbar-track,
              .sidebar::-webkit-scrollbar-corner, .sessions::-webkit-scrollbar-corner {
                background: transparent;
              }
              .sidebar::-webkit-scrollbar-thumb, .sessions::-webkit-scrollbar-thumb {
                background-color: #55575c;
                border: 3px solid transparent;
                border-radius: 5px;
                background-clip: padding-box;
              }
              .sidebar::-webkit-scrollbar-thumb:hover, .sessions::-webkit-scrollbar-thumb:hover {
                background-color: #6b6e75;
              }
              .sidebar-footer:has(.auth-login) { padding: 5px 12px; }
              .auth-login {
                width: 100%; height: 29px; border: 1px solid var(--border-strong);
                border-radius: 3px; color: var(--text-secondary); background: transparent;
                font-size: 11px; cursor: pointer;
              }
              .auth-login:hover { color: var(--accent-strong); border-color: var(--accent); background: var(--accent-soft); }
            </style>
        """.trimIndent()
        val insertionPoint = html.lastIndexOf("</head>").also { require(it >= 0) { "AI Explorer HTML head is missing" } }
        val document = html.substring(0, insertionPoint) + AiExplorerWebLocalization.script() + script + style + html.substring(insertionPoint)
        return Files.createTempFile("ai-explorer-", ".html").also { file ->
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
                        postUiScale()
                        pendingEvents.forEach(::postNow)
                        pendingEvents.clear()
                        refreshSessions()
                    }
                    "chat.new" -> AiChatService.getInstance(project).openChat()
                    "auth.login" -> AiChatService.getInstance(project).login()
                    "session.open" -> AiChatService.getInstance(project).openSession(
                        command.get("sessionId").asString,
                        command.get("title")?.asString ?: "Kimi Code",
                    )
                    "sessions.refresh" -> refreshSessions()
                    "session.rename" -> AiChatService.getInstance(project).renameSession(
                        command.get("sessionId").asString,
                        command.get("title").asString,
                    )
                    "session.delete" -> deleteSession(command.get("sessionId").asString)
                    "history.open" -> ToolWindowManager.getInstance(project).getToolWindow("AI Explorer")?.show()
                    "ui.scale" -> {
                        PropertiesComponent.getInstance(project).setValue("ai.explorer.ui.scale", command.get("value").asString)
                        postUiScale()
                    }
                }
            } catch (e: Exception) {
                log.warn("Invalid AI Explorer sidebar command", e)
            }
        }
    }

    private fun refreshSessions() {
        if (!refreshing.compareAndSet(false, true)) return
        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                val service = AiChatService.getInstance(project)
                val storedSessions = store.list()
                onEdt { service.updateSessionTitles(storedSessions) }
                val sessions = JsonArray().apply {
                    storedSessions.forEach { session ->
                        add(JsonObject().apply {
                            addProperty("sessionId", session.id)
                            addProperty("title", service.sessionTitle(session.id, session.title))
                            addProperty("updatedAt", session.updatedAt)
                        })
                    }
                }
                onEdt { post("sessions") { add("sessions", sessions) } }
            } catch (e: Exception) {
                log.warn("Cannot load Kimi Code sessions", e)
            } finally {
                refreshing.set(false)
            }
        }
    }

    private fun deleteSession(sessionId: String) {
        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                store.delete(sessionId)
                onEdt { AiChatService.getInstance(project).deleteSession(sessionId) }
            } catch (e: Exception) {
                log.warn("Cannot delete Kimi Code session", e)
            }
        }
    }

    private fun postUiScale() {
        val value = PropertiesComponent.getInstance(project).getValue("ai.explorer.ui.scale", "1")
        post("ui.scale") { addProperty("value", value) }
    }

    private fun post(type: String, payload: JsonObject.() -> Unit = {}) {
        if (!SwingUtilities.isEventDispatchThread()) {
            onEdt { post(type, payload) }
            return
        }
        val event = JsonObject().apply {
            addProperty("type", type)
            payload()
        }
        if (ready) postNow(event) else pendingEvents.add(event)
    }

    private fun postNow(event: JsonObject) {
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
