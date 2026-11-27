package com.novoagentehb.app.agent.media

/** Narrow explicit commands only; never infer permission from a general mention of an image. */
object ImageRequestIntent {
    private val pattern = Regex("^(?:gabi\\s*[,!:]\\s*)?(?:crie|criar|cria|gere|gerar|faça|faca|desenhe)\\s+(?:uma?\\s+)?(?:imagem|ilustração|ilustracao|foto)\\s+(?:de|da|do|com|sobre)\\s+(.+)$", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    fun parse(text: String): String? = pattern.matchEntire(text.trim())?.groupValues?.get(1)?.trim()?.takeIf { it.isNotBlank() && it.length <= 4000 }
}
