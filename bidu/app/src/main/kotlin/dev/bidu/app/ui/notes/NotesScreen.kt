package dev.bidu.app.ui.notes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.bidu.app.R
import dev.bidu.app.ui.CollectMessages
import dev.bidu.app.ui.ConfirmDialog
import dev.bidu.app.ui.formatInstant
import dev.bidu.app.ui.history.ActionLogList
import dev.bidu.core.memory.MemoryKeys
import dev.bidu.core.model.MemoryFact
import dev.bidu.core.model.Note
import dev.bidu.core.model.Reminder
import dev.bidu.core.model.ReminderStatus

private enum class NotesTab(val label: Int) {
    NOTES(R.string.ui_tab_notes),
    MEMORY(R.string.ui_tab_memory),
    REMINDERS(R.string.ui_tab_reminders),
    LOG(R.string.ui_tab_log),
}

@Composable
fun NotesScreen(viewModel: NotesViewModel, snackbar: SnackbarHostState) {
    CollectMessages(viewModel.messages, snackbar)
    var selected by rememberSaveable { mutableIntStateOf(0) }
    val tabs = NotesTab.entries
    val tab = tabs[selected.coerceIn(0, tabs.lastIndex)]
    Column(Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = tab.ordinal) {
            tabs.forEach { candidate ->
                Tab(
                    selected = candidate == tab,
                    onClick = { selected = candidate.ordinal },
                    text = { Text(stringResource(candidate.label)) },
                )
            }
        }
        when (tab) {
            NotesTab.NOTES -> NotesTabContent(viewModel)
            NotesTab.MEMORY -> MemoryTabContent(viewModel)
            NotesTab.REMINDERS -> RemindersTabContent(viewModel)
            NotesTab.LOG -> {
                val entries by viewModel.actionLog.collectAsStateWithLifecycle()
                ActionLogList(entries)
            }
        }
    }
}

private data class NoteEdit(val note: Note?)

@Composable
private fun NotesTabContent(viewModel: NotesViewModel) {
    val notes by viewModel.notes.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<NoteEdit?>(null) }
    var deleting by remember { mutableStateOf<Note?>(null) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Button(onClick = { editing = NoteEdit(null) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.ui_notes_add))
            }
        }
        if (notes.isEmpty()) {
            item { EmptyText(stringResource(R.string.ui_notes_empty)) }
        } else {
            items(notes) { note ->
                NoteCard(note, onEdit = { editing = NoteEdit(note) }, onDelete = { deleting = note })
            }
        }
    }
    val edit = editing
    if (edit != null) {
        NoteDialog(
            note = edit.note,
            onSave = { title, body -> if (viewModel.saveNote(edit.note, title, body)) editing = null },
            onDismiss = { editing = null },
        )
    }
    val doomed = deleting
    if (doomed != null) {
        ConfirmDialog(
            title = stringResource(R.string.ui_notes_delete_title),
            text = doomed.title?.takeIf { it.isNotBlank() } ?: doomed.body.take(120),
            confirmLabel = stringResource(R.string.ui_delete),
            onConfirm = { viewModel.deleteNote(doomed) },
            onDismiss = { deleting = null },
        )
    }
}

@Composable
private fun NoteCard(note: Note, onEdit: () -> Unit, onDelete: () -> Unit) {
    val name = note.title?.takeIf { it.isNotBlank() } ?: note.body.take(40)
    val editDescription = stringResource(R.string.ui_a11y_edit, name)
    val deleteDescription = stringResource(R.string.ui_a11y_delete, name)
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val title = note.title
            if (!title.isNullOrBlank()) {
                Text(title, style = MaterialTheme.typography.titleMedium)
            }
            Text(note.body, style = MaterialTheme.typography.bodyMedium)
            Text(
                stringResource(R.string.ui_notes_updated, formatInstant(note.updatedAt)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onEdit, modifier = Modifier.semantics { contentDescription = editDescription }) {
                    Text(stringResource(R.string.ui_edit))
                }
                TextButton(onClick = onDelete, modifier = Modifier.semantics { contentDescription = deleteDescription }) {
                    Text(stringResource(R.string.ui_delete))
                }
            }
        }
    }
}

@Composable
private fun NoteDialog(note: Note?, onSave: (String, String) -> Unit, onDismiss: () -> Unit) {
    var title by remember(note) { mutableStateOf(note?.title.orEmpty()) }
    var body by remember(note) { mutableStateOf(note?.body.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (note == null) R.string.ui_notes_add else R.string.ui_notes_edit)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.ui_notes_title_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = body,
                    onValueChange = { body = it },
                    label = { Text(stringResource(R.string.ui_notes_body_label)) },
                    minLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(title, body) }, enabled = body.isNotBlank()) { Text(stringResource(R.string.ui_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ui_cancel)) } },
    )
}

private data class FactEdit(val originalKey: String?, val key: String, val value: String)

@Composable
private fun MemoryTabContent(viewModel: NotesViewModel) {
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.reloadFacts() }
    val facts by viewModel.facts.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<FactEdit?>(null) }
    var deleting by remember { mutableStateOf<MemoryFact?>(null) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                stringResource(R.string.ui_memory_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            Button(onClick = { editing = FactEdit(null, "", "") }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.ui_memory_add))
            }
        }
        if (facts.isEmpty()) {
            item { EmptyText(stringResource(R.string.ui_memory_empty)) }
        } else {
            items(facts) { fact ->
                FactCard(
                    fact = fact,
                    onEdit = { editing = FactEdit(fact.key, fact.key, fact.value) },
                    onDelete = { deleting = fact },
                )
            }
        }
    }
    val edit = editing
    if (edit != null) {
        FactDialog(
            initial = edit,
            onSave = { key, value -> if (viewModel.saveFact(edit.originalKey, key, value)) editing = null },
            onDismiss = { editing = null },
        )
    }
    val doomed = deleting
    if (doomed != null) {
        ConfirmDialog(
            title = stringResource(R.string.ui_memory_delete_title),
            text = stringResource(R.string.ui_memory_delete_text, factLabel(doomed.key)),
            confirmLabel = stringResource(R.string.ui_delete),
            onConfirm = { viewModel.deleteFact(doomed.key) },
            onDismiss = { deleting = null },
        )
    }
}

/** Friendly name for a memory key; unknown keys are shown as they are stored. */
@Composable
private fun factLabel(key: String): String = when {
    key == MemoryKeys.USER_NAME -> stringResource(R.string.ui_memory_key_user_name)
    key == MemoryKeys.HOME_ADDRESS -> stringResource(R.string.ui_memory_key_home)
    key == MemoryKeys.WORK_ADDRESS -> stringResource(R.string.ui_memory_key_work)
    key == MemoryKeys.CITY -> stringResource(R.string.ui_memory_key_city)
    key == MemoryKeys.PREFERRED_CAB_PROVIDER -> stringResource(R.string.ui_memory_key_cab)
    key.startsWith(MemoryKeys.CONTACT_ALIAS_PREFIX) ->
        stringResource(R.string.ui_memory_key_contact, key.removePrefix(MemoryKeys.CONTACT_ALIAS_PREFIX))
    key.startsWith(MemoryKeys.PLACE_PREFIX) ->
        stringResource(R.string.ui_memory_key_place, key.removePrefix(MemoryKeys.PLACE_PREFIX))
    else -> key
}

@Composable
private fun FactCard(fact: MemoryFact, onEdit: () -> Unit, onDelete: () -> Unit) {
    val label = factLabel(fact.key)
    val editDescription = stringResource(R.string.ui_a11y_edit, label)
    val deleteDescription = stringResource(R.string.ui_a11y_delete, label)
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = MaterialTheme.typography.titleSmall)
            Text(fact.value, style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(R.string.ui_memory_meta, fact.key, formatInstant(fact.updatedAt)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onEdit, modifier = Modifier.semantics { contentDescription = editDescription }) {
                    Text(stringResource(R.string.ui_edit))
                }
                TextButton(onClick = onDelete, modifier = Modifier.semantics { contentDescription = deleteDescription }) {
                    Text(stringResource(R.string.ui_delete))
                }
            }
        }
    }
}

private val COMMON_KEYS = listOf(
    MemoryKeys.HOME_ADDRESS,
    MemoryKeys.WORK_ADDRESS,
    MemoryKeys.CITY,
    MemoryKeys.USER_NAME,
    MemoryKeys.PREFERRED_CAB_PROVIDER,
    MemoryKeys.CONTACT_ALIAS_PREFIX + "<name>",
    MemoryKeys.PLACE_PREFIX + "<name>",
)

@Composable
private fun FactDialog(initial: FactEdit, onSave: (String, String) -> Unit, onDismiss: () -> Unit) {
    var key by remember(initial) { mutableStateOf(initial.key) }
    var value by remember(initial) { mutableStateOf(initial.value) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (initial.originalKey == null) R.string.ui_memory_add else R.string.ui_memory_edit)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it },
                    label = { Text(stringResource(R.string.ui_memory_key_label)) },
                    supportingText = { Text(stringResource(R.string.ui_memory_key_help, COMMON_KEYS.joinToString(", "))) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    label = { Text(stringResource(R.string.ui_memory_value_label)) },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(key, value) }, enabled = key.isNotBlank() && value.isNotBlank()) {
                Text(stringResource(R.string.ui_save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ui_cancel)) } },
    )
}

@Composable
private fun RemindersTabContent(viewModel: NotesViewModel) {
    val reminders by viewModel.reminders.collectAsStateWithLifecycle()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                stringResource(R.string.ui_reminders_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (reminders.isEmpty()) {
            item { EmptyText(stringResource(R.string.ui_reminders_empty)) }
        } else {
            items(reminders) { reminder -> ReminderCard(reminder) }
        }
    }
}

@Composable
private fun ReminderCard(reminder: Reminder) {
    val status = stringResource(
        when (reminder.status) {
            ReminderStatus.SCHEDULED -> R.string.ui_reminder_scheduled
            ReminderStatus.FIRED -> R.string.ui_reminder_fired
            ReminderStatus.DISMISSED -> R.string.ui_reminder_dismissed
            ReminderStatus.CANCELLED -> R.string.ui_reminder_cancelled
        },
    )
    val timing = stringResource(if (reminder.exact) R.string.ui_reminder_exact else R.string.ui_reminder_inexact)
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(reminder.text, style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(R.string.ui_reminder_meta, formatInstant(reminder.dueAt), status, timing),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EmptyText(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
