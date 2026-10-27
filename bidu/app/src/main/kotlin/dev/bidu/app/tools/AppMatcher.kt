package dev.bidu.app.tools

import dev.bidu.core.memory.KnownApp

/** Result of resolving a spoken app name against the installed apps. */
sealed interface AppMatch {
    data class Found(val app: KnownApp, val score: Double) : AppMatch
    data class Ambiguous(val candidates: List<KnownApp>) : AppMatch
    data object NotFound : AppMatch
}

/** Picks the installed app a spoken name refers to. Pure Kotlin, unit-tested. */
object AppMatcher {
    const val MIN_SCORE = 0.62
    private const val TIE_MARGIN = 0.06
    private val FILLER_SUFFIXES = listOf(" app", " application", " ऐप", " एप")

    fun match(spoken: String, apps: List<KnownApp>): AppMatch {
        val query = stripFiller(spoken)
        if (query.isBlank() || apps.isEmpty()) return AppMatch.NotFound

        val scored = apps
            .map { app -> app to bestScore(query, app) }
            .filter { it.second >= MIN_SCORE }
            .sortedByDescending { it.second }
        if (scored.isEmpty()) return AppMatch.NotFound

        val best = scored.first()
        val exact = scored.filter { it.second >= 1.0 }
        if (exact.size == 1) return AppMatch.Found(exact.first().first, 1.0)

        val close = scored.filter { best.second - it.second <= TIE_MARGIN }.distinctBy { it.first.packageName }
        return if (close.size == 1) AppMatch.Found(best.first, best.second) else AppMatch.Ambiguous(close.take(5).map { it.first })
    }

    private fun bestScore(query: String, app: KnownApp): Double =
        (listOf(app.label) + app.aliases).maxOf { TextMatch.score(query, it) }

    private fun stripFiller(spoken: String): String {
        var text = TextMatch.normalize(spoken)
        for (suffix in FILLER_SUFFIXES) {
            if (text.endsWith(suffix) && text.length > suffix.length + 1) text = text.removeSuffix(suffix).trim()
        }
        return text
    }
}
