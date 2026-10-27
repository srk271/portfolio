package dev.bidu.app.data.room

import java.util.Locale

/**
 * Word-level fallback search used when the whole phrase does not occur verbatim
 * ("gym wala note" should still find "kal gym jaana hai").
 */
internal object TextSearch {
    private val FILLER = setOf(
        "wala", "wali", "wale", "waala", "waali", "vala", "vali", "note", "notes", "reminder", "reminders",
        "ka", "ki", "ke", "ko", "se", "me", "mein", "hai", "the", "a", "an", "my", "mera", "meri", "mere", "about",
        "वाला", "वाली", "वाले", "का", "की", "के", "को", "में", "है", "नोट",
    )
    private val SPLIT = Regex("""[\s\p{P}\p{S}]+""")

    fun tokens(query: String): List<String> =
        query.lowercase(Locale.ROOT).split(SPLIT)
            .filter { it.length >= 2 && it !in FILLER }
            .distinct()

    /** Number of [tokens] found in [text]; 0 means no match. */
    fun score(text: String, tokens: List<String>): Int {
        val haystack = text.lowercase(Locale.ROOT)
        return tokens.count { haystack.contains(it) }
    }
}

/** Escapes LIKE wildcards in user text so "50%" matches literally. Pair with `ESCAPE '\'`. */
internal object LikePattern {
    fun escape(text: String): String = buildString(text.length) {
        for (c in text) {
            if (c == '\\' || c == '%' || c == '_') append('\\')
            append(c)
        }
    }
}
