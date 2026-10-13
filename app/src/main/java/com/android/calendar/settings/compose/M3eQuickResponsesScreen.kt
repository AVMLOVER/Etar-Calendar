/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * Material 3 Expressive quick-response editor.
 *
 * Replaces the classic QuickResponsePreferences fragment. It reads and writes exactly the same
 * storage (Utils.KEY_QUICK_RESPONSES, a String[]), so existing responses carry over untouched.
 */
package com.android.calendar.settings.compose

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.android.calendar.chrome.compose.m3eSystemBarPadding
import com.android.calendar.Utils
import com.android.calendar.chrome.compose.M3eChromeScaffold
import com.android.calendar.chrome.compose.M3eScreen
import ws.xsoh.etar.R

@Composable
fun M3eQuickResponsesScreen() {
    val context = androidx.compose.ui.platform.LocalContext.current

    // Same storage the classic screen used; sorted the same way for a stable order.
    var responses by remember {
        mutableStateOf(Utils.getQuickResponses(context).sortedArray())
    }
    var editingIndex by remember { mutableStateOf(-1) }

    if (editingIndex >= 0) {
        val index = editingIndex
        var text by remember(index) { mutableStateOf(responses[index]) }
        AlertDialog(
            onDismissRequest = { editingIndex = -1 },
            shape = MaterialTheme.shapes.extraLarge,
            title = { Text(stringResource(R.string.quick_response_settings_edit_title)) },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = false,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val updated = responses.copyOf()
                    updated[index] = text
                    responses = updated
                    Utils.setSharedPreference(context, Utils.KEY_QUICK_RESPONSES, updated)
                    editingIndex = -1
                }) { Text(stringResource(R.string.create_event_dialog_save)) }
            },
            dismissButton = {
                TextButton(onClick = { editingIndex = -1 }) {
                    Text(stringResource(R.string.m3e_back))
                }
            },
        )
    }

    M3eChromeScaffold(
        selected = M3eScreen.SETTINGS,
        title = { Text(stringResource(R.string.quick_response_settings)) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                // This screen's host is a plain ComponentActivity, so nothing else insets it.
                .m3eSystemBarPadding()
                .padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text(
                    text = stringResource(R.string.quick_response_settings_summary),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp, top = 4.dp, bottom = 6.dp),
                )
            }

            items(responses.size) { index ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { editingIndex = index },
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = responses[index],
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }

            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}
