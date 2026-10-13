/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The countdown's background picture and transparent-background rows, shared.
 *
 * The countdown screen's editor and the event editor both create a countdown, and both offer these
 * two settings for it. They live here so the two stay the same rather than drifting: a countdown made
 * either way has the same options and stores them the same way.
 */
package com.android.calendar.countdown.compose

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ws.xsoh.etar.R

/**
 * The background picture, the switch that decides whether it shows through, and the hint that
 * explains the switch.
 *
 * @param imageUri the chosen picture, or null for none.
 * @param transparent whether the picture is shown bare.
 * @param accent the countdown's colour, used for the switch so the control matches what it affects.
 */
@Composable
fun CountdownBackgroundRows(
    imageUri: String?,
    transparent: Boolean,
    onImageChange: (String?) -> Unit,
    onTransparentChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    // OpenDocument rather than the photo picker: the choice is not limited to the gallery, and its
    // result is a uri a lasting read grant can be taken on, which is what keeps the picture working
    // after a restart.
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { picked ->
            try {
                context.contentResolver.takePersistableUriPermission(
                    picked,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            } catch (t: Throwable) {
                // Some providers hand back a uri that cannot be persisted. Keeping it still works for
                // this session, which is better than refusing the choice.
            }
            onImageChange(picked.toString())
        }
    }

    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { picker.launch(arrayOf("image/*")) }) {
                Text(
                    stringResource(
                        if (imageUri == null) {
                            R.string.m3e_countdown_pick_background
                        } else {
                            R.string.m3e_countdown_change_background
                        }
                    )
                )
            }
            if (imageUri != null) {
                TextButton(onClick = { onImageChange(null) }) {
                    Text(stringResource(R.string.m3e_countdown_clear_background))
                }
            }
        }
        if (imageUri != null) {
            CheckRow(
                label = stringResource(R.string.m3e_countdown_transparent_background),
                checked = transparent,
                onChange = onTransparentChange,
            )
            Text(
                text = stringResource(R.string.m3e_countdown_transparent_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** A checkbox with its label, tappable along the whole row. */
@Composable
fun CheckRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .clickable { onChange(!checked) }
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = onChange)
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
    }
}
