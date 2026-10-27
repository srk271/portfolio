package dev.bidu.core.model

/** Language the user spoke in, or the language Bidu should answer in. */
enum class Language { HINDI, ENGLISH, HINGLISH }

/** Script of the raw utterance. Hinglish arrives in Latin letters, Hindi often in Devanagari. */
enum class Script { LATIN, DEVANAGARI, MIXED }

/**
 * How replies are phrased. [language] drives the response composer; [casual] selects
 * "bhai"-style register over a neutral one. Kept deliberately small: the UI has no theming.
 */
data class ResponseStyle(
    val language: Language = Language.HINGLISH,
    val casual: Boolean = true,
    val concise: Boolean = true,
)
