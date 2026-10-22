/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * Material 3 Expressive event details screen (read-only).
 */
package com.android.calendar.event.compose

import androidx.compose.ui.res.painterResource
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.OutlinedTextField
import androidx.compose.foundation.layout.heightIn
import android.os.Build
import android.text.format.DateFormat
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.clickable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.android.calendar.calendarcommon2.EventRecurrence
import com.android.calendar.chrome.compose.M3eEdgeToEdgeContent
import com.android.calendar.chrome.compose.m3eSystemBarPadding
import com.android.calendar.EventRecurrenceFormatter
import com.google.android.material.color.MaterialColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ws.xsoh.etar.R
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun M3eEventDetailsScreen(
    eventId: Long,
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onDelete: () -> Unit,
    onRemindersChanged: (List<Int>) -> Unit,
    /** Writes a new time zone for the event. */
    onTimezoneChanged: (String) -> Unit,
    /**
     * Bumped by the host on every resume, so returning from the editor re-reads the stored row.
     *
     * This screen stays composed underneath the editor; a load keyed only on [eventId] ran once and
     * kept showing the old start, end, title, description and reminders after an edit.
     */
    refreshKey: Int = 0,
) {
    val context = LocalContext.current
    var loading by remember { mutableStateOf(true) }
    var details by remember { mutableStateOf<EventDetails?>(null) }
    var confirmingDelete by remember { mutableStateOf(false) }
    var editingTimezone by remember { mutableStateOf(false) }

    /**
     * The countdown linked to this event, if any.
     *
     * forEventOrSimilar, not forEvent: the id alone misses a countdown whose link was never recorded,
     * and the name-and-date fallback is what makes the checkbox appear. The agenda asks the same way,
     * which is why its checkbox worked and this screen's did not.
     */
    val linkedCountdown = remember(eventId, details) {
        val event = details
        if (eventId <= 0L && event == null) {
            null
        } else {
            com.android.calendar.countdown.CountdownStore.forEventOrSimilar(
                context = context,
                eventId = eventId,
                eventTitle = event?.title,
                eventStartMillis = event?.start ?: 0L,
                zone = java.time.ZoneId.systemDefault(),
            )
        }
    }

    // Held locally so a reminder the user just added shows up at once. The list is seeded from the
    // loaded details and re-seeded whenever those are re-read.
    var reminders by remember { mutableStateOf<List<Int>>(emptyList()) }

    LaunchedEffect(eventId, refreshKey) {
        val loaded = withContext(Dispatchers.IO) {
            EventDetailsRepository(context.applicationContext).load(eventId)
        }
        if (loaded == null) {
            // The event was deleted while this screen was away (or the id never existed). Closing is
            // the graceful answer: there is nothing truthful left to show, and the screens underneath
            // re-read on their own resume.
            onBack()
            return@LaunchedEffect
        }
        details = loaded
        // Seed the editable reminder list from what was loaded. The load sorts them, so the rows are
        // in a stable order rather than in whatever order the provider returned. Re-seeded on every
        // read, so a reminder changed in the editor does not survive as a stale local copy either.
        reminders = loaded.remindersMinutes.sortedDescending()
        loading = false
    }

    M3eEdgeToEdgeContent {
    Scaffold(
        modifier = Modifier.m3eSystemBarPadding(),
        topBar = {
            TopAppBar(
                // The viewer's own heading, with the same note the editor carries. The note appears
                // once the event has loaded, because until then there is no date to describe.
                title = {
                    EventTitleWithDistance(
                        start = details?.start,
                        heading = stringResource(R.string.m3e_event_view_title),
                    )
                },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text(stringResource(R.string.m3e_back)) }
                },
                actions = {
                    if (details != null) {
                        // Icons rather than words. The German words for edit and delete left no room
                        // for the heading beside them, and an icon is the same width in every
                        // language - which is the only way the heading can be relied on to fit.
                        IconButton(onClick = { onEdit(eventId) }) {
                            Icon(
                                painter = painterResource(R.drawable.ic_m3e_event_edit),
                                contentDescription = stringResource(R.string.m3e_event_edit),
                            )
                        }
                        IconButton(onClick = { confirmingDelete = true }) {
                            Icon(
                                painter = painterResource(R.drawable.ic_m3e_event_delete),
                                contentDescription = stringResource(R.string.m3e_event_delete),
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        val current = details
        when {            loading -> Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { LoadingIndicator() }

            current == null -> Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.m3e_event_missing),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item { HeadlineCard(current) }

                // Always shown: the card carries the "add notification" action, so hiding it when the
                // event has none would leave no way to give it one.
                item {
                    ScheduleCard(
                        details = current,
                        reminders = reminders,
                        editingTimezone = editingTimezone,
                        onEditTimezone = { editingTimezone = true },
                        onRemindersChanged = { minutes ->
                            // Show it here at once, then write it; the write is what survives.
                            reminders = minutes
                            onRemindersChanged(minutes)
                        },
                    )
                }

                if (!current.location.isNullOrBlank() ||
                    !current.description.isNullOrBlank() ||
                    !current.organizer.isNullOrBlank() ||
                    !current.url.isNullOrBlank() ||
                    !current.calendarName.isNullOrBlank()
                ) {
                    item { NotesCard(current) }
                }

                if (current.attendees.isNotEmpty()) {
                    item { AttendeesCard(current.attendees) }
                }
            }
        }
    }
    }

    if (editingTimezone) {
        TimeZonePickerDialog(
            current = details?.timezone,
            onDismiss = { editingTimezone = false },
            onPick = { zone ->
                editingTimezone = false
                onTimezoneChanged(zone)
            },
        )
    }

    if (confirmingDelete) {
        // The same question the agenda asks, so deleting from here offers to remove a linked
        // countdown rather than quietly orphaning it.
        com.android.calendar.countdown.compose.EventDeleteWithCountdownDialog(
            eventTitle = details?.title.orEmpty(),
            countdownName = linkedCountdown?.name,
            onDismiss = { confirmingDelete = false },
            onConfirm = { alsoDeleteCountdown ->
                confirmingDelete = false
                if (linkedCountdown != null && alsoDeleteCountdown) {
                    com.android.calendar.countdown.CountdownStore.remove(context, linkedCountdown.id)
                    com.android.calendar.widget.CountdownWidgetUpdater.updateAll(context)
                } else if (linkedCountdown != null) {
                    com.android.calendar.countdown.CountdownStore.unlinkEvent(context, eventId)
                }
                onDelete()
            },
        )
    }
}

@Composable
private fun HeadlineCard(details: EventDetails) {
    SettingsSurface {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(width = 6.dp, height = 40.dp)
                    .background(color = accent(details.calendarColor), shape = CircleShape)
            )
            Spacer(Modifier.width(14.dp))
            Column {
                Text(
                    text = details.title.ifEmpty {
                        stringResource(R.string.no_title_label)
                    },
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                details.calendarName?.let { name ->
                    Text(
                        text = name,
                        style = MaterialTheme.typography.labelLarge,
                        color = accent(details.calendarColor),
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = whenText(details),
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun ScheduleCard(
    details: EventDetails,
    reminders: List<Int>,
    onRemindersChanged: (List<Int>) -> Unit,
    editingTimezone: Boolean,
    onEditTimezone: () -> Unit,
) {
    SettingsSurface {
        if (details.rrule != null) {
            RowItem(
                label = stringResource(R.string.m3e_event_repeats),
                value = repeatString(details.rrule),
            )
            HorizontalDivider()
        }

        // Notifications: one row whose menu is a list of checkboxes, identical to the editor's -
        // the same composable, so a reminder is chosen the same way in both places, and several can
        // be ticked in one visit. Every change is written straight away and the row shows what is
        // set.
        M3eReminderMenuRow(
            reminders = reminders,
            onRemindersChanged = onRemindersChanged,
            label = stringResource(R.string.event_info_reminders_label),
        )

        // The event's zone, editable here rather than only through the editor: it is one value with
        // an obvious meaning, and routing it through the whole editor to change it is friction.
        HorizontalDivider()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onEditTimezone)
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.accessibility_pick_time_zone),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = details.timezone ?: stringResource(R.string.m3e_timezone_none),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * The time zone chooser.
 *
 * The same searchable list the editor shows, so a zone is picked the same way in both places. The
 * list is the platform's available ids - several hundred of them - which is why the filter, rather
 * than scrolling, is the way in.
 */
@Composable
private fun TimeZonePickerDialog(
    current: String?,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val all = remember { java.util.TimeZone.getAvailableIDs().sorted() }
    val matches = remember(query, all) {
        if (query.isBlank()) all.take(200) else all.filter { it.contains(query, ignoreCase = true) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.extraLarge,
        title = { Text(stringResource(R.string.accessibility_pick_time_zone)) },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text(stringResource(R.string.m3e_timezone_search)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                    items(matches.size) { index ->
                        val id = matches[index]
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPick(id) }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = id,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (id == current) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.m3e_cancel)) }
        },
    )
}
@Composable
private fun NotesCard(details: EventDetails) {
    SettingsSurface {
        var first = true
        details.location?.takeIf { it.isNotBlank() }?.let {
            RowItem(label = stringResource(R.string.hint_where), value = it)
            first = false
        }
        // The row is the event's calendar, not its organiser: the label names the calendar and the
        // value is the calendar's display name, with the calendar's colour dot beside it.
        details.calendarName?.takeIf { it.isNotBlank() }?.let {
            if (!first) HorizontalDivider()
            RowItem(
                label = stringResource(R.string.m3e_event_calendar),
                value = it,
                dotColor = accent(details.calendarColor),
            )
            first = false
        }
        details.description?.takeIf { it.isNotBlank() }?.let {
            if (!first) HorizontalDivider()
            RowItem(label = stringResource(R.string.hint_description), value = it)
            first = false
        }
        // A link row only when there is a URL: no blank row for an event without one. The scheme is
        // checked before opening, so a stored value that is not http(s) is shown but cannot launch
        // anything else through the view intent.
        details.url?.takeIf { it.isNotBlank() }?.let { url ->
            if (!first) HorizontalDivider()
            val context = LocalContext.current
            LinkRowItem(
                label = stringResource(R.string.m3e_event_url),
                value = url,
                onClick = { openEventUrl(context, url) },
            )
        }
    }
}

/**
 * Opens the event's web link in the default browser.
 *
 * The stored value can be scheme-less, padded with whitespace or a newline, or use a non-web scheme.
 * normaliseWebUrl supplies https when the scheme is missing, keeps http/https, and refuses the rest;
 * an empty result means the tap does nothing, which is the safe answer. NEW_TASK is added only for a
 * non-activity context, and the launch is guarded so a device without a browser cannot crash the app.
 */
private fun openEventUrl(context: android.content.Context, url: String) {
    val normalised = com.android.calendar.composeui.normaliseWebUrl(url) ?: return
    val intent = android.content.Intent(
        android.content.Intent.ACTION_VIEW,
        android.net.Uri.parse(normalised),
    )
    if (context !is android.app.Activity) {
        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try {
        context.startActivity(intent)
    } catch (e: android.content.ActivityNotFoundException) {
        android.widget.Toast.makeText(
            context,
            R.string.m3e_event_url_no_browser,
            android.widget.Toast.LENGTH_SHORT,
        ).show()
    } catch (t: Throwable) {
        android.util.Log.w("M3eEventDetails", "Could not open the event url", t)
    }
}

/** A row whose value is a link: underlined, at most two lines, ellipsized, 48dp tall. */
@Composable
private fun LinkRowItem(label: String, value: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            // The minimum touch target comes before clickable so the 48dp height is part of the
            // clickable area, not padding outside it; the ripple is the default indication, which is
            // what makes the row read as tappable.
            .heightIn(min = 48.dp)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary,
            textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun AttendeesCard(attendees: List<AttendeeInfo>) {
    SettingsSurface {
        Text(
            text = stringResource(R.string.hint_attendees),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 5.dp),
        )
        attendees.forEachIndexed { index, attendee ->
            if (index > 0) HorizontalDivider()
            RowItem(
                label = attendee.name ?: attendee.email.orEmpty(),
                value = attendeeStatus(attendee.status),
            )
        }
    }
}

// ------------------------------------------------------------------------------- small helpers

@Composable
private fun SettingsSurface(content: @Composable () -> Unit) {
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
private fun RowItem(label: String, value: String, dotColor: Color? = null) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (dotColor == null) {
            Text(text = value, style = MaterialTheme.typography.bodyLarge)
        } else {
            // A dot before the value, in the colour of the calendar the line belongs to.
            //
            // Asked for on the organiser line: an event can be in one calendar and organised through
            // another account, and the row gave no sign of which calendar was being shown. It is the
            // same colour the headline uses for the event itself, so the two read as one thing.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(color = dotColor, shape = CircleShape)
                )
                Spacer(Modifier.width(8.dp))
                Text(text = value, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable
private fun whenText(details: EventDetails): String {
    val context = LocalContext.current
    val zone = remember { ZoneId.systemDefault() }
    val dateFmt = remember { DateFormat.getMediumDateFormat(context) }
    val timeFmt = remember {
        SimpleDateFormat(
            if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm a",
            Locale.getDefault(),
        )
    }

    return if (details.allDay) {
        // An all-day event keeps its days as UTC days, and its stored end is exclusive: midnight of
        // the day after the last one it covers. The last day is the one before that, and a span of
        // more than one day prints both ends rather than only the first. The formatter is set to UTC
        // for these, or a zone behind it would print the day before the one stored.
        val utcDateFmt = remember {
            DateFormat.getMediumDateFormat(context).apply {
                timeZone = java.util.TimeZone.getTimeZone("UTC")
            }
        }
        val first = utcDay(details.start)
        val last = utcDay(details.end).minusDays(1)
        val allDayLabel = stringResource(R.string.edit_event_all_day_label)
        if (last.isAfter(first)) {
            "${utcDateFmt.format(Date(utcMidnight(first)))} – " +
                    "${utcDateFmt.format(Date(utcMidnight(last)))} · $allDayLabel"
        } else {
            "${utcDateFmt.format(Date(utcMidnight(first)))} · $allDayLabel"
        }
    } else {
        val s = Instant.ofEpochMilli(details.start).atZone(zone).toLocalDate()
        val e = Instant.ofEpochMilli(details.end).atZone(zone).toLocalDate()
        if (s == e) {
            "${dateFmt.format(Date(details.start))}  " +
                    "${timeFmt.format(Date(details.start))} – ${timeFmt.format(Date(details.end))}"
        } else {
            "${dateFmt.format(Date(details.start))}  ${timeFmt.format(Date(details.start))} – " +
                    "${dateFmt.format(Date(details.end))}  ${timeFmt.format(Date(details.end))}"
        }
    }
}

/** The UTC midnight of [date], which is how an all-day event's day is stored. */
private fun utcMidnight(date: LocalDate): Long =
    date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

/** The UTC day a stored all-day instant falls on. */
private fun utcDay(millis: Long): LocalDate =
    Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()

@Composable
private fun repeatString(rrule: String): String {    val context = LocalContext.current
    return remember(rrule) {
        try {
            val recurrence = EventRecurrence()
            recurrence.parse(rrule)
            EventRecurrenceFormatter.getRepeatString(
                context, context.resources, recurrence, true,
            ) ?: rrule
        } catch (t: Throwable) {
            rrule
        }
    }
}

@Composable
private fun attendeeStatus(status: Int): String = when (status) {
    android.provider.CalendarContract.Attendees.ATTENDEE_STATUS_ACCEPTED ->
        stringResource(R.string.m3e_attendee_accepted)
    android.provider.CalendarContract.Attendees.ATTENDEE_STATUS_DECLINED ->
        stringResource(R.string.m3e_attendee_declined)
    android.provider.CalendarContract.Attendees.ATTENDEE_STATUS_INVITED ->
        stringResource(R.string.m3e_attendee_invited)
    android.provider.CalendarContract.Attendees.ATTENDEE_STATUS_TENTATIVE ->
        stringResource(R.string.m3e_attendee_tentative)
    else -> ""
}

@Composable
private fun accent(calendarColor: Int): Color {
    val primary = MaterialTheme.colorScheme.primary.toArgb()
    return remember(calendarColor, primary) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Color(MaterialColors.harmonize(calendarColor, primary))
        } else {
            Color(calendarColor)
        }
    }
}
