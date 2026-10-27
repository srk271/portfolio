package dev.bidu.core.nlu

import dev.bidu.core.memory.MemoryKeys
import dev.bidu.core.model.Language
import dev.bidu.core.model.QuestionKind
import dev.bidu.core.model.Script
import dev.bidu.core.model.UserQuestion
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import java.time.Duration
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class HinglishNluTest {
    private val zone = ZoneId.of("Asia/Kolkata")

    // Tuesday 27 Oct 2026, 10:00 IST.
    private val now = ZonedDateTime.of(2026, 10, 27, 10, 0, 0, 0, zone)
    private val nlu = HinglishNlu()

    private fun parse(text: String, pending: UserQuestion? = null) =
        nlu.parse(text, NluContext(now = now, pendingQuestion = pending, wakePhrases = listOf("hey bidu", "bhai")))

    private fun at(day: Int, hour: Int, minute: Int = 0) = ZonedDateTime.of(2026, 10, day, hour, minute, 0, 0, zone)

    private fun case(text: String, expected: Intent) = DynamicTest.dynamicTest(text) {
        val result = parse(text)
        assertEquals(expected, result.intent, "for '$text' (normalized '${result.normalizedText}')")
        assertTrue(result.confidence >= 0.5, "confidence ${result.confidence} for '$text'")
    }

    @TestFactory
    fun `apps time and device`() = listOf(
        case("bhai whatsapp khol", Intent.OpenApp("WhatsApp")),
        case("open youtube", Intent.OpenApp("YouTube")),
        case("व्हाट्सएप खोलो", Intent.OpenApp("WhatsApp")),
        case("hey bidu instagram kholo yaar", Intent.OpenApp("Instagram")),
        case("uber khol", Intent.OpenApp("Uber")),
        case("time kya hua", Intent.TellTime),
        case("kitne baje hain", Intent.TellTime),
        case("समय क्या हुआ", Intent.TellTime),
        case("what time is it", Intent.TellTime),
        case("aaj date kya hai", Intent.TellDate),
        case("aaj kaunsa din hai", Intent.TellDate),
        case("torch on kar", Intent.Flashlight(SwitchAction.ON)),
        case("flashlight band kar do", Intent.Flashlight(SwitchAction.OFF)),
        case("टॉर्च जलाओ", Intent.Flashlight(SwitchAction.ON)),
        case("battery kitni hai", Intent.BatteryLevel),
        case("charge kitna bacha hai", Intent.BatteryLevel),
        case("bidu so ja", Intent.StopListening),
        case("stop listening", Intent.StopListening),
        case("ruk ja", Intent.Cancel),
        case("rehne do", Intent.Cancel),
        case("help", Intent.Help),
        case("tum kya kar sakte ho", Intent.Help),
    )

    @TestFactory
    fun `reminders notes alarms timers`() = listOf(
        case("kal subah 9 baje yaad dila dena doodh lena hai", Intent.CreateReminder("doodh lena hai", at(28, 9))),
        case("10 minute baad yaad dilana chai bani hai", Intent.CreateReminder("chai bani hai", at(27, 10, 10))),
        case("remind me to call mom at 6 pm", Intent.CreateReminder("call mom", at(27, 18))),
        case("कल सुबह सात बजे याद दिलाना दूध लेना है", Intent.CreateReminder("दूध लेना है", at(28, 7))),
        case("parso shaam 6:30 baje yaad dila meeting hai", Intent.CreateReminder("meeting hai", at(29, 18, 30))),
        case("mere reminders batao", Intent.ListReminders),
        case("doodh wala reminder hata do", Intent.CancelReminder("doodh")),
        case("note likh: kal gym jaana hai", Intent.CreateNote("kal gym jaana hai")),
        case("नोट लिखो दूध लेना है", Intent.CreateNote("दूध लेना है")),
        case("note kar lo ki paper submit kar dena hai", Intent.CreateNote("paper submit kar dena hai")),
        case("mere notes dikhao", Intent.SearchNotes(null)),
        case("gym wala note padh", Intent.SearchNotes("gym")),
        case("gym wala note delete kar", Intent.DeleteNote("gym")),
        case("subah 6 baje alarm laga", Intent.SetAlarm(LocalTime.of(6, 0))),
        case("set alarm for 7:30 am", Intent.SetAlarm(LocalTime.of(7, 30))),
        case("5 minute ka timer laga", Intent.SetTimer(Duration.ofMinutes(5))),
        case("set a timer for 10 minutes", Intent.SetTimer(Duration.ofMinutes(10))),
        case("५ मिनट का टाइमर लगाओ", Intent.SetTimer(Duration.ofMinutes(5))),
        case("dedh ghante ka timer laga", Intent.SetTimer(Duration.ofMinutes(90))),
    )

    @TestFactory
    fun `contacts search cabs movies`() = listOf(
        case("mummy ko call kar", Intent.CallContact("mummy")),
        case("मम्मी को फोन लगाओ", Intent.CallContact("mummy")),
        case("bhai ko call kar", Intent.CallContact("bhai")),
        case("call rahul sharma", Intent.CallContact("rahul sharma")),
        case("rahul ka number batao", Intent.FindContact("rahul")),
        case("what's rahul's number", Intent.FindContact("rahul")),
        case("google kar mumbai weather", Intent.WebSearch("mumbai weather")),
        case("search kar best biryani near me", Intent.WebSearch("best biryani near me")),
        case("ghar jaane ke liye cab book kar", Intent.BookCab("ghar", CabProvider.ANY)),
        case("office ke liye uber bula", Intent.BookCab("office", CabProvider.UBER)),
        case("rapido book kar airport tak", Intent.BookCab("airport", CabProvider.RAPIDO)),
        case("book a cab to the airport", Intent.BookCab("airport", CabProvider.ANY)),
        case(
            "aaj raat 9 baje INOX Marine Lines mein do tickets book kar",
            Intent.MovieTickets(query = null, venue = "INOX Marine Lines", showTime = at(27, 21), seats = 2),
        ),
        case(
            "dhurandhar ki 2 tickets book kar PVR Juhu mein kal shaam 7 baje",
            Intent.MovieTickets(query = "dhurandhar", venue = "PVR Juhu", showTime = at(28, 19), seats = 2),
        ),
        case("bookmyshow pe dhurandhar dekh", Intent.MovieTickets(query = "dhurandhar")),
    )

    @TestFactory
    fun `memory`() = listOf(
        case("yaad rakh mera ghar Andheri mein hai", Intent.Remember(MemoryKeys.HOME_ADDRESS, "Andheri")),
        case("mera naam Sarthak hai", Intent.Remember(MemoryKeys.USER_NAME, "Sarthak")),
        case("mujhe biryani pasand hai", Intent.Remember("likes", "biryani")),
        case("remember my office address is 5 BKC", Intent.Remember(MemoryKeys.WORK_ADDRESS, "5 BKC")),
        case("mummy matlab Sunita Sharma", Intent.Remember(MemoryKeys.contactAlias("mummy"), "Sunita Sharma")),
        case("mujhe kya pasand hai", Intent.Recall("likes")),
        case("mera ghar kahan hai", Intent.Recall(MemoryKeys.HOME_ADDRESS)),
        case("what's my name", Intent.Recall(MemoryKeys.USER_NAME)),
        case("ghar ka address bhool ja", Intent.Forget(MemoryKeys.HOME_ADDRESS)),
        case("forget my office address", Intent.Forget(MemoryKeys.WORK_ADDRESS)),
    )

    @Test
    fun `unsupported or unclear requests stay unknown with low confidence`() {
        for (text in listOf("paytm se 500 rupaye bhej", "rahul ko whatsapp pe message bhej", "asdf qwerty zxcv", "train ticket book kar")) {
            val result = parse(text)
            assertInstanceOf(Intent.Unknown::class.java, result.intent, text)
            assertTrue(result.confidence < 0.5, "confidence ${result.confidence} for '$text'")
        }
    }

    @Test
    fun `incomplete reminder is unknown so the planner can ask`() {
        val result = parse("yaad dila dena doodh lena hai")
        assertInstanceOf(Intent.Unknown::class.java, result.intent)
        assertEquals(MissingSlot.REMINDER_TIME, Cues.missing(result.normalizedText))
    }

    @Test
    fun `answers to a pending question`() {
        val q = UserQuestion("Delete karun?", QuestionKind.CHOICE, listOf("haan", "nahi"))
        assertEquals(Intent.Confirm(true), parse("haan kar do", q).intent)
        assertEquals(Intent.Confirm(true), parse("theek hai", q).intent)
        assertEquals(Intent.Confirm(false), parse("nahi", q).intent)
        assertEquals(Intent.Confirm(false), parse("mat karo", q).intent)
        assertEquals(Intent.Cancel, parse("cancel", q).intent)
        assertEquals(Intent.Confirm(true), parse("हां", q).intent)
    }

    @Test
    fun `normalization strips wake phrase fillers and punctuation`() {
        val result = parse("Hey Bidu, WhatsApp kholo please!")
        assertEquals("whatsapp khol", result.normalizedText)
        assertEquals(Language.HINGLISH, result.language)
        assertEquals(Script.LATIN, result.script)
        assertEquals(Language.ENGLISH, parse("open youtube").language)
        val hindi = parse("व्हाट्सएप खोलो")
        assertEquals(Language.HINDI, hindi.language)
        assertEquals(Script.DEVANAGARI, hindi.script)
    }

    @Test
    fun `spelling variants map to the same intent`() {
        for (text in listOf("torch on krdo", "torch on kro", "torch on karo", "torch on kar de")) {
            assertEquals(Intent.Flashlight(SwitchAction.ON), parse(text).intent, text)
        }
        for (text in listOf("rahul ka number btao", "rahul ka number bata", "rahul ka number batao")) {
            assertEquals(Intent.FindContact("rahul"), parse(text).intent, text)
        }
    }

    @Test
    fun `same input gives the same result`() {
        assertEquals(parse("kal subah 9 baje yaad dila dena doodh lena hai"), parse("kal subah 9 baje yaad dila dena doodh lena hai"))
    }
}
