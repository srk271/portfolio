package dev.bidu.core.model

import java.time.Instant

/** A note the user dictated or typed. Stored locally; never sent to the cloud. */
data class Note(
    val id: Long,
    val title: String?,
    val body: String,
    val createdAt: Instant,
    val updatedAt: Instant,
)

enum class ReminderStatus { SCHEDULED, FIRED, DISMISSED, CANCELLED }

/**
 * A reminder backed by an alarm. [exact] records whether an exact alarm was actually scheduled;
 * when the device denies exact alarms the scheduler falls back to an inexact window and says so.
 */
data class Reminder(
    val id: Long,
    val text: String,
    val dueAt: Instant,
    val createdAt: Instant,
    val status: ReminderStatus,
    val exact: Boolean,
)

/** Small key/value memory ("ghar" address, "mummy" means contact X). Keys are listed in [MemoryKeys]. */
data class MemoryFact(
    val key: String,
    val value: String,
    val updatedAt: Instant,
)

/** A question Bidu needs the user to answer before it can continue. */
data class UserQuestion(
    val text: String,
    val kind: QuestionKind = QuestionKind.FREE_TEXT,
    val options: List<String> = emptyList(),
    /** Capabilities the user must grant when [kind] is [QuestionKind.PERMISSION]. */
    val capabilities: Set<Capability> = emptySet(),
)

enum class QuestionKind {
    /** Pick one of [UserQuestion.options]. */
    CHOICE,
    /** Grant the listed capabilities in the app or system settings. */
    PERMISSION,
    /** The user must finish something in another app or tap a notification. */
    ACTION_REQUIRED,
    /** Any spoken or typed answer. */
    FREE_TEXT,
}
