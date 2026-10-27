package dev.bidu.core.nlu

import java.time.Duration
import java.time.LocalTime
import java.time.ZonedDateTime

enum class SwitchAction { ON, OFF, TOGGLE }

enum class CabProvider { UBER, OLA, RAPIDO, ANY }

/**
 * What the user wants. One intent per utterance; the planner turns it into tool calls.
 * Times are already resolved against the clock in [NluContext.now] so tools only see absolutes.
 */
sealed interface Intent {
    data class OpenApp(val appName: String) : Intent
    data object TellTime : Intent
    data object TellDate : Intent
    data class CreateReminder(val text: String, val dueAt: ZonedDateTime) : Intent
    data object ListReminders : Intent
    data class CancelReminder(val query: String) : Intent
    data class CreateNote(val text: String, val title: String? = null) : Intent
    data class SearchNotes(val query: String?) : Intent
    data class DeleteNote(val query: String) : Intent
    data class FindContact(val name: String) : Intent
    data class CallContact(val name: String) : Intent
    data class SetAlarm(val time: LocalTime, val label: String? = null) : Intent
    data class SetTimer(val duration: Duration, val label: String? = null) : Intent
    data class Flashlight(val action: SwitchAction) : Intent
    data class WebSearch(val query: String) : Intent
    data object BatteryLevel : Intent
    data class BookCab(val destination: String?, val provider: CabProvider = CabProvider.ANY) : Intent
    data class MovieTickets(
        val query: String?,
        val city: String? = null,
        /** Cinema as spoken ("INOX Marine Lines"). Informational: the user picks the show in the booking app. */
        val venue: String? = null,
        /** Requested show time, resolved like reminder times. Informational, never booked by Bidu. */
        val showTime: ZonedDateTime? = null,
        /** Requested number of seats. Informational, never booked by Bidu. */
        val seats: Int? = null,
    ) : Intent
    data class Remember(val key: String, val value: String) : Intent
    data class Recall(val key: String) : Intent
    /** "ghar ka address bhool ja": delete a memory fact. [key] is a MemoryKeys key or the spoken subject. */
    data class Forget(val key: String) : Intent
    data object StopListening : Intent
    /** "haan" / "nahi" while a confirmation or question is pending. */
    data class Confirm(val approved: Boolean) : Intent
    /** "cancel" / "rehne do" / "ruk ja". */
    data object Cancel : Intent
    data object Help : Intent
    /** Could not be understood offline; the planner may hand this to the cloud adapter when allowed. */
    data class Unknown(val text: String) : Intent
}
