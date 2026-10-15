/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The .ics import dialog, shared by the settings screen and the overflow menu.
 *
 * Both entry points offer the same two routes - a file on the device, or a url to download - so they
 * share one implementation rather than growing two that drift apart.
 */
package com.android.calendar.chrome.compose

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ws.xsoh.etar.R

/** The file the download lands in, in the one directory the app's FileProvider exposes. */
private const val ICS_FILE_NAME = "imported.ics"

/** Intent extra naming the calendar the import writes into, or -1 for the first writable one. */
const val EXTRA_ICS_CALENDAR_ID = "m3eIcsCalendarId"

/** One calendar the import may write to: id, name and display colour. */
private data class ImportCalendar(val id: Long, val name: String, val color: Int)

/** File picker restricted to calendar files. */
private fun icsPickerIntent(): Intent =
    Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
        addCategory(Intent.CATEGORY_OPENABLE)
        type = "*/*"
        putExtra(
            Intent.EXTRA_MIME_TYPES,
            arrayOf("text/calendar", "text/x-vcalendar", "application/ics"),
        )
    }

/**
 * The calendars this app may write to, with their display colours.
 *
 * Asked off the main thread - the import dialog composes before the user does anything, and the
 * query belongs on the IO dispatcher like the other calendar reads.
 */
private fun queryImportCalendars(context: Context): List<ImportCalendar> = try {
    val out = ArrayList<ImportCalendar>()
    context.contentResolver.query(
        android.provider.CalendarContract.Calendars.CONTENT_URI,
        arrayOf(
            android.provider.CalendarContract.Calendars._ID,
            android.provider.CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            android.provider.CalendarContract.Calendars.CALENDAR_COLOR,
            android.provider.CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL,
        ),
        null,
        null,
        android.provider.CalendarContract.Calendars.ACCOUNT_NAME + " ASC",
    )?.use { c ->
        while (c.moveToNext()) {
            if (c.getInt(3) < android.provider.CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR) {
                continue
            }
            out.add(
                ImportCalendar(
                    id = c.getLong(0),
                    name = c.getString(1).orEmpty(),
                    color = com.android.calendar.Utils.getDisplayColorFromColor(context, c.getInt(2)),
                )
            )
        }
    }
    out
} catch (t: Throwable) {
    emptyList()
}

/** Hands a calendar file to ImportActivity, which writes its events into the chosen calendar. */
fun startIcsImport(context: Context, uri: Uri, calendarId: Long = -1L) {
    runCatching {
        context.startActivity(
            Intent(context, com.android.calendar.ImportActivity::class.java)
                .setData(uri)
                .putExtra(EXTRA_ICS_CALENDAR_ID, calendarId)
        )
    }.onFailure {
        Toast.makeText(context, R.string.m3e_import_failed, Toast.LENGTH_SHORT).show()
    }
}

/**
 * Import from a local .ics file or by downloading one from a url.
 *
 * The download lands in the external cache directory, which is the only path the app's FileProvider
 * exposes (see res/xml/provider_paths.xml). ImportActivity is in the same package, so the uri needs
 * no explicit grant.
 */
@Composable
fun IcsImportDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var url by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    // The calendar the imported events are written into. Loaded once, off the main thread; the
    // first writable calendar is the initial choice, which is what the import used to pick silently.
    var calendars by remember { mutableStateOf<List<ImportCalendar>>(emptyList()) }
    var targetCalendarId by remember { mutableStateOf(-1L) }
    var calendarMenuOpen by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        val loaded = withContext(Dispatchers.IO) { queryImportCalendars(context) }
        calendars = loaded
        if (targetCalendarId == -1L) {
            targetCalendarId = loaded.firstOrNull()?.id ?: -1L
        }
    }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uri = result.data?.data
        if (uri != null) {
            onDismiss()
            startIcsImport(context, uri, targetCalendarId)
        }
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        shape = MaterialTheme.shapes.extraLarge,
        title = { Text(stringResource(R.string.cal_import_menu_title)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.m3e_import_dialog_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))

                // Where the events land. Every entry carries its calendar's own colour, so the
                // list reads like the calendar manager does.
                val selected = calendars.firstOrNull { it.id == targetCalendarId }
                Box {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !busy) { calendarMenuOpen = true }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.m3e_import_calendar_label),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .background(
                                    color = Color(selected?.color ?: 0),
                                    shape = CircleShape,
                                ),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = selected?.name.orEmpty(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    DropdownMenu(
                        expanded = calendarMenuOpen,
                        onDismissRequest = { calendarMenuOpen = false },
                    ) {
                        calendars.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.name) },
                                leadingIcon = {
                                    Box(
                                        modifier = Modifier
                                            .size(12.dp)
                                            .background(
                                                color = Color(option.color),
                                                shape = CircleShape,
                                            ),
                                    )
                                },
                                onClick = {
                                    calendarMenuOpen = false
                                    targetCalendarId = option.id
                                },
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text(stringResource(R.string.m3e_import_url_label)) },
                    singleLine = true,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = url.isNotBlank() && !busy,
                onClick = {
                    busy = true
                    scope.launch {
                        val uri = downloadIcs(context, url.trim())
                        busy = false
                        if (uri != null) {
                            onDismiss()
                            startIcsImport(context, uri, targetCalendarId)
                        } else {
                            Toast.makeText(
                                context, R.string.m3e_import_failed, Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                },
            ) {
                Text(
                    stringResource(
                        if (busy) R.string.m3e_import_downloading else R.string.m3e_import_download
                    )
                )
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { picker.launch(icsPickerIntent()) }) {
                    Text(stringResource(R.string.m3e_import_pick_file))
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.m3e_back)) }
            }
        },
    )
}

/**
 * A url that can be opened, given what someone typed.
 *
 * A browser fills in a missing scheme; URL() does not, and fails with "no protocol" instead. Typing
 * a bare host is the natural thing to do, so the scheme is supplied here: https, since a url worth
 * importing is served over it, and http only when it was asked for by name.
 */
private fun normalizeUrl(raw: String): String {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) {
        return trimmed
    }
    // A scheme is "letters:" at the start. Anything else is a bare host or path.
    val hasScheme = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://").containsMatchIn(trimmed)
    return if (hasScheme) trimmed else "https://$trimmed"
}

private suspend fun downloadIcs(context: Context, url: String): Uri? =
    withContext(Dispatchers.IO) {
        try {
            downloadIcsOrThrow(context, url)
        } catch (failure: Throwable) {
            // Said with its reason. A bare "could not import" leaves nothing to act on when the cause
            // is a wrong host, a 404, or a server that does not serve the file over http.
            val reason = failure.message ?: failure.javaClass.simpleName
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    context,
                    context.getString(R.string.m3e_import_failed_reason, reason),
                    Toast.LENGTH_LONG,
                ).show()
            }
            null
        }
    }

private fun downloadIcsOrThrow(context: Context, url: String): Uri {
    val target = normalizeUrl(url)
    val connection = (java.net.URL(target).openConnection() as java.net.HttpURLConnection).apply {
        connectTimeout = 20_000
        readTimeout = 30_000
        instanceFollowRedirects = true
        setRequestProperty("User-Agent", "Etar")
    }
    try {
        val status = connection.responseCode
        if (status !in 200..299) {
            throw java.io.IOException("HTTP $status from $target")
        }
        val dir = context.externalCacheDir ?: context.cacheDir
        val file = java.io.File(dir, ICS_FILE_NAME)
        connection.inputStream.use { input ->
            file.outputStream().use { output -> input.copyTo(output) }
        }
        if (file.length() == 0L) {
            throw java.io.IOException("empty response from $target")
        }
        return FileProvider.getUriForFile(context, context.packageName + ".provider", file)
    } finally {
        connection.disconnect()
    }
}
