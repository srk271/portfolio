package dev.bidu.core.agent

import dev.bidu.core.memory.MemoryKeys
import dev.bidu.core.model.Language
import dev.bidu.core.model.QuestionKind
import dev.bidu.core.model.ResponseStyle
import dev.bidu.core.model.say
import dev.bidu.core.nlu.Intent
import dev.bidu.core.plan.Plan
import dev.bidu.core.tool.BuiltinTools
import dev.bidu.core.tool.ToolErrorCode
import dev.bidu.core.tool.ToolExecution
import dev.bidu.core.tool.ToolOutcome
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * Short spoken replies in Hinglish (default), English or Hindi. Success words ("Done", "ho gaya") are used
 * only for VERIFIED results; hand-offs say what was opened and what the user still has to do there; failures
 * say why and what to try next. Facts come from tool data only: no fares, ETAs or guesses.
 */
class HinglishResponseComposer : ResponseComposer {

    override fun compose(input: ComposerInput, style: ResponseStyle): ComposedResponse {
        val spoken = spoken(input, style).trim()
        val details = input.executions.joinToString("\n") { e ->
            "• ${e.call.tool}: ${e.result.outcome.name.lowercase(Locale.ROOT)} — ${e.result.summary}"
        }
        return ComposedResponse(spoken, if (details.isEmpty()) spoken else "$spoken\n$details")
    }

    private fun spoken(input: ComposerInput, style: ResponseStyle): String {
        val lang = style.language
        when (val plan = input.plan) {
            is Plan.Decline -> return plan.explanation
            is Plan.Reply -> return plan.text
            is Plan.Clarify -> return plan.question.text
            else -> Unit
        }
        if (input.missingCapabilities.isNotEmpty() && input.executions.isEmpty()) {
            return input.question?.text ?: permissionText(lang, input.missingCapabilities.joinToString(", ") { it.userLabel })
        }
        if (input.outcome == ToolOutcome.CANCELLED) return cancelled(input, lang)
        if (input.executions.isEmpty()) {
            return lang.say("Kaam nahi ho paya. Ek baar phir bolo.", "That didn't get done. Please try again.", "काम नहीं हो पाया। एक बार फिर बोलिए।")
        }
        return when (input.outcome) {
            ToolOutcome.FAILED -> failed(input, lang)
            ToolOutcome.NEEDS_USER -> needsUser(input, lang)
            ToolOutcome.HANDED_OFF -> handedOff(input, lang)
            ToolOutcome.VERIFIED -> verified(input, style)
            ToolOutcome.CANCELLED -> cancelled(input, lang)
        }
    }

    private fun permissionText(lang: Language, labels: String) = lang.say(
        "Pehle ye permission chahiye: $labels. Bidu app mein Setup kholo.",
        "I need this permission first: $labels. Open Setup in the Bidu app.",
        "पहले यह अनुमति चाहिए: $labels। बिडू ऐप में सेटअप खोलिए।",
    )

    private fun cancelled(input: ComposerInput, lang: Language): String {
        val reason = input.executions.lastOrNull()?.result?.data?.get(DefaultAgentEngine.REASON)
        return when (reason) {
            DefaultAgentEngine.DECLINED -> lang.say("Theek hai, nahi kiya.", "Okay, I didn't do it.", "ठीक है, नहीं किया।")
            DefaultAgentEngine.UNANSWERED -> lang.say(
                "Jawab nahi mila, isliye kuch nahi kiya. Chahiye to phir se bolo.",
                "No answer, so I didn't do anything. Ask again if you want it.",
                "जवाब नहीं मिला, इसलिए कुछ नहीं किया। चाहिए तो फिर से बोलिए।",
            )
            else -> lang.say("Theek hai, rok diya.", "Okay, stopped.", "ठीक है, रोक दिया।")
        }
    }

    // ---- VERIFIED ----

    private fun verified(input: ComposerInput, style: ResponseStyle): String {
        val lang = style.language
        val e = input.executions.last()
        val d = e.result.data
        val done = when (lang) {
            Language.HINGLISH -> if (style.casual) "Done bhai," else "Ho gaya,"
            Language.ENGLISH -> "Done,"
            Language.HINDI -> "हो गया,"
        }
        return when (e.call.tool) {
            BuiltinTools.CREATE_REMINDER_NAME -> {
                val due = dueTime(e, input)
                val whenText = due?.let { whenPhrase(it, input.now, lang) }
                val base = if (whenText != null) {
                    lang.say("$done $whenText yaad dila dunga.", "$done I'll remind you $whenText.", "$done $whenText याद दिला दूँगा।")
                } else {
                    lang.say("$done reminder lag gaya.", "$done the reminder is set.", "$done रिमाइंडर लग गया।")
                }
                if (d["exact"] == "false") {
                    base + " " + lang.say(
                        "Exact alarm ki permission nahi hai, to thoda late bhi ho sakta hai.",
                        "Exact alarms aren't allowed, so it may be a few minutes late.",
                        "सटीक अलार्म की अनुमति नहीं है, तो थोड़ी देर हो सकती है।",
                    )
                } else {
                    base
                }
            }
            BuiltinTools.LIST_REMINDERS_NAME -> {
                val count = d["count"]?.toIntOrNull() ?: 0
                if (count == 0) {
                    lang.say("Koi upcoming reminder nahi hai.", "You have no upcoming reminders.", "कोई आने वाला रिमाइंडर नहीं है।")
                } else {
                    val items = list(d["list"])
                    lang.say("$count reminder hain: $items.", "You have $count reminders: $items.", "$count रिमाइंडर हैं: $items।")
                }
            }
            BuiltinTools.CANCEL_REMINDER_NAME -> lang.say(
                "Reminder cancel kar diya${d["text"]?.let { ": $it" }.orEmpty()}.",
                "Cancelled the reminder${d["text"]?.let { ": $it" }.orEmpty()}.",
                "रिमाइंडर रद्द कर दिया${d["text"]?.let { ": $it" }.orEmpty()}।",
            )
            BuiltinTools.CREATE_NOTE_NAME -> lang.say("$done note save ho gaya.", "$done note saved.", "$done नोट सेव हो गया।")
            BuiltinTools.SEARCH_NOTES_NAME -> {
                val count = d["count"]?.toIntOrNull() ?: 0
                val first = d["first_body"]
                when {
                    count == 0 -> lang.say("Aisa koi note nahi mila.", "No matching notes.", "ऐसा कोई नोट नहीं मिला।")
                    first != null -> lang.say("$count note mile. Pehla: $first", "Found $count notes. First: $first", "$count नोट मिले। पहला: $first")
                    else -> lang.say("$count note mile.", "Found $count notes.", "$count नोट मिले।")
                }
            }
            BuiltinTools.DELETE_NOTE_NAME -> lang.say("Note delete kar diya.", "Deleted the note.", "नोट हटा दिया।")
            BuiltinTools.FIND_CONTACT_NAME -> {
                val name = d["name"] ?: e.call.args.string("name").orEmpty()
                val number = d["number"]
                if (number != null) {
                    lang.say("$name ka number $number hai.", "$name's number is $number.", "$name का नंबर $number है।")
                } else {
                    lang.say("$name contacts mein hai, par number save nahi hai.", "$name is in your contacts but has no number saved.", "$name संपर्कों में है, पर नंबर सेव नहीं है।")
                }
            }
            BuiltinTools.REMEMBER_NAME -> {
                val key = d["key"] ?: e.call.args.string("key").orEmpty()
                val value = d["value"] ?: e.call.args.string("value").orEmpty()
                lang.say("Yaad rakh liya: ${keyLabel(key, lang)} — $value.", "Got it, I'll remember: ${keyLabel(key, lang)} — $value.", "याद रख लिया: ${keyLabel(key, lang)} — $value।")
            }
            BuiltinTools.RECALL_NAME -> recalled(d["key"] ?: e.call.args.string("key").orEmpty(), d["value"].orEmpty(), lang)
            BuiltinTools.FORGET_NAME -> {
                val key = d["key"] ?: e.call.args.string("key").orEmpty()
                lang.say("Bhool gaya: ${keyLabel(key, lang)}.", "Forgotten: ${keyLabel(key, lang)}.", "भूल गया: ${keyLabel(key, lang)}।")
            }
            BuiltinTools.TELL_TIME_NAME -> {
                val h = d["hour"]?.toIntOrNull()
                val m = d["minute"]?.toIntOrNull()
                if (h != null && m != null) {
                    val clock = clockText(h, m)
                    lang.say("Abhi ${partOfDay(h, lang)} ke $clock baje hain.", "It's ${englishClock(h, m)}.", "अभी ${partOfDay(h, lang)} के $clock बजे हैं।")
                } else {
                    d["time_12h"] ?: e.result.summary
                }
            }
            BuiltinTools.TELL_DATE_NAME -> {
                val day = d["weekday"]
                val date = d["date_display"]
                if (day != null && date != null) {
                    lang.say("Aaj $day, $date hai.", "Today is $day, $date.", "आज $day, $date है।")
                } else {
                    e.result.summary
                }
            }
            BuiltinTools.BATTERY_LEVEL_NAME -> {
                val percent = d["percent"]
                val charging = d["charging"] == "true"
                if (percent == null) {
                    e.result.summary
                } else {
                    val tail = if (charging) lang.say(", charge ho rahi hai", ", and charging", ", चार्ज हो रही है") else ""
                    lang.say("Battery $percent% hai$tail.", "Battery is at $percent%$tail.", "बैटरी $percent% है$tail।")
                }
            }
            BuiltinTools.FLASHLIGHT_NAME -> if (d["state"] == "off") {
                lang.say("Torch band kar di.", "Torch is off.", "टॉर्च बंद कर दी।")
            } else {
                lang.say("Torch on kar di.", "Torch is on.", "टॉर्च जला दी।")
            }
            BuiltinTools.STOP_LISTENING_NAME -> lang.say(
                "Theek hai, ab background mein nahi sun raha. Wapas chalu karna ho to app kholo.",
                "Okay, I've stopped listening in the background. Open the app to turn it back on.",
                "ठीक है, अब पीछे से नहीं सुन रहा। फिर से चालू करना हो तो ऐप खोलिए।",
            )
            else -> lang.say("$done ${e.result.summary}", "$done ${e.result.summary}", "$done ${e.result.summary}")
        }
    }

    // ---- HANDED_OFF ----

    private fun handedOff(input: ComposerInput, lang: Language): String {
        val handoffs = input.executions.filter { it.result.outcome == ToolOutcome.HANDED_OFF }
        val e = handoffs.last()
        val d = e.result.data
        val app = d["app"] ?: d["provider"] ?: d["label"]
        return when (e.call.tool) {
            BuiltinTools.OPEN_APP_NAME -> {
                val name = app ?: e.call.args.string("app").orEmpty()
                lang.say("$name khol diya.", "Opened $name.", "$name खोल दिया।")
            }
            BuiltinTools.DIAL_CONTACT_NAME -> {
                val who = d["name"] ?: e.call.args.string("name") ?: d["number"].orEmpty()
                lang.say(
                    "$who ka number dialer mein daal diya — call ka button tum dabana.",
                    "Opened the dialer with $who's number — tap call to ring.",
                    "$who का नंबर डायलर में डाल दिया — कॉल का बटन आप दबाइए।",
                )
            }
            BuiltinTools.SET_ALARM_NAME -> {
                val time = d["time_12h"] ?: alarmTime(e)
                lang.say(
                    "$time ka alarm Clock app ko bhej diya — wahan ek baar check kar lena.",
                    "Sent a $time alarm to the Clock app — check it there.",
                    "$time का अलार्म क्लॉक ऐप को भेज दिया — वहाँ एक बार देख लीजिए।",
                )
            }
            BuiltinTools.SET_TIMER_NAME -> {
                val length = d["duration_display"] ?: "${e.call.args.long("seconds") ?: 0}s"
                lang.say("$length ka timer Clock app ko de diya.", "Started a $length timer in the Clock app.", "$length का टाइमर क्लॉक ऐप को दे दिया।")
            }
            BuiltinTools.WEB_SEARCH_NAME -> {
                val q = d["query"] ?: e.call.args.string("query").orEmpty()
                lang.say("'$q' ka search khol diya.", "Opened a search for '$q'.", "'$q' की खोज खोल दी।")
            }
            BuiltinTools.BOOK_CAB_NAME -> cab(e, lang)
            BuiltinTools.MOVIE_TICKETS_NAME -> movie(input, e, handoffs.size > 1, lang)
            else -> {
                val target = app ?: d["target_package"] ?: e.call.tool
                lang.say("$target ko kaam de diya — aage wahan confirm karna.", "Handed it to $target — finish it there.", "$target को काम दे दिया — आगे वहीं पक्का कीजिए।")
            }
        }
    }

    private fun cab(e: ToolExecution, lang: Language): String {
        val d = e.result.data
        val provider = d["provider"] ?: "Cab app"
        val destination = d["destination"] ?: e.call.args.string("destination")
        return when {
            d["installed"] == "false" && d["destination_prefilled"] != "true" -> lang.say(
                "$provider install nahi hai, uska page khol diya.",
                "$provider isn't installed, so I opened its page.",
                "$provider इंस्टॉल नहीं है, उसका पेज खोल दिया।",
            )
            d["destination_prefilled"] == "true" -> lang.say(
                "$provider khol diya, destination set hai — ride wahan confirm karna.",
                "Opened $provider with the destination set — confirm the ride there.",
                "$provider खोल दिया, मंज़िल सेट है — राइड वहीं पक्की कीजिए।",
            )
            destination != null -> lang.say(
                "$provider khol diya — destination '$destination' wahan daal ke ride confirm karna.",
                "Opened $provider — enter '$destination' as the destination and confirm the ride there.",
                "$provider खोल दिया — मंज़िल '$destination' वहाँ डालकर राइड पक्की कीजिए।",
            )
            else -> lang.say(
                "$provider khol diya — destination daal ke ride wahan confirm karna.",
                "Opened $provider — set the destination and confirm the ride there.",
                "$provider खोल दिया — मंज़िल डालकर राइड वहीं पक्की कीजिए।",
            )
        }
    }

    private fun movie(input: ComposerInput, e: ToolExecution, searchedFirst: Boolean, lang: Language): String {
        val d = e.result.data
        val intent = input.nlu?.intent as? Intent.MovieTickets
        if (d["web_query"] != null) {
            return lang.say(
                "BookMyShow ya District nahi mila, showtimes ka search khol diya — booking aur payment wahan tum karna.",
                "BookMyShow and District aren't installed, so I opened a showtimes search — book and pay there yourself.",
                "बुकमायशो या डिस्ट्रिक्ट नहीं मिला, शो टाइम की खोज खोल दी — बुकिंग और पेमेंट वहीं कीजिए।",
            )
        }
        val app = d["app"] ?: "ticket app"
        val wants = buildList {
            intent?.seats?.let { add(lang.say("$it seats", "$it seats", "$it सीटें")) }
            intent?.showTime?.let { add(whenPhrase(it, input.now, lang) + lang.say(" ka show", " show", " का शो")) }
            intent?.venue?.let { add(it) }
        }.joinToString(", ")
        val opened = if (searchedFirst) lang.say("Showtimes ka search aur $app khol diya", "Opened a showtimes search and $app", "शो टाइम की खोज और $app खोल दिया")
        else lang.say("$app khol diya", "Opened $app", "$app खोल दिया")
        return if (wants.isNotEmpty()) {
            lang.say(
                "$opened — $wants wahan chun ke payment tum confirm karna.",
                "$opened — pick $wants there and confirm the payment yourself.",
                "$opened — $wants वहाँ चुनकर पेमेंट आप पक्का कीजिए।",
            )
        } else {
            lang.say(
                "$opened — show, seats aur payment wahan confirm karna.",
                "$opened — choose the show and seats and pay there.",
                "$opened — शो, सीटें और पेमेंट वहीं पक्का कीजिए।",
            )
        }
    }

    // ---- NEEDS_USER and FAILED ----

    private fun needsUser(input: ComposerInput, lang: Language): String {
        val question = input.question ?: input.executions.lastOrNull()?.result?.question
            ?: return lang.say("Aage badhne ke liye tumhari madad chahiye.", "I need your input to continue.", "आगे बढ़ने के लिए आपकी मदद चाहिए।")
        if (question.kind == QuestionKind.PERMISSION && question.capabilities.isNotEmpty()) {
            return permissionText(lang, question.capabilities.joinToString(", ") { it.userLabel })
        }
        if (question.kind == QuestionKind.CHOICE && question.options.isNotEmpty()) {
            val options = question.options.take(4).joinToString(", ")
            return lang.say("${question.text} $options?", "${question.text} $options?", "${question.text} $options?")
        }
        return question.text
    }

    private fun failed(input: ComposerInput, lang: Language): String {
        val index = input.executions.indexOfFirst { it.result.outcome == ToolOutcome.FAILED }
        val e = input.executions.getOrNull(index) ?: input.executions.last()
        val error = e.result.error
        val next = input.executions.getOrNull(index + 1)?.call ?: (input.plan as? Plan.Execute)?.calls?.getOrNull(index + 1)
        val thing = noun(e.call.tool, lang)
        val done = input.executions.take(index.coerceAtLeast(0)).count { it.result.outcome == ToolOutcome.VERIFIED || it.result.outcome == ToolOutcome.HANDED_OFF }
        val prefix = if (done > 0) lang.say("Pehla kadam ho gaya, par ", "The first step worked, but ", "पहला कदम हो गया, पर ") else ""

        if (e.call.tool == BuiltinTools.RECALL_NAME && error?.code == ToolErrorCode.NOT_FOUND) {
            val key = e.call.args.string("key").orEmpty()
            if (next?.tool == BuiltinTools.BOOK_CAB_NAME) {
                val place = keyLabel(key, lang)
                return lang.say(
                    "$place ka address save nahi hai, isliye cab nahi kholi. Bolo: 'yaad rakh mera ghar Andheri mein hai'.",
                    "I don't have your $place address saved, so I didn't open the cab app. Say: 'remember my home is in Andheri'.",
                    "$place का पता सेव नहीं है, इसलिए कैब नहीं खोली। बोलिए: 'याद रख मेरा घर अंधेरी में है'।",
                )
            }
            return lang.say("Ye mujhe yaad nahi hai. Bata do to yaad rakh lunga.", "I don't have that saved. Tell me and I'll remember it.", "यह मुझे याद नहीं है। बता दीजिए तो याद रख लूँगा।")
        }
        val why = when (error?.code) {
            ToolErrorCode.NOT_FOUND -> lang.say("$thing nahi mila.", "I couldn't find that $thing.", "$thing नहीं मिला।") + " " +
                lang.say("Naam ya shabd thoda alag bolke dekho.", "Try different words.", "थोड़ा अलग शब्दों में बोलिए।")
            ToolErrorCode.APP_NOT_INSTALLED -> lang.say(
                "Ye app is phone pe nahi mila. Naam check karke phir bolo.",
                "That app isn't on this phone. Check the name and try again.",
                "यह ऐप इस फ़ोन पर नहीं मिला। नाम देखकर फिर बोलिए।",
            )
            ToolErrorCode.PERMISSION_DENIED -> lang.say(
                "$thing safety rule ki wajah se roka gaya.",
                "The $thing was blocked by a safety rule.",
                "$thing सुरक्षा नियम की वजह से रोका गया।",
            )
            ToolErrorCode.TIMEOUT -> lang.say("$thing ne time pe jawab nahi diya. Ek baar phir try karo.", "The $thing didn't respond in time. Try once more.", "$thing ने समय पर जवाब नहीं दिया। एक बार फिर कोशिश कीजिए।")
            ToolErrorCode.NETWORK -> lang.say("Internet nahi chal raha. Thodi der baad try karo.", "No internet right now. Try again in a bit.", "इंटरनेट नहीं चल रहा। थोड़ी देर बाद कोशिश कीजिए।")
            ToolErrorCode.UNAVAILABLE -> lang.say(
                "$thing abhi available nahi hai (${error.message}).",
                "The $thing isn't available right now (${error.message}).",
                "$thing अभी उपलब्ध नहीं है (${error.message})।",
            )
            ToolErrorCode.INVALID_ARGS -> lang.say(
                "$thing nahi ho paya: ${error.message}. Thoda detail ke saath phir bolo.",
                "Couldn't do the $thing: ${error.message}. Try again with more detail.",
                "$thing नहीं हो पाया: ${error.message}। थोड़ा विस्तार से फिर बोलिए।",
            )
            ToolErrorCode.AMBIGUOUS -> lang.say("Ek se zyada match mile. Thoda clear bolo.", "Several things matched. Be a bit more specific.", "एक से ज़्यादा मिले। थोड़ा साफ़ बोलिए।")
            else -> lang.say(
                "$thing nahi ho paya, kuch gadbad hui. Ek baar phir try karo.",
                "The $thing didn't work; something went wrong. Please try again.",
                "$thing नहीं हो पाया, कुछ गड़बड़ हुई। एक बार फिर कोशिश कीजिए।",
            )
        }
        return prefix + why
    }

    // ---- wording helpers ----

    private fun noun(tool: String, lang: Language): String = when (tool) {
        BuiltinTools.OPEN_APP_NAME -> lang.say("App", "app", "ऐप")
        BuiltinTools.CREATE_REMINDER_NAME, BuiltinTools.LIST_REMINDERS_NAME, BuiltinTools.CANCEL_REMINDER_NAME -> lang.say("Reminder", "reminder", "रिमाइंडर")
        BuiltinTools.CREATE_NOTE_NAME, BuiltinTools.SEARCH_NOTES_NAME, BuiltinTools.DELETE_NOTE_NAME -> lang.say("Note", "note", "नोट")
        BuiltinTools.FIND_CONTACT_NAME, BuiltinTools.DIAL_CONTACT_NAME -> lang.say("Contact", "contact", "संपर्क")
        BuiltinTools.SET_ALARM_NAME -> lang.say("Alarm", "alarm", "अलार्म")
        BuiltinTools.SET_TIMER_NAME -> lang.say("Timer", "timer", "टाइमर")
        BuiltinTools.FLASHLIGHT_NAME -> lang.say("Torch", "torch", "टॉर्च")
        BuiltinTools.WEB_SEARCH_NAME -> lang.say("Search", "search", "खोज")
        BuiltinTools.BATTERY_LEVEL_NAME -> lang.say("Battery reading", "battery reading", "बैटरी")
        BuiltinTools.BOOK_CAB_NAME -> lang.say("Cab app", "cab app", "कैब ऐप")
        BuiltinTools.MOVIE_TICKETS_NAME -> lang.say("Ticket app", "ticket app", "टिकट ऐप")
        BuiltinTools.REMEMBER_NAME, BuiltinTools.RECALL_NAME, BuiltinTools.FORGET_NAME -> lang.say("Memory", "memory", "याद")
        BuiltinTools.STOP_LISTENING_NAME -> lang.say("Listening band karna", "stopping the listener", "सुनना बंद करना")
        BuiltinTools.TELL_TIME_NAME, BuiltinTools.TELL_DATE_NAME -> lang.say("Clock", "clock", "घड़ी")
        else -> tool.replace('_', ' ')
    }

    private fun keyLabel(key: String, lang: Language): String = when {
        key == MemoryKeys.HOME_ADDRESS -> lang.say("ghar", "home", "घर")
        key == MemoryKeys.WORK_ADDRESS -> lang.say("office", "office", "ऑफिस")
        key == MemoryKeys.USER_NAME -> lang.say("tumhara naam", "your name", "आपका नाम")
        key == MemoryKeys.CITY -> lang.say("shehar", "city", "शहर")
        key == MemoryKeys.PREFERRED_CAB_PROVIDER -> lang.say("pasandida cab", "preferred cab", "पसंदीदा कैब")
        key == LIKES -> lang.say("pasand", "likes", "पसंद")
        key.startsWith(MemoryKeys.CONTACT_ALIAS_PREFIX) -> key.removePrefix(MemoryKeys.CONTACT_ALIAS_PREFIX)
        key.startsWith(MemoryKeys.PLACE_PREFIX) -> key.removePrefix(MemoryKeys.PLACE_PREFIX)
        else -> key.replace('_', ' ')
    }

    private fun recalled(key: String, value: String, lang: Language): String = when (key) {
        MemoryKeys.HOME_ADDRESS -> lang.say("Tumhara ghar: $value.", "Your home: $value.", "आपका घर: $value।")
        MemoryKeys.WORK_ADDRESS -> lang.say("Tumhara office: $value.", "Your office: $value.", "आपका ऑफिस: $value।")
        MemoryKeys.USER_NAME -> lang.say("Tumhara naam $value hai.", "Your name is $value.", "आपका नाम $value है।")
        MemoryKeys.CITY -> lang.say("Tumhara shehar $value hai.", "Your city is $value.", "आपका शहर $value है।")
        LIKES -> lang.say("Tumhe $value pasand hai.", "You like $value.", "आपको $value पसंद है।")
        else -> lang.say("${keyLabel(key, lang)}: $value.", "${keyLabel(key, lang)}: $value.", "${keyLabel(key, lang)}: $value।")
    }

    private fun list(raw: String?): String =
        raw.orEmpty().lines().map { it.trim().replace(Regex("""^\d+\.\s*"""), "") }.filter { it.isNotEmpty() }.take(3).joinToString("; ")

    private fun dueTime(e: ToolExecution, input: ComposerInput): ZonedDateTime? {
        e.result.data["due"]?.let { raw ->
            try {
                return OffsetDateTime.parse(raw).atZoneSameInstant(input.now.zone)
            } catch (_: Exception) {
            }
        }
        e.call.args.long("at")?.let { return Instant.ofEpochMilli(it).atZone(input.now.zone) }
        return (input.nlu?.intent as? Intent.CreateReminder)?.dueAt
    }

    private fun alarmTime(e: ToolExecution): String {
        val h = e.call.args.int("hour") ?: return ""
        val m = e.call.args.int("minute") ?: 0
        return englishClock(h, m)
    }

    /** "kal subah 9 baje", "tomorrow at 9 am", "कल सुबह 9 बजे"; under an hour: "10 minute mein". */
    internal fun whenPhrase(at: ZonedDateTime, now: ZonedDateTime, lang: Language): String {
        val minutes = Duration.between(now, at).toMinutes()
        if (minutes in 1..59 && at.toLocalDate() == now.toLocalDate()) {
            return lang.say("$minutes minute mein", "in $minutes minutes", "$minutes मिनट में")
        }
        val days = ChronoUnit.DAYS.between(now.toLocalDate(), at.toLocalDate())
        val day = when (days) {
            0L -> lang.say("aaj", "today", "आज")
            1L -> lang.say("kal", "tomorrow", "कल")
            2L -> lang.say("parso", "the day after tomorrow", "परसों")
            in 3L..6L -> lang.say(
                at.dayOfWeek.name.lowercase(Locale.ROOT).replaceFirstChar { it.titlecase(Locale.ROOT) } + " ko",
                "on " + at.dayOfWeek.name.lowercase(Locale.ROOT).replaceFirstChar { it.titlecase(Locale.ROOT) },
                HINDI_DAYS[at.dayOfWeek.value - 1] + " को",
            )
            else -> lang.say(at.format(DAY_MONTH), "on " + at.format(DAY_MONTH), at.format(DAY_MONTH) + " को")
        }
        val clock = clockText(at.hour, at.minute)
        return when (lang) {
            Language.ENGLISH -> "$day at ${englishClock(at.hour, at.minute)}"
            Language.HINGLISH -> "$day ${partOfDay(at.hour, lang)} $clock baje"
            Language.HINDI -> "$day ${partOfDay(at.hour, lang)} $clock बजे"
        }
    }

    private fun partOfDay(hour: Int, lang: Language): String = when (hour) {
        in 4..11 -> lang.say("subah", "morning", "सुबह")
        in 12..15 -> lang.say("dopahar", "afternoon", "दोपहर")
        in 16..19 -> lang.say("shaam", "evening", "शाम")
        else -> lang.say("raat", "night", "रात")
    }

    private fun clockText(hour: Int, minute: Int): String {
        val h = if (hour % 12 == 0) 12 else hour % 12
        return if (minute == 0) "$h" else "$h:${minute.toString().padStart(2, '0')}"
    }

    private fun englishClock(hour: Int, minute: Int): String = "${clockText(hour, minute)} ${if (hour < 12) "am" else "pm"}"

    private companion object {
        const val LIKES = "likes"
        val DAY_MONTH: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMMM", Locale.ENGLISH)
        val HINDI_DAYS = listOf("सोमवार", "मंगलवार", "बुधवार", "गुरुवार", "शुक्रवार", "शनिवार", "रविवार")
    }
}
