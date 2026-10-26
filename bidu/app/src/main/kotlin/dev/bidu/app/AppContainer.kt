package dev.bidu.app

import android.app.Application
import dev.bidu.app.assistant.AssistantController
import dev.bidu.app.assistant.DefaultAssistantController
import dev.bidu.app.cloud.AnthropicPlanner
import dev.bidu.app.data.ActionLogRepository
import dev.bidu.app.data.HistoryRepository
import dev.bidu.app.data.NoteRepository
import dev.bidu.app.data.ReminderRepository
import dev.bidu.app.data.room.BiduDatabase
import dev.bidu.app.data.room.RoomActionLogRepository
import dev.bidu.app.data.room.RoomHistoryRepository
import dev.bidu.app.data.room.RoomMemoryStore
import dev.bidu.app.data.room.RoomNoteRepository
import dev.bidu.app.data.room.RoomReminderRepository
import dev.bidu.app.permissions.AndroidPermissionGateway
import dev.bidu.app.permissions.DevicePermissions
import dev.bidu.app.reminders.AlarmManagerReminderScheduler
import dev.bidu.app.reminders.ReminderScheduler
import dev.bidu.app.security.KeystoreSecretStore
import dev.bidu.app.security.SecretStore
import dev.bidu.app.settings.DataStoreSettingsRepository
import dev.bidu.app.settings.SettingsRepository
import dev.bidu.app.tools.AndroidToolSet
import dev.bidu.app.tools.InstalledAppCatalog
import dev.bidu.app.voice.AndroidSpeaker
import dev.bidu.app.voice.DefaultVoiceController
import dev.bidu.app.voice.DefaultVoskModelManager
import dev.bidu.app.voice.ListeningSwitch
import dev.bidu.app.voice.Speaker
import dev.bidu.app.voice.VoiceController
import dev.bidu.app.voice.VoskModelManager
import dev.bidu.core.agent.AgentEngine
import dev.bidu.core.agent.DefaultAgentEngine
import dev.bidu.core.agent.EngineDependencies
import dev.bidu.core.agent.HinglishResponseComposer
import dev.bidu.core.agent.ResponseComposer
import dev.bidu.core.memory.AppCatalog
import dev.bidu.core.memory.MemoryStore
import dev.bidu.core.model.Clock
import dev.bidu.core.model.SystemClock
import dev.bidu.core.nlu.HinglishNlu
import dev.bidu.core.nlu.Nlu
import dev.bidu.core.plan.FallbackPlanner
import dev.bidu.core.plan.Planner
import dev.bidu.core.plan.RulePlanner
import dev.bidu.core.policy.AutonomyPolicy
import dev.bidu.core.policy.DefaultAutonomyPolicy
import dev.bidu.core.policy.PendingConfirmations
import dev.bidu.core.tool.DefaultToolRegistry
import dev.bidu.core.tool.Tool
import dev.bidu.core.tool.ToolRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Manual dependency graph. Everything is constructed here, by constructor, in dependency order.
 * Heavy objects are lazy so Application.onCreate stays fast. The concrete class names and
 * constructor signatures referenced below are the contract each workstream must satisfy; they are
 * listed per workstream in docs/ARCHITECTURE.md.
 */
class AppContainer(private val app: Application) {

    val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val clock: Clock = SystemClock()

    // Settings and secrets (WS3).
    val settings: SettingsRepository by lazy { DataStoreSettingsRepository(app, appScope) }
    val secrets: SecretStore by lazy { KeystoreSecretStore(app) }

    // Persistence (WS3).
    val database: BiduDatabase by lazy { BiduDatabase.create(app) }
    val notes: NoteRepository by lazy { RoomNoteRepository(database.noteDao(), clock) }
    val reminders: ReminderRepository by lazy { RoomReminderRepository(database.reminderDao(), clock) }
    val history: HistoryRepository by lazy { RoomHistoryRepository(database.historyDao()) }
    val memory: MemoryStore by lazy { RoomMemoryStore(database.memoryDao(), clock) }
    val actionLog: ActionLogRepository by lazy { RoomActionLogRepository(database.actionLogDao()) }

    // Platform access (WS3).
    val permissions: DevicePermissions by lazy { AndroidPermissionGateway(app, settings) }
    val reminderScheduler: ReminderScheduler by lazy { AlarmManagerReminderScheduler(app, reminders, clock) }
    val appCatalog: AppCatalog by lazy { InstalledAppCatalog(app) }

    // Voice (WS2).
    val speaker: Speaker by lazy { AndroidSpeaker(app, settings, appScope) }
    val voskModels: VoskModelManager by lazy { DefaultVoskModelManager(app, settings, appScope) }
    val voice: VoiceController by lazy { DefaultVoiceController(app, settings, voskModels, appScope) }

    // Lets the stop_listening tool reach the assistant without a construction cycle.
    val listeningSwitch: ListeningSwitch = object : ListeningSwitch {
        override fun isListening(): Boolean = voice.state.value.serviceRunning
        override fun setListening(enabled: Boolean) = assistant.setListening(enabled)
    }

    // Agent core (WS1), with the shared confirmation hub.
    val confirmations: PendingConfirmations = PendingConfirmations()
    val nlu: Nlu by lazy { HinglishNlu() }
    val policy: AutonomyPolicy by lazy { DefaultAutonomyPolicy() }
    val composer: ResponseComposer by lazy { HinglishResponseComposer() }
    val cloudPlanner: Planner by lazy { AnthropicPlanner(settings, secrets) }
    val planner: Planner by lazy {
        FallbackPlanner(
            offline = RulePlanner(),
            cloud = cloudPlanner,
            cloudAllowed = { settings.current().cloudAllowed() },
        )
    }
    val tools: List<Tool> by lazy {
        AndroidToolSet(
            context = app,
            appCatalog = appCatalog,
            notes = notes,
            reminders = reminders,
            reminderScheduler = reminderScheduler,
            memory = memory,
            permissions = permissions,
            voice = voice,
            listening = listeningSwitch,
            clock = clock,
        ).tools()
    }
    val registry: ToolRegistry by lazy { DefaultToolRegistry(tools, actionLog, clock) }
    val engine: AgentEngine by lazy {
        DefaultAgentEngine(
            EngineDependencies(
                nlu = nlu,
                planner = planner,
                registry = registry,
                permissions = permissions,
                confirmations = confirmations,
                policy = policy,
                composer = composer,
                actionLog = actionLog,
                memory = memory,
                appCatalog = appCatalog,
                clock = clock,
                settings = { settings.current().engineSettings() },
            ),
            appScope,
        )
    }

    // Orchestration hub shared by the service, notifications and the UI (WS2 implements it).
    val assistant: AssistantController by lazy {
        DefaultAssistantController(
            context = app,
            engine = engine,
            nlu = nlu,
            voice = voice,
            speaker = speaker,
            history = history,
            confirmations = confirmations,
            settings = settings,
            permissions = permissions,
            voskModels = voskModels,
            clock = clock,
            scope = appScope,
        )
    }
}
