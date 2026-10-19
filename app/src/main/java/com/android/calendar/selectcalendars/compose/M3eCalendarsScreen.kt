/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * Material 3 Expressive "calendars to display" screen.
 */
package com.android.calendar.selectcalendars.compose

import androidx.compose.ui.res.painterResource
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.android.calendar.persistence.CalendarRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ws.xsoh.etar.R

/**
 * The list of calendars the user can show, and the way into each one's own settings.
 *
 * A row is no longer only a switch and a colour dot: tapping it opens the per-calendar screen, which
 * is where the calendar is renamed, its colour changed through the same picker, and - when this app
 * owns it - where it is deleted.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun M3eCalendarsScreen(
    onBack: () -> Unit,
    /**
     * Bumped by the host on every resume.
     *
     * The per-calendar screen this list opens edits the same rows - a name, a colour, the visible
     * flag, or the calendar itself when it is deleted - so the list has to re-read them when it
     * comes back to the front. Without this it kept the values it had loaded when it was opened,
     * and a calendar renamed or deleted on the screen above it came back unchanged.
     */
    externalReloadKey: Int = 0,
) {
    val context = LocalContext.current
    val repo = remember { CalendarsRepository(context.applicationContext) }
    val scope = rememberCoroutineScope()

    var loading by remember { mutableStateOf(true) }
    var calendars by remember { mutableStateOf<List<CalendarEntry>>(emptyList()) }
    var colorTarget by remember { mutableStateOf<CalendarEntry?>(null) }
    var showOfflineDialog by remember { mutableStateOf(false) }
    // The offline calendar the delete confirmation is open for.
    var deleteTarget by remember { mutableStateOf<CalendarEntry?>(null) }
    // Bumped after a calendar is created so the list picks it up.
    var reloadKey by remember { mutableStateOf(0) }

    LaunchedEffect(reloadKey, externalReloadKey) {
        calendars = withContext(Dispatchers.IO) { repo.load() }
        loading = false
    }

    if (showOfflineDialog) {
        OfflineCalendarDialog(
            onDismiss = { showOfflineDialog = false },
            onCreated = {
                showOfflineDialog = false
                reloadKey++
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.m3e_calendars_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                            Icon(
                                painter = painterResource(R.drawable.ic_arrow_back),
                                contentDescription = stringResource(R.string.m3e_back),
                            )
                        }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        if (loading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { LoadingIndicator() }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                // The three "add a calendar" actions that used to live in the classic settings.
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                        AddCalendarRow(
                            title = stringResource(R.string.preferences_list_add_remote),
                            onClick = { launchCalDav(context) },
                        )
                        AddCalendarRow(
                            title = stringResource(R.string.preferences_list_add_remote_etesync),
                            onClick = { launchEteSync(context) },
                        )
                        AddCalendarRow(
                            title = stringResource(R.string.preferences_list_add_offline),
                            onClick = { showOfflineDialog = true },
                        )
                    }
                }
            }

            item {
                Text(
                    text = stringResource(R.string.m3e_calendars_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp, top = 4.dp, bottom = 6.dp),
                )
            }

            items(calendars, key = { it.id }) { entry ->
                CalendarRow(
                    entry = entry,
                    onOpen = {
                        context.startActivity(
                            ComposeCalendarEditActivity.intent(context, entry.id)
                        )
                    },
                    onToggle = { checked ->
                        calendars = calendars.map {
                            if (it.id == entry.id) it.copy(visible = checked) else it
                        }
                        repo.setVisible(entry.id, checked)
                    },
                    onPickColor = { colorTarget = entry },
                    // Only calendars this app created can be deleted; every other calendar is
                    // owned by its account and stays. The to-do calendar is excluded too, and that
                    // rule lives in CalendarEntry.canBeDeleted so this screen and the per-calendar
                    // one cannot disagree about it.
                    onDelete = if (entry.canBeDeleted) {
                        { deleteTarget = entry }
                    } else {
                        null
                    },
                )
            }

            item {
                Spacer(Modifier.height(8.dp))
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            context.startActivity(Intent(Settings.ACTION_SYNC_SETTINGS))
                        },
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    Text(
                        text = stringResource(R.string.select_synced_calendars_button),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        }
    }

    colorTarget?.let { target ->
        CalendarColorDialog(
            initial = target.color,
            onDismiss = { colorTarget = null },
            onPick = { color ->
                calendars = calendars.map {
                    if (it.id == target.id) it.copy(color = color) else it
                }
                repo.setColor(target.id, color)
                colorTarget = null
            },
        )
    }

    // The confirmation for a swiped calendar. Deleting it removes the calendar and, with it, its
    // events - which the dialog says plainly, and which the checkbox has to be ticked for. The same
    // dialog is what the per-calendar screen opens for its delete row.
    deleteTarget?.let { target ->
        CalendarDeleteDialog(
            name = target.displayName,
            onDismiss = { deleteTarget = null },
            onDelete = {
                val app = context.applicationContext as Application
                val calendarId = target.id
                val account = target.accountName.orEmpty()
                deleteTarget = null
                scope.launch {
                    val deleted = withContext(Dispatchers.IO) {
                        runCatching {
                            CalendarRepository(app).deleteLocalCalendar(account, calendarId)
                        }.getOrDefault(false)
                    }
                    if (deleted) {
                        calendars = calendars.filterNot { it.id == calendarId }
                    } else {
                        Toast.makeText(
                            context, R.string.m3e_calendar_delete_failed, Toast.LENGTH_SHORT
                        ).show()
                        reloadKey++
                    }
                }
            },
        )
    }
}

@Composable
private fun AddCalendarRow(title: String, onClick: () -> Unit) {
    Text(
        text = title,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
    )
}

/** Asks for a name and creates a local calendar, mirroring the classic offline-calendar dialog. */
@Composable
private fun OfflineCalendarDialog(onDismiss: () -> Unit, onCreated: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var showError by remember { mutableStateOf(false) }

    // The colour the new calendar is created with. It starts on the first palette entry and can be
    // set to anything through the picker, the same one the colour dialog on this screen uses.
    var color by remember { mutableStateOf(CALENDAR_COLOR_PALETTE.first()) }
    var custom by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.extraLarge,
        title = { Text(stringResource(R.string.preferences_list_add_offline_title)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.preferences_list_add_offline_message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        showError = false
                    },
                    label = { Text(stringResource(R.string.preferences_list_add_offline_name)) },
                    singleLine = true,
                    isError = showError,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (showError) {
                    Text(
                        text = stringResource(R.string.preferences_list_add_offline_error_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                Spacer(Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.m3e_calendars_color_title),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))

                if (custom) {
                    // The picker, for anything the palette does not cover.
                    com.android.calendar.chrome.compose.HsvColorPicker(
                        initial = color,
                        onColor = { color = it },
                    )
                } else {
                    // The palette, with the current choice ringed. Six to a row, as the colour dialog
                    // on this screen lays them out.
                    CALENDAR_COLOR_PALETTE.chunked(6).forEach { row ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.padding(bottom = 10.dp),
                        ) {
                            row.forEach { swatch ->
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .background(color = Color(swatch), shape = CircleShape)
                                        .border(
                                            width = if (swatch == color) 2.5.dp else 0.dp,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            shape = CircleShape,
                                        )
                                        .clickable { color = swatch },
                                )
                            }
                        }
                    }
                }

                TextButton(onClick = { custom = !custom }) {
                    Text(
                        stringResource(
                            if (custom) R.string.m3e_back else R.string.m3e_event_color_custom
                        )
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isBlank()) {
                    showError = true
                    return@TextButton
                }
                val app = context.applicationContext as Application
                val accountName = context.getString(R.string.offline_account_name)
                val displayName = name.trim()
                val chosenColor = color
                scope.launch {
                    runCatching {
                        val created: android.net.Uri = CalendarRepository(app)
                            .addLocalCalendar(accountName, displayName)
                        // The colour is written after the insert: the create call is the classic one
                        // and does not take a colour, and the row exists by now.
                        val id = android.content.ContentUris.parseId(created)
                        CalendarsRepository(app).setColor(id, chosenColor)
                    }
                    onCreated()
                }
            }) { Text(stringResource(R.string.preferences_list_add_offline_button)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.preferences_list_add_offline_cancel))
            }
        },
    )
}

/** Hands off to DAVx5, which owns the CalDAV account. */
private fun launchCalDav(context: Context) {
    val davX5 = Intent().apply {
        setClassName("at.bitfire.davdroid", "at.bitfire.davdroid.ui.setup.LoginActivity")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    if (context.packageManager.resolveActivity(davX5, 0) != null) {
        runCatching { context.startActivity(davX5) }
    } else {
        openStore(context, "at.bitfire.davdroid")
    }
}

/** Hands off to the EteSync app. */
private fun launchEteSync(context: Context) {
    val packageName = "com.etesync.syncadapter"
    val launch = context.packageManager.getLaunchIntentForPackage(packageName)
    if (launch != null) {
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(launch) }
    } else {
        openStore(context, packageName)
    }
}

/** Offers the store listing, then F-Droid, then gives up with a message. */
private fun openStore(context: Context, packageName: String) {
    val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName"))
    val fDroid = Intent(
        Intent.ACTION_VIEW,
        Uri.parse("https://f-droid.org/repository/browse/?fdid=$packageName"),
    )
    val target = when {
        market.resolveActivity(context.packageManager) != null -> market
        fDroid.resolveActivity(context.packageManager) != null -> fDroid
        else -> null
    }
    if (target == null) {
        Toast.makeText(context, R.string.m3e_no_browser, Toast.LENGTH_SHORT).show()
        return
    }
    target.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(target) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CalendarRow(
    entry: CalendarEntry,
    /** Opens the per-calendar screen for this row; the whole row is the tap target. */
    onOpen: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onPickColor: () -> Unit,
    /** Swiping left asks for the calendar's deletion; null when it is not this app's own. */
    onDelete: (() -> Unit)? = null,
) {
    val content: @Composable () -> Unit = {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ) {
            Row(
                modifier = Modifier
                    .clickable(onClick = onOpen)
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Tapping the swatch opens the colour picker; tapping the rest of the row opens the
                // calendar's own screen. The swatch is a child target, so it takes its own taps.
                CalendarColorSwatch(
                    color = entry.color,
                    modifier = Modifier.clickable(onClick = onPickColor),
                )
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = entry.displayName,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (entry.isPrimary) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    entry.accountName?.let { account ->
                        Text(
                            text = account,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Switch(checked = entry.visible, onCheckedChange = onToggle)
            }
        }
    }

    if (onDelete == null) {
        content()
        return
    }

    // The same swipe the agenda uses: the row slides over a red panel, a short tick confirms the
    // gesture, and the confirmation dialog - not the swipe itself - is what deletes.
    val context = LocalContext.current
    val haptics = remember { HapticFeedbackCompat(context) }
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                haptics.vibrate()
                onDelete()
            }
            false
        },
    )

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = true,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = MaterialTheme.shapes.large,
                    )
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_delete),
                    contentDescription = stringResource(R.string.m3e_event_delete),
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        },
    ) {
        content()
    }
}

/**
 * Fires the platform's short haptic tick, the same one the agenda's swipe plays.
 */
private class HapticFeedbackCompat(private val context: android.content.Context) {
    fun vibrate() {
        try {
            val vibrator = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                val manager = context.getSystemService(android.content.Context.VIBRATOR_MANAGER_SERVICE)
                        as? android.os.VibratorManager
                manager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(android.content.Context.VIBRATOR_SERVICE)
                        as? android.os.Vibrator
            }
            vibrator?.let {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    it.vibrate(android.os.VibrationEffect.createPredefined(
                        android.os.VibrationEffect.EFFECT_TICK))
                } else {
                    @Suppress("DEPRECATION")
                    it.vibrate(30L)
                }
            }
        } catch (t: Throwable) {
            // A device without a vibrator must not break the swipe.
        }
    }
}

