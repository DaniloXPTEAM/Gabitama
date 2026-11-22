package com.novoagentehb.app.bridges

import java.text.Normalizer
import java.util.Locale

/** Exact, unambiguous matching only; never pick the first substring match. */
internal object ContactMatch {
    fun normalize(value: String): String = Normalizer.normalize(value.trim().lowercase(Locale.ROOT), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "").replace(Regex("\\s+"), " ")

    fun uniqueNumber(query: String, contacts: List<Pair<String, String>>): String? {
        val wanted = normalize(query)
        if (wanted.isBlank()) return null
        val numbers = contacts.asSequence().filter { normalize(it.first) == wanted }
            .map { it.second.replace(Regex("[^0-9]"), "") }.filter { it.isNotBlank() }.distinct().take(2).toList()
        return numbers.singleOrNull()
    }
}
