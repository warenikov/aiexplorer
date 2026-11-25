package ru.aiforopenide.explorer

import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorLocation
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.testFramework.LightVirtualFile
import java.beans.PropertyChangeListener
import javax.swing.JComponent

class AiChatFile(
    name: String,
    val kimiArguments: List<String> = emptyList(),
) : LightVirtualFile(name, AiChatFileType, "") {
    private var title = name

    init {
        isWritable = false
    }

    override fun getName(): String = title

    fun updateTitle(value: String) {
        title = value
    }
}

object AiChatFileType : FileType {
    override fun getName() = "AI_EXPLORER_CHAT"
    override fun getDescription() = "AI Explorer chat"
    override fun getDefaultExtension() = ""
    override fun getIcon() = null
    override fun isBinary() = true
    override fun isReadOnly() = true
}

class AiChatEditorProvider : FileEditorProvider, DumbAware {
    override fun accept(project: Project, file: com.intellij.openapi.vfs.VirtualFile) = file is AiChatFile

    override fun createEditor(project: Project, file: com.intellij.openapi.vfs.VirtualFile): FileEditor =
        AiChatEditor(project, file as AiChatFile)

    override fun getEditorTypeId() = "ai-explorer-chat"
    override fun getPolicy() = FileEditorPolicy.HIDE_DEFAULT_EDITOR
}

class AiChatEditor(project: Project, private val file: AiChatFile) : UserDataHolderBase(), FileEditor {
    private val panel = AiExplorerPanel(project, chatFile = file)

    override fun getComponent(): JComponent = panel
    override fun getPreferredFocusedComponent(): JComponent = panel.preferredFocusComponent
    override fun getName() = "AI Explorer"
    override fun getFile(): AiChatFile = file
    override fun setState(state: FileEditorState) = Unit
    override fun isModified() = false
    override fun isValid() = true
    override fun addPropertyChangeListener(listener: PropertyChangeListener) = Unit
    override fun removePropertyChangeListener(listener: PropertyChangeListener) = Unit
    override fun getCurrentLocation(): FileEditorLocation? = null
    override fun selectNotify() = panel.focusSelectedContent()
    override fun dispose() = com.intellij.openapi.util.Disposer.dispose(panel)
}
