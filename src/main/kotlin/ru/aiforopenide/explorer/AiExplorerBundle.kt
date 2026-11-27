package ru.aiforopenide.explorer

import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.PropertyKey
import java.util.Locale
import java.util.ResourceBundle

@NonNls
private const val BUNDLE = "messages.AiExplorerBundle"

internal object AiExplorerBundle {
    fun message(@PropertyKey(resourceBundle = BUNDLE) key: String, vararg params: Any): String {
        val locale = Locale.forLanguageTag(AiExplorerSettings.getInstance().language)
        val bundle = ResourceBundle.getBundle(BUNDLE, locale, AiExplorerBundle::class.java.classLoader)
        val pattern = bundle.getString(key)
        return if (params.isEmpty()) pattern else java.text.MessageFormat.format(pattern, *params)
    }
}
