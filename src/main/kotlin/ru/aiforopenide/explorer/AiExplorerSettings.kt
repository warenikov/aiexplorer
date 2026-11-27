package ru.aiforopenide.explorer

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.messages.Topic
import javax.swing.JComponent
import javax.swing.JEditorPane
import javax.swing.JLabel

@Service
@State(name = "AiExplorerSettings", storages = [Storage("ai-explorer.xml")])
class AiExplorerSettings : PersistentStateComponent<AiExplorerSettings.State> {
    data class State(var language: String = "en")
    private var state = State()
    override fun getState() = state
    override fun loadState(value: State) { state = value }
    var language: String
        get() = state.language
        set(value) { state = state.copy(language = value) }

    companion object {
        fun getInstance(): AiExplorerSettings = com.intellij.openapi.application.ApplicationManager.getApplication().getService(AiExplorerSettings::class.java)
    }
}

class AiExplorerConfigurable : Configurable {
    private var selectedLanguage = AiExplorerSettings.getInstance().language
    private var languageCombo: javax.swing.JComboBox<String>? = null
    private var languageLabel: JLabel? = null
    private var languageComment: JEditorPane? = null
    private var panel: JComponent? = null
    override fun getDisplayName(): @NlsContexts.ConfigurableName String = AiExplorerBundle.message("settings.name")
    override fun createComponent(): JComponent = panel {
        row(JLabel().also { languageLabel = it }) {
            comboBox(listOf("English", "Русский")).apply {
                languageCombo = component
                component.selectedItem = languageName(selectedLanguage)
                component.addActionListener {
                    val language = if (component.selectedItem == "Русский") "ru" else "en"
                    if (selectedLanguage != language) {
                        selectedLanguage = language
                        apply()
                    }
                }
            }
        }
        row { comment("").apply { languageComment = component } }
        updateTexts()
    }.also { panel = it }
    override fun isModified() = false
    override fun apply() {
        val settings = AiExplorerSettings.getInstance()
        if (settings.language == selectedLanguage) return
        settings.language = selectedLanguage
        updateTexts()
        com.intellij.openapi.application.ApplicationManager.getApplication().messageBus.syncPublisher(AiExplorerLocaleListener.TOPIC).localeChanged(selectedLanguage)
    }
    private fun updateTexts() {
        languageLabel?.text = AiExplorerBundle.message("settings.language")
        languageComment?.text = AiExplorerBundle.message("settings.language.comment")
    }
    private fun languageName(language: String) = if (language == "ru") "Русский" else "English"
    override fun reset() {
        selectedLanguage = AiExplorerSettings.getInstance().language
        languageCombo?.selectedItem = languageName(selectedLanguage)
        updateTexts()
    }
    override fun disposeUIResources() {
        panel = null
        languageCombo = null
        languageLabel = null
        languageComment = null
    }
}

fun interface AiExplorerLocaleListener {
    fun localeChanged(language: String)
    companion object {
        val TOPIC: Topic<AiExplorerLocaleListener> = Topic.create("AI Explorer locale", AiExplorerLocaleListener::class.java)
    }
}
