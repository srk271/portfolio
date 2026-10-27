package dev.bidu.app.voice

import dev.bidu.app.settings.WakePhrase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.vosk.LibVosk
import org.vosk.LogLevel
import org.vosk.Model
import java.io.File
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Owns the single Vosk thread and the loaded [Model]. Every Vosk call (model load, recognizer,
 * decoding) runs on [dispatcher], so the native objects are never touched concurrently and the
 * wake-word detector and the offline command recognizer can never both hold the microphone.
 */
internal class VoskRuntime {
    private val executor = Executors.newSingleThreadExecutor { runnable -> Thread(runnable, "bidu-vosk") }
    val dispatcher: CoroutineDispatcher = executor.asCoroutineDispatcher()

    private var model: Model? = null
    private var modelPath: String? = null
    private var logLevelSet = false

    /** Must run on [dispatcher]. Loads once and reuses the model until [closeModel]. */
    fun model(directory: File): Model {
        val path = directory.absolutePath
        val loaded = model
        if (loaded != null && modelPath == path) return loaded
        closeModel()
        if (!logLevelSet) {
            LibVosk.setLogLevel(LogLevel.WARNINGS)
            logLevelSet = true
        }
        return Model(path).also {
            model = it
            modelPath = path
        }
    }

    /** Must run on [dispatcher]. */
    fun closeModel() {
        model?.close()
        model = null
        modelPath = null
    }

    fun hasModel(): Boolean = model != null

    fun shutdown() {
        executor.shutdown()
    }
}

/** Parsing of Vosk's JSON results. Malformed JSON is treated as "nothing recognised". */
internal object VoskJson {
    const val UNK = "[unk]"
    private val WHITESPACE = Regex("\\s+")

    data class Final(val tokens: List<String>, val minConfidence: Double?)

    fun final(json: String?): Final {
        val obj = parse(json) ?: return Final(emptyList(), null)
        val words = obj.optJSONArray("result")
        var minConfidence: Double? = null
        if (words != null) {
            for (i in 0 until words.length()) {
                val word = words.optJSONObject(i) ?: continue
                val conf = word.optDouble("conf", Double.NaN)
                if (!conf.isNaN()) minConfidence = minOf(minConfidence ?: conf, conf)
            }
        }
        return Final(tokenize(obj.optString("text", "")), minConfidence)
    }

    fun partialTokens(json: String?): List<String> = tokenize(parse(json)?.optString("partial", "") ?: "")

    fun text(json: String?): String = parse(json)?.optString("text", "")?.trim().orEmpty()

    fun partialText(json: String?): String = parse(json)?.optString("partial", "")?.trim().orEmpty()

    fun tokenize(text: String): List<String> =
        text.trim().lowercase(Locale.ROOT).split(WHITESPACE).filter { it.isNotEmpty() }

    private fun parse(json: String?): JSONObject? {
        if (json.isNullOrBlank()) return null
        return try {
            JSONObject(json)
        } catch (_: JSONException) {
            null
        }
    }
}

/**
 * Decides whether a final grammar result is a wake phrase. Deliberately strict: the whole
 * utterance must be exactly one configured variant (no "[unk]" anywhere, so a phrase buried in
 * other speech never fires), and the last partial before it must be consistent with it.
 */
internal class WakeMatcher(phrases: List<WakePhrase>) {
    data class Match(val phrase: String, val heard: String)

    private val variants: List<Pair<String, List<String>>> = phrases
        .flatMap { phrase -> phrase.variants.map { phrase.phrase to VoskJson.tokenize(it) } }
        .filter { (_, tokens) -> tokens.isNotEmpty() && VoskJson.UNK !in tokens }
        .distinctBy { it.second }

    val isEmpty: Boolean get() = variants.isEmpty()

    /** Vosk grammar: every variant plus "[unk]" so out-of-grammar speech has somewhere to go. */
    val grammarJson: String = JSONArray(variants.map { it.second.joinToString(" ") } + VoskJson.UNK).toString()

    fun match(finalTokens: List<String>, lastPartialTokens: List<String>): Match? {
        if (finalTokens.isEmpty() || VoskJson.UNK in finalTokens) return null
        val hit = variants.firstOrNull { it.second == finalTokens } ?: return null
        // A very short utterance can end before any partial arrives; a partial that disagrees with
        // the final result means the decoder was unsure, so it does not count.
        val partial = lastPartialTokens.filter { it != VoskJson.UNK }
        if (partial.size > finalTokens.size) return null
        if (finalTokens.subList(0, partial.size) != partial) return null
        return Match(phrase = hit.first, heard = finalTokens.joinToString(" "))
    }
}
