package dev.bidu.core.tool

import dev.bidu.core.model.Capability
import dev.bidu.core.model.RiskLevel

/**
 * The canonical tool specs. The rule planner emits calls against these names and arguments; the
 * Android implementations expose exactly these specs (`override val spec = BuiltinTools.OPEN_APP`).
 * Adding a tool means adding it here first.
 */
object BuiltinTools {
    const val OPEN_APP_NAME = "open_app"
    const val TELL_TIME_NAME = "tell_time"
    const val TELL_DATE_NAME = "tell_date"
    const val CREATE_REMINDER_NAME = "create_reminder"
    const val LIST_REMINDERS_NAME = "list_reminders"
    const val CANCEL_REMINDER_NAME = "cancel_reminder"
    const val CREATE_NOTE_NAME = "create_note"
    const val SEARCH_NOTES_NAME = "search_notes"
    const val DELETE_NOTE_NAME = "delete_note"
    const val FIND_CONTACT_NAME = "find_contact"
    const val DIAL_CONTACT_NAME = "dial_contact"
    const val SET_ALARM_NAME = "set_alarm"
    const val SET_TIMER_NAME = "set_timer"
    const val FLASHLIGHT_NAME = "flashlight"
    const val WEB_SEARCH_NAME = "web_search"
    const val BATTERY_LEVEL_NAME = "battery_level"
    const val BOOK_CAB_NAME = "book_cab"
    const val MOVIE_TICKETS_NAME = "movie_tickets"
    const val REMEMBER_NAME = "remember"
    const val RECALL_NAME = "recall"
    const val STOP_LISTENING_NAME = "stop_listening"
    const val FORGET_NAME = "forget"

    val OPEN_APP = ToolSpec(
        name = OPEN_APP_NAME,
        description = "Open an installed app by spoken name. Outcome is HANDED_OFF (launch delivered) or NEEDS_USER when background launch is blocked and a tap-to-open notification was posted.",
        params = listOf(
            ParamSpec("app", ParamType.STRING, "Spoken app name, e.g. 'whatsapp'", maxLength = 64),
            ParamSpec("package", ParamType.STRING, "Exact package name when already known", required = false, maxLength = 128),
        ),
        risk = RiskLevel.LOW,
        optionalCapabilities = setOf(Capability.DISPLAY_OVER_OTHER_APPS),
        idempotent = true,
        timeoutMillis = 5_000,
        examples = listOf("bhai whatsapp khol", "open youtube", "व्हाट्सएप खोलो"),
    )

    val TELL_TIME = ToolSpec(
        name = TELL_TIME_NAME,
        description = "Current local time.",
        idempotent = true,
        timeoutMillis = 1_000,
        examples = listOf("time kya hua", "kitne baje hain", "समय क्या हुआ"),
    )

    val TELL_DATE = ToolSpec(
        name = TELL_DATE_NAME,
        description = "Today's date and weekday.",
        idempotent = true,
        timeoutMillis = 1_000,
        examples = listOf("aaj date kya hai", "aaj kaunsa din hai"),
    )

    val CREATE_REMINDER = ToolSpec(
        name = CREATE_REMINDER_NAME,
        description = "Persist a reminder and schedule an alarm for it. VERIFIED only after the row and alarm are read back.",
        params = listOf(
            ParamSpec("text", ParamType.STRING, "What to remind", maxLength = 500),
            ParamSpec("at", ParamType.TIMESTAMP_MILLIS, "Due time as epoch millis", minValue = 0),
        ),
        risk = RiskLevel.LOW,
        requiredCapabilities = setOf(Capability.POST_NOTIFICATIONS),
        optionalCapabilities = setOf(Capability.EXACT_ALARMS),
        timeoutMillis = 5_000,
        examples = listOf("kal subah 7 baje yaad dila dena doodh lena hai", "10 minute baad yaad dilana"),
    )

    val LIST_REMINDERS = ToolSpec(
        name = LIST_REMINDERS_NAME,
        description = "List upcoming reminders.",
        params = listOf(ParamSpec("limit", ParamType.INT, "Max rows", required = false, minValue = 1, maxValue = 50)),
        idempotent = true,
        timeoutMillis = 3_000,
        examples = listOf("mere reminders batao"),
    )

    val CANCEL_REMINDER = ToolSpec(
        name = CANCEL_REMINDER_NAME,
        description = "Cancel a reminder by id or by matching text. Destructive: policy asks first.",
        params = listOf(
            ParamSpec("id", ParamType.LONG, "Reminder id", required = false, minValue = 1),
            ParamSpec("query", ParamType.STRING, "Text to match when id unknown", required = false, maxLength = 200),
        ),
        risk = RiskLevel.HIGH,
        timeoutMillis = 5_000,
        examples = listOf("doodh wala reminder hata do"),
    )

    val CREATE_NOTE = ToolSpec(
        name = CREATE_NOTE_NAME,
        description = "Save a note. VERIFIED after the row is read back.",
        params = listOf(
            ParamSpec("text", ParamType.STRING, "Note body", maxLength = 4_000),
            ParamSpec("title", ParamType.STRING, "Optional title", required = false, maxLength = 120),
        ),
        timeoutMillis = 3_000,
        examples = listOf("note likh: kal gym jaana hai", "नोट लिखो दूध लेना है"),
    )

    val SEARCH_NOTES = ToolSpec(
        name = SEARCH_NOTES_NAME,
        description = "Search notes; empty query lists recent notes.",
        params = listOf(
            ParamSpec("query", ParamType.STRING, "Words to search", required = false, maxLength = 200),
            ParamSpec("limit", ParamType.INT, "Max rows", required = false, minValue = 1, maxValue = 50),
        ),
        idempotent = true,
        timeoutMillis = 3_000,
        examples = listOf("mere notes dikhao", "gym wala note padh"),
    )

    val DELETE_NOTE = ToolSpec(
        name = DELETE_NOTE_NAME,
        description = "Delete a note by id or matching text. Destructive: policy asks first.",
        params = listOf(
            ParamSpec("id", ParamType.LONG, "Note id", required = false, minValue = 1),
            ParamSpec("query", ParamType.STRING, "Text to match when id unknown", required = false, maxLength = 200),
        ),
        risk = RiskLevel.HIGH,
        timeoutMillis = 3_000,
        examples = listOf("gym wala note delete kar"),
    )

    val FIND_CONTACT = ToolSpec(
        name = FIND_CONTACT_NAME,
        description = "Search contacts by name or memory alias (mummy, papa). Returns NEEDS_USER with options when several match.",
        params = listOf(ParamSpec("name", ParamType.STRING, "Name or alias", maxLength = 100)),
        requiredCapabilities = setOf(Capability.READ_CONTACTS),
        idempotent = true,
        timeoutMillis = 5_000,
        examples = listOf("rahul ka number batao"),
    )

    val DIAL_CONTACT = ToolSpec(
        name = DIAL_CONTACT_NAME,
        description = "Open the dialer with a number filled in (ACTION_DIAL, no CALL_PHONE). The user presses call. HANDED_OFF.",
        params = listOf(
            ParamSpec("name", ParamType.STRING, "Contact name or alias", required = false, maxLength = 100),
            ParamSpec("number", ParamType.STRING, "Phone number when known", required = false, maxLength = 32),
        ),
        risk = RiskLevel.MEDIUM,
        requiredCapabilities = setOf(Capability.READ_CONTACTS),
        optionalCapabilities = setOf(Capability.DISPLAY_OVER_OTHER_APPS),
        timeoutMillis = 5_000,
        examples = listOf("mummy ko call kar", "मम्मी को फोन लगाओ"),
    )

    val SET_ALARM = ToolSpec(
        name = SET_ALARM_NAME,
        description = "Ask the Clock app to set an alarm (AlarmClock.ACTION_SET_ALARM). HANDED_OFF.",
        params = listOf(
            ParamSpec("hour", ParamType.INT, "Hour 0-23", minValue = 0, maxValue = 23),
            ParamSpec("minute", ParamType.INT, "Minute 0-59", minValue = 0, maxValue = 59),
            ParamSpec("label", ParamType.STRING, "Optional label", required = false, maxLength = 100),
        ),
        timeoutMillis = 5_000,
        examples = listOf("subah 6 baje alarm laga", "set alarm for 7 30"),
    )

    val SET_TIMER = ToolSpec(
        name = SET_TIMER_NAME,
        description = "Ask the Clock app to start a timer (AlarmClock.ACTION_SET_TIMER). HANDED_OFF.",
        params = listOf(
            ParamSpec("seconds", ParamType.INT, "Timer length in seconds", minValue = 1, maxValue = 86_400),
            ParamSpec("label", ParamType.STRING, "Optional label", required = false, maxLength = 100),
        ),
        timeoutMillis = 5_000,
        examples = listOf("5 minute ka timer laga", "timer 30 second"),
    )

    val FLASHLIGHT = ToolSpec(
        name = FLASHLIGHT_NAME,
        description = "Switch the torch. VERIFIED through CameraManager torch callback.",
        params = listOf(ParamSpec("state", ParamType.STRING, "on, off or toggle", allowedValues = setOf("on", "off", "toggle"))),
        idempotent = true,
        timeoutMillis = 4_000,
        examples = listOf("torch on kar", "flashlight band kar", "टॉर्च जलाओ"),
    )

    val WEB_SEARCH = ToolSpec(
        name = WEB_SEARCH_NAME,
        description = "Open a web search for the query in the browser. HANDED_OFF; Bidu does not read results.",
        params = listOf(ParamSpec("query", ParamType.STRING, "Search text", maxLength = 300)),
        optionalCapabilities = setOf(Capability.DISPLAY_OVER_OTHER_APPS),
        idempotent = true,
        timeoutMillis = 5_000,
        examples = listOf("google kar mumbai weather", "search kar best biryani near me"),
    )

    val BATTERY_LEVEL = ToolSpec(
        name = BATTERY_LEVEL_NAME,
        description = "Battery percentage and charging state. VERIFIED (measured).",
        idempotent = true,
        timeoutMillis = 2_000,
        examples = listOf("battery kitni hai", "charge kitna bacha hai"),
    )

    val BOOK_CAB = ToolSpec(
        name = BOOK_CAB_NAME,
        description = "Open a cab app (Uber/Ola/Rapido) with the destination pre-filled where the app supports it. HANDED_OFF; never quotes fares or ETAs; the user confirms the ride in the app.",
        params = listOf(
            ParamSpec("destination", ParamType.STRING, "Destination text; 'home'/'ghar' resolved from memory", required = false, maxLength = 300),
            ParamSpec("provider", ParamType.STRING, "uber, ola, rapido or any", required = false, allowedValues = setOf("uber", "ola", "rapido", "any")),
        ),
        risk = RiskLevel.MEDIUM,
        optionalCapabilities = setOf(Capability.DISPLAY_OVER_OTHER_APPS),
        timeoutMillis = 6_000,
        examples = listOf("ghar jaane ke liye cab book kar", "uber bula office ke liye"),
    )

    val MOVIE_TICKETS = ToolSpec(
        name = MOVIE_TICKETS_NAME,
        description = "Open BookMyShow or District at a search for the movie. HANDED_OFF; booking happens in the app.",
        params = listOf(
            ParamSpec("query", ParamType.STRING, "Movie or show name", required = false, maxLength = 200),
            ParamSpec("city", ParamType.STRING, "City when spoken", required = false, maxLength = 80),
        ),
        risk = RiskLevel.MEDIUM,
        optionalCapabilities = setOf(Capability.DISPLAY_OVER_OTHER_APPS),
        timeoutMillis = 6_000,
        examples = listOf("movie ticket book kar", "bookmyshow pe dhurandhar dekh"),
    )

    val REMEMBER = ToolSpec(
        name = REMEMBER_NAME,
        description = "Store a fact in memory (home address, contact alias). VERIFIED after read-back.",
        params = listOf(
            ParamSpec("key", ParamType.STRING, "Memory key, see MemoryKeys", maxLength = 100),
            ParamSpec("value", ParamType.STRING, "Value", maxLength = 500),
        ),
        idempotent = true,
        timeoutMillis = 3_000,
        examples = listOf("yaad rakh ghar ka address 12 MG Road hai", "mummy matlab Sunita Sharma"),
    )

    val RECALL = ToolSpec(
        name = RECALL_NAME,
        description = "Read a fact from memory.",
        params = listOf(ParamSpec("key", ParamType.STRING, "Memory key", maxLength = 100)),
        idempotent = true,
        timeoutMillis = 2_000,
        examples = listOf("ghar ka address kya hai"),
    )

    val STOP_LISTENING = ToolSpec(
        name = STOP_LISTENING_NAME,
        description = "Stop background wake-word listening (the foreground service). VERIFIED when the service reports stopped.",
        idempotent = true,
        timeoutMillis = 3_000,
        examples = listOf("bidu so ja", "stop listening", "band ho ja"),
    )

    val FORGET = ToolSpec(
        name = FORGET_NAME,
        description = "Delete a fact from memory (e.g. home address, contact alias). Destructive: policy asks first. VERIFIED after the fact is gone.",
        params = listOf(ParamSpec("key", ParamType.STRING, "Memory key to forget, see MemoryKeys", maxLength = 100)),
        risk = RiskLevel.HIGH,
        timeoutMillis = 3_000,
        examples = listOf("ghar ka address bhool ja", "forget my office address"),
    )

    val all: List<ToolSpec> = listOf(
        OPEN_APP, TELL_TIME, TELL_DATE, CREATE_REMINDER, LIST_REMINDERS, CANCEL_REMINDER,
        CREATE_NOTE, SEARCH_NOTES, DELETE_NOTE, FIND_CONTACT, DIAL_CONTACT, SET_ALARM, SET_TIMER,
        FLASHLIGHT, WEB_SEARCH, BATTERY_LEVEL, BOOK_CAB, MOVIE_TICKETS, REMEMBER, RECALL, STOP_LISTENING, FORGET,
    )

    fun byName(name: String): ToolSpec? = all.firstOrNull { it.name == name }
}
