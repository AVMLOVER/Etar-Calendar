/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * A countdown's actions: edit, save as an image, delete.
 *
 * Shown on a long press. The detail sheet covers the same ground for a tap, so both end up in the
 * same editor and the same delete confirmation - there is one of each, not one per entry point.
 */
package com.android.calendar.countdown.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.android.calendar.chrome.compose.ColorSwatch
import com.android.calendar.countdown.Countdown
import ws.xsoh.etar.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CountdownActionsSheet(
    countdown: Countdown,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onSaveImage: () -> Unit,
    onDelete: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            // What the actions apply to, so there is no doubt which row was pressed.
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ColorSwatch(color = countdown.color, size = 20.dp)
                Spacer(Modifier.size(12.dp))
                Column {
                    Text(countdown.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = countdown.date.format(
                            com.android.calendar.countdown.CountdownStore.DATE_FORMAT
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            ActionRow(
                label = stringResource(R.string.m3e_countdown_edit),
                iconRes = R.drawable.ic_edit_countdown,
                onClick = onEdit,
            )
            ActionRow(
                label = stringResource(R.string.m3e_countdown_save_image),
                iconRes = R.drawable.ic_download,
                onClick = onSaveImage,
            )
            ActionRow(
                label = stringResource(R.string.m3e_event_delete),
                iconRes = R.drawable.ic_delete,
                onClick = onDelete,
                destructive = true,
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ActionRow(
    label: String,
    iconRes: Int,
    onClick: () -> Unit,
    destructive: Boolean = false,
) {
    val tint = if (destructive) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
    ) {
        TextButton(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Start,
            ) {
                Icon(painter = painterResource(iconRes), contentDescription = null, tint = tint)
                Spacer(Modifier.size(16.dp))
                Text(text = label, color = tint)
            }
        }
    }
}
