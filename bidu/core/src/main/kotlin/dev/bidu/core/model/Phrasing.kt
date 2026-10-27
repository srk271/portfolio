package dev.bidu.core.model

/** Picks the reply variant for this language. All three are written by hand; nothing is machine-translated. */
internal fun Language.say(hinglish: String, english: String, hindi: String): String = when (this) {
    Language.HINGLISH -> hinglish
    Language.ENGLISH -> english
    Language.HINDI -> hindi
}
