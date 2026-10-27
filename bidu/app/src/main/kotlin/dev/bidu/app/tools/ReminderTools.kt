package dev.bidu.app.tools

import android.content.Context
import dev.bidu.app.data.ReminderRepository
import dev.bidu.app.reminders.ReminderAlarms
import dev.bidu.app.reminders.ReminderScheduler
import dev.bidu.core.model.Clock
import dev.bidu.core.model.Reminder
import dev.bidu.core.model.ReminderStatus
import dev.bidu.core.tool.BuiltinTools
import dev.bidu.core.tool.Evidence
import dev.bidu.core.tool.EvidenceKind
import dev.bidu.core.tool.Tool
import dev.bidu.core.tool.ToolArgs
import dev.bidu.core.tool.ToolContext
import dev.bidu.core.tool.ToolErrorCode
import dev.bidu.core.tool.ToolOutcome
import dev.bidu.core.tool.ToolResult
import dev.bidu.core.tool.ToolSpec
import java.time.Instant

class CreateReminderTool(
    context: Context,
    private val reminders: ReminderRepository,
    private val scheduler: ReminderScheduler,
    private val clock: Clock,
) : Tool {
    private val app = context.applicationContext
    override val spec: ToolSpec = BuiltinTools.CREATE_REMINDER

    override suspend fun execute(args: ToolArgs, context: ToolContext): ToolResult {
        val text = args.requireString("text").trim()
        if (SecretGuard.looksSecret(text)) return ToolResult.failed(ToolErrorCode.INVALID_ARGS, SecretGuard.REFUSAL)
        val dueAt = Instant.ofEpochMilli(args.requireLong("at"))
        val now = clock.now()
        if (dueAt.isBefore(now.minusSeconds(PAST_TOLERANCE_SECONDS))) {
            return ToolResult.failed(ToolErrorCode.INVALID_ARGS, "That time (${Formats.dayTime(dueAt, clock.zone)}) is already past")
        }

        val created = reminders.create(text, dueAt, scheduler.canScheduleExact())
        val alarm = try {
            scheduler.schedule(created)
        } catch (e: RuntimeException) {
            reminders.updateStatus(created.id, ReminderStatus.CANCELLED)
            return ToolResult.failed(ToolErrorCode.INTERNAL, "The alarm for the reminder could not be scheduled", retryable = false)
        }

        val stored = reminders.get(created.id)
        val armed = ReminderAlarms.isArmed(app, created.id)
        if (stored == null || stored.status != ReminderStatus.SCHEDULED || !armed) {
            return ToolResult.failed(ToolErrorCode.INTERNAL, "Reminder could not be verified after saving")
        }

        val due = Formats.dayTime(alarm.triggerAt, clock.zone)
        val data = mapOf(
            "id" to stored.id.toString(),
            "text" to stored.text,
            "due" to alarm.triggerAt.atZone(clock.zone).toOffsetDateTime().toString(),
            "due_display" to due,
            "exact" to alarm.exact.toString(),
        )
        val precision = if (alarm.exact) "exact alarm" else "inexact alarm (Android may deliver it a few minutes late; allow exact alarms for precise timing)"
        return ToolResult(
            outcome = ToolOutcome.VERIFIED,
            summary = "Reminder saved for $due with an $precision",
            data = data,
            evidence = listOf(
                Evidence(EvidenceKind.OBSERVED_STATE, "Reminder row ${stored.id} read back as SCHEDULED"),
                Evidence(EvidenceKind.OBSERVED_STATE, "Alarm PendingIntent registered (${if (alarm.exact) "exact" else "inexact"})"),
            ),
        )
    }

    private companion object {
        const val PAST_TOLERANCE_SECONDS = 60L
    }
}

class ListRemindersTool(private val reminders: ReminderRepository, private val clock: Clock) : Tool {
    override val spec: ToolSpec = BuiltinTools.LIST_REMINDERS

    override suspend fun execute(args: ToolArgs, context: ToolContext): ToolResult {
        val limit = args.int("limit") ?: DEFAULT_LIMIT
        val upcoming = reminders.upcoming(limit)
        val lines = upcoming.mapIndexed { i, r -> "${i + 1}. ${r.text.preview()} (${Formats.dayTime(r.dueAt, clock.zone)})" }
        val data = mapOf("count" to upcoming.size.toString(), "list" to lines.joinToString("\n"))
        val summary = if (upcoming.isEmpty()) "No upcoming reminders" else "${upcoming.size} upcoming reminder(s)"
        return ToolResult.observed(summary, "Read ${upcoming.size} SCHEDULED reminder rows", data)
    }

    private companion object {
        const val DEFAULT_LIMIT = 10
    }
}

class CancelReminderTool(
    context: Context,
    private val reminders: ReminderRepository,
    private val scheduler: ReminderScheduler,
    private val clock: Clock,
) : Tool {
    private val app = context.applicationContext
    override val spec: ToolSpec = BuiltinTools.CANCEL_REMINDER

    override suspend fun execute(args: ToolArgs, context: ToolContext): ToolResult {
        val id = args.long("id")
        val query = args.string("query")
        val target: Reminder = when {
            id != null -> reminders.get(id)?.takeIf { it.status == ReminderStatus.SCHEDULED }
                ?: return ToolResult.failed(ToolErrorCode.NOT_FOUND, "No active reminder with id $id")
            query != null -> {
                val matches = reminders.search(query, MAX_MATCHES).filter { it.status == ReminderStatus.SCHEDULED }
                when (matches.size) {
                    0 -> return ToolResult.failed(ToolErrorCode.NOT_FOUND, "No active reminder matches '${query.preview(40)}'")
                    1 -> matches.single()
                    else -> return needsChoice(
                        "${matches.size} reminders match '${query.preview(40)}'",
                        "Which reminder should I cancel?",
                        matches.take(5).map { "${it.text.preview(40)} (${Formats.dayTime(it.dueAt, clock.zone)})" },
                        mapOf("ids" to matches.take(5).joinToString(",") { it.id.toString() }),
                    )
                }
            }
            else -> return ToolResult.failed(ToolErrorCode.INVALID_ARGS, "Say which reminder to cancel")
        }

        scheduler.cancel(target.id)
        reminders.updateStatus(target.id, ReminderStatus.CANCELLED)

        val after = reminders.get(target.id)
        if ((after != null && after.status != ReminderStatus.CANCELLED) || ReminderAlarms.isArmed(app, target.id)) {
            return ToolResult.failed(ToolErrorCode.INTERNAL, "Reminder could not be verified as cancelled")
        }
        return ToolResult.observed(
            "Cancelled the reminder '${target.text.preview(40)}'",
            "Reminder ${target.id} read back as CANCELLED and its alarm is no longer registered",
            mapOf("id" to target.id.toString(), "text" to target.text, "due_display" to Formats.dayTime(target.dueAt, clock.zone)),
        )
    }

    private companion object {
        const val MAX_MATCHES = 10
    }
}
