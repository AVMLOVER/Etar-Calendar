/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The confirmation for deleting a calendar.
 *
 * Shared by the two places that can delete one - the swipe on the calendars list and the delete row
 * on the per-calendar screen - so the two ask the same question in the same words. Deleting a
 * calendar takes its events with it and has no undo, so the confirmation is not a single tap: a
 * checkbox records that the consequence was read, and the delete button stays disabled until it is
 * ticked. A second copy of this dialog on the other screen would be free to drift, and a deletion
 * confirmed on one screen would look less serious than the same deletion on the other.
 */
package com.android.calendar.selectcalendars.compose

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import ws.xsoh.etar.R

/**
 * Asks whether the calendar named [name] should be deleted.
 *
 * [onDelete] is only reachable after the checkbox has been ticked; dismissing the dialog calls
 * [onDismiss] and leaves the calendar alone.
 */
@Composable
internal fun CalendarDeleteDialog(
    name: String,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
) {
    // Ticking the box is part of the confirmation, so it survives a rotation with the dialog itself.
    var confirmed by rememberSaveable { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.extraLarge,
        title = { Text(stringResource(R.string.m3e_calendar_delete_confirm_title)) },
        text = {
            Column {
                Text(stringResource(R.string.m3e_calendar_delete_confirm_message, name))
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        // The label is part of the target, not just the box: the checkbox is about
                        // the sentence beside it, and the row as a whole is what the user reads.
                        .toggleable(
                            value = confirmed,
                            role = Role.Checkbox,
                            onValueChange = { confirmed = it },
                        )
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = confirmed,
                        onCheckedChange = null,
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.m3e_calendar_delete_checkbox),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDelete, enabled = confirmed) {
                Text(
                    text = stringResource(R.string.m3e_calendar_delete),
                    color = if (confirmed) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    },
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.m3e_cancel))
            }
        },
    )
}
