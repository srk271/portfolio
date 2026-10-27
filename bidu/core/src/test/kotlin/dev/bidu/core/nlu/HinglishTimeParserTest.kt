package dev.bidu.core.nlu

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import java.time.ZoneId
import java.time.ZonedDateTime

class HinglishTimeParserTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val parser = HinglishTimeParser()

    // Tuesday 27 Oct 2026.
    private fun now(hour: Int, minute: Int = 0) = ZonedDateTime.of(2026, 10, 27, hour, minute, 0, 0, zone)
    private fun at(day: Int, hour: Int, minute: Int = 0) = ZonedDateTime.of(2026, 10, day, hour, minute, 0, 0, zone)

    private fun case(text: String, expected: ZonedDateTime, kind: TimeKind = TimeKind.ABSOLUTE, nowAt: ZonedDateTime = now(10)) =
        DynamicTest.dynamicTest("$text @ ${nowAt.toLocalTime()}") {
            val match = parser.parse(text, nowAt)
            assertNotNull(match, text)
            match!!
            assertEquals(expected, match.dateTime, text)
            assertEquals(kind, match.kind, text)
        }

    @TestFactory
    fun `expressions resolve against a fixed clock`() = listOf(
        case("kal subah 9 baje", at(28, 9)),
        case("aaj raat 9 baje", at(27, 21)),
        case("shaam 6:30", at(27, 18, 30)),
        case("aaj shaam 6:30 baje", at(27, 18, 30)),
        case("parso", at(29, 9)),
        case("10 minute baad", at(27, 10, 10), TimeKind.RELATIVE),
        case("2 ghante mein", at(27, 12), TimeKind.RELATIVE),
        case("in 15 minutes", at(27, 10, 15), TimeKind.RELATIVE),
        case("aadhe ghante baad", at(27, 10, 30), TimeKind.RELATIVE),
        case("9 pm", at(27, 21)),
        case("9:15 am", at(28, 9, 15)),
        case("21:00", at(27, 21)),
        case("Monday ko", ZonedDateTime.of(2026, 11, 2, 9, 0, 0, 0, zone)),
        case("next tuesday", ZonedDateTime.of(2026, 11, 3, 9, 0, 0, 0, zone)),
        case("कल सुबह सात बजे", at(28, 7)),
        case("आज शाम 6 बजे", at(27, 18)),
        case("saadhe nau baje raat", at(27, 21, 30)),
        case("raat 12 baje", at(28, 0)),
        case("dopahar 2 baje", at(27, 14)),
        case("tomorrow at 7", at(28, 7)),
    )

    @TestFactory
    fun `bare clock picks the next future occurrence`() = listOf(
        case("9 baje", at(27, 21), nowAt = now(10)),
        case("8 baje", at(27, 20), nowAt = now(10)),
        case("11 baje", at(27, 11), nowAt = now(10)),
        case("9 baje", at(28, 9), nowAt = now(22)),
        case("subah 7 baje", at(28, 7), nowAt = now(10)),
        case("9 pm", at(28, 21), nowAt = now(21, 30)),
    )

    @Test
    fun `remainder keeps the rest of the sentence`() {
        val match = parser.parse("kal subah 9 baje doodh lena hai", now(10))!!
        assertEquals("doodh lena hai", match.remainder)
        assertEquals("kal subah 9 baje", match.matchedText)
    }

    @Test
    fun `no time or a bare today gives null`() {
        assertNull(parser.parse("whatsapp khol", now(10)))
        assertNull(parser.parse("aaj", now(10)))
        assertNull(parser.parse("do tickets book kar", now(10)))
    }

    @Test
    fun `result keeps the zone of now`() {
        val utc = ZonedDateTime.of(2026, 10, 27, 10, 0, 0, 0, ZoneId.of("UTC"))
        val match = parser.parse("kal subah 9 baje", utc)!!
        assertEquals(ZonedDateTime.of(2026, 10, 28, 9, 0, 0, 0, ZoneId.of("UTC")), match.dateTime)
    }
}
