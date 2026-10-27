package dev.bidu.core.nlu

import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZonedDateTime
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/** A time found in a token list; [indices] are the tokens it consumed. */
internal data class FoundTime(
    val dateTime: ZonedDateTime,
    val kind: TimeKind,
    val indices: Set<Int>,
    val hasClock: Boolean,
    val hasDay: Boolean,
    val hasPart: Boolean,
) {
    val isRelative: Boolean get() = kind == TimeKind.RELATIVE
}

internal data class FoundDuration(val duration: Duration, val indices: Set<Int>)

/**
 * Offline time expressions in Hinglish, English and Devanagari Hindi, resolved against `now` in its zone:
 * "kal subah 9 baje", "aaj raat 9 baje", "shaam 6:30", "parso", "10 minute baad", "2 ghante mein",
 * "in 15 minutes", "9 pm", "21:00", "Monday ko", "कल सुबह सात बजे", "saadhe nau baje".
 *
 * Resolution rules: a part of day (subah/dopahar/shaam/raat) disambiguates 12-hour clocks; a bare "9 baje"
 * becomes the next future 9 o'clock; a time already past today moves to the next day (weekdays to next week);
 * a day without a clock defaults to 9:00 and a part of day to its typical hour.
 */
class HinglishTimeParser : TimeExpressionParser {

    override fun parse(text: String, now: ZonedDateTime): TimeMatch? {
        val tokens = Normalizer.tokenize(text)
        val found = find(tokens, now) ?: return null
        return TimeMatch(
            dateTime = found.dateTime,
            kind = found.kind,
            matchedText = found.indices.sorted().joinToString(" ") { tokens[it].surface },
            remainder = tokens.indices.filterNot { it in found.indices }.joinToString(" ") { tokens[it].surface },
        )
    }

    internal fun find(tokens: List<Token>, now: ZonedDateTime): FoundTime? {
        val c = tokens.map { it.canon }
        return relative(c, now) ?: absolute(c, now)
    }

    /** A plain duration such as "5 minute", "1 ghanta 30 minute", "aadha ghanta", "half an hour". */
    internal fun findDuration(tokens: List<Token>): FoundDuration? {
        val c = tokens.map { it.canon }
        for (i in c.indices) {
            val span = durationAt(c, i) ?: continue
            return FoundDuration(span.duration, (span.start until span.end).toSet())
        }
        return null
    }

    // ---- relative ("10 minute baad", "in 2 hours") ----

    private data class DurationSpan(val duration: Duration, val start: Int, val end: Int)

    private fun relative(c: List<String>, now: ZonedDateTime): FoundTime? {
        var i = 0
        while (i < c.size) {
            val span = durationAt(c, i)
            if (span == null) {
                i++
                continue
            }
            val markerBefore = c.getOrNull(i - 1) in setOf("in", "after", "within")
            val markerAfter = markerLength(c, span.end)
            if (markerBefore || markerAfter > 0) {
                val indices = (span.start until span.end + markerAfter).toMutableSet()
                if (markerBefore) indices += i - 1
                return FoundTime(
                    dateTime = now.plus(span.duration).withNano(0),
                    kind = TimeKind.RELATIVE,
                    indices = indices,
                    hasClock = true,
                    hasDay = false,
                    hasPart = false,
                )
            }
            i = span.end
        }
        return null
    }

    private fun markerLength(c: List<String>, j: Int): Int = when {
        c.getOrNull(j) in setOf("baad", "bad", "mein", "me", "mai", "main", "later", "andar") -> 1
        c.getOrNull(j) == "ke" && c.getOrNull(j + 1) in setOf("baad", "andar") -> 2
        c.getOrNull(j) == "from" && c.getOrNull(j + 1) == "now" -> 2
        else -> 0
    }

    private fun durationAt(c: List<String>, start: Int): DurationSpan? {
        var i = start
        var total = Duration.ZERO
        var any = false
        while (true) {
            val number = numberForUnit(c, i) ?: break
            var j = i + number.length
            if (c.getOrNull(j) in setOf("a", "an")) j++
            val unit = unitSeconds(c.getOrNull(j)) ?: break
            total += Duration.ofMillis((number.value * unit * 1000).roundToLong())
            any = true
            i = j + 1
            if (c.getOrNull(i) in setOf("aur", "and") && numberForUnit(c, i + 1) != null) i++
        }
        return if (any && !total.isZero) DurationSpan(total, start, i) else null
    }

    private fun numberForUnit(c: List<String>, i: Int): Numbers.Read? =
        if (c.getOrNull(i) in setOf("a", "an")) Numbers.Read(1.0, 1) else Numbers.read(c, i)

    private fun unitSeconds(word: String?): Long? = when (word) {
        "second" -> 1
        "minute" -> 60
        "ghanta", "hour" -> 3_600
        "din", "day" -> 86_400
        else -> null
    }

    // ---- absolute ("kal subah 9 baje", "9 pm", "Monday ko") ----

    private enum class Part(val defaultHour: Int) { MORNING(9), AFTERNOON(14), EVENING(18), NIGHT(21) }
    private enum class Meridiem { AM, PM }

    private data class DayHit(val offset: Int, val weekday: Boolean, val indices: Set<Int>)
    private data class PartHit(val part: Part, val index: Int, val indices: Set<Int>)
    private data class ClockHit(val hour: Int, val minute: Int, val meridiem: Meridiem?, val explicit24: Boolean, val indices: Set<Int>)

    private val parts = mapOf(
        "subah" to Part.MORNING, "morning" to Part.MORNING,
        "dopahar" to Part.AFTERNOON, "afternoon" to Part.AFTERNOON,
        "shaam" to Part.EVENING, "evening" to Part.EVENING,
        "raat" to Part.NIGHT, "night" to Part.NIGHT, "tonight" to Part.NIGHT,
    )
    private val weekdays = mapOf(
        "monday" to DayOfWeek.MONDAY, "tuesday" to DayOfWeek.TUESDAY, "wednesday" to DayOfWeek.WEDNESDAY,
        "thursday" to DayOfWeek.THURSDAY, "friday" to DayOfWeek.FRIDAY, "saturday" to DayOfWeek.SATURDAY,
        "sunday" to DayOfWeek.SUNDAY,
    )
    private val colonClock = Regex("""(\d{1,2}):(\d{2})""")
    private val twoDigits = Regex("""\d{2}""")

    private fun absolute(c: List<String>, now: ZonedDateTime): FoundTime? {
        val day = findDay(c, now)
        val part = findPart(c, day?.indices.orEmpty())
        val clock = findClock(c, day?.indices.orEmpty() + part?.indices.orEmpty(), part, day)
        if (day == null && part == null && clock == null) return null
        // "aaj" alone is not a usable time.
        if (clock == null && part == null && day?.offset == 0 && !day.weekday) return null

        val today = now.toLocalDate()
        val date = today.plusDays((day?.offset ?: 0).toLong())
        val candidates: List<LocalDateTime> = when {
            clock != null -> clockCandidates(clock, part?.part, day, date, today)
            part != null -> listOf(date.atTime(part.part.defaultHour, 0))
            else -> listOf(date.atTime(9, 0))
        }
        val nowLocal = now.toLocalDateTime()
        var chosen = candidates.firstOrNull { it.isAfter(nowLocal) } ?: candidates.first()
        val step = if (day?.weekday == true) 7L else 1L
        while (!chosen.isAfter(nowLocal)) chosen = chosen.plusDays(step)

        return FoundTime(
            dateTime = chosen.atZone(now.zone),
            kind = TimeKind.ABSOLUTE,
            indices = day?.indices.orEmpty() + part?.indices.orEmpty() + clock?.indices.orEmpty(),
            hasClock = clock != null,
            hasDay = day != null,
            hasPart = part != null,
        )
    }

    private fun clockCandidates(clock: ClockHit, part: Part?, day: DayHit?, date: LocalDate, today: LocalDate): List<LocalDateTime> {
        val h = clock.hour
        val m = clock.minute
        val explicitFutureDay = day != null && (day.offset > 0 || day.weekday)
        return when {
            clock.explicit24 || h >= 13 -> listOf(date.atTime(h % 24, m))
            clock.meridiem == Meridiem.AM -> listOf(date.atTime(h % 12, m))
            clock.meridiem == Meridiem.PM -> listOf(date.atTime(h % 12 + 12, m))
            part == Part.NIGHT && (h == 12 || h in 1..4) ->
                // "raat 12 baje" / "raat 2 baje" is after midnight.
                if (day != null) listOf(date.plusDays(1).atTime(h % 12, m))
                else listOf(today.atTime(h % 12, m), today.plusDays(1).atTime(h % 12, m))
            part != null -> listOf(date.atTime(to24(part, h), m))
            explicitFutureDay -> listOf(date.atTime(dayHeuristic(h), m))
            h == 12 -> listOf(date.atTime(12, m), date.plusDays(1).atTime(0, m))
            h == 0 -> listOf(date.atTime(0, m))
            else -> listOf(date.atTime(h, m), date.atTime(h + 12, m))
        }
    }

    private fun to24(part: Part, h: Int): Int = when (part) {
        Part.MORNING -> if (h == 12) 0 else h
        Part.AFTERNOON -> if (h in 1..6) h + 12 else h
        Part.EVENING -> if (h in 1..11) h + 12 else h
        Part.NIGHT -> if (h in 5..11) h + 12 else h % 12
    }

    /** "kal 9 baje" -> 9 AM, "kal 5 baje" -> 5 PM: what people usually mean when they name a future day. */
    private fun dayHeuristic(h: Int): Int = when (h) {
        in 7..11 -> h
        12 -> 12
        else -> h + 12
    }

    private fun findDay(c: List<String>, now: ZonedDateTime): DayHit? {
        for (i in c.indices) {
            when (val w = c[i]) {
                "aaj", "today", "tonight" -> return DayHit(0, false, setOf(i))
                "kal", "tomorrow" -> return DayHit(1, false, setOf(i))
                "parso" -> return DayHit(2, false, setOf(i))
                "narso" -> return DayHit(3, false, setOf(i))
                "day" -> if (c.getOrNull(i + 1) == "after" && c.getOrNull(i + 2) == "tomorrow") {
                    val start = if (c.getOrNull(i - 1) == "the") i - 1 else i
                    return DayHit(2, false, (start..i + 2).toSet())
                }
                else -> {
                    val target = weekdays[w] ?: continue
                    val indices = mutableSetOf(i)
                    var next = false
                    c.getOrNull(i - 1)?.let { prev ->
                        if (prev in setOf("next", "agle", "agla", "coming", "this", "is", "on")) indices += i - 1
                        if (prev in setOf("next", "agle", "agla")) next = true
                    }
                    if (c.getOrNull(i + 1) == "ko") indices += i + 1
                    var offset = (target.value - now.dayOfWeek.value + 7) % 7
                    if (offset == 0 && next) offset = 7
                    return DayHit(offset, true, indices)
                }
            }
        }
        return null
    }

    private fun findPart(c: List<String>, taken: Set<Int>): PartHit? {
        for (i in c.indices) {
            if (i in taken && c[i] != "tonight") continue
            val part = parts[c[i]] ?: continue
            val indices = mutableSetOf(i)
            if (c.getOrNull(i + 1) in setOf("ko", "ke", "mein", "me")) indices += i + 1
            if (c.getOrNull(i - 1) == "the" && c.getOrNull(i - 2) == "in") indices += setOf(i - 1, i - 2)
            if (c.getOrNull(i - 1) == "at" && c[i] == "night") indices += i - 1
            return PartHit(part, i, indices)
        }
        return null
    }

    private fun findClock(c: List<String>, taken: Set<Int>, part: PartHit?, day: DayHit?): ClockHit? {
        for (i in c.indices) {
            if (i in taken) continue
            val w = c[i]
            colonClock.matchEntire(w)?.let { m ->
                val h = m.groupValues[1].toInt()
                val min = m.groupValues[2].toInt()
                if (h > 23 || min > 59) return@let
                val indices = mutableSetOf(i)
                if (c.getOrNull(i - 1) == "at") indices += i - 1
                val meridiem = meridiemAt(c, i + 1)
                if (meridiem != null || c.getOrNull(i + 1) == "baje") indices += i + 1
                return ClockHit(h, min, meridiem, explicit24 = h >= 13 || h == 0 || w.startsWith("0"), indices = indices)
            }
            if (w == "noon") return ClockHit(12, 0, null, explicit24 = true, indices = setOf(i))
            if (w == "midnight") return ClockHit(0, 0, null, explicit24 = true, indices = setOf(i))

            val number = Numbers.read(c, i) ?: continue
            if (number.value < 0 || number.value > 24) continue
            val j = i + number.length
            val next = c.getOrNull(j)
            if (next in Lexicon.UNITS || next in Lexicon.COUNTED_NOUNS && next != "baje") continue
            val hour = floor(number.value).toInt()
            var minute = ((number.value - hour) * 60).roundToInt()
            val whole = number.value == floor(number.value)

            if (next == "baje" || next == "bajkar" || next == "oclock" || (next == "o" && c.getOrNull(j + 1) == "clock")) {
                var end = if (next == "o") j + 2 else j + 1
                val k = if (c.getOrNull(end) == "ke") end + 1 else end
                val extra = Numbers.read(c, k)
                if (extra != null && c.getOrNull(k + extra.length) == "minute" && extra.value in 0.0..59.0) {
                    minute = extra.value.toInt()
                    end = k + extra.length + 1
                }
                val meridiem = meridiemAt(c, end)
                val indices = (i until end).toMutableSet()
                if (meridiem != null) indices += end
                if (c.getOrNull(i - 1) == "at") indices += i - 1
                return ClockHit(hour, minute, meridiem, explicit24 = false, indices = indices)
            }
            val meridiem = meridiemAt(c, j)
            if (meridiem != null && hour in 1..12) {
                val indices = (i..j).toMutableSet()
                if (c.getOrNull(i - 1) == "at") indices += i - 1
                return ClockHit(hour, minute, meridiem, explicit24 = false, indices = indices)
            }
            if (!whole) continue
            val prev = c.getOrNull(i - 1)
            val markedBefore = prev in setOf("at", "for", "by") || (part != null && part.indices.max() == i - 1) ||
                (day != null && day.indices.max() == i - 1)
            if (next != null && twoDigits.matches(next) && hour in 0..23) {
                val min = next.toInt()
                val after = c.getOrNull(j + 1)
                val meridiemAfter = meridiemAt(c, j + 1)
                if (min in 0..59 && after !in Lexicon.UNITS && (markedBefore || meridiemAfter != null || after == "baje")) {
                    val indices = (i..j).toMutableSet()
                    if (prev == "at") indices += i - 1
                    if (meridiemAfter != null || after == "baje") indices += j + 1
                    return ClockHit(hour, min, meridiemAfter, explicit24 = false, indices = indices)
                }
            }
            if (prev == "at" && hour in 0..23) return ClockHit(hour, 0, null, explicit24 = false, indices = setOf(i - 1, i))
            if (part != null && part.indices.max() == i - 1 && hour in 1..12) return ClockHit(hour, 0, null, explicit24 = false, indices = setOf(i))
        }
        return null
    }

    private fun meridiemAt(c: List<String>, i: Int): Meridiem? = when (c.getOrNull(i)) {
        "am" -> Meridiem.AM
        "pm" -> Meridiem.PM
        else -> null
    }
}

/** Reads spoken or written numbers: "9", "nau", "saadhe nau" (9.5), "dedh" (1.5), "twenty five". */
internal object Numbers {
    data class Read(val value: Double, val length: Int)

    private val digits = Regex("""\d{1,3}(\.\d+)?""")

    fun read(c: List<String>, i: Int): Read? {
        val w = c.getOrNull(i) ?: return null
        Lexicon.modifier(w)?.let { delta ->
            val base = plain(c, i + 1) ?: return null
            return Read(base.value + delta, base.length + 1)
        }
        return plain(c, i)
    }

    /** Whole number at [i] without modifiers, for counts ("do tickets"). */
    fun count(c: List<String>, i: Int): Int? {
        val read = plain(c, i) ?: return null
        return if (read.value == floor(read.value) && read.value >= 1) read.value.toInt() else null
    }

    private fun plain(c: List<String>, i: Int): Read? {
        val w = c.getOrNull(i) ?: return null
        if (digits.matches(w)) return Read(w.toDouble(), 1)
        val value = Lexicon.number(w) ?: return null
        if (Lexicon.isEnglishTens(w)) {
            val unit = c.getOrNull(i + 1)?.takeIf(Lexicon::isEnglishUnit)?.let(Lexicon::number)
            if (unit != null) return Read(value + unit, 2)
        }
        return Read(value, 1)
    }
}
