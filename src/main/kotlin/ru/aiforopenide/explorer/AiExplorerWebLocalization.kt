package ru.aiforopenide.explorer

internal object AiExplorerWebLocalization {
    fun script(): String {
        val source = javaClass.getResourceAsStream("/web/i18n.js")?.bufferedReader()?.use { it.readText() }
            ?: error("AI Explorer localization resource is missing")
        return "<script>window.AI_EXPLORER_LOCALE = \"${AiExplorerSettings.getInstance().language}\";</script><script>$source</script>"
    }
}
