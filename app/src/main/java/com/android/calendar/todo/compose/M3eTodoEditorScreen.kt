/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The to-do editor: a name, the checklist, and whether and when to be reminded.
 *
 * Shown over the to-do screen rather than as an activity of its own - it belongs to the day the
 * screen is on, and closing it has to leave that day where it was.
 */
package com.android.calendar.todo.compose

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.android.calendar.todo.Todo
import com.android.calendar.todo.TodoItem
import com.android.calendar.todo.TodoStore
import ws.xsoh.etar.R
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun M3eTodoEditorScreen(
    initial: Todo,
    onCancel: () -> Unit,
    onSave: (Todo) -> Unit,
    onDelete: (Todo) -> Unit,
) {
    val context = LocalContext.current
    // A new to-do starts with one line, so there is somewhere to type without reaching for the add
    // button first; an existing one keeps the list it has.
    var name by remember { mutableStateOf(initial.name) }
    var items by remember {
        mutableStateOf(
            initial.items.ifEmpty { listOf(TodoStore.newItem()) }
        )
    }
    var remind by remember { mutableStateOf(initial.remind) }
    var minuteOfDay by remember { mutableStateOf(initial.remindMinuteOfDay) }
    var note by remember { mutableStateOf(initial.note) }
    var editingTime by remember { mutableStateOf(false) }
    var confirmingDelete by remember { mutableStateOf(false) }
    var pendingItemDelete by remember { mutableStateOf<TodoItem?>(null) }

    /** A to-do needs at least one line; without one there is nothing to save. */
    val hasItem = items.any { it.text.isNotBlank() }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Icons rather than words, the same three actions the event editor's own bar carries:
                // a close, a delete - the cross the event editor uses - and the floppy that saves.
                IconButton(onClick = onCancel) {
                    Icon(
                        painter = painterResource(R.drawable.ic_close),
                        contentDescription = stringResource(R.string.m3e_back),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Spacer(Modifier.weight(1f))
                if (initial.eventId > 0) {
                    IconButton(onClick = { confirmingDelete = true }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_m3e_event_delete),
                            contentDescription = stringResource(R.string.m3e_todo_delete),
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                // A to-do with no items is only a title, so it cannot be saved: the button stays
                // grey until at least one line has been written.
                IconButton(
                    onClick = {
                        onSave(
                            initial.copy(
                                name = name.trim().ifEmpty {
                                    context.getString(R.string.m3e_todo_title)
                                },
                                // Blank lines are not worth keeping; a checklist of empty rows
                                // would only be a list of things to delete.
                                items = items.filter { it.text.isNotBlank() },
                                note = note.trim(),
                                remind = remind,
                                remindMinuteOfDay = minuteOfDay,
                            )
                        )
                    },
                    enabled = hasItem,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.outline_save_event),
                        contentDescription = stringResource(R.string.create_event_dialog_save),
                        tint = if (hasItem) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        },
                    )
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Column {
                        Text(
                            text = stringResource(R.string.m3e_todo_edit_title),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = dayLabel(initial.date),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                item {
                    Card {
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text(stringResource(R.string.m3e_todo_name_hint)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                item {
                    Card {
                        CardTitle(stringResource(R.string.m3e_todo_items))
                        TodoItemColumn(
                            items = items,
                            editable = true,
                            onTextChange = { item, text ->
                                items = items.map { if (it.id == item.id) it.copy(text = text) else it }
                            },
                            onMove = { from, to -> items = items.moved(from, to) },
                            onDeleteRequest = { item -> pendingItemDelete = item },
                            modifier = Modifier.padding(top = 8.dp),
                        )
                        Spacer(Modifier.height(10.dp))
                        TodoAddItemRow(
                            text = stringResource(R.string.m3e_todo_add_item),
                            onClick = { items = items + TodoStore.newItem() },
                        )
                    }
                }

                // The note sits above the reminder: it is part of what the day is about, while the
                // reminder is only how the day is announced.
                item {
                    Card {
                        CardTitle(stringResource(R.string.m3e_todo_note))
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = note,
                            onValueChange = { note = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text(stringResource(R.string.m3e_todo_note_hint)) },
                            singleLine = false,
                            maxLines = 8,
                        )
                    }
                }

                item {
                    Card {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource(R.string.m3e_todo_remind),
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f),
                            )
                            Switch(checked = remind, onCheckedChange = { remind = it })
                        }
                        if (remind) {
                            HorizontalDivider()
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { editingTime = true }
                                    .padding(vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = stringResource(R.string.m3e_todo_remind_time),
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    text = timeLabel(minuteOfDay),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (editingTime) {
        val state = rememberTimePickerState(
            initialHour = minuteOfDay / 60,
            initialMinute = minuteOfDay % 60,
            is24Hour = android.text.format.DateFormat.is24HourFormat(context),
        )
        AlertDialog(
            onDismissRequest = { editingTime = false },
            shape = MaterialTheme.shapes.extraLarge,
            title = { Text(stringResource(R.string.m3e_todo_remind_time)) },
            text = { TimePicker(state = state) },
            confirmButton = {
                TextButton(onClick = {
                    minuteOfDay = state.hour * 60 + state.minute
                    editingTime = false
                }) { Text(stringResource(R.string.create_event_dialog_save)) }
            },
            dismissButton = {
                TextButton(onClick = { editingTime = false }) {
                    Text(stringResource(R.string.m3e_back))
                }
            },
        )
    }

    pendingItemDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { pendingItemDelete = null },
            shape = MaterialTheme.shapes.extraLarge,
            title = { Text(stringResource(R.string.m3e_todo_delete_item_title)) },
            text = { Text(stringResource(R.string.m3e_todo_delete_item_message, item.text)) },
            confirmButton = {
                TextButton(onClick = {
                    items = items.filterNot { it.id == item.id }
                    pendingItemDelete = null
                }) { Text(stringResource(R.string.m3e_event_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingItemDelete = null }) {
                    Text(stringResource(R.string.m3e_back))
                }
            },
        )
    }

    if (confirmingDelete) {
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            shape = MaterialTheme.shapes.extraLarge,
            title = { Text(stringResource(R.string.m3e_todo_delete_title)) },
            text = { Text(stringResource(R.string.m3e_todo_delete_message)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmingDelete = false
                    onDelete(initial)
                }) { Text(stringResource(R.string.m3e_event_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmingDelete = false }) {
                    Text(stringResource(R.string.m3e_back))
                }
            },
        )
    }
}

/** The rows of the list, in the order given, with one row moved from [from] to [to]. */
private fun List<TodoItem>.moved(from: Int, to: Int): List<TodoItem> {
    if (from == to || from !in indices || to !in indices) return this
    val list = toMutableList()
    val item = list.removeAt(from)
    list.add(to, item)
    return list
}

@Composable
private fun CardTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(modifier = Modifier.padding(16.dp)) { content() }
    }
}

@Composable
private fun dayLabel(date: LocalDate): String = remember(date) {
    java.text.DateFormat.getDateInstance(java.text.DateFormat.FULL).format(
        Date(date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli())
    )
}

@Composable
private fun timeLabel(minuteOfDay: Int): String = remember(minuteOfDay) {
    java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT)
        .format(Date(LocalDate.now().atStartOfDay(ZoneId.systemDefault())
            .plusMinutes(minuteOfDay.toLong()).toInstant().toEpochMilli()))
}
