package ru.aiforopenide.explorer

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.project.Project
import com.intellij.util.messages.Topic

@Service(Service.Level.PROJECT)
class AiChatService(private val project: Project) {
    private val openChats = mutableMapOf<String, AiChatFile>()
    private var currentFile: AiChatFile? = null

    fun openChat() {
        val file = AiChatFile("Kimi Code")
        currentFile = file
        FileEditorManager.getInstance(project).openFile(file, true)
        project.messageBus.syncPublisher(ChatSelectionListener.TOPIC).chatSelected(null)
    }

    fun continueLatest() = openTerminal("Kimi Code — последняя сессия", listOf("--continue"))

    fun login() = openTerminal("Вход в Kimi Code", listOf("login"))

    fun openSession(session: KimiSessionSummary) = openSession(session.id, session.title)

    fun openSession(sessionId: String, title: String = "Kimi Code") {
        val file = openChats.getOrPut(sessionId) {
            AiChatFile(tabTitle(sessionTitle(sessionId, title)), listOf("--session", sessionId))
        }
        currentFile = file
        FileEditorManager.getInstance(project).openFile(file, true)
        project.messageBus.syncPublisher(ChatSelectionListener.TOPIC).chatSelected(sessionId)
    }

    fun openCurrentChat() {
        val file = currentFile
        if (file == null) openChat()
        else FileEditorManager.getInstance(project).openFile(file, true)
    }

    fun sessionTitle(sessionId: String, fallback: String): String =
        PropertiesComponent.getInstance(project).getValue("ai.explorer.session.title.$sessionId", fallback)

    fun renameSession(sessionId: String, title: String) {
        val normalized = title.replace(Regex("\\s+"), " ").trim()
        if (normalized.isEmpty()) return
        PropertiesComponent.getInstance(project).setValue("ai.explorer.session.title.$sessionId", normalized)
        openChats[sessionId]?.let { file ->
            file.updateTitle(tabTitle(normalized))
            (FileEditorManager.getInstance(project) as? FileEditorManagerEx)?.updateFilePresentation(file)
        }
        project.messageBus.syncPublisher(ChatMetadataListener.TOPIC).sessionsChanged()
    }

    fun updateSessionTitles(sessions: List<KimiSessionSummary>) {
        sessions.forEach { session ->
            val file = openChats[session.id] ?: return@forEach
            val title = tabTitle(sessionTitle(session.id, session.title))
            if (file.name == title) return@forEach
            file.updateTitle(title)
            (FileEditorManager.getInstance(project) as? FileEditorManagerEx)?.updateFilePresentation(file)
        }
    }

    fun deleteSession(sessionId: String) {
        val file = openChats.remove(sessionId)
        if (file != null) {
            FileEditorManager.getInstance(project).closeFile(file)
            if (currentFile === file) currentFile = null
        }
        PropertiesComponent.getInstance(project).unsetValue("ai.explorer.session.title.$sessionId")
        project.messageBus.syncPublisher(ChatMetadataListener.TOPIC).sessionsChanged()
    }

    private fun openTerminal(title: String, arguments: List<String>) {
        val file = AiChatFile(title, arguments)
        currentFile = file
        FileEditorManager.getInstance(project).openFile(file, true)
    }

    private fun tabTitle(title: String): String {
        val normalized = title.replace(Regex("\\s+"), " ").trim().ifEmpty { "Kimi Code" }
        return if (normalized.length > 36) normalized.take(35).trimEnd() + "…" else normalized
    }

    companion object {
        fun getInstance(project: Project): AiChatService = project.getService(AiChatService::class.java)
    }
}

fun interface TerminalInputListener {
    fun insertText(text: String)

    companion object {
        val TOPIC: Topic<TerminalInputListener> = Topic.create("AI Explorer terminal input", TerminalInputListener::class.java)
    }
}

fun interface ChatSelectionListener {
    fun chatSelected(sessionId: String?)

    companion object {
        val TOPIC: Topic<ChatSelectionListener> = Topic.create("AI Explorer chat selection", ChatSelectionListener::class.java)
    }
}

fun interface ChatMetadataListener {
    fun sessionsChanged()

    companion object {
        val TOPIC: Topic<ChatMetadataListener> = Topic.create("AI Explorer sessions changed", ChatMetadataListener::class.java)
    }
}
