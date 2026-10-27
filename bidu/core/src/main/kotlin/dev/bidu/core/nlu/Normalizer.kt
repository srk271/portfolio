package dev.bidu.core.nlu

import dev.bidu.core.model.Language
import dev.bidu.core.model.Script
import java.util.Locale

/** One word of an utterance. [surface] keeps the user's spelling for payloads; rules match [canon]. */
internal data class Token(val surface: String, val canon: String) {
    val isDevanagari: Boolean get() = surface.any(TextFolding::isDevanagari)

    /** What to put in a name slot: the romanised form for Devanagari words the lexicon knows (मम्मी -> mummy). */
    val spoken: String
        get() = if (isDevanagari && canon.none(TextFolding::isDevanagari)) canon else surface
}

internal object TextFolding {
    private const val NUKTA_BASE = "कखगजडढफय"

    fun isDevanagari(ch: Char): Boolean = ch in 'ऀ'..'ॿ'

    /** NFC, Devanagari digits to ASCII, nukta dropped, chandrabindu folded into anusvara, dandas to spaces. */
    fun fold(text: String): String {
        val nfc = java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFC)
        val out = StringBuilder(nfc.length)
        for (ch in nfc) {
            when (ch) {
                in '०'..'९' -> out.append('0' + (ch - '०'))
                '़', '‌', '‍' -> Unit
                'ँ' -> out.append('ं')
                in 'क़'..'य़' -> out.append(NUKTA_BASE[ch - 'क़'])
                '।', '॥' -> out.append(' ')
                else -> out.append(ch)
            }
        }
        return out.toString()
    }
}

internal object Normalizer {
    val DEFAULT_WAKE_PHRASES: List<String> = listOf(
        "hey bidu", "hi bidu", "hello bidu", "ok bidu", "okay bidu", "bidu", "biddu", "beedu", "bidoo",
        "हे बिडू", "बिडू", "बीडू", "बिदु",
    )
    private val WAKE_NAMES = setOf("bidu", "biddu", "beedu", "bidoo", "बिडू", "बीडू", "बिदु")
    private val INTERJECTIONS = setOf("hey", "hi", "hello", "oye", "oy", "arre", "arey", "suno", "suniye", "yo", "हे", "सुनो", "अरे")

    private val AM_PM = Regex("""(\d)\s*([AaPp])\.?\s?[Mm](?!\p{L})\.?""")
    private val CLOCK_DOT = Regex("""(?<!\d)(\d{1,2})\.(\d{2})(?!\d)""")
    private val DIGIT_LETTER = Regex("""(?<=\d)(?=\p{L})|(?<=\p{L})(?=\d)""")
    private val POSSESSIVE = Regex("""(\p{L})['’]s(?!\p{L})""")
    private val PUNCT = Regex("""[^\p{L}\p{M}\p{N}\s:.]""")
    private val STRAY_SEPARATOR = Regex("""(?<!\d)[:.]|[:.](?!\d)""")
    private val WHITESPACE = Regex("""\s+""")

    /** Splits [text] into tokens with canonical forms. Nothing is dropped. */
    fun tokenize(text: String): List<Token> {
        var s = TextFolding.fold(text)
        s = AM_PM.replace(s) { "${it.groupValues[1]} ${it.groupValues[2].lowercase(Locale.ROOT)}m" }
        s = CLOCK_DOT.replace(s, "$1:$2")
        s = DIGIT_LETTER.replace(s, " ")
        s = POSSESSIVE.replace(s, "$1 s")
        s = s.replace("'", "").replace("’", "")
        s = PUNCT.replace(s, " ")
        s = STRAY_SEPARATOR.replace(s, " ")
        return s.split(WHITESPACE).filter { it.isNotEmpty() }.map { Token(it, Lexicon.canon(it.lowercase(Locale.ROOT))) }
    }

    /**
     * [tokenize], then strip wake phrases ("hey bidu", "bhai" at the start), filler words ("yaar", "please")
     * and auxiliaries that follow a verb ("kar do" -> "kar"), as documented on NluResult.normalizedText.
     */
    fun normalize(text: String, wakePhrases: List<String>): List<Token> {
        val tokens = tokenize(text).toMutableList()
        stripWake(tokens, wakePhrases)
        return dropFillers(tokens)
    }

    private fun stripWake(tokens: MutableList<Token>, configured: List<String>) {
        val phrases = (configured + DEFAULT_WAKE_PHRASES)
            .map { phrase -> tokenize(phrase).map { it.surface.lowercase(Locale.ROOT) } }
            .filter { it.isNotEmpty() }
            .distinct()
            .sortedByDescending { it.size }
        var changed = true
        while (changed && tokens.isNotEmpty()) {
            changed = false
            if (tokens.size > 1 && tokens[0].surface.lowercase(Locale.ROOT) in INTERJECTIONS) {
                tokens.removeAt(0)
                changed = true
                continue
            }
            for (phrase in phrases) {
                if (tokens.size < phrase.size) continue
                val matches = phrase.indices.all { tokens[it].surface.lowercase(Locale.ROOT) == phrase[it] }
                // "bhai ko call kar" means the brother, not the wake word.
                val addressed = tokens.getOrNull(phrase.size)?.canon in Lexicon.POSTPOSITIONS
                if (matches && !addressed) {
                    repeat(phrase.size) { tokens.removeAt(0) }
                    changed = true
                    break
                }
            }
        }
        while (tokens.isNotEmpty() && tokens.last().surface.lowercase(Locale.ROOT) in WAKE_NAMES) tokens.removeAt(tokens.lastIndex)
    }

    private fun dropFillers(tokens: List<Token>): List<Token> {
        val out = ArrayList<Token>(tokens.size)
        for ((i, token) in tokens.withIndex()) {
            val c = token.canon
            if (token.surface.lowercase(Locale.ROOT) in WAKE_NAMES) continue
            if (c in Lexicon.FILLERS) {
                val kinship = c in Lexicon.KINSHIP_FILLERS &&
                    (tokens.getOrNull(i + 1)?.canon in Lexicon.POSTPOSITIONS || out.lastOrNull()?.canon in Lexicon.POSSESSIVES)
                if (!kinship) continue
            }
            // "kar do" drops the auxiliary, "book kar do tickets" keeps the number.
            val countsSomething = c == "do" && tokens.getOrNull(i + 1)?.canon in Lexicon.COUNTED_NOUNS
            if (c in Lexicon.AUXILIARIES && !countsSomething && out.lastOrNull()?.canon in Lexicon.VERBS) continue
            out += token
        }
        return out
    }
}

internal object LanguageDetector {
    fun script(raw: String): Script {
        var devanagari = 0
        var latin = 0
        for (ch in raw) {
            when {
                TextFolding.isDevanagari(ch) -> devanagari++
                ch in 'a'..'z' || ch in 'A'..'Z' -> latin++
            }
        }
        return when {
            devanagari > 0 && latin > 0 -> Script.MIXED
            devanagari > 0 -> Script.DEVANAGARI
            else -> Script.LATIN
        }
    }

    fun language(tokens: List<Token>, script: Script): Language {
        if (script == Script.DEVANAGARI) return Language.HINDI
        if (script == Script.MIXED) {
            val deva = tokens.count { it.isDevanagari }
            return if (deva * 2 > tokens.size) Language.HINDI else Language.HINGLISH
        }
        val hindi = tokens.count { it.canon in Lexicon.HINDI_MARKERS }
        val english = tokens.count { it.canon in Lexicon.ENGLISH_MARKERS }
        return when {
            hindi > 0 -> Language.HINGLISH
            english > 0 -> Language.ENGLISH
            else -> Language.HINGLISH
        }
    }
}
