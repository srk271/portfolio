package dev.bidu.core.nlu

import java.util.Locale

/** Index-based view of a normalised utterance used by the rules. */
internal class Utterance(val tokens: List<Token>) {
    val canon: List<String> = tokens.map { it.canon }
    private val canonSet = canon.toSet()
    val size: Int get() = tokens.size

    fun has(vararg words: String): Boolean = words.any { it in canonSet }
    fun hasAny(words: Set<String>): Boolean = canon.any { it in words }
    fun indexOf(word: String, from: Int = 0): Int = (from until size).firstOrNull { canon[it] == word } ?: -1
    fun firstIndex(words: Set<String>, from: Int = 0): Int = (from until size).firstOrNull { canon[it] in words } ?: -1
    fun indicesOf(words: Set<String>): Set<Int> = canon.indices.filterTo(mutableSetOf()) { canon[it] in words }

    /** Start index of the consecutive canonical sequence, or -1. */
    fun phrase(vararg seq: String): Int {
        if (seq.isEmpty() || seq.size > size) return -1
        return (0..size - seq.size).firstOrNull { start -> seq.indices.all { canon[start + it] == seq[it] } } ?: -1
    }

    /** Indices outside [exclude], with leading/trailing words in [trim] removed. */
    fun content(exclude: Set<Int>, trim: Set<String>, trimTrailing: Set<String> = trim): List<Int> {
        val idx = canon.indices.filterTo(ArrayList()) { it !in exclude }
        while (idx.isNotEmpty() && canon[idx.first()] in trim) idx.removeAt(0)
        while (idx.isNotEmpty() && canon[idx.last()] in trimTrailing) idx.removeAt(idx.lastIndex)
        return idx
    }

    /** Contiguous indices ending at [from] (inclusive) going left until a stop word or a used index. */
    fun runBackward(from: Int, stop: Set<String>, used: Set<Int> = emptySet()): List<Int> {
        val out = ArrayList<Int>()
        var i = from
        while (i >= 0 && i !in used && canon[i] !in stop) {
            out.add(0, i)
            i--
        }
        return out
    }

    /** Contiguous indices starting at [from] going right until a stop word or a used index. */
    fun runForward(from: Int, stop: Set<String>, used: Set<Int> = emptySet()): List<Int> {
        val out = ArrayList<Int>()
        var i = from
        while (i in canon.indices && i !in used && canon[i] !in stop) {
            out.add(i)
            i++
        }
        return out
    }

    fun surface(indices: Collection<Int>): String = indices.sorted().joinToString(" ") { tokens[it].surface }

    /** Dictated text: surfaces plus the auxiliaries dropped inside the span ("submit kar dena hai"). */
    fun payload(indices: Collection<Int>): String {
        val sorted = indices.sorted()
        return sorted.joinToString(" ") { i ->
            val t = tokens[i]
            if (i == sorted.last() || t.tail.isEmpty()) t.surface else (listOf(t.surface) + t.tail).joinToString(" ")
        }
    }

    /** Longest contiguous run of indices whose canon is not in [stop]. */
    fun longestRun(stop: Set<String>, exclude: Set<Int> = emptySet()): List<Int> {
        var best = emptyList<Int>()
        var cur = ArrayList<Int>()
        for (i in canon.indices) {
            if (i in exclude || canon[i] in stop) {
                if (cur.size > best.size) best = cur
                cur = ArrayList()
            } else {
                cur.add(i)
            }
        }
        return if (cur.size > best.size) cur else best
    }
    fun spoken(indices: Collection<Int>): String = indices.sorted().joinToString(" ") { tokens[it].spoken }
}

/** Shared word sets, so the NLU and the planner's missing-slot detection agree. */
internal object Words {
    val OPEN_VERBS = setOf("khol", "open", "chala", "start")
    val REMOVE_VERBS = setOf("cancel", "hata", "delete", "mita", "clear")
    val STOP_VERBS = setOf("band", "off", "stop", "bujha")
    val CREATE_VERBS = setOf("laga", "set", "bana", "add", "create", "daal", "new", "naya", "save", "make")
    val CALL_WORDS = setOf("call", "phone", "dial", "ring")
    val CALL_VERBS = setOf("kar", "laga", "mila")
    val CONNECTORS = setOf(
        "ki", "ke", "ka", "ko", "that", "to", "about", "for", "liye", "of", "se", "ye", "this", "baare", "mein", "bhi", "and", "aur",
        "please", "wala", "is",
    )
    val CAB_WORDS = setOf("cab", "taxi", "ride", "auto")
    val CAB_PROVIDERS = setOf("uber", "ola", "rapido")
    val CINEMA_BRANDS = setOf("inox", "pvr", "cinepolis", "carnival", "miraj", "movietime", "imax", "mukta", "rajhans", "wave", "maxus", "regal")
    val TRAVEL = setOf("train", "flight", "bus", "irctc", "railway", "plane", "rail", "metro")
    val HOME_WORDS = setOf("ghar", "home", "house")
    val WORK_WORDS = setOf("office", "work")
    val MESSAGING = setOf("message", "sms", "whatsapp", "email", "mail", "text", "video")
    val PAYMENT = setOf(
        "pay", "payment", "paise", "paisa", "rupaye", "rupees", "rupee", "rs", "transfer", "upi", "recharge", "kharid", "kharido",
        "buy", "purchase", "order", "money", "bill",
    )
    val FAMILY = setOf(
        "mummy", "mumma", "mom", "mommy", "maa", "mother", "papa", "pappa", "dad", "daddy", "father", "pitaji", "didi", "bhaiya",
        "bhai", "behen", "sister", "brother", "wife", "biwi", "husband", "pati", "dadi", "dada", "nani", "nana", "chacha", "chachi",
        "mama", "mami", "bua", "boss",
    )
    val CITIES = setOf(
        "mumbai", "bombay", "delhi", "bangalore", "bengaluru", "pune", "hyderabad", "chennai", "kolkata", "ahmedabad", "jaipur",
        "lucknow", "noida", "gurgaon", "gurugram", "thane", "chandigarh", "indore", "bhopal", "surat", "nagpur", "kochi", "goa",
        "patna", "nashik", "vadodara",
    )
}

/** What an utterance that the NLU could not complete was missing; the rule planner turns it into a question. */
internal enum class MissingSlot {
    REMINDER_TIME, REMINDER_WHICH, ALARM_TIME, TIMER_DURATION, CALLEE, CONTACT_NAME, NOTE_TEXT, NOTE_WHICH, SEARCH_QUERY,
    APP_NAME, MEMORY_FACT, UNSUPPORTED_PAYMENT, UNSUPPORTED_MESSAGING, UNSUPPORTED_TRAVEL, UNSUPPORTED_ALARM_CHANGE,
    UNSUPPORTED_TIMER_CHANGE,
}

internal object Cues {
    /** Detects a recognisable but incomplete or unsupported request from NluResult.normalizedText. */
    fun missing(normalizedText: String): MissingSlot? {
        val u = Utterance(normalizedText.split(' ').filter { it.isNotBlank() }.map { Token(it, it) })
        if (u.size == 0) return null
        val moneyWord = u.hasAny(Words.PAYMENT) || (u.has("bhej", "send") && u.has("paise", "money", "rupaye"))
        if (moneyWord) return MissingSlot.UNSUPPORTED_PAYMENT
        if (u.hasAny(Words.MESSAGING) && (u.hasAny(setOf("bhej", "send", "kar", "call", "reply", "forward")) || u.has("message"))) {
            return MissingSlot.UNSUPPORTED_MESSAGING
        }
        if (u.hasAny(Words.TRAVEL) && u.has("ticket", "book")) return MissingSlot.UNSUPPORTED_TRAVEL
        val reminderCue = u.phrase("yaad", "dila") >= 0 || u.has("remind", "reminder")
        if (u.has("reminder") && u.hasAny(Words.REMOVE_VERBS)) return MissingSlot.REMINDER_WHICH
        if (reminderCue) return MissingSlot.REMINDER_TIME
        if (u.has("alarm") && u.hasAny(Words.REMOVE_VERBS + Words.STOP_VERBS)) return MissingSlot.UNSUPPORTED_ALARM_CHANGE
        if (u.has("alarm", "utha", "jaga", "wake")) return MissingSlot.ALARM_TIME
        if (u.has("timer") && u.hasAny(Words.REMOVE_VERBS + Words.STOP_VERBS)) return MissingSlot.UNSUPPORTED_TIMER_CHANGE
        if (u.has("timer", "countdown")) return MissingSlot.TIMER_DURATION
        if (u.has("note") && u.hasAny(Words.REMOVE_VERBS)) return MissingSlot.NOTE_WHICH
        if (u.has("note") || u.canon.first() == "likh") return MissingSlot.NOTE_TEXT
        if (u.hasAny(Words.CALL_WORDS)) return MissingSlot.CALLEE
        if (u.has("number", "contact")) return MissingSlot.CONTACT_NAME
        if (u.has("google", "search", "khoj")) return MissingSlot.SEARCH_QUERY
        if (u.phrase("yaad", "rakh") >= 0 || u.has("remember")) return MissingSlot.MEMORY_FACT
        if (u.hasAny(Words.OPEN_VERBS)) return MissingSlot.APP_NAME
        return null
    }
}

/** Resolves an answer to a CHOICE question: ordinals ("pehla wala", "second", "2") or an option's words. */
internal object ChoiceMatcher {
    private val ordinals = mapOf(
        "pehla" to 0, "pehle" to 0, "pahla" to 0, "pehli" to 0, "first" to 0, "1" to 0, "1st" to 0, "ek" to 0, "one" to 0,
        "dusra" to 1, "doosra" to 1, "dusre" to 1, "dusri" to 1, "second" to 1, "2" to 1, "2nd" to 1, "do" to 1, "two" to 1,
        "teesra" to 2, "tisra" to 2, "teesri" to 2, "third" to 2, "3" to 2, "3rd" to 2, "teen" to 2, "three" to 2,
        "chautha" to 3, "chauthi" to 3, "fourth" to 3, "4" to 3, "4th" to 3, "char" to 3, "four" to 3,
    )
    private val lastWords = setOf("last", "aakhri", "akhri", "aakhiri", "antim")
    private val noise = setOf("wala", "option", "number", "the", "one", "hai", "vala", "kar", "haan", "ok", "please")

    fun match(text: String, options: List<String>): String? {
        if (options.isEmpty()) return null
        val words = Normalizer.tokenize(text).map { it.canon }
        if (words.isEmpty()) return null
        val wanted = words.filterNot { it in noise }.ifEmpty { words }
        if (wanted.size <= 2) {
            if (wanted.any { it in lastWords }) return options.last()
            wanted.firstNotNullOfOrNull { ordinals[it] }?.let { return options.getOrNull(it) }
        }
        val spoken = words.toSet()
        val byWords = options.filter { option ->
            val optionWords = Normalizer.tokenize(option).map { it.canon.lowercase(Locale.ROOT) }
            optionWords.isNotEmpty() && (optionWords.all { it in spoken } || wanted.isNotEmpty() && wanted.all { it in optionWords })
        }
        return byWords.singleOrNull()
    }
}
