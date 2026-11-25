package ru.aiforopenide.explorer

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea

class AddSelectionToAiChatAction : AnAction(
    "Добавить в AI Explorer…",
    "Добавить выделенный код и комментарий в текущий чат",
    AllIcons.Actions.IntentionBulbGrey,
) {
    override fun update(event: AnActionEvent) {
        val editor = event.getData(CommonDataKeys.EDITOR)
        val file = event.getData(CommonDataKeys.VIRTUAL_FILE)
        event.presentation.isEnabledAndVisible = editor != null && file != null && editor.selectionModel.hasSelection()
    }

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val editor = event.getData(CommonDataKeys.EDITOR) ?: return
        val file = event.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
        val selection = editor.selectionModel.selectedText ?: return
        val comment = SelectionCommentDialog(project).let { dialog ->
            if (!dialog.showAndGet()) return
            dialog.comment()
        }
        val document = FileDocumentManager.getInstance().getDocument(file) ?: return
        val startLine = document.getLineNumber(editor.selectionModel.selectionStart) + 1
        val endLine = document.getLineNumber(editor.selectionModel.selectionEnd) + 1
        val path = project.basePath?.let { file.path.removePrefix("$it/") } ?: file.path
        val location = if (startLine == endLine) "$path:$startLine" else "$path:$startLine-$endLine"
        val content = buildString {
            if (comment.isNotEmpty()) {
                append(comment)
                append("\n\n")
            }
            append('`').append(location).append('`')
            append("\n\n")
            val longestFence = Regex("`+").findAll(selection).maxOfOrNull { it.value.length } ?: 0
            val fence = "`".repeat(maxOf(3, longestFence + 1))
            append(fence).append(languageFor(file.extension))
            append('\n').append(selection).append('\n').append(fence)
        }
        AiChatService.getInstance(project).openCurrentChat()
        project.messageBus.syncPublisher(TerminalInputListener.TOPIC).insertText(content)
    }
}

private fun languageFor(extension: String?) = when (extension?.lowercase()) {
    "kt", "kts" -> "kotlin"
    "py" -> "python"
    "js", "jsx" -> "javascript"
    "ts", "tsx" -> "typescript"
    "yml" -> "yaml"
    "sh" -> "bash"
    else -> extension?.lowercase().orEmpty()
}

private class SelectionCommentDialog(project: Project) : DialogWrapper(project) {
    private val commentArea = JBTextArea(5, 52)

    init {
        title = "Добавить в AI Explorer"
        commentArea.lineWrap = true
        commentArea.wrapStyleWord = true
        commentArea.emptyText.text = "Комментарий к выделенному коду…"
        setOKButtonText("Добавить в чат")
        init()
    }

    override fun createCenterPanel() = JBScrollPane(commentArea)
    override fun getPreferredFocusedComponent() = commentArea
    fun comment() = commentArea.text.trim()
}
