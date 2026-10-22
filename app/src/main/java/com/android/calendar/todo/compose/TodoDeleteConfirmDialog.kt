/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The one whole-to-do delete confirmation.
 *
 * Three places ask the same question: the editor's delete button, the title bar's delete action and
 * the swipe that removes a to-do's last line. They must show the same words, so the dialog lives here
 * once and each caller only decides what "confirm" means.
 */
package com.android.calendar.todo.compose

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import ws.xsoh.etar.R

/** "Delete this to-do?" with the same labels the editor has always used. */
@Composable
fun TodoDeleteConfirmDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.extraLarge,
        title = { Text(stringResource(R.string.m3e_todo_delete_title)) },
        text = { Text(stringResource(R.string.m3e_todo_delete_message)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.m3e_event_delete))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.m3e_back))
            }
        },
    )
}
