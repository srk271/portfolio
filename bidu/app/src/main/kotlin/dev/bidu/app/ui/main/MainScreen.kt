package dev.bidu.app.ui.main

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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.bidu.app.R
import dev.bidu.app.assistant.AssistantUiState
import dev.bidu.app.ui.HomeStatus
import dev.bidu.app.ui.SectionTitle
import dev.bidu.app.ui.history.ConversationTurnCard
import dev.bidu.app.ui.homeStatus
import dev.bidu.app.ui.rememberPermissionGate
import dev.bidu.core.agent.AgentState
import dev.bidu.core.model.Capability
import dev.bidu.core.model.QuestionKind
import dev.bidu.core.model.UserQuestion
import dev.bidu.core.tool.ToolOutcome

@Composable
fun MainScreen(viewModel: MainViewModel, onOpenSetup: () -> Unit) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val turns by viewModel.turns.collectAsStateWithLifecycle()
    var command by rememberSaveable { mutableStateOf("") }
    var dismissedQuestion by rememberSaveable { mutableStateOf<String?>(null) }

    val gate = rememberPermissionGate(onResult = viewModel::refreshPermissions, onDenied = viewModel::permissionsDenied)
    val status = homeStatus(ui, settings.wakePhrases)
    val submit: () -> Unit = { if (viewModel.submit(command)) command = "" }
    val startListening: () -> Unit = { gate.request(viewModel.listeningPermissions()) { viewModel.setListening(true) } }
    val pushToTalk: () -> Unit = { gate.request(viewModel.microphonePermissions()) { viewModel.pushToTalk() } }

    val pending = ui.pendingConfirmation
    val report = ui.lastReport
    val question = if (report != null && report.outcome == ToolOutcome.NEEDS_USER && !ui.busy && dismissedQuestion != report.requestId) {
        report.question
    } else {
        null
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { StatusCard(status, ui) }
        if (pending != null) {
            item { ConfirmationCard(pending.prompt, onAnswer = { approved -> viewModel.answer(pending.id, approved) }) }
        }
        if (ui.missingCapabilities.isNotEmpty()) {
            item { MissingCapabilitiesCard(ui.missingCapabilities, onOpenSetup) }
        }
        if (report != null && question != null) {
            item {
                QuestionCard(
                    question = question,
                    onAnswer = { option -> viewModel.submit(option) },
                    onOpenSetup = onOpenSetup,
                    onDismiss = { dismissedQuestion = report.requestId },
                )
            }
        }
        item {
            Controls(
                listening = ui.voice.serviceRunning,
                busy = ui.busy,
                onStart = startListening,
                onStop = { viewModel.setListening(false) },
                onPushToTalk = pushToTalk,
                onCancel = viewModel::cancel,
            )
        }
        item { CommandField(value = command, onChange = { command = it }, onSubmit = submit) }
        item { SectionTitle(stringResource(R.string.ui_home_recent)) }
        if (turns.isEmpty()) {
            item {
                Text(
                    stringResource(R.string.ui_home_recent_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            items(turns) { turn -> ConversationTurnCard(turn) }
        }
    }
}

private fun workingDetail(state: AgentState): Int? = when (state) {
    AgentState.CAPTURING_COMMAND -> R.string.ui_state_capturing
    AgentState.UNDERSTANDING -> R.string.ui_state_understanding
    AgentState.PLANNING -> R.string.ui_state_planning
    AgentState.CHECKING_PERMISSIONS -> R.string.ui_state_checking_permissions
    AgentState.EXECUTING -> R.string.ui_state_executing
    AgentState.VERIFYING -> R.string.ui_state_verifying
    AgentState.REPORTING -> R.string.ui_state_reporting
    AgentState.IDLE, AgentState.LISTENING_WAKE, AgentState.ERROR -> null
}

@Composable
private fun statusLines(status: HomeStatus): Pair<String, String?> = when (status) {
    is HomeStatus.Listening -> Pair(
        if (status.phrases.isEmpty()) {
            stringResource(R.string.ui_status_listening_generic)
        } else {
            stringResource(R.string.ui_status_listening, status.phrases.joinToString(", ") { "“$it”" })
        },
        stringResource(R.string.ui_status_listening_detail),
    )
    is HomeStatus.Working -> {
        val detail = status.state?.let(::workingDetail)
        Pair(stringResource(R.string.ui_status_working), if (detail != null) stringResource(detail) else null)
    }
    is HomeStatus.Error -> {
        val reason = status.reason
        Pair(
            stringResource(R.string.ui_status_error),
            if (reason.isNullOrBlank()) stringResource(R.string.ui_status_error_unknown) else reason,
        )
    }
    HomeStatus.Paused -> Pair(stringResource(R.string.ui_status_paused), stringResource(R.string.ui_status_paused_detail))
    HomeStatus.Starting -> Pair(stringResource(R.string.ui_status_starting), null)
    HomeStatus.ListeningOff -> Pair(stringResource(R.string.ui_status_ptt_only), stringResource(R.string.ui_status_listening_off_detail))
    HomeStatus.WakeWordUnavailable -> Pair(stringResource(R.string.ui_status_ptt_only), stringResource(R.string.ui_status_no_model_detail))
}

@Composable
private fun StatusCard(status: HomeStatus, ui: AssistantUiState) {
    val (headline, detail) = statusLines(status)
    val partial = ui.voice.partialTranscript
    val latest = if (status is HomeStatus.Working) ui.activity.maxByOrNull { it.at } else null
    val lastError = ui.lastError
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = headline,
                style = MaterialTheme.typography.headlineSmall,
                color = if (status is HomeStatus.Error) MaterialTheme.colorScheme.error else Color.Unspecified,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
            if (detail != null) {
                Text(detail, style = MaterialTheme.typography.bodyMedium)
            }
            if (status is HomeStatus.Working && !partial.isNullOrBlank()) {
                Text(stringResource(R.string.ui_home_heard, partial), style = MaterialTheme.typography.bodyMedium)
            }
            if (latest != null) {
                Text(latest.text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (status !is HomeStatus.Error && !lastError.isNullOrBlank()) {
                Text(
                    stringResource(R.string.ui_home_last_error, lastError),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Text(
                stringResource(if (ui.voice.serviceRunning) R.string.ui_home_background_on else R.string.ui_home_background_off),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ConfirmationCard(prompt: String, onAnswer: (Boolean) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(R.string.ui_confirm_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
            Text(prompt, style = MaterialTheme.typography.bodyLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onAnswer(true) }) { Text(stringResource(R.string.ui_confirm_yes)) }
                OutlinedButton(onClick = { onAnswer(false) }) { Text(stringResource(R.string.ui_confirm_no)) }
            }
        }
    }
}

@Composable
private fun MissingCapabilitiesCard(missing: Set<Capability>, onOpenSetup: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(R.string.ui_home_missing, missing.joinToString(", ") { it.userLabel }),
                style = MaterialTheme.typography.bodyLarge,
            )
            Button(onClick = onOpenSetup) { Text(stringResource(R.string.ui_open_setup)) }
        }
    }
}

@Composable
private fun QuestionCard(question: UserQuestion, onAnswer: (String) -> Unit, onOpenSetup: () -> Unit, onDismiss: () -> Unit) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.ui_home_question_title), style = MaterialTheme.typography.titleMedium)
            Text(question.text, style = MaterialTheme.typography.bodyLarge)
            if (question.kind == QuestionKind.CHOICE) {
                question.options.forEach { option ->
                    OutlinedButton(onClick = { onAnswer(option) }, modifier = Modifier.fillMaxWidth()) { Text(option) }
                }
            }
            if (question.kind == QuestionKind.PERMISSION) {
                Button(onClick = onOpenSetup) { Text(stringResource(R.string.ui_open_setup)) }
            }
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.ui_dismiss)) }
        }
    }
}

@Composable
private fun Controls(
    listening: Boolean,
    busy: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onPushToTalk: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (listening) {
                OutlinedButton(onClick = onStop, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.ui_stop_listening))
                }
            } else {
                Button(onClick = onStart, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.ui_start_listening))
                }
            }
            Button(onClick = onPushToTalk, enabled = !busy, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.ui_push_to_talk))
            }
        }
        if (busy) {
            OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.ui_cancel_request))
            }
        }
    }
}

@Composable
private fun CommandField(value: String, onChange: (String) -> Unit, onSubmit: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            modifier = Modifier.weight(1f),
            label = { Text(stringResource(R.string.ui_command_label)) },
            placeholder = { Text(stringResource(R.string.ui_command_placeholder)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { onSubmit() }),
        )
        Spacer(Modifier.width(8.dp))
        Button(onClick = onSubmit, enabled = value.isNotBlank()) { Text(stringResource(R.string.ui_send)) }
    }
}
