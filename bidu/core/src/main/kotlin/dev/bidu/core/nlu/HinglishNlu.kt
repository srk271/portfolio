package dev.bidu.core.nlu

import dev.bidu.core.memory.KnownApps
import dev.bidu.core.memory.MemoryKeys
import java.time.Duration
import java.time.ZonedDateTime
import java.util.Locale

/**
 * Offline, rule-based understanding of romanised Hinglish, English, Indian slang and Devanagari Hindi.
 *
 * Rules run in a fixed order and the first one that recognises a complete request wins. Anything the rules
 * cannot complete comes back as [Intent.Unknown] with a low confidence, so the planner can ask for the
 * missing piece or (when allowed) try cloud reasoning instead of guessing.
 */
class HinglishNlu : Nlu {
    private val times = HinglishTimeParser()

    override fun parse(text: String, context: NluContext): NluResult {
        val script = LanguageDetector.script(text)
        val tokens = Normalizer.normalize(text, context.wakePhrases)
        val language = LanguageDetector.language(tokens, script)
        val u = Utterance(tokens)
        val hit = understand(u, context, text)
        val intent = if (hit.confidence < MIN_CONFIDENCE && hit.intent !is Intent.Unknown) Intent.Unknown(text.trim()) else hit.intent
        return NluResult(
            intent = intent,
            confidence = hit.confidence.coerceIn(0.0, 1.0),
            language = language,
            script = script,
            normalizedText = u.canon.joinToString(" "),
            rawText = text,
        )
    }

    private data class Hit(val intent: Intent, val confidence: Double)

    private fun understand(u: Utterance, ctx: NluContext, raw: String): Hit {
        if (u.size == 0) return if (raw.isNotBlank()) Hit(Intent.Help, 0.6) else Hit(Intent.Unknown(raw.trim()), 0.0)
        if (ctx.pendingQuestion != null) answer(u)?.let { return it }
        val rules: List<() -> Hit?> = listOf(
            { stopListening(u) },
            { cancel(u) },
            { help(u) },
            { bareYesNo(u) },
            { reminder(u, ctx.now) },
            { note(u) },
            { forget(u) },
            { recall(u) },
            { remember(u) },
            { if (unsupported(u)) Hit(Intent.Unknown(raw.trim()), 0.3) else null },
            { alarm(u, ctx.now) },
            { timer(u) },
            { movie(u, ctx.now) },
            { cab(u) },
            { contact(u) },
            { flashlight(u) },
            { battery(u) },
            { webSearch(u) },
            { openApp(u, ctx) },
            { timeOrDate(u) },
            { greeting(u) },
        )
        for (rule in rules) rule()?.let { return it }
        return Hit(Intent.Unknown(raw.trim()), 0.2)
    }

    // ---- answers, control ----

    private fun answer(u: Utterance): Hit? {
        if (u.size > 4 || !u.canon.all { it in ANSWER_WORDS }) return null
        return when {
            u.hasAny(NO) -> Hit(Intent.Confirm(false), 0.95)
            u.hasAny(CANCEL_CORE) -> Hit(Intent.Cancel, 0.9)
            u.hasAny(YES) -> Hit(Intent.Confirm(true), 0.95)
            else -> null
        }
    }

    private fun bareYesNo(u: Utterance): Hit? {
        if (u.size > 2 || !u.canon.all { it in ANSWER_WORDS } || u.canon.all { it == "kar" }) return null
        return when {
            u.hasAny(NO) -> Hit(Intent.Confirm(false), 0.6)
            u.hasAny(YES) -> Hit(Intent.Confirm(true), 0.6)
            else -> null
        }
    }

    private fun stopListening(u: Utterance): Hit? {
        val sleep = u.phrase("so", "ja") >= 0 || (u.size == 1 && u.canon[0] == "so") || u.phrase("go", "to", "sleep") >= 0 ||
            (u.size <= 2 && u.has("sleep"))
        val listening = u.has("listening", "sun", "mic") && u.hasAny(Words.STOP_VERBS + setOf("disable")) && u.size <= 4
        val bandHoJa = u.phrase("band", "ho", "ja") >= 0 && u.size <= 3
        return if (sleep || listening || bandHoJa) Hit(Intent.StopListening, 0.9) else null
    }

    private fun cancel(u: Utterance): Hit? {
        val core = u.hasAny(CANCEL_CORE) || u.phrase("never", "mind") >= 0 || u.phrase("forget", "it") >= 0 ||
            u.phrase("jaana", "do") >= 0
        return if (core && u.canon.all { it in CANCEL_ALLOWED }) Hit(Intent.Cancel, 0.9) else null
    }

    private fun help(u: Utterance): Hit? {
        val asks = u.has("help", "madad") || u.phrase("what", "can", "you", "do") >= 0 ||
            (u.has("kya") && u.has("kar") && u.has("sakta")) || u.phrase("kya", "kya", "kar") >= 0
        return if (asks) Hit(Intent.Help, 0.9) else null
    }

    private fun greeting(u: Utterance): Hit? =
        if (u.canon.all { it in GREETING }) Hit(Intent.Help, 0.6) else null

    // ---- reminders ----

    private fun reminder(u: Utterance, now: ZonedDateTime): Hit? {
        val cue = mutableSetOf<Int>()
        val yaadDila = u.phrase("yaad", "dila")
        if (yaadDila >= 0) cue += setOf(yaadDila, yaadDila + 1)
        cue += u.indicesOf(setOf("remind", "reminder"))
        if (cue.isEmpty()) return null
        val noun = u.has("reminder")

        if (noun && (u.hasAny(Words.REMOVE_VERBS) || u.has("band", "off"))) {
            val query = u.content(cue + u.indicesOf(Words.REMOVE_VERBS + setOf("band", "off", "kar")), QUERY_TRIM)
            return if (query.isEmpty()) null else Hit(Intent.CancelReminder(u.surface(query)), 0.88)
        }
        if (noun && yaadDila < 0 && !u.has("remind") && !u.hasAny(Words.CREATE_VERBS) &&
            (u.hasAny(LIST_VERBS) || u.size <= 2)
        ) {
            return Hit(Intent.ListReminders, 0.9)
        }

        val time = times.find(u.tokens, now) ?: return null
        val exclude = cue.toMutableSet()
        for (i in u.indicesOf(setOf("reminder"))) {
            var j = i + 1
            while (j < u.size && u.canon[j] in REMINDER_VERBS) exclude += j++
        }
        exclude += time.indices
        val textIdx = u.content(exclude, REMINDER_LEAD, REMINDER_TRAIL)
        val text = u.payload(textIdx)
        val confidence = if (text.isNotBlank() && (time.hasClock || time.hasPart)) 0.92 else 0.75
        return Hit(Intent.CreateReminder(text.ifBlank { "Reminder" }, time.dateTime), confidence)
    }

    // ---- notes ----

    private fun note(u: Utterance): Hit? {
        val noteIdx = u.indicesOf(setOf("note"))
        val dictateFirst = u.canon[0] == "likh" || (u.canon[0] == "write" && u.has("down"))
        if (noteIdx.isEmpty() && !dictateFirst) return null

        if (noteIdx.isNotEmpty() && u.hasAny(Words.REMOVE_VERBS)) {
            val query = u.content(noteIdx + u.indicesOf(Words.REMOVE_VERBS + setOf("kar")), QUERY_TRIM)
            return if (query.isEmpty()) null else Hit(Intent.DeleteNote(u.surface(query)), 0.88)
        }
        if (noteIdx.isNotEmpty() && u.hasAny(NOTE_READ) && !u.hasAny(NOTE_WRITE_STRONG)) {
            val query = u.content(noteIdx + u.indicesOf(NOTE_READ + setOf("kar")), QUERY_TRIM)
            return Hit(Intent.SearchNotes(query.takeIf { it.isNotEmpty() }?.let(u::surface)), 0.88)
        }
        if (dictateFirst || u.hasAny(NOTE_WRITE)) {
            val exclude = noteIdx.toMutableSet()
            for (n in noteIdx) {
                var j = n + 1
                while (j < u.size && u.canon[j] in NOTE_WRITE) exclude += j++
                j = n - 1
                while (j >= 0 && (u.canon[j] in NOTE_WRITE || u.canon[j] in setOf("a", "an", "ek"))) exclude += j--
            }
            var j = 0
            while (j < u.size && j <= 1 && u.canon[j] in NOTE_WRITE) exclude += j++
            val textIdx = u.content(exclude, NOTE_LEAD, NOTE_TRAIL)
            if (textIdx.isEmpty()) return null
            return Hit(Intent.CreateNote(u.payload(textIdx)), 0.9)
        }
        return if (noteIdx.isNotEmpty() && u.size <= 2) Hit(Intent.SearchNotes(null), 0.7) else null
    }

    // ---- memory ----

    private fun forget(u: Utterance): Hit? {
        val cue = u.indicesOf(setOf("bhool", "bhula", "forget"))
        if (cue.isEmpty() || u.has("mat", "dont", "not")) return null
        val idx = u.content(cue + u.indicesOf(setOf("ja", "kar", "do")), MEMORY_TRIM)
        if (idx.isEmpty()) return null
        return Hit(Intent.Forget(memoryKey(u, idx) ?: u.spoken(idx).lowercase(Locale.ROOT)), 0.85)
    }

    private fun recall(u: Utterance): Hit? {
        if (!u.hasAny(RECALL_QUESTION)) return null
        if (u.hasAny(setOf("number", "contact", "time", "samay", "waqt", "date", "tareekh", "battery", "reminder", "note"))) return null
        val possessive = u.hasAny(OWN)
        val key = when {
            u.has("pasand") || (u.has("like", "likes") && u.has("i", "do", "what")) -> LIKES_KEY
            u.has("naam", "name") && possessive -> MemoryKeys.USER_NAME
            u.has("naam", "name") && u.hasAny(Words.FAMILY) -> MemoryKeys.contactAlias(u.canon.first { it in Words.FAMILY })
            u.hasAny(Words.HOME_WORDS) || u.phrase("where", "do", "i", "live") >= 0 || (u.has("rehta") && u.has("kahan")) ->
                MemoryKeys.HOME_ADDRESS
            u.hasAny(Words.WORK_WORDS) && (u.has("kahan", "where", "address", "pata") || possessive) -> MemoryKeys.WORK_ADDRESS
            u.has("city") && possessive -> MemoryKeys.CITY
            u.has("address", "pata", "location") -> {
                val owner = u.content(u.indicesOf(setOf("address", "pata", "location")) + u.indicesOf(RECALL_QUESTION), MEMORY_TRIM)
                if (owner.isEmpty()) return null
                MemoryKeys.place(u.spoken(owner))
            }
            possessive && u.has("kahan", "kya", "where", "what") -> {
                val subject = u.content(u.indicesOf(RECALL_QUESTION), MEMORY_TRIM)
                if (subject.isEmpty() || subject.size > 3) return null
                u.spoken(subject).lowercase(Locale.ROOT)
            }
            else -> return null
        }
        return Hit(Intent.Recall(key), 0.85)
    }

    private fun remember(u: Utterance): Hit? {
        val cue = mutableSetOf<Int>()
        val yaadRakh = u.phrase("yaad", "rakh")
        if (yaadRakh >= 0) cue += setOf(yaadRakh, yaadRakh + 1)
        cue += u.indicesOf(setOf("remember"))
        val explicit = cue.isNotEmpty()
        val payload = u.content(cue, REMEMBER_LEAD, REMEMBER_TRAIL)
        if (payload.isEmpty() || payload.any { u.canon[it] in RECALL_QUESTION - setOf("bata", "dikha", "tell") }) return null
        val fact = fact(u, payload, explicit) ?: return null
        if (fact.second.isBlank()) return null
        return Hit(Intent.Remember(fact.first, fact.second), if (explicit) 0.9 else 0.75)
    }

    /** Key/value of a stated fact. Implicit statements ("mera naam X hai") only match well-known shapes. */
    private fun fact(u: Utterance, idx: List<Int>, explicit: Boolean): Pair<String, String>? {
        val c = idx.map { u.canon[it] }
        fun value(from: Int): String {
            val rest = idx.drop(from).toMutableList()
            while (rest.isNotEmpty() && u.canon[rest.first()] in VALUE_LEAD) rest.removeAt(0)
            while (rest.isNotEmpty() && u.canon[rest.last()] in VALUE_TRAIL) rest.removeAt(rest.lastIndex)
            return u.surface(rest)
        }

        val means = c.indexOfFirst { it == "matlab" || it == "means" }
        if (means > 0 && explicit || means > 0 && c[0] in Words.FAMILY) {
            val alias = c.take(means).filterNot { it in OWN }.joinToString(" ")
            val key = if (alias in Words.FAMILY) MemoryKeys.contactAlias(alias) else alias
            return key to value(means + 1)
        }
        val likeAt = c.indexOfFirst { it == "pasand" || it == "like" || it == "love" }
        if (likeAt >= 0) {
            val liked = idx.filterNot { u.canon[it] in LIKE_NOISE }
            if (liked.isEmpty()) return null
            val text = u.surface(liked)
            val provider = text.lowercase(Locale.ROOT)
            return if (provider in Words.CAB_PROVIDERS) MemoryKeys.PREFERRED_CAB_PROVIDER to provider else LIKES_KEY to text
        }
        val nameAt = c.indexOfFirst { it == "naam" || it == "name" }
        if (nameAt >= 0) {
            val owner = c.take(nameAt).filterNot { it in setOf("ka", "ki", "ke", "s", "of", "the") }
            val key = when {
                owner.isEmpty() || owner.all { it in OWN } -> MemoryKeys.USER_NAME
                owner.size == 1 && owner[0] in Words.FAMILY -> MemoryKeys.contactAlias(owner[0])
                explicit -> owner.filterNot { it in OWN }.joinToString("_") + "_name"
                else -> return null
            }
            return key to value(nameAt + 1)
        }
        val addressAt = c.indexOfFirst { it == "address" || it == "pata" || it == "location" }
        if (addressAt > 0) {
            val owner = c.take(addressAt).filterNot { it in OWN || it in setOf("ka", "ki", "ke", "s", "the") }
            val key = when {
                owner.any { it in Words.HOME_WORDS } -> MemoryKeys.HOME_ADDRESS
                owner.any { it in Words.WORK_WORDS } -> MemoryKeys.WORK_ADDRESS
                explicit && owner.isNotEmpty() -> MemoryKeys.place(owner.joinToString(" "))
                else -> return null
            }
            return key to value(addressAt + 1)
        }
        val liveAt = c.indexOfFirst { it == "rehta" || it == "live" }
        if (liveAt >= 0) {
            val place = idx.filterNot { u.canon[it] in LIVE_NOISE }
            if (place.isEmpty()) return null
            val text = u.surface(place)
            return (if (text.lowercase(Locale.ROOT) in Words.CITIES) MemoryKeys.CITY else MemoryKeys.HOME_ADDRESS) to text
        }
        val start = c.indexOfFirst { it !in OWN }
        if (start < 0 || start + 1 >= c.size) return null
        val subject = c[start]
        val key = when {
            subject in Words.HOME_WORDS -> MemoryKeys.HOME_ADDRESS
            subject in Words.WORK_WORDS -> MemoryKeys.WORK_ADDRESS
            subject == "city" -> MemoryKeys.CITY
            explicit && subject !in Lexicon.VERBS && subject !in Lexicon.POSTPOSITIONS -> u.tokens[idx[start]].spoken.lowercase(Locale.ROOT)
            else -> return null
        }
        if (!explicit && start == 0) return null
        return key to value(start + 1)
    }

    private fun memoryKey(u: Utterance, idx: List<Int>): String? {
        val c = idx.map { u.canon[it] }
        return when {
            c.any { it in Words.HOME_WORDS } -> MemoryKeys.HOME_ADDRESS
            c.any { it in Words.WORK_WORDS } -> MemoryKeys.WORK_ADDRESS
            c.any { it == "pasand" || it == "likes" } -> LIKES_KEY
            c.any { it == "naam" || it == "name" } && c.none { it in Words.FAMILY } -> MemoryKeys.USER_NAME
            c.any { it in Words.FAMILY } -> MemoryKeys.contactAlias(c.first { it in Words.FAMILY })
            c.any { it == "city" } -> MemoryKeys.CITY
            c.any { it == "address" || it == "pata" } -> {
                val owner = idx.filterNot { u.canon[it] in setOf("address", "pata", "ka", "ki", "ke", "s") }
                if (owner.isEmpty()) null else MemoryKeys.place(u.spoken(owner))
            }
            else -> null
        }
    }

    // ---- things Bidu refuses or cannot do offline ----

    private fun unsupported(u: Utterance): Boolean {
        if (u.hasAny(Words.PAYMENT)) return true
        val sending = u.has("bhej", "send", "reply", "forward")
        if (u.hasAny(Words.MESSAGING) && (sending || u.has("call") || u.has("message") && !u.hasAny(Words.OPEN_VERBS) ||
                u.has("kar") && !u.hasAny(Words.OPEN_VERBS))
        ) {
            return true
        }
        return u.hasAny(Words.TRAVEL) && u.has("ticket", "book")
    }

    // ---- alarm and timer ----

    private fun alarm(u: Utterance, now: ZonedDateTime): Hit? {
        val cue = u.has("alarm") || u.has("utha", "jaga") || u.phrase("wake", "up") >= 0 || u.phrase("wake", "me") >= 0
        if (!cue) return null
        if (u.has("alarm") && (u.hasAny(Words.REMOVE_VERBS) || u.hasAny(Words.STOP_VERBS))) return null
        val time = times.find(u.tokens, now)
        if (time != null && time.hasClock) {
            val at = time.dateTime.toLocalTime().withSecond(0).withNano(0)
            return Hit(Intent.SetAlarm(at, label(u, time.indices)), 0.9)
        }
        if (u.has("alarm")) {
            times.findDuration(u.tokens)?.let { d -> timerHit(u, d)?.let { return it.copy(confidence = 0.7) } }
        }
        return null
    }

    private fun timer(u: Utterance): Hit? {
        if (!u.has("timer", "countdown")) return null
        if (u.hasAny(Words.REMOVE_VERBS) || u.hasAny(Words.STOP_VERBS)) return null
        val d = times.findDuration(u.tokens) ?: return null
        return timerHit(u, d)
    }

    private fun timerHit(u: Utterance, d: FoundDuration): Hit? {
        if (d.duration.seconds !in 1..86_400) return null
        return Hit(Intent.SetTimer(Duration.ofSeconds(d.duration.seconds), label(u, d.indices)), 0.92)
    }

    /** "pasta ke liye 10 minute ka timer" -> "pasta". */
    private fun label(u: Utterance, used: Set<Int>): String? {
        val liye = u.indexOf("liye")
        if (liye <= 0) return null
        var from = liye - 1
        if (u.canon[from] == "ke") from--
        if (from < 0) return null
        val run = u.runBackward(from, LABEL_STOP, used)
        return run.takeIf { it.isNotEmpty() && it.size <= 4 }?.let(u::surface)
    }

    // ---- hand-offs: movie tickets, cabs ----

    private fun movie(u: Utterance, now: ZonedDateTime): Hit? {
        if (u.hasAny(Words.TRAVEL)) return null
        val brand = u.firstIndex(Words.CINEMA_BRANDS)
        val ticketApp = u.has("bookmyshow", "district")
        val cue = u.has("movie", "cinema") || brand >= 0 && u.has("ticket", "seat", "book", "show", "movie") ||
            u.has("ticket", "seat") && u.has("book", "show") ||
            ticketApp && u.has("book", "ticket", "dekh", "show", "watch", "movie")
        if (!cue) return null

        val used = mutableSetOf<Int>()
        val time = times.find(u.tokens, now)?.takeIf { it.hasClock || it.hasPart }
        time?.let { used += it.indices }

        var seats: Int? = null
        for (i in u.canon.indices) {
            if (u.canon[i] !in SEAT_NOUNS || i == 0) continue
            val n = Numbers.count(u.canon, i - 1)
            if (n != null && n in 1..20 && (i - 1) !in used) {
                seats = n
                used += setOf(i - 1, i)
                break
            }
        }

        val city = u.firstIndex(Words.CITIES).takeIf { it >= 0 && it !in used }
        var venue: String? = null
        if (brand >= 0 && brand !in used) {
            val run = u.runForward(brand, VENUE_STOP, used)
            venue = u.surface(run)
            used += run
        } else {
            val hall = u.indexOf("cinema")
            if (hall > 0) {
                val run = u.runBackward(hall, VENUE_STOP - setOf("cinema"), used)
                if (run.size > 1) {
                    venue = u.surface(run)
                    used += run
                }
            }
        }
        city?.let { used += it }

        val title = u.longestRun(MOVIE_STOP, used).takeIf { it.size in 1..5 }?.let(u::surface)
        return Hit(
            Intent.MovieTickets(
                query = title,
                city = city?.let { u.tokens[it].surface.replaceFirstChar { ch -> ch.titlecase(Locale.ROOT) } },
                venue = venue,
                showTime = time?.dateTime,
                seats = seats,
            ),
            0.9,
        )
    }

    private fun cab(u: Utterance): Hit? {
        val cabWord = u.hasAny(Words.CAB_WORDS - setOf("auto"))
        val providerAt = u.firstIndex(Words.CAB_PROVIDERS)
        if (!cabWord && providerAt < 0 && !u.has("auto")) return null
        val destination = cabDestination(u)
        val booking = u.has("book", "bula", "chahiye", "call") || destination != null
        if (!cabWord && !booking) return null
        val provider = when (providerAt.takeIf { it >= 0 }?.let { u.canon[it] }) {
            "uber" -> CabProvider.UBER
            "ola" -> CabProvider.OLA
            "rapido" -> CabProvider.RAPIDO
            else -> CabProvider.ANY
        }
        return Hit(Intent.BookCab(destination, provider), 0.9)
    }

    private fun cabDestination(u: Utterance): String? {
        val run: List<Int> = run {
            for (anchor in listOf("liye", "tak", "jaana", "chal")) {
                val at = u.indexOf(anchor)
                if (at <= 0) continue
                var from = at - 1
                if (u.canon[from] == "ke" && from > 0) from--
                if (u.canon[from] == "jaana" && from > 0) from--
                val back = u.runBackward(from, DEST_STOP)
                if (back.isNotEmpty()) return@run back
            }
            for (anchor in listOf("to", "for")) {
                val at = u.indexOf(anchor)
                if (at < 0) continue
                val fwd = u.runForward(at + 1, DEST_STOP_FORWARD).dropWhile { u.canon[it] == "the" }
                if (fwd.isNotEmpty()) return@run fwd
            }
            emptyList()
        }
        if (run.isEmpty()) return null
        val c = run.map { u.canon[it] }
        return when {
            c.size == 1 && c[0] in Words.HOME_WORDS -> "ghar"
            c.size == 1 && c[0] in Words.WORK_WORDS -> "office"
            else -> u.surface(run)
        }
    }

    // ---- contacts ----

    private fun contact(u: Utterance): Hit? {
        val callWord = u.has("call", "dial", "ring") || (u.has("phone") && u.hasAny(Words.CALL_VERBS))
        val lookup = u.has("number", "contact")
        if (!callWord && !lookup) return null
        val run = u.longestRun(PERSON_STOP)
        if (run.isEmpty() || run.size > 4) return null
        val name = u.spoken(run)
        if (lookup && !u.has("call", "dial", "ring") && !(u.has("phone") && u.has("laga", "mila"))) {
            if (u.hasAny(OWN) && run.none { u.canon[it] in Words.FAMILY }) return null
            return Hit(Intent.FindContact(name), 0.88)
        }
        return Hit(Intent.CallContact(name), 0.9)
    }

    // ---- device ----

    private fun flashlight(u: Utterance): Hit? {
        if (!u.has("torch", "flashlight") && u.phrase("flash", "light") < 0) return null
        val action = when {
            u.hasAny(TORCH_OFF) -> SwitchAction.OFF
            u.hasAny(TORCH_ON) -> SwitchAction.ON
            else -> SwitchAction.TOGGLE
        }
        return Hit(Intent.Flashlight(action), 0.95)
    }

    private fun battery(u: Utterance): Hit? {
        val asks = u.has("battery") || u.has("charge") && u.hasAny(setOf("kitna", "bacha", "level", "percent", "percentage", "how", "kya"))
        if (!asks || u.has("saver", "mode")) return null
        return Hit(Intent.BatteryLevel, 0.95)
    }

    private fun webSearch(u: Utterance): Hit? {
        val cue = u.indicesOf(SEARCH_CUES).toMutableSet()
        val lookUp = u.phrase("look", "up")
        if (lookUp >= 0) cue += setOf(lookUp, lookUp + 1)
        if (cue.isEmpty()) return null
        val exclude = cue.toMutableSet()
        for (i in cue) {
            for (j in listOf(i - 1, i + 1)) if (j in u.canon.indices && u.canon[j] in SEARCH_VERBS) exclude += j
        }
        val query = u.content(exclude, SEARCH_LEAD, SEARCH_TRAIL)
        if (query.isEmpty()) return null
        return Hit(Intent.WebSearch(u.surface(query)), 0.88)
    }

    private fun openApp(u: Utterance, ctx: NluContext): Hit? {
        val names = appNames(ctx)
        val verbs = u.indicesOf(Words.OPEN_VERBS)
        if (verbs.isEmpty()) {
            if (u.size > 3) return null
            return names[u.surface(u.canon.indices.toList()).lowercase(Locale.ROOT)]?.let { Hit(Intent.OpenApp(it), 0.6) }
        }
        val idx = u.content(verbs + u.indicesOf(setOf("kar")), APP_TRIM)
        if (idx.isEmpty()) return null
        val spoken = u.surface(idx)
        names[spoken.lowercase(Locale.ROOT)]?.let { return Hit(Intent.OpenApp(it), 0.92) }
        names[u.spoken(idx).lowercase(Locale.ROOT)]?.let { return Hit(Intent.OpenApp(it), 0.92) }
        for (size in minOf(3, idx.size) downTo 1) {
            for (start in 0..idx.size - size) {
                val window = idx.subList(start, start + size)
                names[u.surface(window).lowercase(Locale.ROOT)]?.let { return Hit(Intent.OpenApp(it), 0.75) }
            }
        }
        return Hit(Intent.OpenApp(spoken), if (idx.size <= 3) 0.7 else 0.4)
    }

    private fun appNames(ctx: NluContext): Map<String, String> = buildMap {
        for (name in ctx.knownAppNames) put(TextFolding.fold(name).lowercase(Locale.ROOT), name)
        for (app in KnownApps.hints) {
            put(app.label.lowercase(Locale.ROOT), app.label)
            for (alias in app.aliases) put(TextFolding.fold(alias).lowercase(Locale.ROOT), app.label)
        }
    }

    // ---- time and date ----

    private fun timeOrDate(u: Utterance): Hit? {
        val timeAsked = u.has("time", "samay", "waqt") && u.hasAny(TIME_QUESTION) || u.has("kitna") && u.has("baje") ||
            u.phrase("what", "time") >= 0
        if (timeAsked) {
            val extra = u.canon.count { it !in TIME_ALLOWED }
            return when (extra) {
                0 -> Hit(Intent.TellTime, 0.95)
                1 -> Hit(Intent.TellTime, 0.6)
                else -> null
            }
        }
        val dateAsked = u.has("date", "tareekh") && u.hasAny(TIME_QUESTION + setOf("aaj", "today")) ||
            u.has("din", "day") && u.has("kaunsa", "kya", "what")
        if (dateAsked && !u.has("kal", "parso", "tomorrow")) {
            val extra = u.canon.count { it !in DATE_ALLOWED }
            return when (extra) {
                0 -> Hit(Intent.TellDate, 0.95)
                1 -> Hit(Intent.TellDate, 0.6)
                else -> null
            }
        }
        return null
    }

    private companion object {
        const val MIN_CONFIDENCE = 0.5
        const val LIKES_KEY = "likes"

        val YES = setOf(
            "haan", "yes", "yeah", "yep", "yup", "ok", "theek", "sahi", "bilkul", "sure", "confirm", "go", "correct", "accha",
            "achha", "acha", "proceed", "kar", "done", "chalo", "right",
        )
        val NO = setOf("nahi", "no", "nope", "mat", "na", "nai")
        val CANCEL_CORE = setOf("cancel", "ruk", "chhod", "rehne", "bas", "stop", "chup", "abort", "nevermind")
        val CANCEL_ALLOWED = CANCEL_CORE + setOf(
            "ho", "ja", "kar", "it", "that", "never", "mind", "forget", "de", "ab", "abhi", "sab", "everything", "jaana", "do", "please",
        )
        val ANSWER_WORDS = YES + NO + CANCEL_CORE + setOf("hai", "ho", "ji", "do", "it", "please", "ahead", "ja", "karo", "de", "na")
        val GREETING = setOf(
            "hi", "hello", "hey", "namaste", "namaskar", "kaise", "kaisa", "kaisi", "ho", "hai", "how", "are", "you", "sup", "hola",
            "good", "morning", "evening", "kya", "haal", "chal", "raha", "rahe", "hii", "helo", "hlo", "salaam", "ram", "aur",
        )

        val LIST_VERBS = setOf(
            "bata", "dikha", "list", "show", "padh", "sun", "kya", "kaunsa", "kitna", "what", "which", "all", "sab", "check", "dekh",
            "any", "koi", "hai", "hain",
        )
        val REMINDER_VERBS = setOf("laga", "set", "bana", "kar", "add", "create", "daal", "save", "do", "de")
        val QUERY_TRIM = setOf(
            "wala", "mera", "my", "the", "a", "an", "ko", "ka", "ki", "ke", "for", "about", "of", "se", "ye", "this", "that", "is", "do",
            "de", "please", "sab", "all", "mein", "me", "wale", "and", "aur", "kar", "jo", "hai", "tha", "on", "at",
        )
        val REMINDER_LEAD = setOf(
            "mujhe", "me", "to", "ki", "ke", "ko", "that", "about", "for", "liye", "ka", "kar", "mera", "my", "please", "a", "an", "the",
            "ek", "set", "laga", "bana", "add", "create", "ye", "is", "on", "at", "hai", "do", "de", "na",
        )
        val REMINDER_TRAIL = setOf(
            "ka", "ki", "ke", "ko", "liye", "wala", "kar", "the", "for", "to", "me", "mujhe", "please", "set", "laga", "bana", "na",
            "dena", "a", "an", "ek", "about", "that", "at", "on", "by", "do", "de",
        )

        val NOTE_READ = setOf(
            "dikha", "bata", "padh", "sun", "list", "show", "search", "dhundh", "khoj", "find", "read", "kya", "kaunsa", "check",
            "what", "which", "open", "khol", "dekh",
        )
        val NOTE_WRITE_STRONG = setOf("likh", "write", "take", "bana", "add", "save", "daal", "create", "new", "naya", "make")
        val NOTE_WRITE = NOTE_WRITE_STRONG + setOf("kar", "le", "lo", "down", "rakh", "do", "de")
        val NOTE_LEAD = setOf(
            "a", "an", "ek", "ki", "that", "ke", "ko", "mein", "me", "ye", "is", "the", "please", "mera", "my", "naya", "new", "ka",
            "saying", "about",
        )
        val NOTE_TRAIL = setOf("ka", "ki", "ke", "ko", "liye", "mein", "kar", "le", "lo", "the", "wala", "please", "note", "do", "de", "mera")

        val OWN = setOf("mera", "my", "apna", "our", "hamara", "main", "i")
        val RECALL_QUESTION = setOf("kya", "kahan", "kaun", "kaunsa", "what", "where", "who", "which", "bata", "dikha", "tell")
        val MEMORY_TRIM = setOf(
            "mera", "my", "the", "ka", "ki", "ke", "about", "is", "hai", "s", "of", "please", "sab", "ko", "kya", "what", "where",
            "kahan", "bata", "tell", "me", "mujhe", "ye", "wala", "do", "de",
        )
        val REMEMBER_LEAD = setOf("ki", "that", "ke", "please", "ye", "yeh", "is", "bhi", "and", "aur")
        val REMEMBER_TRAIL = setOf("kar", "le", "lo", "na", "please", "rakh", "ki", "bhi", "do", "de")
        val VALUE_LEAD = setOf("is", "are", "hai", "ka", "ki", "ke", "in", "at", "=", "matlab", "means", "se")
        val VALUE_TRAIL = setOf("hai", "hain", "h", "tha", "mein", "me", "pe", "par", "ko", "is", "please")
        val LIKE_NOISE = setOf(
            "mujhe", "i", "pasand", "hai", "like", "love", "bahut", "really", "very", "achha", "lagta", "hain", "do", "to", "mera",
            "my", "humein", "hamein", "bhi", "too", "a", "lot", "zyada", "sabse", "most", "ko",
        )
        val LIVE_NOISE = setOf("main", "i", "rehta", "live", "in", "mein", "me", "hoon", "hu", "hun", "hai", "at", "abhi", "now", "ham", "hum")

        val LABEL_STOP = setOf("timer", "alarm", "laga", "set", "kar", "ka", "ki", "ke", "mujhe", "ek", "a", "the", "do", "de")
        val SEAT_NOUNS = setOf("ticket", "seat", "log", "people", "person", "persons")
        val VENUE_STOP = setOf(
            "mein", "me", "pe", "par", "at", "se", "ke", "ka", "ki", "ko", "for", "liye", "book", "ticket", "seat", "show", "movie",
            "kar", "aaj", "kal", "parso", "subah", "shaam", "raat", "dopahar", "the", "in", "on", "to", "tonight", "today", "tomorrow",
            "mujhe", "hai", "chahiye", "baje",
        )
        val MOVIE_STOP = Lexicon.VERBS + setOf(
            "book", "kar", "ticket", "seat", "movie", "cinema", "show", "mein", "me", "pe", "par", "ke", "ki", "ka", "ko", "liye",
            "for", "the", "a", "an", "at", "in", "on", "of", "to", "i", "we", "want", "hum", "ham", "mujhe", "humein", "chahiye", "dekh",
            "watch", "bookmyshow", "district", "app", "aaj", "kal", "hai", "hain", "jaana", "chal", "wala", "log", "people", "ek",
            "get", "please", "naya", "new", "koi", "achha", "latest", "na", "se", "and", "aur", "seats", "tickets", "do", "de", "tonight",
            "today", "tomorrow", "book", "baje",
        )
        val DEST_STOP = setOf(
            "cab", "taxi", "auto", "ride", "uber", "ola", "rapido", "book", "bula", "kar", "mujhe", "se", "ek", "a", "an", "me", "mera",
            "my", "the", "chahiye", "hai", "ko", "ke", "ka", "ki", "liye", "tak", "jaana", "chal", "please", "abhi", "i", "want", "need",
            "from", "le", "lo", "hum", "ham", "do", "de", "aur", "and", "jaldi",
        )
        val DEST_STOP_FORWARD = setOf("by", "via", "with", "on", "now", "please", "using", "from", "at", "abhi", "book", "kar", "se", "pe")
        val PERSON_STOP = Words.CALL_WORDS + Words.CALL_VERBS + setOf(
            "ko", "ka", "ki", "ke", "s", "se", "pe", "par", "on", "at", "to", "mujhe", "mera", "my", "me", "the", "a", "an", "up",
            "number", "mobile", "contact", "abhi", "jaldi", "please", "bata", "dikha", "find", "search", "khoj", "dhundh", "kya", "hai",
            "what", "is", "show", "send", "de", "do", "give", "video", "audio", "voice", "of", "for", "now", "ek", "baar", "unko",
            "usko", "nikal", "wala", "phone", "back", "tell", "save", "saved",
        )
        val TORCH_ON = setOf("on", "jala", "chalu", "start", "enable", "laga")
        val TORCH_OFF = setOf("off", "band", "bujha", "stop", "disable")
        val SEARCH_CUES = setOf("google", "search", "khoj", "dhundh")
        val SEARCH_VERBS = setOf("kar", "laga", "pe", "par", "on", "karo", "do", "de")
        val SEARCH_LEAD = setOf(
            "pe", "par", "on", "for", "the", "about", "ke", "ki", "ka", "ko", "mein", "please", "internet", "web", "net", "zara", "kuch",
            "ye", "this", "kar", "do", "de",
        )
        val SEARCH_TRAIL = setOf(
            "pe", "par", "on", "for", "the", "about", "ke", "ki", "ka", "ko", "mein", "baare", "please", "internet", "web", "net", "kar",
            "do", "de", "se",
        )
        val APP_TRIM = setOf(
            "app", "application", "mera", "my", "the", "ko", "kar", "do", "de", "please", "wala", "a", "an", "ye", "is", "up", "on",
            "mein", "pe", "zara",
        )
        val TIME_QUESTION = setOf("kya", "kitna", "what", "bata", "tell", "hua", "current", "abhi")
        val TIME_ALLOWED = setOf(
            "time", "samay", "waqt", "kya", "kitna", "baje", "hain", "hai", "hua", "ho", "gaya", "abhi", "what", "is", "it", "the",
            "current", "bata", "tell", "me", "right", "now", "please", "sahi", "ka", "mujhe", "s", "do", "you", "know", "exact", "bhi",
        )
        val DATE_ALLOWED = setOf(
            "aaj", "today", "kya", "hai", "what", "is", "the", "kaunsa", "din", "day", "date", "tareekh", "bata", "tell", "me", "s",
            "it", "of", "week", "current", "ka", "ki", "todays", "mujhe",
        )
    }
}
