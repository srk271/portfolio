package dev.bidu.app.tools

import android.content.Context
import android.util.Log
import dev.bidu.app.data.NoteRepository
import dev.bidu.app.data.ReminderRepository
import dev.bidu.app.permissions.DevicePermissions
import dev.bidu.app.reminders.ReminderScheduler
import dev.bidu.app.voice.ListeningSwitch
import dev.bidu.app.voice.VoiceController
import dev.bidu.core.memory.AppCatalog
import dev.bidu.core.memory.MemoryStore
import dev.bidu.core.model.Clock
import dev.bidu.core.tool.BuiltinTools
import dev.bidu.core.tool.Tool

/**
 * Android implementations of every [BuiltinTools] spec plus [AppToolSpecs.FORGET]. Capability checks
 * happen in the engine before a tool runs; [permissions] is kept for tools that need a live re-check.
 */
class AndroidToolSet(
    private val context: Context,
    private val appCatalog: AppCatalog,
    private val notes: NoteRepository,
    private val reminders: ReminderRepository,
    private val reminderScheduler: ReminderScheduler,
    private val memory: MemoryStore,
    @Suppress("unused") private val permissions: DevicePermissions,
    private val voice: VoiceController,
    private val listening: ListeningSwitch,
    private val clock: Clock,
) {
    fun tools(): List<Tool> {
        val app = context.applicationContext
        val launcher = IntentLauncher(app)
        val contacts = ContactsLookup(app, memory)
        val tools = listOf(
            OpenAppTool(appCatalog, launcher),
            TellTimeTool(clock),
            TellDateTool(clock),
            CreateReminderTool(app, reminders, reminderScheduler, clock),
            ListRemindersTool(reminders, clock),
            CancelReminderTool(app, reminders, reminderScheduler, clock),
            CreateNoteTool(notes),
            SearchNotesTool(notes),
            DeleteNoteTool(notes),
            FindContactTool(contacts),
            DialContactTool(contacts, launcher),
            SetAlarmTool(launcher),
            SetTimerTool(launcher),
            FlashlightTool(app),
            WebSearchTool(launcher),
            BatteryLevelTool(app),
            BookCabTool(memory, launcher),
            MovieTicketsTool(memory, launcher, clock),
            RememberTool(memory),
            RecallTool(memory),
            StopListeningTool(voice, listening),
            ForgetTool(memory),
        )
        val missing = BuiltinTools.all.map { it.name } - tools.map { it.spec.name }.toSet()
        if (missing.isNotEmpty()) Log.w(TAG, "No Android implementation for: $missing")
        return tools
    }

    private companion object {
        const val TAG = "BiduTools"
    }
}
