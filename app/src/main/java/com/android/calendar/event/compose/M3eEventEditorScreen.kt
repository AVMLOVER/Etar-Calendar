/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * Material 3 Expressive event editor form.
 *
 * Covers the same properties the classic EditEventView does: title, calendar, colour, all day,
 * start and end, repeats, location, description, link, notifications, availability, visibility and
 * the signed-in user's own guest response. Reminders, recurrence and extended properties are loaded
 * and written back through EventEditorRepository, so nothing is lost by saving from this screen.
 */
package com.android.calendar.event.compose

import androidx.compose.ui.res.painterResource
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import com.android.calendar.chrome.compose.M3eDatePickerDialog
import android.provider.CalendarContract
import android.text.format.DateFormat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.android.calendar.chrome.compose.ColorPaletteGrid
import com.android.calendar.chrome.compose.HsvColorPicker
import com.android.calendar.chrome.compose.M3eEdgeToEdgeContent
import com.android.calendar.chrome.compose.m3eSystemBarPadding
import com.android.calendar.countdown.CountdownStore
import ws.xsoh.etar.R
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.Date
import java.util.Locale

/** What the editor hands back to the activity for saving. */
data class EditedEvent(
    val title: String,
    val location: String,
    val description: String,
    val url: String,
    val allDay: Boolean,
    val start: Long,
    val end: Long,
    val calendarId: Long,
    val calendarName: String,
    /** RFC 5545 recurrence rule, or null for an event that does not repeat. */
    val rrule: String?,
    /** CalendarContract.Events.AVAILABILITY_*, or -1 to leave the stored value alone. */
    val availability: Int,
    /** CalendarContract.Events.ACCESS_*, or -1 to leave the stored value alone. */
    val accessLevel: Int,
    /** An explicit event colour, or 0 when the event follows its calendar. */
    val eventColor: Int,
    /** Minutes before the start; negative values are ignored. */
    val reminderMinutes: List<Int>,
    /** Attendees.ATTENDEE_STATUS_*, or -1 to leave the stored value alone. */
    val selfAttendeeStatus: Int,
    /** The event's time zone id. */
    val timezone: String,
    /** Guest e-mail addresses, de-duplicated. */
    val attendees: List<String>,
    /** True to also create a countdown for this event, using its title and start date. */
    val addToCountdown: Boolean,
    /** The colour for that countdown. */
    val countdownColor: Int,
    /** The countdown's background picture, or null for none. */
    val countdownBackgroundImage: String? = null,
    /** Whether a background picture is shown bare rather than under the countdown's colour. */
    val countdownTransparent: Boolean = true,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun M3eEventEditorScreen(
    initialTitle: String,
    initialLocation: String,
    initialDescription: String,
    initialUrl: String,
    initialAllDay: Boolean,
    initialStart: Long,
    initialEnd: Long,
    initialCalendarId: Long,
    initialRrule: String?,
    initialAvailability: Int,
    initialAccessLevel: Int,
    initialEventColor: Int,
    initialReminders: List<Int>,
    initialHasAttendeeData: Boolean,
    initialSelfAttendeeStatus: Int,
    initialTimezone: String,
    initialAttendees: List<String>,
    /**
     * The event colours this account offers. Empty means the account has no colour table, and the
     * picker then falls back to the app's palette.
     */
    initialEventColors: List<Int> = emptyList(),
    /** True when the event already exists and the calendar allows deleting from it. */
    canDelete: Boolean,
    /**
     * The event being edited, or -1 for a new one.
     *
     * Needed so the delete confirmation can offer to remove a countdown linked to this event, which
     * is the same question the agenda asks.
     */
    eventId: Long = -1L,
    /** True when the screen was opened read-only, so saving and deleting are not offered. */
    readOnly: Boolean = false,
    calendars: List<CalendarOption>,
    onBack: () -> Unit,
    onSave: (EditedEvent) -> Unit,
    onDelete: () -> Unit,
) {
    val zone = remember { ZoneId.systemDefault() }
    val context = LocalContext.current

    // The account's own palette when it has one, otherwise the app's fallback ten, so the picker
    // always has colours to show.
    val palette = remember(initialEventColors) {
        initialEventColors.ifEmpty { EventEditorRepository.DEFAULT_EVENT_COLORS }
    }

    var title by remember { mutableStateOf(initialTitle) }
    var location by remember { mutableStateOf(initialLocation) }
    var description by remember { mutableStateOf(initialDescription) }
    var url by remember { mutableStateOf(initialUrl) }
    var allDay by remember { mutableStateOf(initialAllDay) }
    var start by remember { mutableStateOf(initialStart) }
    var end by remember { mutableStateOf(initialEnd) }
    var calendarId by remember { mutableStateOf(initialCalendarId) }
    var calendarName by remember {
        mutableStateOf(calendars.firstOrNull { it.id == initialCalendarId }?.name.orEmpty())
    }
    var rrule by remember { mutableStateOf(initialRrule) }
    var availability by remember { mutableStateOf(initialAvailability) }
    var accessLevel by remember { mutableStateOf(initialAccessLevel) }
    var eventColor by remember { mutableStateOf(initialEventColor) }
    var reminders by remember { mutableStateOf(initialReminders.toList()) }
    var selfStatus by remember { mutableStateOf(initialSelfAttendeeStatus) }
    var timezone by remember { mutableStateOf(initialTimezone) }
    var attendees by remember { mutableStateOf(initialAttendees) }
    var addingGuest by remember { mutableStateOf(false) }
    var editingTimezone by remember { mutableStateOf(false) }
    var locationSuggestions by remember { mutableStateOf<List<String>>(emptyList()) }
    var addToCountdown by remember { mutableStateOf(false) }
    // A different starting colour from the event's own, so the two entries are told apart in the
    // countdown list when both exist.
    var countdownColor by remember { mutableStateOf(CountdownStore.nextColor(context)) }
    /** The countdown's background picture and whether it shows through, as the countdown screen sets them. */
    var countdownBackgroundImage by remember { mutableStateOf<String?>(null) }
    var countdownTransparent by remember { mutableStateOf(true) }

    // Look up suggestions a moment after typing stops, so a burst of keystrokes runs one query
    // rather than one per character. The lookup is skipped while the field already holds a
    // suggestion the user picked.
    LaunchedEffect(location) {
        if (location.length < 2) {
            locationSuggestions = emptyList()
            return@LaunchedEffect
        }
        kotlinx.coroutines.delay(250)
        locationSuggestions = LocationSuggestions(context).forQuery(location)
    }

    var editingStart by remember { mutableStateOf(false) }
    var editingEnd by remember { mutableStateOf(false) }
    var calendarMenu by remember { mutableStateOf(false) }
    var confirmingDelete by remember { mutableStateOf(false) }

    M3eEdgeToEdgeContent {
    Scaffold(
        modifier = Modifier.m3eSystemBarPadding(),
        topBar = {
            TopAppBar(
                // The heading says what the screen is for, and how far off the day being edited is.
                //
                // Read from the start being edited rather than from the event as it was opened, so
                // moving the date updates the note with it. The note itself is recomputed as the date
                // rolls over, so a screen left open overnight does not keep yesterday's answer.
                title = { EventTitleWithDistance(start) },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text(stringResource(R.string.m3e_back)) }
                },
                actions = {
                    // Icons rather than words, for the same reason as the viewer: the German words
                    // left the heading no room, and an icon is the same width in every language.
                    // Only for an event that already exists: a new one is discarded by leaving.
                    if (canDelete) {
                        IconButton(onClick = { confirmingDelete = true }) {
                            Icon(
                                painter = painterResource(R.drawable.ic_m3e_event_delete),
                                contentDescription = stringResource(R.string.m3e_event_delete),
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                    if (!readOnly) {
                    IconButton(onClick = {
                        onSave(
                            EditedEvent(
                                title = title.trim(),
                                location = location.trim(),
                                description = description.trim(),
                                url = url.trim(),
                                allDay = allDay,
                                start = start,
                                end = if (end < start) start else end,
                                calendarId = calendarId,
                                calendarName = calendarName,
                                rrule = rrule,
                                availability = availability,
                                accessLevel = accessLevel,
                                eventColor = eventColor,
                                reminderMinutes = reminders,
                                selfAttendeeStatus = if (initialHasAttendeeData) selfStatus else -1,
                                timezone = timezone,
                                attendees = attendees,
                                addToCountdown = addToCountdown,
                                countdownColor = countdownColor,
                        countdownBackgroundImage = countdownBackgroundImage,
                        countdownTransparent = countdownTransparent,
                            )
                        )
                    }) {
                        Icon(
                            painter = painterResource(R.drawable.outline_save_event),
                            contentDescription = stringResource(R.string.create_event_dialog_save),
                        )
                    }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Card {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text(stringResource(R.string.hint_what)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            item {
                Card {
                    SwitchRow(
                        title = stringResource(R.string.edit_event_all_day_label),
                        checked = allDay,
                        onCheckedChange = { on ->
                            allDay = on
                            // The two kinds of event keep their days in different places: a timed one
                            // in local time, an all-day one at UTC midnight, which is the only form
                            // CalendarProvider builds Instances rows for. Turning the switch moves
                            // the dates between the two, keeping the days they name.
                            if (on) {
                                val firstDay = Instant.ofEpochMilli(start).atZone(zone).toLocalDate()
                                val lastDay = Instant.ofEpochMilli(end).atZone(zone).toLocalDate()
                                start = utcMidnight(firstDay)
                                // A timed event that ends at midnight ends at the start of its last
                                // day; as an all-day event that day is still one of its own, so the
                                // end - which is exclusive - is the day after it.
                                val lastIncluded =
                                    if (lastDay.isAfter(firstDay)) lastDay else firstDay
                                end = utcMidnight(lastIncluded.plusDays(1))
                            } else {
                                val firstDay = utcDateOf(start)
                                val lastDay = utcDateOf(end).minusDays(1)
                                start = firstDay.atStartOfDay(zone).toInstant().toEpochMilli()
                                end = lastDay.plusDays(1)
                                    .atStartOfDay(zone).toInstant().toEpochMilli()
                            }
                        },
                    )
                    Spacer(Modifier.height(4.dp))
                    DateTimeRow(
                        label = stringResource(R.string.m3e_event_starts),
                        millis = start,
                        allDay = allDay,
                        endRow = false,
                        onClick = { editingStart = true },
                    )
                    DateTimeRow(
                        label = stringResource(R.string.m3e_event_ends),
                        millis = end,
                        allDay = allDay,
                        endRow = true,
                        onClick = { editingEnd = true },
                    )
                    HorizontalDivider()
                    // The event's own zone, which is what the classic editor's "time zone" button
                    // sets. It matters for a recurring event that has to keep its wall-clock time
                    // when the device travels.
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { editingTimezone = true }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.accessibility_pick_time_zone),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = timezone,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            // Guests, added and removed here. The classic editor's multi-recipient field is gone, but
            // the list it produced is not: this keeps the same data without the chips widget.
            item {
                Card {
                    Text(
                        text = stringResource(R.string.m3e_event_guests),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Spacer(Modifier.height(4.dp))
                    if (attendees.isEmpty()) {
                        Text(
                            text = stringResource(R.string.m3e_no_guests),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    attendees.forEach { email ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = email,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            TextButton(onClick = {
                                attendees = attendees.filterNot { it == email }
                            }) {
                                Text(stringResource(R.string.m3e_reminder_remove))
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    TextButton(onClick = { addingGuest = true }) {
                        Text(stringResource(R.string.m3e_guest_add))
                    }
                }
            }

            item {
                Card {
                    OutlinedTextField(
                        value = location,
                        onValueChange = { location = it },
                        label = { Text(stringResource(R.string.hint_where)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    // Suggestions drop in under the field as it is typed: contact addresses and
                    // locations used on earlier events, both read from the device.
                    if (locationSuggestions.isNotEmpty()) {
                        locationSuggestions.take(4).forEach { suggestion ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        location = suggestion
                                        locationSuggestions = emptyList()
                                    }
                                    .padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = suggestion,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text(stringResource(R.string.hint_description)) },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        label = { Text(stringResource(R.string.m3e_event_url)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            // Repeats, availability and colour: the three properties the classic editor exposes and
            // this one used to drop, which meant editing an event could silently reset them.
            item {
                Card {
                    OptionRow(
                        title = stringResource(R.string.m3e_event_repeats),
                        value = rrule,
                        options = RRULE_VALUES,
                        labelOf = { repeatLabel(it) },
                        onSelect = { rrule = it },
                    )
                    HorizontalDivider()
                    OptionRow(
                        title = stringResource(R.string.m3e_event_availability),
                        value = availability,
                        options = AVAILABILITY_VALUES,
                        labelOf = { availabilityLabel(it) },
                        onSelect = { availability = it },
                    )
                    HorizontalDivider()
                    OptionRow(
                        title = stringResource(R.string.m3e_event_visibility),
                        value = accessLevel,
                        options = ACCESS_VALUES,
                        labelOf = { visibilityLabel(it) },
                        onSelect = { accessLevel = it },
                    )
                    HorizontalDivider()
                    // No colour row here.
                    //
                    // The colour belongs to the countdown: with "add to countdown" off there is no
                    // countdown for a colour to belong to, and an event's own colour is the calendar's.
                    // It is offered in the countdown card below when that switch is on.
                }
            }

            // Also track this event as a countdown. Off by default: it is a second thing created
            // from one save, and the event is usually the point on its own.
            item {
                Card {
                    SwitchRow(
                        title = stringResource(R.string.m3e_event_add_to_countdown),
                        checked = addToCountdown,
                        onCheckedChange = { addToCountdown = it },
                    )
                    if (addToCountdown) {
                        HorizontalDivider()
                        // The colour the countdown is created with, chosen here rather than after the
                        // countdown screen has been opened again. It is the same palette the countdown
                        // screen offers, so a countdown made either way is the same thing.
                        ColorRow(
                            selected = countdownColor,
                            colors = palette,
                            onSelect = { countdownColor = it },
                        )
                        // And the countdown's own background picture and transparent-background
                        // switch, from the same shared rows the countdown screen's editor uses.
                        com.android.calendar.countdown.compose.CountdownBackgroundRows(
                            imageUri = countdownBackgroundImage,
                            transparent = countdownTransparent,
                            onImageChange = { countdownBackgroundImage = it },
                            onTransparentChange = { countdownTransparent = it },
                        )
                    }
                }
            }

            // Guests: only the signed-in user's own response is editable, which is what the classic
            // editor allows too; the full attendee list needs the details screen.
            if (initialHasAttendeeData) {
                item {
                    Card {
                        Text(
                            text = stringResource(R.string.m3e_event_guests),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ResponseChip(
                                label = stringResource(R.string.m3e_response_yes),
                                selected = selfStatus == ATTENDEE_YES,
                                onClick = { selfStatus = ATTENDEE_YES },
                            )
                            ResponseChip(
                                label = stringResource(R.string.m3e_response_maybe),
                                selected = selfStatus == ATTENDEE_MAYBE,
                                onClick = { selfStatus = ATTENDEE_MAYBE },
                            )
                            ResponseChip(
                                label = stringResource(R.string.m3e_response_no),
                                selected = selfStatus == ATTENDEE_NO,
                                onClick = { selfStatus = ATTENDEE_NO },
                            )
                        }
                    }
                }
            }

            // Notifications: one row whose menu is a list of checkboxes, shared with the details
            // screen so reminders are chosen the same way in both places. Several can be ticked in
            // one visit; the summary on the row shows what is set, and the save carries the whole
            // set.
            item {
                Card {
                    M3eReminderMenuRow(
                        reminders = reminders,
                        onRemindersChanged = { reminders = it },
                        label = stringResource(R.string.m3e_event_reminders),
                    )
                }
            }

            item {
                Card {
                    Box {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { calendarMenu = true }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource(R.string.edit_event_calendar_label),
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f),
                            )
                            // The calendar's colour beside its name. Which calendar an event belongs
                            // to is the thing its colour follows, so a name on its own leaves the
                            // one clue the grid will show unstated.
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .background(
                                        color = Color(
                                            calendars.firstOrNull { it.id == calendarId }?.color
                                                ?: 0
                                        ),
                                        shape = CircleShape,
                                    ),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = calendarName.ifEmpty {
                                    stringResource(R.string.m3e_event_calendar_unknown)
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        DropdownMenu(
                            expanded = calendarMenu,
                            onDismissRequest = { calendarMenu = false },
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
                                        calendarId = option.id
                                        calendarName = option.name
                                        calendarMenu = false
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    }

    if (editingTimezone) {
        TimeZoneDialog(
            current = timezone,
            onDismiss = { editingTimezone = false },
            onPick = { picked ->
                timezone = picked
                editingTimezone = false
            },
        )
    }

    if (addingGuest) {
        GuestDialog(
            existing = attendees,
            onDismiss = { addingGuest = false },
            onAdd = { email ->
                attendees = attendees + email
                addingGuest = false
            },
        )
    }

    if (confirmingDelete) {
        // The same question the agenda and the countdown list ask, so that deleting from the editor
        // offers to remove a linked countdown too rather than quietly orphaning it.
        // forEventOrSimilar, not forEvent: the id alone misses a countdown whose link was never
        // recorded, and the name-and-date fallback is what makes the checkbox appear.
        val linkedCountdown = remember(eventId, title) {
            if (eventId <= 0L) {
                null
            } else {
                com.android.calendar.countdown.CountdownStore.forEventOrSimilar(
                    context = context,
                    eventId = eventId,
                    eventTitle = title,
                    eventStartMillis = start,
                    zone = zone,
                )
            }
        }
        com.android.calendar.countdown.compose.EventDeleteWithCountdownDialog(
            eventTitle = title,
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

    if (editingStart || editingEnd) {
        val initial = if (editingStart) start else end
        // An all-day event is a day, not a moment: its pickers work from the stored UTC days and it
        // has no time to choose.
        val initialDay = if (allDay) utcDateOf(initial) else
            Instant.ofEpochMilli(initial).atZone(zone).toLocalDate()
        var pickedDate by remember(initial, allDay) {
            mutableStateOf(
                if (allDay && editingEnd) initialDay.minusDays(1) else initialDay
            )
        }
        var showTime by remember { mutableStateOf(false) }

        if (!showTime) {
            // The app's own calendar grid rather than Material's DatePicker, which renders every
            // column of its weekday header as the first character of the name - seven "星" in
            // Chinese. The same picker is used by the countdown screen and the date calculator.
            M3eDatePickerDialog(
                initial = pickedDate,
                firstDayOfWeek = java.time.DayOfWeek.MONDAY,
                onDismiss = { editingStart = false; editingEnd = false },
                onPick = { picked ->
                    if (allDay) {
                        // The day picked is part of the event, at both ends: the start is that day,
                        // and the end - stored as the midnight after the last day - is the day after
                        // the one picked. Choosing the same day for both is a single all-day event.
                        if (editingStart) {
                            val last = utcDateOf(end).minusDays(1)
                            start = utcMidnight(picked)
                            if (!last.isAfter(picked)) {
                                end = utcMidnight(picked.plusDays(1))
                            }
                        } else {
                            end = utcMidnight(picked.plusDays(1))
                        }
                        editingStart = false
                        editingEnd = false
                    } else {
                        pickedDate = picked
                        // The date is settled; the time follows in its own step.
                        showTime = true
                    }
                },
            )
        } else {
            val timeState = rememberTimePickerState(
                initialHour = Instant.ofEpochMilli(initial).atZone(zone).hour,
                initialMinute = Instant.ofEpochMilli(initial).atZone(zone).minute,
                is24Hour = DateFormat.is24HourFormat(context),
            )
            AlertDialog(
                onDismissRequest = { editingStart = false; editingEnd = false },
                shape = MaterialTheme.shapes.extraLarge,
                title = { Text(stringResource(R.string.m3e_event_time)) },
                text = { TimePicker(state = timeState) },
                confirmButton = {
                    TextButton(onClick = {
                        val millis = pickedDate
                            .atTime(LocalTime.of(timeState.hour, timeState.minute))
                            .atZone(zone).toInstant().toEpochMilli()
                        if (editingStart) {
                            val duration = end - start
                            start = millis
                            end = millis + duration.coerceAtLeast(60L * 60L * 1000L)
                        } else {
                            end = millis
                        }
                        editingStart = false
                        editingEnd = false
                    }) { Text(stringResource(R.string.create_event_dialog_save)) }
                },
                dismissButton = {
                    TextButton(onClick = { editingStart = false; editingEnd = false }) {
                        Text(stringResource(R.string.m3e_back))
                    }
                },
            )
        }
    }
}

// --------------------------------------------------------------------------------- small pieces

/**
 * Time zone picker.
 *
 * A searchable list rather than a dialog fragment: the platform has around six hundred zone ids and
 * scrolling them is unusable, so the filter is the primary way in.
 */
@Composable
private fun TimeZoneDialog(
    current: String,
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
                                modifier = Modifier.weight(1f),
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

/** Adds one guest by e-mail address. */
@Composable
private fun GuestDialog(
    existing: List<String>,
    onDismiss: () -> Unit,
    onAdd: (String) -> Unit,
) {
    var email by remember { mutableStateOf("") }
    val valid = email.contains("@") && email.length > 2 &&
        existing.none { it.equals(email.trim(), ignoreCase = true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.extraLarge,
        title = { Text(stringResource(R.string.m3e_guest_add)) },
        text = {
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text(stringResource(R.string.m3e_guest_email)) },
                singleLine = true,
                isError = email.isNotEmpty() && !valid,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onAdd(email.trim()) },
                enabled = valid,
            ) { Text(stringResource(R.string.m3e_guest_add)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.m3e_cancel)) }
        },
    )
}

/** RFC 5545 rules offered for the repeats row; null means the event does not repeat. */
private val RRULE_VALUES: List<String?> = listOf(
    null,
    "FREQ=DAILY",
    "FREQ=WEEKLY",
    "FREQ=MONTHLY",
    "FREQ=YEARLY",
)

private val AVAILABILITY_VALUES: List<Int> = listOf(
    CalendarContract.Events.AVAILABILITY_BUSY,
    CalendarContract.Events.AVAILABILITY_FREE,
)

private val ACCESS_VALUES: List<Int> = listOf(
    CalendarContract.Events.ACCESS_DEFAULT,
    CalendarContract.Events.ACCESS_PUBLIC,
    CalendarContract.Events.ACCESS_PRIVATE,
    CalendarContract.Events.ACCESS_CONFIDENTIAL,
)

/** "0" is the sentinel for "follow the calendar's colour"; -1 is the calendar's own colour. */
private val EVENT_COLOR_VALUES: List<Int> =
    listOf(0, -1, -2, -3, -4, -5, -6, -7, -8)

private const val ATTENDEE_YES = CalendarContract.Attendees.ATTENDEE_STATUS_ACCEPTED
private const val ATTENDEE_MAYBE = CalendarContract.Attendees.ATTENDEE_STATUS_TENTATIVE
private const val ATTENDEE_NO = CalendarContract.Attendees.ATTENDEE_STATUS_DECLINED

@Composable
private fun <T> OptionRow(
    title: String,
    value: T,
    options: List<T>,
    labelOf: @Composable (T) -> String,
    onSelect: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = true }
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = labelOf(value),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(labelOf(option)) },
                    onClick = { expanded = false; onSelect(option) },
                )
            }
        }
    }
}

@Composable
private fun ResponseChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(MaterialTheme.shapes.large)
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.secondaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                }
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) {
                MaterialTheme.colorScheme.onSecondaryContainer
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
    }
}

@Composable
private fun repeatLabel(value: String?): String = when (value) {
    null -> stringResource(R.string.m3e_repeat_none)
    "FREQ=DAILY" -> stringResource(R.string.m3e_repeat_daily)
    "FREQ=WEEKLY" -> stringResource(R.string.m3e_repeat_weekly)
    "FREQ=MONTHLY" -> stringResource(R.string.m3e_repeat_monthly)
    "FREQ=YEARLY" -> stringResource(R.string.m3e_repeat_yearly)
    // Anything richer than the presets (an existing rule with an interval or a count) is shown as
    // it is rather than being silently replaced by a preset.
    else -> value
}

@Composable
private fun availabilityLabel(value: Int): String = when (value) {
    CalendarContract.Events.AVAILABILITY_FREE -> stringResource(R.string.m3e_availability_free)
    else -> stringResource(R.string.m3e_availability_busy)
}

@Composable
private fun visibilityLabel(value: Int): String = when (value) {
    CalendarContract.Events.ACCESS_PUBLIC -> stringResource(R.string.visibility_public)
    CalendarContract.Events.ACCESS_PRIVATE -> stringResource(R.string.visibility_private)
    CalendarContract.Events.ACCESS_CONFIDENTIAL -> stringResource(R.string.visibility_confidential)
    else -> stringResource(R.string.visibility_default)
}

/**
 * What the colour row says beside its swatch.
 *
 * A colour is never named by its hex value: the swatch beside this text is what shows it, and a hex
 * string tells the reader nothing about what the event will look like.
 */
@Composable
private fun colorLabel(value: Int): String = when (value) {
    0 -> stringResource(R.string.m3e_event_color_calendar)
    -1 -> stringResource(R.string.event_color_set_to_default)
    else -> stringResource(R.string.m3e_event_color_custom)
}

/**
 * The colour row: a swatch of what is selected, and a palette behind it.
 *
 * The row used to show the colour as a hex string, which says nothing about what the event will look
 * like. It now shows the colour itself, and the palette is a grid of the account's real event
 * colours plus the two special choices - follow the calendar, or take the calendar's own colour.
 */
@Composable
private fun ColorRow(
    selected: Int,
    colors: List<Int>,
    onSelect: (Int) -> Unit,
) {
    var open by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { open = true }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.m3e_event_color),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Swatch(color = selected, size = 24.dp)
        Spacer(Modifier.width(8.dp))
        Text(
            text = colorLabel(selected),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }

    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            shape = MaterialTheme.shapes.extraLarge,
            title = { Text(stringResource(R.string.m3e_event_color)) },
            text = {
                Column {
                    // The two special choices, each shown with the colour it means.
                    ColorChoiceRow(
                        color = 0,
                        label = stringResource(R.string.m3e_event_color_calendar),
                        selected = selected == 0,
                        onClick = { open = false; onSelect(0) },
                    )
                    ColorChoiceRow(
                        color = -1,
                        label = stringResource(R.string.event_color_set_to_default),
                        selected = selected == -1,
                        onClick = { open = false; onSelect(-1) },
                    )
                    if (colors.isNotEmpty()) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        // The account's palette, wrapped so a long list stays manageable.
                        colors.chunked(5).forEach { row ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                row.forEach { color ->
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(Color(color))
                                            .clickable { open = false; onSelect(color) },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        if (color == selected) {
                                            Text(
                                                text = "✓",
                                                color = Color.White,
                                                style = MaterialTheme.typography.titleMedium,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    Text(
                        text = stringResource(R.string.m3e_event_color_pick_any),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    // Continuous adjustment. Whatever the sliders land on is a real event colour, so
                    // it is written straight through rather than being a separate "custom" mode.
                    HsvColorPicker(
                        initial = if (selected > 0) selected else colors.firstOrNull() ?: 0xFF64B5F6.toInt(),
                        onColor = { onSelect(it) },
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { open = false }) {
                    Text(stringResource(R.string.m3e_cancel))
                }
            },
        )
    }
}

/** One of the two special colour choices, with the colour it stands for shown beside it. */
@Composable
private fun ColorChoiceRow(
    color: Int,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Swatch(color = color, size = 24.dp)
        Spacer(Modifier.width(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Text("✓", color = MaterialTheme.colorScheme.primary)
        }
    }
}

/**
 * Continuous colour adjustment.
 *
 * Three sliders rather than a wheel: hue picks the angle, then saturation and lightness refine it,
 * with a preview showing the result. Each slider's track is drawn as the gradient it controls, so
 * what moving it will do is visible before touching it.
 *
 * The state lives here and is emitted on every change, so the editor's colour row follows the
 * sliders as they move.
 */
@Composable
private fun HsvColorPicker(initial: Int, onColor: (Int) -> Unit) {
    val start = remember(initial) {
        FloatArray(3).also { android.graphics.Color.colorToHSV(initial, it) }
    }
    var hue by remember(initial) { mutableFloatStateOf(start[0]) }
    var saturation by remember(initial) { mutableFloatStateOf(start[1]) }
    var lightness by remember(initial) { mutableFloatStateOf(start[2]) }

    fun emit() {
        onColor(android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, lightness)))
    }

    /**
     * A slider whose track is the gradient it controls.
     *
     * Drawn by hand: Material's Slider takes a single colour, and a colour picker whose sliders are
     * one flat colour does not say what they do.
     */
    @Composable
    fun gradientSlider(
        value: Float,
        range: ClosedFloatingPointRange<Float>,
        onChange: (Float) -> Unit,
        stops: List<Color>,
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(32.dp)
                .pointerInput(range) {
                    detectHorizontalDragGestures { change, _ ->
                        val fraction = (change.position.x / size.width).coerceIn(0f, 1f)
                        onChange(range.start + fraction * (range.endInclusive - range.start))
                    }
                }
                .pointerInput(range) {
                    detectTapGestures { offset ->
                        val fraction = (offset.x / size.width).coerceIn(0f, 1f)
                        onChange(range.start + fraction * (range.endInclusive - range.start))
                    }
                },
        ) {
            val barHeight = 12.dp.toPx()
            val top = (size.height - barHeight) / 2f
            drawRoundRect(
                brush = Brush.horizontalGradient(stops),
                topLeft = Offset(0f, top),
                size = Size(size.width, barHeight),
                cornerRadius = CornerRadius(barHeight / 2f),
            )
            val fraction = ((value - range.start) / (range.endInclusive - range.start))
                .coerceIn(0f, 1f)
            val centre = Offset(fraction * size.width, size.height / 2f)
            drawCircle(color = Color.White, radius = barHeight * 0.75f, center = centre)
            drawCircle(
                color = Color.Black,
                radius = barHeight * 0.75f,
                center = centre,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f),
            )
        }
    }

    Column {
        // Hue: the full spectrum.
        gradientSlider(
            value = hue,
            range = 0f..360f,
            onChange = { hue = it; emit() },
            stops = (0..6).map {
                Color(android.graphics.Color.HSVToColor(floatArrayOf(it * 60f, 1f, 1f)))
            },
        )
        Spacer(Modifier.height(6.dp))
        // Saturation: grey through to the chosen hue at full strength.
        gradientSlider(
            value = saturation,
            range = 0f..1f,
            onChange = { saturation = it; emit() },
            stops = listOf(
                Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, 0f, lightness))),
                Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, 1f, lightness))),
            ),
        )
        Spacer(Modifier.height(6.dp))
        // Lightness: black through the hue to white.
        gradientSlider(
            value = lightness,
            range = 0f..1f,
            onChange = { lightness = it; emit() },
            stops = listOf(
                Color.Black,
                Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, 0.5f))),
                Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, 1f))),
            ),
        )
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(
                        Color(
                            android.graphics.Color.HSVToColor(
                                floatArrayOf(hue, saturation, lightness)
                            )
                        )
                    ),
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = stringResource(R.string.m3e_event_color_preview),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * A colour chip.
 *
 * 0 and -1 are not colours: they mean "follow the calendar" and "the calendar's own colour". Drawn
 * as an outlined circle with a diagonal, so the two read as choices about a colour rather than as
 * two colours.
 */
@Composable
private fun Swatch(color: Int, size: androidx.compose.ui.unit.Dp) {
    if (color == 0 || color == -1) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "／",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    } else {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(Color(color))
        )
    }
}

/** The same palette the classic colour picker offers. */
private val PALETTE = intArrayOf(
    0xFFE57373.toInt(), 0xFFF06292.toInt(), 0xFFBA68C8.toInt(), 0xFF9575CD.toInt(),
    0xFF7986CB.toInt(), 0xFF64B5F6.toInt(), 0xFF4FC3F7.toInt(), 0xFF4DD0E1.toInt(),
    0xFF4DB6AC.toInt(), 0xFF81C784.toInt(),
)

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
private fun SwitchRow(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun DateTimeRow(
    label: String,
    millis: Long,
    allDay: Boolean,
    endRow: Boolean,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    // An all-day event's days are UTC days, and its end is exclusive - midnight of the day after the
    // last one - so the end row prints the day before it. That is the day the event still covers,
    // which is the one the user picked.
    val shownMillis = remember(millis, allDay, endRow) {
        if (allDay && endRow) utcMidnight(utcDateOf(millis).minusDays(1)) else millis
    }
    val dateText = remember(shownMillis, allDay) {
        if (allDay) {
            DateFormat.getMediumDateFormat(context)
                .format(Date(utcMidnight(utcDateOf(shownMillis))))
        } else {
            DateFormat.getMediumDateFormat(context).format(Date(shownMillis))
        }
    }
    val timeText = remember(millis, allDay) {
        if (allDay) {
            context.getString(R.string.edit_event_all_day_label)
        } else {
            SimpleDateFormat(
                if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm a",
                Locale.getDefault(),
            ).format(Date(millis))
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = if (allDay) dateText else "$dateText  $timeText",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/** The UTC midnight of [date], which is how an all-day event's day is stored. */
private fun utcMidnight(date: LocalDate): Long =
    date.atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()

/** The UTC day a stored all-day instant falls on. */
private fun utcDateOf(millis: Long): LocalDate =
    Instant.ofEpochMilli(millis).atZone(java.time.ZoneOffset.UTC).toLocalDate()
