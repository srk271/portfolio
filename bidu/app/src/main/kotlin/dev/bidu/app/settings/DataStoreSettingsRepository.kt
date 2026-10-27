package dev.bidu.app.settings

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.bidu.core.model.Language
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

private val Context.biduSettingsStore: DataStore<Preferences> by preferencesDataStore(
    name = "bidu_settings",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

/**
 * [SettingsRepository] on DataStore Preferences. The first snapshot is read synchronously (bounded wait)
 * so [current] is correct even for a BroadcastReceiver that runs right after boot; later changes flow in
 * through a collector, and [update] publishes its own result immediately.
 */
class DataStoreSettingsRepository(context: Context, scope: CoroutineScope) : SettingsRepository {
    private val store: DataStore<Preferences> = context.applicationContext.biduSettingsStore
    private val state = MutableStateFlow(readInitial())
    override val settings: StateFlow<BiduSettings> = state.asStateFlow()

    init {
        scope.launch {
            store.data
                .catch { e -> if (e is IOException) Log.w(TAG, "Settings read failed: ${e.javaClass.simpleName}") else throw e }
                .map { decode(it) }
                .collect { state.value = it }
        }
    }

    override suspend fun update(transform: (BiduSettings) -> BiduSettings) {
        val written = store.edit { prefs ->
            val next = sanitize(transform(decode(prefs)))
            encode(prefs, next)
        }
        state.value = decode(written)
    }

    private fun readInitial(): BiduSettings = runBlocking(Dispatchers.IO) {
        withTimeoutOrNull(INITIAL_READ_TIMEOUT_MILLIS) {
            try {
                decode(store.data.first())
            } catch (e: IOException) {
                Log.w(TAG, "Initial settings read failed: ${e.javaClass.simpleName}")
                null
            }
        }
    } ?: BiduSettings()

    private object Keys {
        val ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
        val LISTENING_ENABLED = booleanPreferencesKey("listening_enabled")
        val WAKE_PHRASES = stringPreferencesKey("wake_phrases_json")
        val WAKE_MIN_CONFIDENCE = doublePreferencesKey("wake_min_confidence")
        val WAKE_COOLDOWN_MILLIS = longPreferencesKey("wake_cooldown_millis")
        val VOSK_MODEL_CONSENT = booleanPreferencesKey("vosk_model_consent")
        val RECOGNIZER_LANGUAGE_TAG = stringPreferencesKey("recognizer_language_tag")
        val PREFER_OFFLINE_RECOGNITION = booleanPreferencesKey("prefer_offline_recognition")
        val RESPONSE_LANGUAGE = stringPreferencesKey("response_language")
        val CASUAL_TONE = booleanPreferencesKey("casual_tone")
        val TTS_ENGINE = stringPreferencesKey("tts_engine")
        val TTS_VOICE = stringPreferencesKey("tts_voice")
        val CONFIRM_OPEN_APPS = booleanPreferencesKey("confirm_before_opening_apps")
        val CONFIRM_HANDOFFS = booleanPreferencesKey("confirm_before_handoffs")
        val CONFIRMATION_TIMEOUT_MILLIS = longPreferencesKey("confirmation_timeout_millis")
        val CLOUD_ENABLED = booleanPreferencesKey("cloud_enabled")
        val CLOUD_CONSENT = booleanPreferencesKey("cloud_consent_given")
        val CLOUD_MODEL = stringPreferencesKey("cloud_model")
        val HISTORY_LIMIT = intPreferencesKey("history_limit")
    }

    private companion object {
        const val TAG = "BiduSettings"
        const val INITIAL_READ_TIMEOUT_MILLIS = 2_000L
        private val DEFAULTS = BiduSettings()

        fun decode(p: Preferences): BiduSettings = BiduSettings(
            onboardingComplete = p[Keys.ONBOARDING_COMPLETE] ?: DEFAULTS.onboardingComplete,
            listeningEnabled = p[Keys.LISTENING_ENABLED] ?: DEFAULTS.listeningEnabled,
            wakePhrases = p[Keys.WAKE_PHRASES]?.let { decodeWakePhrases(it) } ?: DEFAULTS.wakePhrases,
            wakeMinConfidence = p[Keys.WAKE_MIN_CONFIDENCE] ?: DEFAULTS.wakeMinConfidence,
            wakeCooldownMillis = p[Keys.WAKE_COOLDOWN_MILLIS] ?: DEFAULTS.wakeCooldownMillis,
            voskModelConsent = p[Keys.VOSK_MODEL_CONSENT] ?: DEFAULTS.voskModelConsent,
            recognizerLanguageTag = p[Keys.RECOGNIZER_LANGUAGE_TAG] ?: DEFAULTS.recognizerLanguageTag,
            preferOfflineRecognition = p[Keys.PREFER_OFFLINE_RECOGNITION] ?: DEFAULTS.preferOfflineRecognition,
            responseLanguage = p[Keys.RESPONSE_LANGUAGE]?.let { name -> Language.entries.firstOrNull { it.name == name } }
                ?: DEFAULTS.responseLanguage,
            casualTone = p[Keys.CASUAL_TONE] ?: DEFAULTS.casualTone,
            ttsEngine = p[Keys.TTS_ENGINE],
            ttsVoice = p[Keys.TTS_VOICE],
            confirmBeforeOpeningApps = p[Keys.CONFIRM_OPEN_APPS] ?: DEFAULTS.confirmBeforeOpeningApps,
            confirmBeforeHandoffs = p[Keys.CONFIRM_HANDOFFS] ?: DEFAULTS.confirmBeforeHandoffs,
            confirmationTimeoutMillis = p[Keys.CONFIRMATION_TIMEOUT_MILLIS] ?: DEFAULTS.confirmationTimeoutMillis,
            cloudEnabled = p[Keys.CLOUD_ENABLED] ?: DEFAULTS.cloudEnabled,
            cloudConsentGiven = p[Keys.CLOUD_CONSENT] ?: DEFAULTS.cloudConsentGiven,
            cloudModel = p[Keys.CLOUD_MODEL],
            historyLimit = p[Keys.HISTORY_LIMIT] ?: DEFAULTS.historyLimit,
        )

        fun encode(p: MutablePreferences, s: BiduSettings) {
            p[Keys.ONBOARDING_COMPLETE] = s.onboardingComplete
            p[Keys.LISTENING_ENABLED] = s.listeningEnabled
            p[Keys.WAKE_PHRASES] = encodeWakePhrases(s.wakePhrases)
            p[Keys.WAKE_MIN_CONFIDENCE] = s.wakeMinConfidence
            p[Keys.WAKE_COOLDOWN_MILLIS] = s.wakeCooldownMillis
            p[Keys.VOSK_MODEL_CONSENT] = s.voskModelConsent
            p[Keys.RECOGNIZER_LANGUAGE_TAG] = s.recognizerLanguageTag
            p[Keys.PREFER_OFFLINE_RECOGNITION] = s.preferOfflineRecognition
            p[Keys.RESPONSE_LANGUAGE] = s.responseLanguage.name
            p[Keys.CASUAL_TONE] = s.casualTone
            p.setOrRemove(Keys.TTS_ENGINE, s.ttsEngine)
            p.setOrRemove(Keys.TTS_VOICE, s.ttsVoice)
            p[Keys.CONFIRM_OPEN_APPS] = s.confirmBeforeOpeningApps
            p[Keys.CONFIRM_HANDOFFS] = s.confirmBeforeHandoffs
            p[Keys.CONFIRMATION_TIMEOUT_MILLIS] = s.confirmationTimeoutMillis
            p[Keys.CLOUD_ENABLED] = s.cloudEnabled
            p[Keys.CLOUD_CONSENT] = s.cloudConsentGiven
            p.setOrRemove(Keys.CLOUD_MODEL, s.cloudModel)
            p[Keys.HISTORY_LIMIT] = s.historyLimit
        }

        fun sanitize(s: BiduSettings): BiduSettings = s.copy(
            wakePhrases = s.wakePhrases.ifEmpty { BiduSettings.DEFAULT_WAKE_PHRASES },
            wakeMinConfidence = s.wakeMinConfidence.takeIf { !it.isNaN() }?.coerceIn(0.0, 1.0) ?: DEFAULTS.wakeMinConfidence,
            wakeCooldownMillis = s.wakeCooldownMillis.coerceIn(0L, 60_000L),
            recognizerLanguageTag = s.recognizerLanguageTag.trim().ifEmpty { DEFAULTS.recognizerLanguageTag },
            ttsEngine = s.ttsEngine?.trim()?.takeIf { it.isNotEmpty() },
            ttsVoice = s.ttsVoice?.trim()?.takeIf { it.isNotEmpty() },
            confirmationTimeoutMillis = s.confirmationTimeoutMillis.coerceIn(5_000L, 300_000L),
            cloudModel = s.cloudModel?.trim()?.takeIf { it.isNotEmpty() },
            historyLimit = s.historyLimit.coerceIn(10, 2_000),
        )

        fun <T> MutablePreferences.setOrRemove(key: Preferences.Key<T>, value: T?) {
            if (value == null) remove(key) else this[key] = value
        }

        fun encodeWakePhrases(phrases: List<WakePhrase>): String {
            val array = JSONArray()
            for (wp in phrases) {
                array.put(JSONObject().put("phrase", wp.phrase).put("variants", JSONArray(wp.variants)))
            }
            return array.toString()
        }

        fun decodeWakePhrases(json: String): List<WakePhrase>? = try {
            val array = JSONArray(json)
            val phrases = (0 until array.length()).mapNotNull { i ->
                val obj = array.optJSONObject(i) ?: return@mapNotNull null
                val phrase = obj.optString("phrase").trim()
                val variantsJson = obj.optJSONArray("variants") ?: JSONArray()
                val variants = (0 until variantsJson.length())
                    .map { variantsJson.optString(it).trim().lowercase() }
                    .filter { it.isNotEmpty() }
                    .distinct()
                if (phrase.isEmpty() || variants.isEmpty()) null else WakePhrase(phrase.lowercase(), variants)
            }
            phrases.ifEmpty { null }
        } catch (e: org.json.JSONException) {
            null
        }
    }
}
