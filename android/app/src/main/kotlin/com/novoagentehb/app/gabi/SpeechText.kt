package com.novoagentehb.app.gabi

/** A speech-only projection; callers must retain the original text for display and storage. */
object SpeechText {
    fun fromMarkdown(text: String): String = text
        .replace(Regex("(?m)^\\s*```[^\\n]*$"), "")
        .replace(Regex("!\\[([^]]*)]\\([^)]*\\)"), "$1")
        .replace(Regex("\\[([^]]+)]\\([^)]*\\)"), "$1")
        .replace(Regex("(?m)^\\s{0,3}#{1,6}\\s+"), "")
        .replace(Regex("(?m)^\\s*>\\s?"), "")
        .replace(Regex("(?m)^\\s*[-+*]\\s+"), "")
        .replace(Regex("(?m)^\\s*\\d+[.)]\\s+"), "")
        .replace(Regex("\\*\\*(.+?)\\*\\*"), "$1")
        .replace(Regex("__(.+?)__"), "$1")
        .replace(Regex("(?<!\\w)\\*([^*\\n]+)\\*(?!\\w)"), "$1")
        .replace(Regex("(?<!\\w)_([^_\\n]+)_(?!\\w)"), "$1")
        .replace(Regex("`+([^`]+)`+"), "$1")
        .replace(Regex("(?m)^\\s*[-*_]{3,}\\s*$"), "")
        .trim()
}
