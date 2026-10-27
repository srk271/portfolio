package dev.bidu.core.plan

import dev.bidu.core.memory.MemoryKeys
import dev.bidu.core.model.Language
import dev.bidu.core.model.QuestionKind
import dev.bidu.core.model.RiskLevel
import dev.bidu.core.model.UserQuestion
import dev.bidu.core.model.say
import dev.bidu.core.nlu.CabProvider
import dev.bidu.core.nlu.Cues
import dev.bidu.core.nlu.Intent
import dev.bidu.core.nlu.MissingSlot
import dev.bidu.core.nlu.SwitchAction
import dev.bidu.core.tool.BuiltinTools
import dev.bidu.core.tool.ToolArgs
import dev.bidu.core.tool.ToolCall
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Deterministic planner for every offline intent. Emits calls only against tools present in
 * [PlanRequest.tools]; incomplete requests become a [Plan.Clarify], things Bidu must not or cannot do
 * become a [Plan.Decline] that says what the user can do instead.
 */
class RulePlanner : Planner {

    override suspend fun plan(request: PlanRequest): Plan {
        val lang = request.context.language
        val draft = draft(request, lang)
        if (draft !is Plan.Execute) return draft
        val specs = request.tools.associateBy { it.name }
        if (draft.calls.any { it.tool !in specs }) {
            return Plan.Decline(
                DeclineReason.UNSUPPORTED,
                lang.say("Ye kaam abhi is phone pe mere paas nahi hai.", "I can't do that on this phone yet.", "यह काम अभी इस फ़ोन पर मैं नहीं कर सकता।"),
                PlanSource.RULES,
            )
        }
        val risky = draft.calls.any { (specs.getValue(it.tool).risk) > RiskLevel.LOW }
        if (request.nlu.confidence < LOW_CONFIDENCE && risky) {
            return clarify(lang.say("Pakka samajh nahi aaya. Thoda clear bolo?", "I'm not sure I got that. Could you say it again?", "ठीक से समझ नहीं आया। फिर से बोलिए?"))
        }
        return draft
    }

    private fun draft(request: PlanRequest, lang: Language): Plan {
        val now = request.context.now
        val memory = request.context.memory.associate { it.key to it.value }
        val confidence = request.nlu.confidence
        fun run(goal: String, vararg calls: ToolCall) = Plan.Execute(goal, calls.toList(), PlanSource.RULES, confidence)

        return when (val intent = request.nlu.intent) {
            is Intent.OpenApp -> run("Open ${intent.appName}", call(1, BuiltinTools.OPEN_APP_NAME, "Open ${intent.appName}", "app" to intent.appName))
            Intent.TellTime -> run("Tell the time", call(1, BuiltinTools.TELL_TIME_NAME, "Read the clock"))
            Intent.TellDate -> run("Tell the date", call(1, BuiltinTools.TELL_DATE_NAME, "Read the date"))
            Intent.BatteryLevel -> run("Battery level", call(1, BuiltinTools.BATTERY_LEVEL_NAME, "Read the battery"))
            is Intent.CreateReminder -> {
                if (!intent.dueAt.isAfter(now)) {
                    return clarify(lang.say("Ye time nikal chuka hai. Kab yaad dilaun?", "That time has already passed. When should I remind you?", "यह समय निकल चुका है। कब याद दिलाऊँ?"))
                }
                run(
                    "Reminder: ${intent.text}",
                    call(1, BuiltinTools.CREATE_REMINDER_NAME, "Save reminder", "text" to intent.text, "at" to intent.dueAt.toInstant().toEpochMilli()),
                )
            }
            Intent.ListReminders -> run("List reminders", call(1, BuiltinTools.LIST_REMINDERS_NAME, "Read upcoming reminders", "limit" to 10))
            is Intent.CancelReminder -> run(
                "Cancel reminder",
                call(1, BuiltinTools.CANCEL_REMINDER_NAME, "Cancel reminder '${intent.query}'", *idOrQuery(intent.query)),
            )
            is Intent.CreateNote -> run("Save note", call(1, BuiltinTools.CREATE_NOTE_NAME, "Save note", "text" to intent.text, "title" to intent.title))
            is Intent.SearchNotes -> run(
                "Find notes",
                call(1, BuiltinTools.SEARCH_NOTES_NAME, "Search notes", "query" to intent.query, "limit" to 10),
            )
            is Intent.DeleteNote -> run("Delete note", call(1, BuiltinTools.DELETE_NOTE_NAME, "Delete note '${intent.query}'", *idOrQuery(intent.query)))
            is Intent.FindContact -> run("Find ${intent.name}", call(1, BuiltinTools.FIND_CONTACT_NAME, "Look up ${intent.name}", "name" to intent.name))
            is Intent.CallContact -> {
                val digits = intent.name.filter { it.isDigit() || it == '+' }
                val args = if (digits.count(Char::isDigit) >= 6 && intent.name.none(Char::isLetter)) arrayOf("number" to digits) else arrayOf("name" to intent.name)
                run("Call ${intent.name}", call(1, BuiltinTools.DIAL_CONTACT_NAME, "Open the dialer for ${intent.name}", *args))
            }
            is Intent.SetAlarm -> run(
                "Alarm at ${intent.time}",
                call(1, BuiltinTools.SET_ALARM_NAME, "Set alarm ${intent.time}", "hour" to intent.time.hour, "minute" to intent.time.minute, "label" to intent.label),
            )
            is Intent.SetTimer -> {
                val seconds = intent.duration.seconds
                if (seconds !in 1..86_400) {
                    return clarify(lang.say("Timer kitni der ka? 24 ghante tak ho sakta hai.", "How long should the timer be? Up to 24 hours.", "टाइमर कितनी देर का? 24 घंटे तक हो सकता है।"))
                }
                run("Timer ${seconds}s", call(1, BuiltinTools.SET_TIMER_NAME, "Start timer", "seconds" to seconds, "label" to intent.label))
            }
            is Intent.Flashlight -> run(
                "Torch ${intent.action.name.lowercase(Locale.ROOT)}",
                call(1, BuiltinTools.FLASHLIGHT_NAME, "Switch the torch", "state" to intent.action.arg()),
            )
            is Intent.WebSearch -> run("Search ${intent.query}", call(1, BuiltinTools.WEB_SEARCH_NAME, "Web search", "query" to intent.query))
            is Intent.BookCab -> cabPlan(intent, memory, confidence)
            is Intent.MovieTickets -> moviePlan(intent, memory, confidence)
            is Intent.Remember -> {
                val value = if (intent.key == LIKES_KEY) mergeList(memory[LIKES_KEY], intent.value) else intent.value
                run("Remember ${intent.key}", call(1, BuiltinTools.REMEMBER_NAME, "Save ${intent.key}", "key" to intent.key, "value" to value))
            }
            is Intent.Recall -> run("Recall ${intent.key}", call(1, BuiltinTools.RECALL_NAME, "Read ${intent.key}", "key" to intent.key))
            is Intent.Forget -> run("Forget ${intent.key}", call(1, BuiltinTools.FORGET_NAME, "Forget ${intent.key}", "key" to intent.key))
            Intent.StopListening -> run("Stop listening", call(1, BuiltinTools.STOP_LISTENING_NAME, "Stop background listening"))
            is Intent.Confirm -> Plan.Reply(
                lang.say("Abhi kuch confirm karne ko pending nahi hai.", "There's nothing waiting for a yes or no right now.", "अभी कुछ पक्का करने को बाकी नहीं है।"),
                PlanSource.RULES,
            )
            Intent.Cancel -> Plan.Reply(lang.say("Theek hai, rehne dete hain.", "Okay, never mind.", "ठीक है, रहने देते हैं।"), PlanSource.RULES)
            Intent.Help -> Plan.Reply(helpText(lang), PlanSource.RULES)
            is Intent.Unknown -> unknown(request, lang)
        }
    }

    private fun cabPlan(intent: Intent.BookCab, memory: Map<String, String>, confidence: Double): Plan {
        val provider = intent.provider.takeIf { it != CabProvider.ANY }?.name?.lowercase(Locale.ROOT)
        val spoken = intent.destination?.trim()
        val key = when (spoken?.lowercase(Locale.ROOT)) {
            null, "" -> null
            in HOME_WORDS -> MemoryKeys.HOME_ADDRESS
            in WORK_WORDS -> MemoryKeys.WORK_ADDRESS
            else -> MemoryKeys.place(spoken).takeIf { it in memory }
        }
        val book = { destination: String? ->
            ToolCall(
                id = "c${if (key == null) 1 else 2}",
                tool = BuiltinTools.BOOK_CAB_NAME,
                args = args("destination" to destination, "provider" to provider),
                purpose = "Open a cab app" + (destination?.let { " to $it" } ?: ""),
            )
        }
        if (key == null) return Plan.Execute("Cab", listOf(book(spoken)), PlanSource.RULES, confidence)
        // Two steps: confirm the saved place exists, then hand the resolved address to the cab app.
        val saved = memory[key]
        return Plan.Execute(
            "Cab to $spoken",
            listOf(call(1, BuiltinTools.RECALL_NAME, "Look up saved address for $spoken", "key" to key), book(saved ?: spoken)),
            PlanSource.RULES,
            confidence,
        )
    }

    private fun moviePlan(intent: Intent.MovieTickets, memory: Map<String, String>, confidence: Double): Plan {
        val city = intent.city ?: memory[MemoryKeys.CITY]
        val tickets = { n: Int ->
            ToolCall(
                id = "c$n",
                tool = BuiltinTools.MOVIE_TICKETS_NAME,
                args = args("query" to intent.query, "city" to city),
                purpose = "Open the ticket app" + (intent.query?.let { " for $it" } ?: ""),
            )
        }
        if (intent.venue == null && intent.showTime == null) return Plan.Execute("Movie tickets", listOf(tickets(1)), PlanSource.RULES, confidence)
        val searchText = listOfNotNull(
            intent.query ?: "movie",
            "showtimes",
            intent.venue,
            city?.takeIf { c -> intent.venue?.contains(c, ignoreCase = true) != true },
            intent.showTime?.format(SHOW_FORMAT),
        ).joinToString(" ")
        return Plan.Execute(
            "Movie tickets",
            listOf(call(1, BuiltinTools.WEB_SEARCH_NAME, "Search showtimes", "query" to searchText), tickets(2)),
            PlanSource.RULES,
            confidence,
        )
    }

    private fun unknown(request: PlanRequest, lang: Language): Plan {
        val q = { hinglish: String, english: String, hindi: String -> clarify(lang.say(hinglish, english, hindi)) }
        val decline = { hinglish: String, english: String, hindi: String ->
            Plan.Decline(DeclineReason.UNSUPPORTED, lang.say(hinglish, english, hindi), PlanSource.RULES)
        }
        return when (Cues.missing(request.nlu.normalizedText)) {
            MissingSlot.REMINDER_TIME -> q("Kab yaad dilaun? Jaise 'kal subah 9 baje' ya '10 minute baad'.", "When should I remind you? For example 'tomorrow 9 am' or 'in 10 minutes'.", "कब याद दिलाऊँ? जैसे 'कल सुबह 9 बजे' या '10 मिनट बाद'।")
            MissingSlot.REMINDER_WHICH -> q("Kaunsa reminder hataun? Uska kaam bolo, jaise 'doodh wala'.", "Which reminder should I cancel? Say what it's about.", "कौन सा रिमाइंडर हटाऊँ? उसका काम बोलिए।")
            MissingSlot.ALARM_TIME -> q("Alarm kitne baje ka lagaun?", "What time should the alarm be?", "अलार्म कितने बजे का लगाऊँ?")
            MissingSlot.TIMER_DURATION -> q("Timer kitni der ka? Jaise '5 minute'.", "How long should the timer be? For example '5 minutes'.", "टाइमर कितनी देर का? जैसे '5 मिनट'।")
            MissingSlot.CALLEE -> q("Kisko call karun?", "Who should I call?", "किसको फ़ोन लगाऊँ?")
            MissingSlot.CONTACT_NAME -> q("Kiska number chahiye?", "Whose number do you need?", "किसका नंबर चाहिए?")
            MissingSlot.NOTE_TEXT -> q("Note mein kya likhun?", "What should the note say?", "नोट में क्या लिखूँ?")
            MissingSlot.NOTE_WHICH -> q("Kaunsa note delete karun? Uska koi shabd bolo.", "Which note should I delete? Say a word from it.", "कौन सा नोट हटाऊँ? उसका कोई शब्द बोलिए।")
            MissingSlot.SEARCH_QUERY -> q("Kya search karun?", "What should I search for?", "क्या खोजूँ?")
            MissingSlot.APP_NAME -> q("Kaunsa app kholun?", "Which app should I open?", "कौन सा ऐप खोलूँ?")
            MissingSlot.MEMORY_FACT -> q("Kya yaad rakhun? Jaise 'mera ghar Andheri mein hai'.", "What should I remember? For example 'my home is in Andheri'.", "क्या याद रखूँ? जैसे 'मेरा घर अंधेरी में है'।")
            MissingSlot.UNSUPPORTED_PAYMENT -> decline(
                "Payment, recharge ya order main khud nahi karta. App main khol sakta hoon, paisa tum wahan confirm karna.",
                "I don't make payments, recharges or orders myself. I can open the app; you confirm any payment there.",
                "पेमेंट, रिचार्ज या ऑर्डर मैं खुद नहीं करता। ऐप खोल सकता हूँ, पैसे आप वहीं पक्का कीजिए।",
            )
            MissingSlot.UNSUPPORTED_MESSAGING -> decline(
                "Message bhejna abhi mere bas mein nahi hai. 'WhatsApp khol' bolo, message tum bhej dena.",
                "I can't send messages yet. Say 'open WhatsApp' and send it yourself.",
                "मैसेज भेजना अभी मैं नहीं कर सकता। 'व्हाट्सएप खोलो' बोलिए, मैसेज आप भेजिए।",
            )
            MissingSlot.UNSUPPORTED_TRAVEL -> decline(
                "Train ya flight ticket main book nahi karta. App khol sakta hoon, booking wahan karna.",
                "I don't book train or flight tickets. I can open the app; book it there.",
                "ट्रेन या फ़्लाइट टिकट मैं बुक नहीं करता। ऐप खोल सकता हूँ, बुकिंग वहीं कीजिए।",
            )
            MissingSlot.UNSUPPORTED_ALARM_CHANGE -> decline(
                "Alarm hatana ya band karna Clock app mein hi hoga. 'Clock khol' bolo.",
                "Alarms can only be changed in the Clock app. Say 'open clock'.",
                "अलार्म बदलना क्लॉक ऐप में ही होगा। 'घड़ी खोलो' बोलिए।",
            )
            MissingSlot.UNSUPPORTED_TIMER_CHANGE -> decline(
                "Timer rokna Clock app mein hi hoga. 'Clock khol' bolo.",
                "Timers can only be stopped in the Clock app. Say 'open clock'.",
                "टाइमर रोकना क्लॉक ऐप में ही होगा। 'घड़ी खोलो' बोलिए।",
            )
            null -> Plan.Decline(DeclineReason.NOT_UNDERSTOOD, notUnderstood(lang), PlanSource.RULES)
        }
    }

    private fun clarify(text: String) = Plan.Clarify(UserQuestion(text, QuestionKind.FREE_TEXT), PlanSource.RULES)

    private fun call(n: Int, tool: String, purpose: String, vararg pairs: Pair<String, Any?>) =
        ToolCall(id = "c$n", tool = tool, args = args(*pairs), purpose = purpose)

    private fun args(vararg pairs: Pair<String, Any?>) = ToolArgs(pairs.toMap())

    private fun idOrQuery(query: String): Array<Pair<String, Any?>> =
        query.trim().toLongOrNull()?.takeIf { it > 0 }?.let { arrayOf<Pair<String, Any?>>("id" to it) } ?: arrayOf("query" to query)

    private fun SwitchAction.arg(): String = when (this) {
        SwitchAction.ON -> "on"
        SwitchAction.OFF -> "off"
        SwitchAction.TOGGLE -> "toggle"
    }

    private fun mergeList(existing: String?, added: String): String {
        val items = existing?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()
        return if (items.any { it.equals(added.trim(), ignoreCase = true) }) items.joinToString(", ") else (items + added.trim()).joinToString(", ")
    }

    companion object {
        internal const val LOW_CONFIDENCE = 0.5
        internal const val LIKES_KEY = "likes"
        private val HOME_WORDS = setOf("ghar", "home", "house", "घर", "mera ghar", "my home")
        private val WORK_WORDS = setOf("office", "work", "daftar", "ऑफिस", "दफ्तर")
        private val SHOW_FORMAT = DateTimeFormatter.ofPattern("d MMM h:mm a", Locale.ENGLISH)

        internal fun notUnderstood(lang: Language): String = lang.say(
            "Samajh nahi aaya. Thoda alag tareeke se bolo, ya 'help' bolo.",
            "Sorry, I didn't understand. Try saying it differently, or say 'help'.",
            "समझ नहीं आया। थोड़ा अलग तरह से बोलिए, या 'मदद' बोलिए।",
        )

        internal fun helpText(lang: Language): String = lang.say(
            "Main app khol sakta hoon, reminder, alarm, timer laga sakta hoon, notes likh sakta hoon, call ke liye number laga sakta hoon, " +
                "torch, battery, time bata sakta hoon, cab ya movie app khol sakta hoon. Jaise: 'kal subah 9 baje yaad dila dena'.",
            "I can open apps, set reminders, alarms and timers, take notes, open the dialer for a contact, switch the torch, " +
                "tell the time or battery, and open cab or movie apps. Try: 'remind me tomorrow at 9 am'.",
            "मैं ऐप खोल सकता हूँ, रिमाइंडर, अलार्म, टाइमर लगा सकता हूँ, नोट लिख सकता हूँ, फ़ोन लगाने के लिए नंबर डाल सकता हूँ, " +
                "टॉर्च, बैटरी, समय बता सकता हूँ, कैब या मूवी ऐप खोल सकता हूँ। जैसे: 'कल सुबह 9 बजे याद दिला देना'।",
        )
    }
}
