package ru.aiforopenide.explorer

import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory

class AiExplorerToolWindowFactory : ToolWindowFactory, DumbAware {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = ChatListPanel(project)
        val content = ContentFactory.getInstance().createContent(panel, "Chats", false)
        Disposer.register(content, panel)
        toolWindow.contentManager.addContent(content)
        listOf("Skills" to "skills", "Settings" to "settings", "MCP" to "mcp").forEach { (title, kind) ->
            val resourcePanel = KimiResourcePanel(project, kind)
            val resourceContent = ContentFactory.getInstance().createContent(resourcePanel, title, false)
            Disposer.register(resourceContent, resourcePanel)
            toolWindow.contentManager.addContent(resourceContent)
        }
    }

    override suspend fun isApplicableAsync(project: Project) = true
}
