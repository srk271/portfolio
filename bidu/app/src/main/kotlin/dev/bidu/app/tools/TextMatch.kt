package dev.bidu.app.tools

import java.text.Normalizer
import java.util.Locale

/** Normalisation and fuzzy similarity for spoken names (apps, contacts). Pure Kotlin, unit-tested. */
object TextMatch {
    private val PUNCTUATION = Regex("""[\p{P}\p{S}]+""")
    private val WHITESPACE = Regex("""\s+""")
    private val DIACRITICS = Regex("""\p{InCombiningDiacriticalMarks}+""")

    /** Lower-case, Latin accents folded, punctuation to spaces. Devanagari marks are kept. */
    fun normalize(text: String): String {
        val decomposed = Normalizer.normalize(text, Normalizer.Form.NFD).replace(DIACRITICS, "")
        return decomposed.lowercase(Locale.ROOT).replace(PUNCTUATION, " ").replace(WHITESPACE, " ").trim()
    }

    fun compact(text: String): String = normalize(text).replace(" ", "")

    fun tokens(text: String): List<String> = normalize(text).split(' ').filter { it.isNotEmpty() }

    fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        var previous = IntArray(b.length + 1) { it }
        var current = IntArray(b.length + 1)
        for (i in 1..a.length) {
            current[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                current[j] = minOf(current[j - 1] + 1, previous[j] + 1, previous[j - 1] + cost)
            }
            val swap = previous
            previous = current
            current = swap
        }
        return previous[b.length]
    }

    /** 1.0 for identical strings, falling towards 0.0 with edit distance relative to length. */
    fun similarity(a: String, b: String): Double {
        val maxLength = maxOf(a.length, b.length)
        if (maxLength == 0) return 1.0
        return 1.0 - levenshtein(a, b).toDouble() / maxLength
    }

    /**
     * How well a spoken [query] matches a [candidate] name, 0.0..1.0. Exact (ignoring spaces) is 1.0;
     * whole-word containment and prefixes score high, weighted by how much of the name was said, so
     * "whatsap" prefers "WhatsApp" over "WhatsApp Business"; otherwise edit-distance similarity.
     */
    fun score(query: String, candidate: String): Double {
        val q = compact(query)
        val c = compact(candidate)
        if (q.isEmpty() || c.isEmpty()) return 0.0
        if (q == c) return 1.0
        val qTokens = tokens(query)
        val cTokens = tokens(candidate)
        var best = similarity(q, c) * 0.9
        if (qTokens.isNotEmpty() && cTokens.containsAll(qTokens)) best = maxOf(best, 0.8 + 0.15 * q.length / c.length)
        if (q.length >= 3 && c.startsWith(q)) best = maxOf(best, 0.7 + 0.25 * q.length / c.length)
        if (c.length >= 3 && q.startsWith(c) && c.length * 2 >= q.length) best = maxOf(best, 0.6 + 0.25 * c.length / q.length)
        if (q.length >= 4 && cTokens.any { it.length >= 4 && (it.startsWith(q) || q.startsWith(it)) }) best = maxOf(best, 0.75)
        return best.coerceAtMost(0.99)
    }
}
