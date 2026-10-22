/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The Material 3 Expressive screen for one calendar.
 *
 * Tapping a calendar in the calendars list used to do nothing but toggle it. It now opens this: the
 * same page the classic calendar put behind that tap, with the calendar's visible switch, the way
 * into the system's notification settings for it, its colour, a rename, and - for the calendars this
 * app owns - deleting it, followed by the calendar's own information.
 *
 * Everything here is Compose and Material 3 Expressive: the rows sit on the same high-container
 * surface the rest of the app's cards use, the dialogs are the Compose AlertDialog with the
 * extra-large corner token, and the colour picker is the same one the list already opens, so a colour
 * chosen here is the colour chosen there.
 */
package com.android.calendar.selectcalendars.compose

import android.app.Application
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.android.calendar.chrome.compose.M3eEdgeToEdgeContent
import com.android.calendar.persistence.CalendarRepository
import java.time.format.DateTimeFormatter
import java.time.LocalDate
import com.android.calendar.composeui.IcsExport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ws.xsoh.etar.R

/**
 * The screen for the calendar with [calendarId].
 *
 * @param reloadKey bumped by the host on every resume, which re-reads the calendar: the list, the
 *   colour dialog and the system settings screen can all have changed it while this screen was
 *   stopped, and the screen must describe the calendar as it is now.
 * @param onCalendarName called with the calendar's display name once it has been read, so the host
 *   can put it under the heading.
 * @param onFinished closes the screen, which is what back and a completed delete do: the list it was
 *   opened from is underneath and is revealed by the finish.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun M3eCalendarEditScreen(
    calendarId: Long,
    reloadKey: Int,
    onCalendarName: (String) -> Unit,
    onFinished: () -> Unit,
) {
    val context = LocalContext.current
    val app = remember(context) { context.applicationContext as Application }
    val repo = remember { CalendarsRepository(app) }
    val scope = rememberCoroutineScope()

    var calendar by remember { mutableStateOf<CalendarEntry?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var eventCount by remember { mutableStateOf(0L) }

    // The three dialogs survive a rotation. Their content is derived from the calendar that is on
    // screen, so only whether one is open has to be remembered; the name field keeps its own state
    // below.
    var colorDialogOpen by rememberSaveable { mutableStateOf(false) }
    var renameDialogOpen by rememberSaveable { mutableStateOf(false) }
    var deleteDialogOpen by rememberSaveable { mutableStateOf(false) }

    // The export runs off the main thread, so the screen shows a progress dialog and closes it with
    // either the exported count or the failure toast.
    var exporting by remember { mutableStateOf(false) }
    var exportedCount by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(calendarId, reloadKey) {
        val fresh = withContext(Dispatchers.IO) { repo.loadOne(calendarId) }
        loaded = true
        calendar = fresh
        if (fresh == null) {
            // The calendar is gone - deleted here a moment ago, or removed by its account while
            // this screen was in the background. There is nothing left to edit, so hand the user
            // back to the list instead of showing an empty page.
            onFinished()
            return@LaunchedEffect
        }
        onCalendarName(fresh.displayName)
        // The count is Nullable in the repository: a provider that cannot answer is not an error the
        // screen should take the app down for, so it reads as "none known" and the row shows zero.
        eventCount = withContext(Dispatchers.IO) {
            runCatching { CalendarRepository(app).queryNumberOfEvents(calendarId) }.getOrNull() ?: 0L
        }
    }

    if (!loaded) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            LoadingIndicator()
        }
        return
    }

    val entry = calendar
    if (entry == null) {
        // onFinished() above is closing the screen; drawing nothing avoids a flash of an empty page.
        return
    }

    // Where the export is written is the user's choice through the Storage Access Framework: the
    // document picker needs no storage permission and the app only ever writes the stream it is given.
    val exportLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("text/calendar"),
    ) { uri ->
        if (uri == null) {
            return@rememberLauncherForActivityResult
        }
        exporting = true
        scope.launch {
            val count = withContext(Dispatchers.IO) {
                runCatching {
                    val events = CalendarExportRepository(context).load(calendarId)
                    val text = IcsExport.build(events)
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(text.toByteArray(Charsets.UTF_8))
                    } ?: error("The chosen document could not be opened for writing")
                    events.size
                }.getOrNull()
            }
            exporting = false
            if (count == null) {
                Toast.makeText(
                    context,
                    R.string.m3e_calendar_export_failed,
                    Toast.LENGTH_SHORT,
                ).show()
            } else {
                exportedCount = count
            }
        }
    }

    // Edge to edge with the navigation bar left immersive, as on every other Material 3 screen: the
    // box consumes the bottom inset instead of turning it into padding.
    M3eEdgeToEdgeContent {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Spacer(Modifier.height(4.dp))

            // The actions on the calendar itself.
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            // The whole row is the switch's target, which is what a settings row
                            // that reads "display events" invites. The Switch itself takes no
                            // input of its own (null onCheckedChange): the toggleable row owns the
                            // gesture, so one tap cannot toggle twice and the accessibility node is
                            // the row rather than a switch inside a clickable row.
                            .toggleable(
                                value = entry.visible,
                                role = Role.Switch,
                                onValueChange = { visible ->
                                    calendar = entry.copy(visible = visible)
                                    scope.launch {
                                        withContext(Dispatchers.IO) {
                                            repo.setVisible(calendarId, visible)
                                        }
                                    }
                                },
                            )
                            .padding(horizontal = 20.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.m3e_calendar_show_events),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f),
                        )
                        Switch(
                            checked = entry.visible,
                            onCheckedChange = null,
                        )
                    }

                    RowDivider()

                    ActionRow(
                        icon = R.drawable.ic_outline_notifications,
                        label = stringResource(R.string.m3e_calendar_manage_notifications),
                        onClick = { openNotificationSettings(context) },
                    )

                    RowDivider()

                    ActionRow(
                        icon = R.drawable.outline_color_lens,
                        label = stringResource(R.string.m3e_calendar_color),
                        onClick = { colorDialogOpen = true },
                        // The current colour, so the row says what the choice is before it is
                        // opened. The same swatch the list draws, from the same helper.
                        trailing = {
                            CalendarColorSwatch(color = entry.color)
                            Spacer(Modifier.width(12.dp))
                        },
                    )

                    RowDivider()

                    ActionRow(
                        icon = R.drawable.ic_m3e_event_edit,
                        label = stringResource(R.string.m3e_calendar_rename),
                        onClick = { renameDialogOpen = true },
                    )

                    RowDivider()

                    // Directly below rename: both are "do something with this calendar's data", and
                    // this one hands the whole calendar to a file the user chooses. The icon is the
                    // import arrow mirrored, so the direction out of the calendar reads at a glance.
                    ActionRow(
                        icon = R.drawable.ic_m3e_export,
                        label = stringResource(R.string.m3e_calendar_export_ics),
                        onClick = {
                            val suggested = entry.displayName
                                .replace('/', '_')
                                .replace('\\', '_')
                                .ifEmpty { "calendar" } +
                                "-" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + ".ics"
                            exportLauncher.launch(suggested)
                        },
                    )

                    // Deleting is only offered for the calendars this app created - the same rule
                    // the list's swipe uses, from CalendarEntry.canBeDeleted. A calendar owned by
                    // an account is not this app's to remove, and the to-do calendar is excluded
                    // because every to-do lives in it.
                    if (entry.canBeDeleted) {
                        RowDivider()
                        ActionRow(
                            icon = R.drawable.ic_delete,
                            label = stringResource(R.string.m3e_calendar_delete),
                            accent = MaterialTheme.colorScheme.error,
                            onClick = { deleteDialogOpen = true },
                        )
                    }
                }
            }

            // The calendar's own information, the way the classic page ended with it.
            Text(
                text = stringResource(R.string.m3e_calendar_info),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp, top = 8.dp),
            )

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
                    Text(
                        text = pluralStringResource(
                            R.plurals.m3e_calendar_event_count,
                            eventCount.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                            eventCount,
                        ),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = calendarKind(entry),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }

    if (colorDialogOpen) {
        CalendarColorDialog(
            initial = entry.color,
            onDismiss = { colorDialogOpen = false },
            onPick = { color ->
                colorDialogOpen = false
                calendar = entry.copy(color = color)
                // The same write the list makes when its colour dot is used.
                scope.launch {
                    withContext(Dispatchers.IO) { repo.setColor(calendarId, color) }
                }
            },
        )
    }

    if (renameDialogOpen) {
        RenameCalendarDialog(
            currentName = entry.displayName,
            onDismiss = { renameDialogOpen = false },
            onRename = { name ->
                renameDialogOpen = false
                scope.launch {
                    val renamed = withContext(Dispatchers.IO) {
                        repo.setDisplayName(calendarId, name)
                    }
                    if (renamed) {
                        calendar = entry.copy(displayName = name)
                        onCalendarName(name)
                    } else {
                        Toast.makeText(
                            context, R.string.m3e_calendar_rename_failed, Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            },
        )
    }

    if (deleteDialogOpen) {
        // The same dialog the list's swipe opens: same message, same mandatory checkbox.
        CalendarDeleteDialog(
            name = entry.displayName,
            onDismiss = { deleteDialogOpen = false },
            onDelete = {
                deleteDialogOpen = false
                val account = entry.accountName.orEmpty()
                scope.launch {
                    // The same call the list's swipe-delete makes: the provider removes the
                    // calendar and, with it, its events.
                    val deleted = withContext(Dispatchers.IO) {
                        runCatching {
                            CalendarRepository(app).deleteLocalCalendar(account, calendarId)
                        }.getOrDefault(false)
                    }
                    if (deleted) {
                        // Finish rather than redraw: the list underneath is what the user should be
                        // looking at once the calendar is gone, and it re-reads itself on resume.
                        onFinished()
                    } else {
                        Toast.makeText(
                            context, R.string.m3e_calendar_delete_failed, Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            },
        )
    }

    if (exporting) {
        AlertDialog(
            onDismissRequest = {},
            shape = MaterialTheme.shapes.extraLarge,
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LoadingIndicator()
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(R.string.m3e_calendar_export_progress))
                }
            },
            confirmButton = {},
        )
    }

    // The count includes zero: an empty calendar exports a valid, empty VCALENDAR and says so.
    exportedCount?.let { count ->
        AlertDialog(
            onDismissRequest = { exportedCount = null },
            shape = MaterialTheme.shapes.extraLarge,
            text = { Text(stringResource(R.string.m3e_calendar_export_done, count)) },
            confirmButton = {
                TextButton(onClick = { exportedCount = null }) {
                    Text(stringResource(R.string.m3e_ok))
                }
            },
        )
    }
}

/** A divider between two rows of a card, inset so it stops short of the card's rounded corners. */
@Composable
private fun RowDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 20.dp),
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}

/**
 * One actionable row: leading icon, label, optional trailing content and a chevron.
 *
 * [accent] colours the icon and the chevron away from the neutral variant - the delete row is the
 * one that uses it, in the error colour, so it does not read as another ordinary setting.
 */
@Composable
private fun ActionRow(
    icon: Int,
    label: String,
    onClick: () -> Unit,
    accent: Color? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val iconTint = accent ?: MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(16.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = accent ?: MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        trailing?.invoke()
        Spacer(Modifier.width(6.dp))
        Icon(
            painter = painterResource(R.drawable.ic_widget_chevron_right),
            contentDescription = null,
            tint = iconTint,
        )
    }
}

/**
 * The rename dialog.
 *
 * Pre-filled with the name the calendar has now: a rename is usually a small edit to it, and an
 * empty field would make the user retype a name they can already see. The name is written through
 * the same CalendarsRepository the list uses.
 */
@Composable
private fun RenameCalendarDialog(
    currentName: String,
    onDismiss: () -> Unit,
    onRename: (String) -> Unit,
) {
    // Keyed on the current name: the dialog is composed only while it is open, but if the calendar
    // is renamed and the dialog opened again, the field must start from the new name.
    var name by rememberSaveable(currentName) { mutableStateOf(currentName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.extraLarge,
        title = { Text(stringResource(R.string.m3e_calendar_rename_title)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.m3e_calendar_name_label)) },
                singleLine = true,
                isError = name.isBlank(),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onRename(name.trim()) },
                // A calendar with no name cannot be picked out of the list, so the empty field is
                // refused rather than written.
                enabled = name.isNotBlank(),
            ) {
                Text(stringResource(R.string.m3e_calendar_rename_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.m3e_cancel))
            }
        },
    )
}

/**
 * The calendar's kind, as the information card says it.
 *
 * An offline calendar is the app's own: it exists on this phone and nowhere else. Every other
 * calendar belongs to an account, and naming the account is what tells the user where it syncs from.
 */
@Composable
private fun calendarKind(entry: CalendarEntry): String {
    if (entry.isOffline) {
        return stringResource(R.string.m3e_calendar_offline)
    }
    val account = entry.accountName?.takeIf { it.isNotBlank() }
        ?: stringResource(R.string.m3e_event_calendar_unknown)
    return stringResource(R.string.m3e_calendar_synced, account)
}

/**
 * Opens the system's notification settings for this app.
 *
 * Per-calendar notification control is the system's, not the app's: the calendar's channel settings
 * are the app's notification categories. ACTION_APP_NOTIFICATION_SETTINGS is that screen; the app
 * details screen is the fallback, because the notification one is missing on some builds and it is
 * the page that leads to the same place. Both are tried rather than one being assumed present:
 * resolveActivity can report nothing under package visibility rules even when the screen exists, so
 * the start itself is caught too.
 */
private fun openNotificationSettings(context: Context) {
    val notifications = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    val opened = runCatching {
        if (notifications.resolveActivity(context.packageManager) != null) {
            context.startActivity(notifications)
            true
        } else {
            false
        }
    }.getOrDefault(false)
    if (opened) {
        return
    }

    val details = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", context.packageName, null),
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(details)
    } catch (e: ActivityNotFoundException) {
        // No settings screen to open at all. Nothing is left for this app to do about a settings
        // screen the platform does not have, and taking the screen down over it would be worse.
    }
}
