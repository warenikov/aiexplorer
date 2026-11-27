package ru.aiforopenide.explorer

import com.intellij.ide.BrowserUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBLabel
import com.jediterm.terminal.ProcessTtyConnector
import com.jediterm.terminal.model.hyperlinks.HyperlinkFilter
import com.jediterm.terminal.model.hyperlinks.LinkInfo
import com.jediterm.terminal.model.hyperlinks.LinkResult
import com.jediterm.terminal.model.hyperlinks.LinkResultItem
import com.jediterm.terminal.ui.JediTermWidget
import com.pty4j.PtyProcessBuilder
import org.jetbrains.plugins.terminal.JBTerminalSystemSettingsProvider
import java.awt.BorderLayout
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.KeyEventDispatcher
import java.awt.KeyboardFocusManager
import java.awt.event.KeyEvent
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import javax.swing.JComponent
import javax.swing.JPanel

class KimiTerminalPanel(
    project: Project,
    arguments: List<String>,
) : JPanel(BorderLayout()), Disposable {
    private val log = Logger.getInstance(KimiTerminalPanel::class.java)
    private val terminal = JediTermWidget(JBTerminalSystemSettingsProvider())
    private val pasteDispatcher = KeyEventDispatcher { event ->
        val focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner
        val isPaste = event.id == KeyEvent.KEY_PRESSED && event.keyCode == KeyEvent.VK_V &&
            (event.modifiersEx == KeyEvent.META_DOWN_MASK || event.modifiersEx == KeyEvent.CTRL_DOWN_MASK)
        if (!isPaste || focusOwner == null || !terminal.isAncestorOf(focusOwner)) {
            false
        } else {
            val clipboard = Toolkit.getDefaultToolkit().systemClipboard
            if (event.isControlDown || clipboard.isDataFlavorAvailable(DataFlavor.imageFlavor)) {
                terminal.ttyConnector?.write("\u0016")
                event.consume()
                true
            } else {
                false
            }
        }
    }

    val preferredFocusComponent: JComponent
        get() = terminal

    init {
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(pasteDispatcher)
        add(terminal, BorderLayout.CENTER)
        terminal.addHyperlinkFilter(HyperlinkFilter { line ->
            val links = URL_PATTERN.findAll(line).map { match ->
                val url = match.value.trimEnd('.', ',', ';', ':', '!', '?', ')', ']', '}')
                LinkResultItem(match.range.first, match.range.first + url.length, LinkInfo { BrowserUtil.browse(url) })
            }.toList()
            if (links.isEmpty()) null else LinkResult(links)
        })
        try {
            val workingDirectory = project.basePath ?: System.getProperty("user.home")
            val process = PtyProcessBuilder()
                .setCommand((listOf(findKimiExecutable()) + arguments).toTypedArray())
                .setDirectory(workingDirectory)
                .setEnvironment(System.getenv() + mapOf("TERM" to "xterm-256color", "COLORTERM" to "truecolor"))
                .setInitialColumns(120)
                .setInitialRows(36)
                .start()
            terminal.ttyConnector = object : ProcessTtyConnector(process, StandardCharsets.UTF_8) {
                override fun getName() = "Kimi Code"
            }
            terminal.start()
        } catch (e: Exception) {
            log.warn("Cannot start Kimi Code terminal", e)
            removeAll()
            add(JBLabel("<html><center>${AiExplorerBundle.message("terminal.start.failed", e.message ?: "Unknown error")}</center></html>", JBLabel.CENTER), BorderLayout.CENTER)
        }
    }

    fun insertText(text: String) {
        terminal.ttyConnector?.write(text)
        terminal.requestFocusInWindow()
    }

    override fun dispose() {
        KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(pasteDispatcher)
        terminal.ttyConnector?.close()
    }

    private companion object {
        val URL_PATTERN = Regex("""https?://[^\s<>"']+""")
    }

    private fun findKimiExecutable(): String {
        val home = System.getProperty("user.home")
        val candidates = listOf(
            Path.of(home, ".kimi-code/bin/kimi"),
            Path.of("/opt/homebrew/bin/kimi"),
            Path.of("/usr/local/bin/kimi"),
        )
        return candidates.firstOrNull(Files::isExecutable)?.toString() ?: "kimi"
    }
}
