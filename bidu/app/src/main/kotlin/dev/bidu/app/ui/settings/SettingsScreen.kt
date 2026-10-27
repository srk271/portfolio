package dev.bidu.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.bidu.app.R
import dev.bidu.app.settings.BiduSettings
import dev.bidu.app.settings.WakePhrase
import dev.bidu.app.ui.CollectMessages
import dev.bidu.app.ui.ConfirmDialog
import dev.bidu.app.ui.RadioGroup
import dev.bidu.app.ui.RadioOption
import dev.bidu.app.ui.SectionTitle
import dev.bidu.app.ui.SwitchRow
import dev.bidu.app.ui.displayPhrase
import dev.bidu.app.ui.rememberPermissionGate
import dev.bidu.app.ui.variantsText
import dev.bidu.app.ui.voiceChoices
import dev.bidu.core.model.Language

private typealias SettingsUpdate = ((BiduSettings) -> BiduSettings) -> Unit

@Composable
fun SettingsScreen(viewModel: SettingsViewModel, snackbar: SnackbarHostState, onOpenSetup: () -> Unit) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val ttsReady by viewModel.ttsReady.collectAsStateWithLifecycle()
    val tts by viewModel.tts.collectAsStateWithLifecycle()
    val hasApiKey by viewModel.hasApiKey.collectAsStateWithLifecycle()
    CollectMessages(viewModel.messages, snackbar)

    val gate = rememberPermissionGate(onResult = viewModel::refreshPermissions, onDenied = viewModel::permissionsDenied)
    val onListeningChange: (Boolean) -> Unit = { on ->
        if (on) {
            gate.request(viewModel.listeningPermissions()) { viewModel.setListening(true) }
        } else {
            viewModel.setListening(false)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        ListeningSection(listening = ui.voice.serviceRunning, onChange = onListeningChange)
        WakePhrasesSection(
            phrases = settings.wakePhrases,
            onSave = viewModel::saveWakePhrase,
            onDelete = viewModel::deleteWakePhrase,
            onReset = viewModel::resetWakePhrases,
        )
        LanguageSection(settings, viewModel::update)
        VoiceSection(settings, ttsReady, tts, viewModel::update, viewModel::testSpeak)
        RepliesSection(settings, viewModel::update)
        CloudSection(
            settings = settings,
            hasApiKey = hasApiKey,
            onEnable = viewModel::enableCloud,
            onDisable = viewModel::disableCloud,
            onSaveKey = viewModel::saveApiKey,
            onRemoveKey = viewModel::removeApiKey,
            onSaveModel = viewModel::saveCloudModel,
        )
        DataSection(
            onClearHistory = viewModel::clearHistory,
            onClearLog = viewModel::clearActionLog,
            onOpenSetup = onOpenSetup,
        )
    }
}

@Composable
private fun ListeningSection(listening: Boolean, onChange: (Boolean) -> Unit) {
    Column {
        SectionTitle(stringResource(R.string.ui_settings_listening))
        SwitchRow(
            title = stringResource(R.string.ui_settings_listening_switch),
            description = stringResource(if (listening) R.string.ui_settings_listening_on else R.string.ui_settings_listening_off),
            checked = listening,
            onCheckedChange = onChange,
        )
    }
}

private data class WakeEdit(val index: Int?, val phrase: String, val variants: String)

@Composable
private fun WakePhrasesSection(
    phrases: List<WakePhrase>,
    onSave: (Int?, String, String) -> Boolean,
    onDelete: (Int) -> Unit,
    onReset: () -> Unit,
) {
    var editing by remember { mutableStateOf<WakeEdit?>(null) }
    var confirmReset by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle(stringResource(R.string.ui_settings_wake))
        Text(
            stringResource(R.string.ui_settings_wake_help),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        phrases.forEachIndexed { index, phrase ->
            val name = displayPhrase(phrase.phrase)
            val editDescription = stringResource(R.string.ui_a11y_edit, name)
            val deleteDescription = stringResource(R.string.ui_a11y_delete, name)
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(name, style = MaterialTheme.typography.titleSmall)
                    Text(
                        stringResource(R.string.ui_settings_wake_variants, variantsText(phrase)),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(
                            onClick = { editing = WakeEdit(index, phrase.phrase, variantsText(phrase)) },
                            modifier = Modifier.semantics { contentDescription = editDescription },
                        ) { Text(stringResource(R.string.ui_edit)) }
                        TextButton(
                            onClick = { onDelete(index) },
                            enabled = phrases.size > 1,
                            modifier = Modifier.semantics { contentDescription = deleteDescription },
                        ) { Text(stringResource(R.string.ui_delete)) }
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { editing = WakeEdit(null, "", "") }) {
                Text(stringResource(R.string.ui_settings_wake_add))
            }
            TextButton(onClick = { confirmReset = true }) {
                Text(stringResource(R.string.ui_settings_wake_reset))
            }
        }
    }
    val current = editing
    if (current != null) {
        WakePhraseDialog(
            initial = current,
            onSave = { phrase, variants -> if (onSave(current.index, phrase, variants)) editing = null },
            onDismiss = { editing = null },
        )
    }
    if (confirmReset) {
        ConfirmDialog(
            title = stringResource(R.string.ui_settings_wake_reset),
            text = stringResource(R.string.ui_settings_wake_reset_confirm),
            confirmLabel = stringResource(R.string.ui_reset),
            onConfirm = onReset,
            onDismiss = { confirmReset = false },
        )
    }
}

@Composable
private fun WakePhraseDialog(initial: WakeEdit, onSave: (String, String) -> Unit, onDismiss: () -> Unit) {
    var phrase by remember(initial) { mutableStateOf(initial.phrase) }
    var variants by remember(initial) { mutableStateOf(initial.variants) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(if (initial.index == null) R.string.ui_settings_wake_add else R.string.ui_settings_wake_edit))
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = phrase,
                    onValueChange = { phrase = it },
                    label = { Text(stringResource(R.string.ui_settings_wake_phrase_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = variants,
                    onValueChange = { variants = it },
                    label = { Text(stringResource(R.string.ui_settings_wake_variants_label)) },
                    supportingText = { Text(stringResource(R.string.ui_settings_wake_variants_help)) },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(phrase, variants) }, enabled = phrase.isNotBlank()) {
                Text(stringResource(R.string.ui_save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ui_cancel)) } },
    )
}

@Composable
private fun LanguageSection(settings: BiduSettings, onUpdate: SettingsUpdate) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle(stringResource(R.string.ui_settings_language))
        Text(stringResource(R.string.ui_settings_reply_language), style = MaterialTheme.typography.labelLarge)
        RadioGroup(
            options = listOf(
                RadioOption(Language.HINGLISH, stringResource(R.string.ui_lang_hinglish)),
                RadioOption(Language.ENGLISH, stringResource(R.string.ui_lang_english)),
                RadioOption(Language.HINDI, stringResource(R.string.ui_lang_hindi)),
            ),
            selected = settings.responseLanguage,
            onSelect = { language -> onUpdate { it.copy(responseLanguage = language) } },
        )
        Text(stringResource(R.string.ui_settings_recognition_language), style = MaterialTheme.typography.labelLarge)
        RadioGroup(
            options = listOf(
                RadioOption(RECOGNIZER_EN_IN, stringResource(R.string.ui_recognition_en_in)),
                RadioOption(RECOGNIZER_HI_IN, stringResource(R.string.ui_recognition_hi_in)),
            ),
            selected = settings.recognizerLanguageTag,
            onSelect = { tag -> onUpdate { it.copy(recognizerLanguageTag = tag) } },
        )
        SwitchRow(
            title = stringResource(R.string.ui_settings_prefer_offline),
            description = stringResource(R.string.ui_settings_prefer_offline_desc),
            checked = settings.preferOfflineRecognition,
            onCheckedChange = { on -> onUpdate { it.copy(preferOfflineRecognition = on) } },
        )
    }
}

private const val RECOGNIZER_EN_IN = "en-IN"
private const val RECOGNIZER_HI_IN = "hi-IN"

private data class PickerOption(val value: String?, val label: String)

@Composable
private fun Picker(label: String, current: String, options: List<PickerOption>, enabled: Boolean, onPick: (String?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val description = "$label: $current"
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Box {
            OutlinedButton(
                onClick = { open = true },
                enabled = enabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = description },
            ) {
                Text(current, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.label) },
                        onClick = {
                            open = false
                            onPick(option.value)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun VoiceSection(
    settings: BiduSettings,
    ready: Boolean,
    tts: TtsChoices,
    onUpdate: SettingsUpdate,
    onTest: (String) -> Unit,
) {
    val systemDefault = stringResource(R.string.ui_settings_tts_system_default)
    val defaultVoice = stringResource(R.string.ui_settings_tts_default_voice)
    val voices = voiceChoices(tts.voices, settings.ttsEngine)
    val sample = stringResource(
        when (settings.responseLanguage) {
            Language.HINGLISH -> R.string.ui_tts_sample_hinglish
            Language.ENGLISH -> R.string.ui_tts_sample_english
            Language.HINDI -> R.string.ui_tts_sample_hindi
        },
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle(stringResource(R.string.ui_settings_voice))
        if (!ready) {
            Text(stringResource(R.string.ui_settings_tts_not_ready), style = MaterialTheme.typography.bodySmall)
        }
        Picker(
            label = stringResource(R.string.ui_settings_tts_engine),
            current = tts.engines.firstOrNull { it.packageName == settings.ttsEngine }?.label ?: settings.ttsEngine ?: systemDefault,
            options = listOf(PickerOption(null, systemDefault)) + tts.engines.map { PickerOption(it.packageName, it.label) },
            enabled = ready,
            onPick = { engine -> onUpdate { it.copy(ttsEngine = engine, ttsVoice = null) } },
        )
        Picker(
            label = stringResource(R.string.ui_settings_tts_voice),
            current = settings.ttsVoice ?: defaultVoice,
            options = listOf(PickerOption(null, defaultVoice)) + voices.map { PickerOption(it.name, "${it.name} (${it.languageTag})") },
            enabled = ready,
            onPick = { voice -> onUpdate { it.copy(ttsVoice = voice) } },
        )
        if (ready) {
            val available = stringResource(R.string.ui_available)
            val missing = stringResource(R.string.ui_not_installed)
            Text(
                stringResource(R.string.ui_settings_tts_hindi, if (tts.hindiAvailable) available else missing),
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                stringResource(R.string.ui_settings_tts_english_india, if (tts.englishIndiaAvailable) available else missing),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        OutlinedButton(onClick = { onTest(sample) }, enabled = ready) {
            Text(stringResource(R.string.ui_settings_tts_test))
        }
    }
}

@Composable
private fun RepliesSection(settings: BiduSettings, onUpdate: SettingsUpdate) {
    Column {
        SectionTitle(stringResource(R.string.ui_settings_replies))
        SwitchRow(
            title = stringResource(R.string.ui_settings_casual),
            description = stringResource(R.string.ui_settings_casual_desc),
            checked = settings.casualTone,
            onCheckedChange = { on -> onUpdate { it.copy(casualTone = on) } },
        )
        SwitchRow(
            title = stringResource(R.string.ui_settings_confirm_apps),
            checked = settings.confirmBeforeOpeningApps,
            onCheckedChange = { on -> onUpdate { it.copy(confirmBeforeOpeningApps = on) } },
        )
        SwitchRow(
            title = stringResource(R.string.ui_settings_confirm_handoffs),
            description = stringResource(R.string.ui_settings_confirm_handoffs_desc),
            checked = settings.confirmBeforeHandoffs,
            onCheckedChange = { on -> onUpdate { it.copy(confirmBeforeHandoffs = on) } },
        )
    }
}

@Composable
private fun CloudSection(
    settings: BiduSettings,
    hasApiKey: Boolean?,
    onEnable: () -> Unit,
    onDisable: () -> Unit,
    onSaveKey: (String) -> Boolean,
    onRemoveKey: () -> Unit,
    onSaveModel: (String) -> Unit,
) {
    var askConsent by remember { mutableStateOf(false) }
    // Deliberately not saveable: the key must never land in the saved-state bundle.
    var keyDraft by remember { mutableStateOf("") }
    var modelDraft by rememberSaveable(settings.cloudModel) { mutableStateOf(settings.cloudModel.orEmpty()) }
    val cloudOn = settings.cloudAllowed()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle(stringResource(R.string.ui_settings_cloud))
        SwitchRow(
            title = stringResource(R.string.ui_settings_cloud_switch),
            description = stringResource(R.string.ui_settings_cloud_desc),
            checked = cloudOn,
            onCheckedChange = { on ->
                if (on) {
                    askConsent = true
                } else {
                    onDisable()
                }
            },
        )
        Text(
            stringResource(R.string.ui_settings_cloud_billing),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (cloudOn && (hasApiKey == false || settings.cloudModel.isNullOrBlank())) {
            Text(
                stringResource(R.string.ui_settings_cloud_incomplete),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        Text(
            stringResource(
                when (hasApiKey) {
                    true -> R.string.ui_settings_cloud_key_stored
                    false -> R.string.ui_settings_cloud_key_none
                    null -> R.string.ui_settings_cloud_key_checking
                },
            ),
            style = MaterialTheme.typography.bodyMedium,
        )
        OutlinedTextField(
            value = keyDraft,
            onValueChange = { keyDraft = it },
            label = { Text(stringResource(R.string.ui_settings_cloud_key_label)) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { if (onSaveKey(keyDraft)) keyDraft = "" }, enabled = keyDraft.isNotBlank()) {
                Text(stringResource(R.string.ui_settings_cloud_key_save))
            }
            OutlinedButton(onClick = onRemoveKey, enabled = hasApiKey == true) {
                Text(stringResource(R.string.ui_settings_cloud_key_remove))
            }
        }
        OutlinedTextField(
            value = modelDraft,
            onValueChange = { modelDraft = it },
            label = { Text(stringResource(R.string.ui_settings_cloud_model_label)) },
            supportingText = { Text(stringResource(R.string.ui_settings_cloud_model_help)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth(),
        )
        Button(onClick = { onSaveModel(modelDraft) }, enabled = modelDraft.trim() != settings.cloudModel.orEmpty()) {
            Text(stringResource(R.string.ui_settings_cloud_model_save))
        }
    }
    if (askConsent) {
        AlertDialog(
            onDismissRequest = { askConsent = false },
            title = { Text(stringResource(R.string.ui_cloud_consent_title)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.cloud_consent_disclosure))
                    Text(stringResource(R.string.ui_cloud_consent_extra))
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    askConsent = false
                    onEnable()
                }) { Text(stringResource(R.string.ui_cloud_consent_agree)) }
            },
            dismissButton = {
                TextButton(onClick = { askConsent = false }) { Text(stringResource(R.string.ui_cancel)) }
            },
        )
    }
}

private enum class ClearTarget { HISTORY, ACTION_LOG }

@Composable
private fun DataSection(onClearHistory: () -> Unit, onClearLog: () -> Unit, onOpenSetup: () -> Unit) {
    var confirm by remember { mutableStateOf<ClearTarget?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle(stringResource(R.string.ui_settings_data))
        OutlinedButton(onClick = { confirm = ClearTarget.HISTORY }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.ui_settings_clear_history))
        }
        OutlinedButton(onClick = { confirm = ClearTarget.ACTION_LOG }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.ui_settings_clear_log))
        }
        SectionTitle(stringResource(R.string.ui_settings_setup))
        Button(onClick = onOpenSetup, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.ui_settings_open_setup))
        }
    }
    when (confirm) {
        ClearTarget.HISTORY -> ConfirmDialog(
            title = stringResource(R.string.ui_settings_clear_history),
            text = stringResource(R.string.ui_settings_clear_history_confirm),
            confirmLabel = stringResource(R.string.ui_clear),
            onConfirm = onClearHistory,
            onDismiss = { confirm = null },
        )
        ClearTarget.ACTION_LOG -> ConfirmDialog(
            title = stringResource(R.string.ui_settings_clear_log),
            text = stringResource(R.string.ui_settings_clear_log_confirm),
            confirmLabel = stringResource(R.string.ui_clear),
            onConfirm = onClearLog,
            onDismiss = { confirm = null },
        )
        null -> Unit
    }
}
