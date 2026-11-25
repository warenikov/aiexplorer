package ru.aiforopenide.explorer

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.util.Disposer
import java.awt.BorderLayout

class ChatListPanel(project: Project) : SimpleToolWindowPanel(true, true), Disposable {
    init {
        val webPanel = JcefSessionListPanel(project)
        add(webPanel, BorderLayout.CENTER)
        Disposer.register(this, webPanel)
    }

    override fun dispose() = Unit
}
