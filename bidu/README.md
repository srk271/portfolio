# Bidu

Bidu is a personal Hinglish voice assistant for one phone, a Samsung Galaxy S25, installed by sideloading.
It runs in the background. You set it up once, then say the wake word and a command, for example
"bhai, whatsapp khol" or "kal subah 9 baje yaad dila doodh lena hai". You can also tap **Talk** on its
notification, tap **Push to talk** in the app, or type a command in the app.

**About the wake word:** the offline wake-word recognizer (a small Vosk English (India) model) can only
match words that are in its vocabulary, and "Bidu" is not one of them. So each wake phrase has a list of
in-vocabulary variants, and those variants are what the recognizer listens for. A variant that contains an
unknown word can never match. The defaults (you can change them in Settings → Wake phrases):

| You say | The recognizer listens for |
|---|---|
| Hey Bidu | `hey bidu`, `hey buddy`, `hey be do`, `hey bee do`, `hey beedu` |
| Bhai | `bhai`, `bye`, `by`, `buy` |

In practice the stand-ins such as "hey buddy" are what fire. Everyday words like "bye" or "buy" can wake
Bidu by mistake, so remove those variants if that happens. A wake word only counts when every word has a
confidence of at least 0.7, and after one fires the next 2.5 s are ignored.

## Install on the S25

1. On the phone, open <https://github.com/srk271/portfolio/releases/tag/bidu-latest> and download `bidu-debug.apk`.
2. Open the downloaded file from Chrome's download prompt or from **My Files → Downloads**.
3. If Android says the install is blocked, tap **Settings**, turn on **Allow from this source** for Chrome
   (or My Files), and go back.
4. Tap **Install**. Play Protect may warn about an unrecognised app. Tap **More details → Install anyway**
   (the wording varies).
5. Open Bidu once. It starts on the Setup screen.

This is a **debug build**. It is not signed for the Play Store, its package name is `dev.bidu.app.debug` and its version is `0.1.0-debug`.

## One-time setup (the Setup screen)

Each item's status updates when you come back to the screen.

| # | Item | Why |
|---|---|---|
| 1 | **Microphone** | To hear the wake word and your commands. Required. |
| 2 | **Notifications** | For the listening notification, confirmations and reminders. Required for background listening. |
| 3 | **Contacts** (optional) | For "mummy ko call kar". Bidu only opens the dialer and never places a call itself. |
| 4 | **Display over other apps** | Android stops background apps from opening other apps. With this permission Bidu can open WhatsApp, Uber and so on directly. Without it, Bidu posts a "Tap to open …" notification instead. |
| 5 | **Exact alarms** | Makes reminders ring on time. Without it they can be a few minutes late. |
| 6 | **Battery: Unrestricted** | Stops Android from putting Bidu to sleep. Then do the Samsung steps below. |
| 7 | **Wake-word model** | A one-time download of about 36 MB from alphacephei.com, after you agree in a consent dialog. Mobile data counts. Without the model, only push-to-talk works. |
| 8 | **Start Bidu** | Needs the Microphone and Notifications permissions. After that you can close the app. |

The Samsung battery steps (Setup has buttons that open these screens):

1. Settings → Apps → Bidu → Battery → **Unrestricted**.
2. Settings → Battery → Background usage limits → **Never sleeping apps** → add Bidu.
3. In Background usage limits, turn off **Put unused apps to sleep**.

## "Always-on": what it really means

- **The notification can't be hidden.** Android requires it for any app that keeps the mic open in the
  background. It shows what Bidu is doing and has **Stop** (turns listening off and releases the mic right
  away), **Talk** and **Pause 30 min** buttons. If you swipe it away, Bidu puts it back. You can also say
  "bidu so ja" or "stop listening".
- **After a reboot or an app update**, Android 14 and later won't let an app start the microphone from the
  background. Bidu reschedules your reminders and posts **"Bidu paused: Tap to resume listening"**. Tap it,
  or open the app, to start listening again. Bidu never starts listening without you knowing.
- **Samsung battery management** can still stop background apps if the three battery steps are skipped. If
  Bidu stops responding, check that its notification is still showing.
- **Sharing the mic:** Bidu lets go of the microphone when another app starts recording, during phone and
  VoIP calls, and while Bidu itself is speaking. It starts listening again automatically afterwards.
- **Battery use** of always-on listening has not been measured yet.

## Example commands

These are taken from the `:core` unit tests (`HinglishNluTest`), so the offline parser handles them. You can
use romanised Hinglish, English or Devanagari.

| What | Say |
|---|---|
| Apps | `bhai whatsapp khol` · `open youtube` · `व्हाट्सएप खोलो` |
| Time / date | `time kya hua` · `समय क्या हुआ` · `aaj kaunsa din hai` |
| Torch / battery | `torch on kar` · `flashlight band kar do` · `टॉर्च जलाओ` · `battery kitni hai` |
| Reminders | `kal subah 9 baje yaad dila dena doodh lena hai` · `10 minute baad yaad dilana chai bani hai` · `कल सुबह सात बजे याद दिलाना दूध लेना है` · `remind me to call mom at 6 pm` · `mere reminders batao` · `doodh wala reminder hata do` |
| Notes | `note likh: kal gym jaana hai` · `नोट लिखो दूध लेना है` · `gym wala note padh` · `gym wala note delete kar` |
| Alarm / timer | `subah 6 baje alarm laga` · `5 minute ka timer laga` · `५ मिनट का टाइमर लगाओ` · `dedh ghante ka timer laga` |
| Calls | `mummy ko call kar` · `मम्मी को फोन लगाओ` · `rahul ka number batao` |
| Search | `google kar mumbai weather` |
| Cab | `ghar jaane ke liye cab book kar` · `office ke liye uber bula` · `rapido book kar airport tak` |
| Movies | `bookmyshow pe dhurandhar dekh` · `dhurandhar ki 2 tickets book kar PVR Juhu mein kal shaam 7 baje` |
| Memory | `yaad rakh mera ghar Andheri mein hai` · `mummy matlab Sunita Sharma` · `mera ghar kahan hai` · `ghar ka address bhool ja` |
| Control | `ruk ja` (cancel) · `bidu so ja` (stop listening) · `tum kya kar sakte ho` (help) · `haan` / `nahi` (answer a question) |

The time parser understands words like kal, parso, aaj, subah, dopahar, shaam, raat, "saadhe nau baje",
"aadhe ghante baad", "Monday ko" and "next tuesday". A bare "9 baje" means the next 9 o'clock to come.
The tests also confirm what Bidu turns down: `paytm se 500 rupaye bhej` (payment, refused),
`rahul ko whatsapp pe message bhej` (sending messages isn't supported) and `train ticket book kar` (not supported).

## Feature status

**None of these features has been tested on a real S25 yet.** CI builds the APK, but every row below is
*built, not yet tested on a device*. The understanding, planning and safety logic is unit-tested (see Test results).
"Hand-off" means Bidu opens another app with the details filled in, and you finish the task there.

| Feature | Status | Notes |
|---|---|---|
| Wake word | Working offline | Works after the one-time model download. Uses the stand-in variants above, and real-world accuracy is unknown. |
| Push-to-talk | Working offline | The app button or the notification's **Talk** button. Doesn't need the wake-word model. The words themselves go through STT (next row). |
| Speech-to-text (commands) | Needs internet (usually) | Uses Android's `SpeechRecognizer` (English (India) by default, Hindi optional), which may send audio to Google's servers. Settings → **Prefer offline recognition** uses the on-device recognizer if the offline language pack is installed. If no recognizer is available, Bidu falls back to the offline Vosk model (if it has been downloaded), which is less accurate. |
| Spoken replies (TTS) | Working offline | Android text-to-speech. Needs a Hindi or English (India) voice installed on the phone. You pick the engine and voice in Settings. |
| Open app | Hand-off | Fuzzy-matches the apps installed on the phone. When Bidu is in the background this needs "Display over other apps", otherwise you get a tap-to-open notification. |
| Time, date | Working offline | Read from the system clock. |
| Battery level | Working offline | Read from `BatteryManager`, and also says whether the phone is charging. |
| Reminders | Working offline | Bidu's own reminders, saved in its database and scheduled with `AlarmManager`. They're checked after saving, rescheduled after a reboot, and one-off only. |
| Notes | Working offline | Create, search, read and delete. Deleting asks first. |
| Contacts + dial | Hand-off | Finds the contact (it knows relation words and aliases like "mummy matlab …") and opens the **dialer** with the number filled in. You press call. |
| Alarm, timer | Hand-off | Sent to the Clock app. Bidu can't see whether the alarm or timer was actually set, and it can't change or stop them. |
| Torch | Working offline | Reports success only after the camera's torch callback confirms the new state. |
| Web search | Needs internet | Hand-off to the browser or search app. Bidu doesn't read the results. |
| Memory | Working offline | Remember, recall and forget facts (home and work address, city, name, contact nicknames, places). You can edit them in **Notes & memory**. Forgetting asks first. |
| Cab | Hand-off | **Uber** opens with pickup at your location and the destination filled in ("ghar" and "office" come from memory). If the Uber app isn't installed, the Uber website opens instead. **Ola / Rapido** just open the app (or its Play Store page), and you type the destination. There is **no fare or ETA comparison**, because these services have no public APIs. Bidu never makes up a fare and never books. |
| Movies | Hand-off | Opens BookMyShow or District if installed, otherwise a web search for showtimes. If you name a cinema or time, it searches for showtimes too. You pick the seats and pay. |
| Cloud AI fallback | Needs internet (optional) | Off by default. See below. |
| Payments, recharges, orders | Not supported | Refused on the phone and never sent to the cloud. Bidu offers to open the app so you can pay there yourself. |
| Sending messages | Not supported | Say "WhatsApp khol" and send the message yourself. |
| Calendar, photo search | Not supported | On the roadmap. |

## Privacy and security

- **Your data stays on the phone.** Notes, reminders, conversation history, memory and the action log are
  stored in a local database, and settings in app-private storage. The cloud API key is encrypted with an
  AES-256-GCM key held in the Android Keystore. App backup and device transfer are switched off.
- **What leaves the phone, and when:**
  1. The wake-word model download from alphacephei.com, once, only after you agree to it.
  2. The audio of your command, sent to Android's speech recognizer. Depending on the phone's settings this
     may be processed on Google's servers. The wake-word audio itself is processed only on the phone.
  3. Cloud AI requests, but only if you turn it on and agree (see below).
  4. Whatever you hand off to another app, such as a search query, a cab destination or a phone number in the dialer.
- **No audio is ever saved.** The only file Bidu writes to storage is the downloaded model.
- **Credentials are refused.** Any request involving an OTP, UPI/ATM PIN, password, CVV or banking login is
  turned down before anything runs. Notes, memory and reminders also won't store such values or long card
  or account numbers.
- **Bidu asks before risky actions.** Deleting a note, cancelling a reminder and forgetting a memory always
  ask first. You can answer by voice ("haan" or "nahi"), with the notification's Yes/No buttons, or in the
  app. Payment-type actions would always need explicit confirmation and are never retried, but Bidu
  currently has none, because payments are refused outright. In Settings you can also make Bidu ask before
  opening apps or before any hand-off.
- **Every action is logged on the phone** with private details removed. You can view and clear the log under
  Notes & memory → Log.

## Optional cloud AI

When Bidu can't understand a command offline, it can ask a cloud model to map the command onto Bidu's own tools.

- **Off by default.** Turn it on in Settings → Cloud AI. A consent dialog explains what is sent.
- **Use your own Anthropic API key.** Paste it into Settings, where it is stored encrypted on the phone.
  Then type a **model id** in Settings. Bidu has no built-in default, and no cloud request is sent until both
  the key and the model id are saved.
- **What is sent:** only the command text, the current time, your reply language, the list of Bidu's tools and
  the names of apps installed on the phone. Notes, contacts, reminders and memory are never sent.
- **What the cloud can do:** it can only propose calls to Bidu's own tools, and payment-type tools are never
  offered to it. Every proposed call is checked against the tool's rules. The usual confirmations still apply,
  and anything invalid is rejected.
- **Cost:** a Claude Pro or Max subscription does **not** include API credits. API usage is billed separately,
  per token, to your API account. Each request is short (one command plus the tool list), so a single request
  typically costs a fraction of a cent. The exact cost depends on the model you choose, so check the
  provider's pricing page.

## Build from source

**CI** (`.github/workflows/bidu-android.yml`) runs on every push that changes `bidu/**`, or when started by
hand. It uses JDK 21, Android platform 36 and build-tools 36.0.0, and has four jobs:

- `build` runs `:app:assembleDebug` and uploads the artifact `bidu-debug-apk`.
- `test` runs `:core:test :app:testDebugUnitTest` and uploads `bidu-test-reports`.
- `release` runs `:app:assembleRelease` (minified). The APK is signed only when all four secrets
  `BIDU_KEYSTORE_BASE64`, `BIDU_KEYSTORE_PASSWORD`, `BIDU_KEY_ALIAS` and `BIDU_KEY_PASSWORD` are set. Otherwise
  it uploads `bidu-release-apk-unsigned`.
- `publish` runs after a successful `build`, on a push to `ccr-b9cc0e68-gw642j` or a manual run. It moves the
  `bidu-latest` tag to that commit and updates the **Bidu (latest debug build)** pre-release with `bidu-debug.apk`.

**Locally:**

```sh
cd bidu
./gradlew :core:test          # pure Kotlin/JVM module, needs only a JDK (21 is used here and in CI)
```

`:app` needs the Android SDK. Set `ANDROID_HOME` (or `ANDROID_SDK_ROOT`), or put `sdk.dir=...` in
`bidu/local.properties`. Without an SDK, `settings.gradle.kts` includes only `:core` and never contacts
Google Maven. With an SDK:

```sh
./gradlew :app:assembleDebug  # -> app/build/outputs/apk/debug/
adb install -r app/build/outputs/apk/debug/*.apk
```

The design notes are in [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md).

## Test results

- **Local run** (27 Oct 2026, JDK 21, `./gradlew :core:test --rerun`): **163 tests in 11 classes, 0 failures,
  0 skipped**, BUILD SUCCESSFUL. The largest classes are `HinglishNluTest` (72), `HinglishTimeParserTest` (29)
  and `DefaultAgentEngineTest` (14). The rest cover the planners, autonomy policy, tool registry,
  confirmations, response composer and log sanitizer.
- **CI** runs `:core:test :app:testDebugUnitTest` (the `test` job) and `:app:assembleDebug` (the `build` job).
  The `:app` unit tests need the Android SDK, so they only run in CI. Check the repository's Actions tab for
  the current results; this README doesn't report a CI result.

## Known limitations

- Nothing has been tested on the S25 yet (see Feature status).
- How well the wake word works in practice is unknown, because it depends on the stand-in variants and can
  fire by mistake.
- Recognising spoken commands usually depends on Google's speech service, and Hinglish accuracy depends on
  that service.
- Bidu handles one command per sentence. "torch on kar aur whatsapp khol" isn't split into two commands.
- Reminders are one-off. Repeating reminders such as "har roz" aren't supported.
- Bidu can't send messages, make payments, place calls by itself, book anything, read search results, see
  fares or ETAs, or change existing alarms and timers.
- Ola and Rapido open without the destination filled in.
- **Updating the app:** CI signs each debug build with a temporary debug key, so installing a newer APK over
  an older one may fail with "package conflicts". If that happens, uninstall Bidu first. This deletes its
  notes, memory, reminders and saved API key. A fixed signing key would fix this.

## Roadmap (in priority order)

1. **P0:** improve wake-word accuracy by tuning the variants and threshold, then train a custom wake-word
   model that really knows "Bidu".
2. **P0:** test every feature in the status table on the S25 and measure battery use.
3. **P1:** a Quick Settings tile for push-to-talk and turning listening on and off.
4. **P1:** let Bidu become the phone's default assistant (`VoiceInteractionService` / assistant role).
5. **P1:** calendar and photo search.
6. **P1:** multiple commands in one sentence.
7. **P1:** repeating reminders.
8. **Later:** real cab and movie integrations (fares, booking), but only if official public APIs become
   available. Bidu won't scrape sites or invent fares.
