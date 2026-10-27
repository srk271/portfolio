package dev.bidu.app.ui.onboarding

import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.bidu.app.R
import dev.bidu.app.ui.CollectMessages
import dev.bidu.app.ui.StatusPill
import dev.bidu.app.ui.appDetailsIntent
import dev.bidu.app.ui.downloadFraction
import dev.bidu.app.ui.fallbackSettingsIntent
import dev.bidu.app.ui.findActivity
import dev.bidu.app.ui.hasPermission
import dev.bidu.app.ui.openSettings
import dev.bidu.app.voice.ModelState
import dev.bidu.app.voice.VoskModelManager
import dev.bidu.core.model.Capability
import dev.bidu.core.model.PermissionStatus

private data class SetupItem(val capability: Capability, val title: Int, val description: Int, val optional: Boolean = false)

// Battery stays last: the Samsung steps card follows it.
private val SETUP_ITEMS = listOf(
    SetupItem(Capability.RECORD_AUDIO, R.string.ui_setup_mic_title, R.string.ui_setup_mic_desc),
    SetupItem(Capability.POST_NOTIFICATIONS, R.string.ui_setup_notifications_title, R.string.ui_setup_notifications_desc),
    SetupItem(Capability.READ_CONTACTS, R.string.ui_setup_contacts_title, R.string.ui_setup_contacts_desc, optional = true),
    SetupItem(Capability.DISPLAY_OVER_OTHER_APPS, R.string.ui_setup_overlay_title, R.string.ui_setup_overlay_desc),
    SetupItem(Capability.EXACT_ALARMS, R.string.ui_setup_alarms_title, R.string.ui_setup_alarms_desc),
    SetupItem(Capability.IGNORE_BATTERY_OPTIMIZATIONS, R.string.ui_setup_battery_title, R.string.ui_setup_battery_desc),
)

@Composable
fun OnboardingScreen(
    viewModel: OnboardingViewModel,
    snackbar: SnackbarHostState,
    onStarted: () -> Unit,
    onLeave: () -> Unit,
) {
    val statuses by viewModel.statuses.collectAsStateWithLifecycle()
    val model by viewModel.model.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    CollectMessages(viewModel.messages, snackbar)

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }

    // Capabilities whose runtime dialog Android will no longer show; their button opens app settings instead.
    var blocked by remember { mutableStateOf(emptySet<Capability>()) }
    var requesting by rememberSaveable { mutableStateOf<Capability?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        val capability = requesting
        requesting = null
        viewModel.refresh()
        val activity = context.findActivity()
        val refusedForGood = activity != null && result.any { (permission, granted) ->
            !granted && !activity.shouldShowRequestPermissionRationale(permission)
        }
        if (capability != null && refusedForGood) blocked = blocked + capability
    }

    // Only permissions not yet granted can be asked for; a granted permission whose capability is still
    // missing (notifications switched off in system settings) needs the settings screen instead.
    val missingRuntime: (Capability) -> List<String> = { capability ->
        viewModel.runtimePermissions(capability).filterNot { context.hasPermission(it) }
    }
    val act: (Capability) -> Unit = { capability ->
        val runtime = missingRuntime(capability)
        if (runtime.isNotEmpty() && capability !in blocked) {
            requesting = capability
            launcher.launch(runtime.toTypedArray())
        } else {
            val primary = viewModel.settingsIntent(capability) ?: fallbackSettingsIntent(context, capability)
            if (!context.openSettings(primary, appDetailsIntent(context))) viewModel.settingsUnavailable()
        }
    }

    val micGranted = viewModel.statusOf(statuses, Capability.RECORD_AUDIO) == PermissionStatus.GRANTED
    val notificationsGranted = viewModel.statusOf(statuses, Capability.POST_NOTIFICATIONS) == PermissionStatus.GRANTED

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Text(stringResource(R.string.ui_setup_intro), style = MaterialTheme.typography.bodyLarge) }
        items(SETUP_ITEMS) { entry ->
            val capability = entry.capability
            val status = viewModel.statusOf(statuses, capability)
            val usesDialog = missingRuntime(capability).isNotEmpty() && capability !in blocked
            PermissionRow(
                title = stringResource(entry.title),
                description = stringResource(entry.description),
                status = status,
                optional = entry.optional,
                actionLabel = stringResource(if (usesDialog) R.string.ui_setup_allow else R.string.ui_setup_open_settings),
                onAction = { act(capability) },
            )
        }
        item {
            SamsungBatteryCard(
                onOpenAppSettings = {
                    if (!context.openSettings(appDetailsIntent(context))) viewModel.settingsUnavailable()
                },
                onOpenBatterySettings = {
                    if (!context.openSettings(viewModel.batterySettingsIntent(), appDetailsIntent(context))) viewModel.settingsUnavailable()
                },
            )
        }
        item {
            ModelCard(
                state = model,
                consentGiven = settings.voskModelConsent,
                onDownload = viewModel::downloadModel,
                onCancel = viewModel::cancelDownload,
            )
        }
        item { PrivacyCard() }
        item {
            StartCard(
                ready = micGranted && notificationsGranted,
                modelInstalled = model is ModelState.Installed,
                firstRun = !settings.onboardingComplete,
                onStart = {
                    viewModel.startBidu()
                    onStarted()
                },
                onLeave = onLeave,
            )
        }
    }
}

@Composable
private fun PermissionRow(
    title: String,
    description: String,
    status: PermissionStatus,
    optional: Boolean,
    actionLabel: String,
    onAction: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val (label, container, content) = when (status) {
        PermissionStatus.GRANTED -> Triple(stringResource(R.string.ui_status_granted), colors.primaryContainer, colors.onPrimaryContainer)
        PermissionStatus.DENIED ->
            if (optional) {
                Triple(stringResource(R.string.ui_status_optional_missing), colors.surfaceVariant, colors.onSurfaceVariant)
            } else {
                Triple(stringResource(R.string.ui_status_missing), colors.errorContainer, colors.onErrorContainer)
            }
        PermissionStatus.UNAVAILABLE -> Triple(stringResource(R.string.ui_status_unavailable), colors.surfaceVariant, colors.onSurfaceVariant)
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                StatusPill(label, container, content)
            }
            Text(description, style = MaterialTheme.typography.bodyMedium)
            if (status == PermissionStatus.DENIED) {
                val accessibleLabel = "$actionLabel: $title"
                Button(onClick = onAction, modifier = Modifier.semantics { contentDescription = accessibleLabel }) {
                    Text(actionLabel)
                }
            }
        }
    }
}

@Composable
private fun SamsungBatteryCard(onOpenAppSettings: () -> Unit, onOpenBatterySettings: () -> Unit) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.ui_setup_samsung_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.ui_setup_samsung_steps), style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(onClick = onOpenAppSettings, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.ui_setup_open_app_battery))
            }
            OutlinedButton(onClick = onOpenBatterySettings, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.ui_setup_open_device_battery))
            }
        }
    }
}

@Composable
private fun ModelCard(state: ModelState, consentGiven: Boolean, onDownload: () -> Unit, onCancel: () -> Unit) {
    val context = LocalContext.current
    var askConsent by remember { mutableStateOf(false) }
    val approxSize = Formatter.formatShortFileSize(context, VoskModelManager.APPROX_SIZE_BYTES)
    val requestDownload: () -> Unit = {
        if (consentGiven) {
            onDownload()
        } else {
            askConsent = true
        }
    }
    val colors = MaterialTheme.colorScheme
    val (label, container, content) = when (state) {
        ModelState.NotInstalled -> Triple(stringResource(R.string.ui_model_not_installed), colors.surfaceVariant, colors.onSurfaceVariant)
        is ModelState.Downloading -> Triple(stringResource(R.string.ui_model_downloading), colors.secondaryContainer, colors.onSecondaryContainer)
        ModelState.Extracting -> Triple(stringResource(R.string.ui_model_extracting), colors.secondaryContainer, colors.onSecondaryContainer)
        is ModelState.Installed -> Triple(stringResource(R.string.ui_model_installed), colors.primaryContainer, colors.onPrimaryContainer)
        is ModelState.Failed -> Triple(stringResource(R.string.ui_model_failed), colors.errorContainer, colors.onErrorContainer)
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.ui_setup_model_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                StatusPill(label, container, content)
            }
            Text(stringResource(R.string.ui_setup_model_desc, approxSize), style = MaterialTheme.typography.bodyMedium)
            when (state) {
                ModelState.NotInstalled -> Button(onClick = requestDownload) {
                    Text(stringResource(R.string.ui_setup_model_download, approxSize))
                }
                is ModelState.Downloading -> {
                    val fraction = downloadFraction(state)
                    if (fraction != null) {
                        LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    val done = Formatter.formatShortFileSize(context, state.downloadedBytes)
                    val total = Formatter.formatShortFileSize(context, state.totalBytes ?: VoskModelManager.APPROX_SIZE_BYTES)
                    Text(stringResource(R.string.ui_setup_model_progress, done, total), style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick = onCancel) { Text(stringResource(R.string.ui_setup_model_cancel)) }
                }
                ModelState.Extracting -> {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Text(stringResource(R.string.ui_setup_model_unpacking), style = MaterialTheme.typography.bodySmall)
                }
                is ModelState.Installed -> Text(
                    stringResource(R.string.ui_setup_model_ready, state.name),
                    style = MaterialTheme.typography.bodySmall,
                )
                is ModelState.Failed -> {
                    Text(
                        stringResource(R.string.ui_setup_model_error, state.message),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Button(onClick = requestDownload) { Text(stringResource(R.string.ui_retry)) }
                }
            }
        }
    }
    if (askConsent) {
        AlertDialog(
            onDismissRequest = { askConsent = false },
            title = { Text(stringResource(R.string.ui_model_consent_title)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.model_consent_disclosure))
                    Text(stringResource(R.string.ui_model_consent_details, VoskModelManager.MODEL_URL))
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    askConsent = false
                    onDownload()
                }) { Text(stringResource(R.string.ui_model_consent_agree)) }
            },
            dismissButton = {
                TextButton(onClick = { askConsent = false }) { Text(stringResource(R.string.ui_not_now)) }
            },
        )
    }
}

@Composable
private fun PrivacyCard() {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.ui_privacy_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.ui_privacy_text), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun StartCard(ready: Boolean, modelInstalled: Boolean, firstRun: Boolean, onStart: () -> Unit, onLeave: () -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!ready) {
            Text(
                stringResource(R.string.ui_setup_start_needs),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        } else if (!modelInstalled) {
            Text(stringResource(R.string.ui_setup_start_without_model), style = MaterialTheme.typography.bodyMedium)
        }
        Button(onClick = onStart, enabled = ready, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.ui_setup_start))
        }
        TextButton(onClick = onLeave, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(if (firstRun) R.string.ui_setup_skip else R.string.ui_setup_done))
        }
    }
}
