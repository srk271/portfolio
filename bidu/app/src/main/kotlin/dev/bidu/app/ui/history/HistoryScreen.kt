package dev.bidu.app.ui.history

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
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.bidu.app.R
import dev.bidu.app.ui.StatusPill
import dev.bidu.app.ui.formatInstant
import dev.bidu.core.agent.ActionLogEntry
import dev.bidu.core.agent.ConversationTurn
import dev.bidu.core.tool.ToolOutcome

@Composable
fun OutcomeBadge(outcome: ToolOutcome, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val (label, container, content) = when (outcome) {
        ToolOutcome.VERIFIED -> Triple(R.string.ui_outcome_verified, colors.primaryContainer, colors.onPrimaryContainer)
        ToolOutcome.HANDED_OFF -> Triple(R.string.ui_outcome_handed_off, colors.secondaryContainer, colors.onSecondaryContainer)
        ToolOutcome.NEEDS_USER -> Triple(R.string.ui_outcome_needs_you, colors.tertiaryContainer, colors.onTertiaryContainer)
        ToolOutcome.FAILED -> Triple(R.string.ui_outcome_failed, colors.errorContainer, colors.onErrorContainer)
        ToolOutcome.CANCELLED -> Triple(R.string.ui_outcome_cancelled, colors.surfaceVariant, colors.onSurfaceVariant)
    }
    StatusPill(stringResource(label), container, content, modifier)
}

/** One request and Bidu's answer, as shown in the home screen's recent activity. */
@Composable
fun ConversationTurnCard(turn: ConversationTurn, modifier: Modifier = Modifier) {
    Card(modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutcomeBadge(turn.outcome)
                Spacer(Modifier.width(8.dp))
                Text(
                    formatInstant(turn.at),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(stringResource(R.string.ui_turn_you, turn.requestText), style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(R.string.ui_turn_bidu, turn.display.ifBlank { turn.spoken }),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

/** Read-only viewer of every tool run, newest first. */
@Composable
fun ActionLogList(entries: List<ActionLogEntry>, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Text(
                stringResource(R.string.ui_log_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (entries.isEmpty()) {
            item { Text(stringResource(R.string.ui_log_empty), style = MaterialTheme.typography.bodyMedium) }
        } else {
            items(entries) { entry -> ActionLogCard(entry) }
        }
    }
}

@Composable
private fun ActionLogCard(entry: ActionLogEntry) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(entry.toolName, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                OutcomeBadge(entry.outcome)
            }
            Text(
                stringResource(R.string.ui_log_meta, formatInstant(entry.at), entry.durationMillis, entry.attempts),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (entry.argsSummary.isNotBlank()) {
                Text(stringResource(R.string.ui_log_args, entry.argsSummary), style = MaterialTheme.typography.bodySmall)
            }
            if (entry.evidenceSummary.isNotBlank()) {
                Text(stringResource(R.string.ui_log_evidence, entry.evidenceSummary), style = MaterialTheme.typography.bodySmall)
            }
            val error = entry.errorCode
            if (error != null) {
                Text(
                    stringResource(R.string.ui_log_error, error.name),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}
