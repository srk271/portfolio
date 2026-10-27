# Bidu architecture

Personal, sideloaded, always-on Hinglish voice assistant for one Samsung Galaxy S25 (Android 15/16, One UI).
Two Gradle modules, manual DI, local-first storage, optional consented cloud reasoning.

## 1. Modules and packages

| Module | Builds | Contents |
|---|---|---|
| `:core` (Kotlin/JVM, no Android) | locally and in CI (`./gradlew :core:build`) | contracts, agent engine, Hinglish NLU, planner, policy, composer, tool registry, unit tests |
| `:app` (Android, minSdk 31, target/compile 36) | CI only (`.github/workflows/bidu-android.yml`) | foreground service, Vosk/SpeechRecognizer/TTS, Room, DataStore, Keystore, Android tool impls, cloud adapter, Compose UI |

`settings.gradle.kts` includes `:app` only when an Android SDK is detected (`ANDROID_HOME`/`ANDROID_SDK_ROOT` or `sdk.dir`);
without one neither Google Maven nor AGP is ever resolved. All Gradle plugins share the root build script classloader (the
Kotlin Android plugin and KSP need AGP classes): the root `build.gradle.kts` puts AGP on its `buildscript` classpath only when
`:app` is included, and `app/build.gradle.kts` applies `com.android.application` by id without a version. Versions: AGP 8.13.0, Gradle wrapper 8.14.3, Kotlin 2.2.21,
KSP 2.2.21-2.0.5, coroutines 1.10.2, Compose BOM 2025.09.00, Room 2.7.2, DataStore 1.1.7, vosk-android 0.3.75 + jna 5.18.1@aar, JUnit 5.13.4 (core) / JUnit 4 (app).

```
core/src/main/kotlin/dev/bidu/core/
  model/    RiskLevel, Capability+PermissionStatus, Clock, Language/Script/ResponseStyle, Note/Reminder/MemoryFact/UserQuestion
  tool/     Tool, ToolContext, ToolCall, ToolExecution, ToolRegistry, ToolSpec/ParamSpec/RetryPolicy, ToolArgs, ToolResult(+Evidence), BuiltinTools
  nlu/      Intent (sealed), Nlu, NluContext/NluResult, TimeExpressionParser/TimeMatch
  plan/     Plan (Execute|Reply|Clarify|Decline), Planner, PlanRequest/PlanContext
  policy/   AutonomyPolicy(+PolicySettings/PolicyDecision/RequestScreening), ConfirmationGateway+PendingConfirmations, PermissionGateway
  agent/    AgentEngine, EngineDependencies/EngineSettings, ResponseComposer, AgentState/AgentEvent/AgentRequest/AgentReport, ConversationTurn, ActionLogEntry/ActionLogSink, LogSanitizer
  memory/   MemoryStore+MemoryKeys, AppCatalog/KnownApp/KnownApps
app/src/main/kotlin/dev/bidu/app/
  BiduApplication, AppContainer            assistant/   AssistantController (+Default impl, WS2)
  voice/       contracts + WS2 impls        system/      SystemEventReceiver (WS2)
  data/        repositories + data/room/    settings/    SettingsRepository (+DataStore impl)
  security/    SecretStore (+Keystore impl) permissions/ DevicePermissions (+Android impl)
  reminders/   ReminderScheduler (+impl, receiver)  tools/   AndroidToolSet + one class per tool
  cloud/       AnthropicPlanner             notifications/ channels, ids, actions
  ui/          MainActivity, screens, view models (WS4)
```

## 2. Agent loop (one request)

```
text ──> AssistantController.submitText ──> AgentEngine.handle(AgentRequest)
  UNDERSTANDING   policy.screen(utterance) -> Forbidden => Decline(FORBIDDEN)   |  nlu.parse(text, NluContext)
  PLANNING        planner.plan(PlanRequest)  RulePlanner for every known Intent; FallbackPlanner hands Intent.Unknown to the
                  cloud planner only when settings.cloudAllowed() and a key exists; otherwise Decline(NOT_UNDERSTOOD)
  CHECKING_PERMISSIONS  permissions.missing(union of requiredCapabilities) -> NEEDS_USER(PERMISSION) before anything runs
  EXECUTING       for each ToolCall: policy.decide -> Proceed | Confirm(confirmations.confirm, voice "haan/nahi", notification
                  Yes/No, in-app dialog) | Deny; registry.execute (validate, timeout, retry if idempotent, cancel, action log)
                  first FAILED / NEEDS_USER / rejected confirmation stops the plan
  VERIFYING       outcome must match evidence (ToolResult init enforces); anything inconsistent is downgraded to FAILED
  REPORTING       composer.compose -> AgentReport(spoken, display); controller: history.append, speaker.speak, notification
```
Outcome aggregation: any FAILED -> FAILED; else any NEEDS_USER -> NEEDS_USER; else any HANDED_OFF -> HANDED_OFF; else VERIFIED.
HANDED_OFF wording is mandatory and honest ("Uber khol diya, destination set hai; ride wahin confirm karna"). No fares, ETAs or
"done" for things Bidu cannot observe. Follow-up utterances while a question is pending go through `NluContext.pendingQuestion`.

## 3. Voice pipeline and mic lifecycle

```
BiduForegroundService (type microphone, START_STICKY, stopWithTask=false, persistent notification with Stop)
  onStartCommand -> startForeground(NotificationIds.FOREGROUND) -> voice.startWakeListening()
  Vosk SpeechService + grammar of all WakePhrase.variants (+ "[unk]") holds the AudioRecord
  WakeDetected(phrase, heard, confidence): per-word confidence >= wakeMinConfidence, cooldown wakeCooldownMillis, ignored while
  CAPTURING/PAUSED/busy -> DefaultAssistantController: voice.captureCommand()
    1. Vosk SpeechService.stop() and model recognizer released (AudioRecord closed)      [mode = CAPTURING]
    2. SpeechRecognizer on Dispatchers.Main, EXTRA_LANGUAGE = settings.recognizerLanguageTag,
       EXTRA_PREFER_OFFLINE = settings.preferOfflineRecognition (always true on the on-device recognizer). With the setting on,
       the on-device recognizer (API 33+ when available) is tried first and the regular one is the fallback; otherwise the
       reverse. Busy -> one retry; all unavailable -> Vosk free-form recogniser on the same model (lower accuracy, offline)
    3. result -> submitText(origin = VOICE_WAKE); during TTS: voice.pauseWakeListening(); after speak(): resumeWakeListening()
    4. wake listening resumes                                                            [mode = WAKE_LISTENING]
Push-to-talk (UI) = captureCommand() without the service; works with no Vosk model.
Boot / MY_PACKAGE_REPLACED: SystemEventReceiver -> reminderScheduler.rescheduleAll(); if settings.listeningEnabled post the
"Bidu paused — tap to resume" notification (NotificationIds.RESUME, HIGH-importance channel NotificationChannels.RESUME) whose
PendingIntent starts the service (user interaction exempts the mic FGS start restriction). Never start the service directly from the receiver.
Opening apps from the service: IntentLauncher checks Settings.canDrawOverlays(); if granted startActivity(NEW_TASK), else posts
a "Tap to open X" notification (NotificationIds.OPEN_APP_FALLBACK) and returns NEEDS_USER(ACTION_REQUIRED).
```
Samsung onboarding steps (WS4): Battery -> Unrestricted, "Never sleeping apps", ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
notification permission, mic, Display over other apps, exact alarms, optional contacts, Vosk model consent + download.

## 4. State machine

`AgentState`: IDLE -> LISTENING_WAKE -> CAPTURING_COMMAND -> UNDERSTANDING -> PLANNING -> CHECKING_PERMISSIONS -> EXECUTING ->
VERIFYING -> REPORTING -> (LISTENING_WAKE | IDLE). ERROR is entered from any engine state on an unexpected exception and leaves on
the next request. The engine owns UNDERSTANDING..REPORTING and ERROR; `VoiceController` owns IDLE/LISTENING_WAKE/
CAPTURING_COMMAND via `VoiceMode`; `DefaultAssistantController` merges both into `AssistantUiState.agentState`.
Cancellation (`cancel()`, notification Cancel, "ruk ja"): engine job cancelled, registry writes a CANCELLED log row,
report outcome CANCELLED, state returns to the voice state.

## 5. Risk levels and autonomy policy

| Risk | Meaning | Default decision | Settings can |
|---|---|---|---|
| LOW | read-only or reversible local effect | Proceed | add confirmation for `open_app` |
| MEDIUM | hands an intent to another app; user acts there | Proceed | add confirmation for all hand-offs |
| HIGH | sends a message, shares private data, deletes | Confirm, always | nothing |
| CRITICAL | payment/purchase/paid booking/transfer by Bidu itself | Confirm, one call per request, never retried | nothing |

`AutonomyPolicy.screen` refuses OTP / UPI PIN / password / banking-credential requests before planning (Decline FORBIDDEN).
NLU confidence < 0.5 on anything above LOW -> Clarify instead of Execute. Cloud plans may only use the tools in `PlanRequest.tools`.

## 6. Tools (`BuiltinTools`, names are the contract)

| Tool | Risk | Capabilities (required / optional) | Honest outcome |
|---|---|---|---|
| open_app | LOW | — / DISPLAY_OVER_OTHER_APPS | HANDED_OFF or NEEDS_USER (tap-to-open) |
| tell_time, tell_date, battery_level | LOW | — | VERIFIED (MEASURED) |
| create_reminder | LOW | POST_NOTIFICATIONS / EXACT_ALARMS | VERIFIED after row + alarm read back; says "inexact" when fallback used |
| list_reminders, search_notes, recall, find_contact | LOW (contacts: READ_CONTACTS) | | VERIFIED (OBSERVED_STATE) / NEEDS_USER (CHOICE) |
| create_note, remember | LOW | — | VERIFIED after read-back |
| cancel_reminder, delete_note | HIGH | — | VERIFIED after row gone |
| dial_contact | MEDIUM | READ_CONTACTS / overlay | HANDED_OFF (ACTION_DIAL, never CALL_PHONE) |
| set_alarm, set_timer | LOW | — | HANDED_OFF (AlarmClock intents) |
| flashlight | LOW | — | VERIFIED via CameraManager.TorchCallback |
| web_search | LOW | — / overlay | HANDED_OFF |
| book_cab | MEDIUM | — / overlay | HANDED_OFF; Uber/Ola/Rapido deep link or launch; destination from memory `home_address` |
| movie_tickets | MEDIUM | — / overlay | HANDED_OFF; BookMyShow/District search |
| stop_listening | LOW | — | VERIFIED when `voice.state.serviceRunning` becomes false |

## 7. Threading and coroutines

- `AppContainer.appScope = SupervisorJob + Dispatchers.Default`; the controller runs requests one at a time (Mutex/Channel).
- Engine and registry are dispatcher-agnostic; tools do I/O on `Dispatchers.IO`; `withTimeout(spec.timeoutMillis)` per attempt.
- Room: suspend DAOs + Flow. DataStore: Flow -> `StateFlow` via `stateIn(appScope, Eagerly)` so `current()` is synchronous.
- SpeechRecognizer and TextToSpeech are created and driven on `Dispatchers.Main.immediate`; callbacks bridged with
  `CompletableDeferred`/`callbackFlow`. Vosk runs on its own thread; its listener posts into `appScope`.
- BroadcastReceivers use `goAsync()` + `appScope.launch` for Room/AlarmManager work and finish within ~10 s.
- `PendingConfirmations` is the single confirmation hub: engine suspends in `confirm`; voice, notification and UI call `resolve`.

## 8. Data, security, cloud

Room `BiduDatabase` (WS3): notes, reminders, history (ConversationTurn), memory (MemoryFact), action_log (ActionLogEntry);
exported schema in `app/schemas/`. Settings: DataStore Preferences -> `BiduSettings`. Secrets: `KeystoreSecretStore` =
AES-256-GCM key in AndroidKeyStore, ciphertext+IV in a private file; no androidx.security. `LogSanitizer` scrubs every action-log
arg. Cloud (`AnthropicPlanner`, WS3, HttpURLConnection, off by default): sends only utterance text, current time, reply language,
tool specs (names/params) and installed app names (see `R.string.cloud_consent_disclosure`); never notes, contacts, memory values
or reminders. Response must be a JSON plan restricted to the given tools; anything else -> Decline(NOT_UNDERSTOOD).

## 9. FILE OWNERSHIP MAP

Integrator-owned (do not edit; request changes in your report): every file that exists at scaffold time —
`settings.gradle.kts`, `build.gradle.kts`, `gradle/**`, `gradle.properties`, `app/build.gradle.kts`, `app/proguard-rules.pro`,
`app/src/main/AndroidManifest.xml`, `app/src/main/kotlin/dev/bidu/app/{BiduApplication,AppContainer}.kt`,
`app/.../assistant/AssistantController.kt`, `app/.../voice/VoiceContracts.kt`, `app/.../settings/SettingsRepository.kt`,
`app/.../security/SecretStore.kt`, `app/.../permissions/DevicePermissions.kt`, `app/.../reminders/ReminderScheduler.kt`,
`app/.../data/Repositories.kt`, `app/.../notifications/NotificationChannels.kt`, `app/src/main/res/**` listed today
(`values/strings.xml`, `values/colors.xml`, drawables, mipmaps, `xml/data_extraction_rules.xml`), all `core/src/main` files
listed in section 1, `docs/**`, `.github/workflows/bidu-android.yml`.

### WS1 — `:core` internals (new files only, under `core/`)
- `core/.../nlu/HinglishNlu.kt` — `class HinglishNlu : Nlu`, no-arg constructor. Romanized Hinglish + Devanagari (digits, common
  verbs: khol/kholo/खोलो, laga/lagao, yaad dila, likh/लिखो, call kar/फोन लगाओ, band/on/off), wake-phrase stripping, slang.
- `core/.../nlu/HinglishTimeParser.kt` — `class HinglishTimeParser : TimeExpressionParser` ("kal subah 9 baje", "raat 9 baje",
  "10 minute baad", "parso", "aaj shaam 6:30", "कल सुबह सात बजे", "subah/dopahar/shaam/raat" am-pm disambiguation).
- `core/.../nlu/Normalizer.kt`, `core/.../nlu/Lexicon.kt` (or as WS1 prefers).
- `core/.../plan/RulePlanner.kt` — `class RulePlanner : Planner`, no-arg. Resolves "ghar"/"home" via `PlanContext.memory`.
- `core/.../plan/FallbackPlanner.kt` — `class FallbackPlanner(offline: Planner, cloud: Planner, cloudAllowed: () -> Boolean) : Planner`.
- `core/.../policy/DefaultAutonomyPolicy.kt` — `class DefaultAutonomyPolicy : AutonomyPolicy`, no-arg (table in section 5).
- `core/.../tool/DefaultToolRegistry.kt` — `class DefaultToolRegistry(tools: List<Tool>, actionLog: ActionLogSink, clock: Clock) : ToolRegistry`.
- `core/.../agent/DefaultAgentEngine.kt` — `class DefaultAgentEngine(deps: EngineDependencies, scope: CoroutineScope) : AgentEngine`.
- `core/.../agent/HinglishResponseComposer.kt` — `class HinglishResponseComposer : ResponseComposer`, no-arg.
- `core/src/test/**` — all tests (existing ones may be extended).

### WS2 — `:app` voice and service (package `dev.bidu.app.voice`, `dev.bidu.app.system`, `dev.bidu.app.assistant`)
- `voice/BiduForegroundService.kt` — `class BiduForegroundService : Service` (manifest: `.voice.BiduForegroundService`).
- `voice/NotificationActionReceiver.kt` — `class NotificationActionReceiver : BroadcastReceiver` handling `NotificationActions.*`.
- `voice/ListeningNotification.kt` — builds FOREGROUND/RESUME/RESULT/CONFIRMATION notifications on `NotificationChannels`.
- `voice/DefaultVoiceController.kt` — `class DefaultVoiceController(context: Context, settings: SettingsRepository, models: VoskModelManager, scope: CoroutineScope) : VoiceController`.
- `voice/VoskWakeWordDetector.kt`, `voice/SystemCommandRecognizer.kt` — internals of the controller.
- `voice/AndroidSpeaker.kt` — `class AndroidSpeaker(context: Context, settings: SettingsRepository, scope: CoroutineScope) : Speaker`
  (checks `isLanguageAvailable` for hi-IN/en-IN, honours `ttsEngine`/`ttsVoice`).
- `voice/DefaultVoskModelManager.kt` — `class DefaultVoskModelManager(context: Context, settings: SettingsRepository, scope: CoroutineScope) : VoskModelManager`
  (HttpURLConnection download to cacheDir, zip entry sanity check, unpack to `filesDir/vosk/<MODEL_NAME>`, atomic rename).
- `system/SystemEventReceiver.kt` — `class SystemEventReceiver : BroadcastReceiver` (BOOT_COMPLETED, MY_PACKAGE_REPLACED).
- `assistant/DefaultAssistantController.kt` — `class DefaultAssistantController(context: Context, engine: AgentEngine, nlu: Nlu,
  voice: VoiceController, speaker: Speaker, history: HistoryRepository, confirmations: PendingConfirmations,
  settings: SettingsRepository, permissions: DevicePermissions, voskModels: VoskModelManager, clock: Clock, scope: CoroutineScope) : AssistantController`.
  `setListening(true)` must verify RECORD_AUDIO + POST_NOTIFICATIONS before `startForegroundService`, else surface `missingCapabilities`.
- `app/src/main/res/values/strings_voice.xml`.

### WS3 — `:app` data, tools, security, cloud
- `data/room/BiduDatabase.kt` — `abstract class BiduDatabase : RoomDatabase` with `companion object { fun create(context: Context): BiduDatabase }`
  and DAOs `noteDao()`, `reminderDao()`, `historyDao()`, `memoryDao()`, `actionLogDao()`; entities + DAOs in `data/room/`.
- `data/room/RoomNoteRepository.kt` — `(dao: NoteDao, clock: Clock) : NoteRepository`; `RoomReminderRepository(dao: ReminderDao, clock: Clock)`;
  `RoomHistoryRepository(dao: HistoryDao)`; `RoomMemoryStore(dao: MemoryDao, clock: Clock) : MemoryStore`; `RoomActionLogRepository(dao: ActionLogDao)`.
- `settings/DataStoreSettingsRepository.kt` — `(context: Context, scope: CoroutineScope) : SettingsRepository`.
- `security/KeystoreSecretStore.kt` — `(context: Context) : SecretStore`.
- `permissions/AndroidPermissionGateway.kt` — `(context: Context, settings: SettingsRepository) : DevicePermissions`
  (CLOUD_REASONING = `settings.cloudAllowed()`; EXACT_ALARMS = `canScheduleExactAlarms()`; overlay = `Settings.canDrawOverlays`).
- `reminders/AlarmManagerReminderScheduler.kt` — `(context: Context, reminders: ReminderRepository, clock: Clock) : ReminderScheduler`;
  `reminders/ReminderAlarmReceiver.kt` — `class ReminderAlarmReceiver : BroadcastReceiver` (posts REMINDER_BASE+id notification, status FIRED).
- `tools/AndroidToolSet.kt` — `class AndroidToolSet(context: Context, appCatalog: AppCatalog, notes: NoteRepository, reminders: ReminderRepository,
  reminderScheduler: ReminderScheduler, memory: MemoryStore, permissions: DevicePermissions, voice: VoiceController, listening: ListeningSwitch,
  clock: Clock) { fun tools(): List<Tool> }` — one class per `BuiltinTools` spec, `override val spec = BuiltinTools.X`.
- `tools/InstalledAppCatalog.kt` — `(context: Context) : AppCatalog` (launcher query + `KnownApps.hints`); `tools/IntentLauncher.kt`.
- `cloud/AnthropicPlanner.kt` — `(settings: SettingsRepository, secrets: SecretStore) : Planner` (HttpURLConnection, JSON via `org.json`).
- `app/schemas/**`, `app/src/test/kotlin/dev/bidu/app/{data,tools,cloud,security}/**`, `res/values/strings_tools.xml`.

### WS4 — `:app` UI (package `dev.bidu.app.ui`)
- `ui/MainActivity.kt` — `class MainActivity : ComponentActivity` (manifest `.ui.MainActivity`), edge-to-edge, plain `MaterialTheme()`.
- `ui/BiduApp.kt` (screen switching without a nav library), `ui/main/MainScreen.kt` + `MainViewModel`, `ui/onboarding/OnboardingScreen.kt`,
  `ui/settings/SettingsScreen.kt`, `ui/notes/NotesScreen.kt` (notes + memory editor), `ui/history/HistoryScreen.kt`,
  `ui/ViewModelFactory.kt` (reads `context.biduContainer`), `app/src/test/kotlin/dev/bidu/app/ui/**`, `res/values/strings_ui.xml`.
- Uses only: `AssistantController`, `SettingsRepository`, `SecretStore`, `DevicePermissions`, `VoskModelManager`, `Speaker`
  (engines/voices), `NoteRepository`, `ReminderRepository`, `HistoryRepository`, `ActionLogRepository`, `MemoryStore`.

## 10. Build and CI

Local: `cd bidu && ./gradlew :core:build`. CI (`bidu-android.yml`, concurrency group `bidu-<ref>`, cancel in progress): JDK 21,
platform 36 + build-tools 36.0.0, four jobs:
- `build` — `:app:assembleDebug`, uploads artifact `bidu-debug-apk` (the critical path; nothing else gates it).
- `test` — `:core:test :app:testDebugUnitTest`, uploads `bidu-test-reports`. App unit tests never touch the network.
- `release` — `:app:assembleRelease` (minify + shrinkResources, Vosk/JNA keep rules; signed only when
  `BIDU_KEYSTORE_BASE64/PASSWORD/KEY_ALIAS/KEY_PASSWORD` secrets exist), uploads `bidu-release-apk[-unsigned]`.
- `publish` — after a successful `build`, on push to the working branch or `workflow_dispatch`: moves tag `bidu-latest` to the
  commit and creates/updates the public pre-release "Bidu (latest debug build)" with asset `bidu-debug.apk`
  (notes: short SHA, build date, "Debug build for sideloading; not Play-signed").

Debug builds install as `dev.bidu.app.debug`.
