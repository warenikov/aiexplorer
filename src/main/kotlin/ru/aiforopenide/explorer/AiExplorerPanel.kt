package ru.aiforopenide.explorer

import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.CardLayout
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SwingConstants
import javax.swing.SwingUtilities

class AiExplorerPanel(
    private val project: Project,
    private val chatFile: AiChatFile? = null,
) : JPanel(BorderLayout()), Disposable {
    private val terminalPanel = KimiTerminalPanel(project, chatFile?.kimiArguments.orEmpty())
    private val cardLayout = CardLayout()
    private val content = JPanel(cardLayout)
    private val toggleButton = JButton("Граф файлов")
    private var graphPanel: KimiFileGraphPanel? = null
    private var graphVisible = false
    val preferredFocusComponent: JComponent
        get() = terminalPanel.preferredFocusComponent

    init {
        toggleButton.apply {
            putClientProperty("JButton.buttonType", "borderless")
            isBorderPainted = false
            isContentAreaFilled = false
            isFocusPainted = false
            text = null
            icon = AllIcons.Actions.Share
            toolTipText = "Показать граф активности"
            accessibleContext.accessibleName = toolTipText
            horizontalAlignment = SwingConstants.LEFT
            border = JBUI.Borders.empty(5, 8)
            font = JBUI.Fonts.smallFont()
        }
        toggleButton.addActionListener {
            graphVisible = !graphVisible
            if (graphVisible) showGraph()
            cardLayout.show(content, if (graphVisible) "graph" else "terminal")
            toggleButton.icon = if (graphVisible) AllIcons.Debugger.Console else AllIcons.Actions.Share
            toggleButton.toolTipText = if (graphVisible) "Вернуться в терминал" else "Показать граф активности"
            toggleButton.accessibleContext.accessibleName = toggleButton.toolTipText
            if (!graphVisible) requestTerminalFocus()
        }
        val togglePanel = JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(2, 4)
            add(toggleButton, BorderLayout.WEST)
        }
        add(togglePanel, BorderLayout.NORTH)
        content.add(terminalPanel, "terminal")
        add(content, BorderLayout.CENTER)
        Disposer.register(this, terminalPanel)
        project.messageBus.connect(this).subscribe(TerminalInputListener.TOPIC, TerminalInputListener { text ->
            if (chatFile != null && com.intellij.openapi.fileEditor.FileEditorManager.getInstance(project).selectedFiles.any { it === chatFile }) {
                terminalPanel.insertText(text)
            }
        })
    }

    fun focusSelectedContent() {
        if (!graphVisible) requestTerminalFocus()
    }

    private fun requestTerminalFocus() {
        SwingUtilities.invokeLater { terminalPanel.preferredFocusComponent.requestFocusInWindow() }
    }

    private fun showGraph() {
        if (graphPanel == null) {
            graphPanel = KimiFileGraphPanel(project, chatFile?.kimiArguments.orEmpty()).also { panel ->
                content.add(panel, "graph")
                Disposer.register(this, panel)
            }
        }
    }

    override fun dispose() = Unit
}
