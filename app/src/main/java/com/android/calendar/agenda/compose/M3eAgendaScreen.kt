/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * Material 3 Expressive agenda ("日程") screen.
 */
package com.android.calendar.agenda.compose

import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.IconButton
import android.os.Build
import android.text.format.DateFormat
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.google.android.material.color.MaterialColors
import com.android.calendar.chrome.compose.M3eChromeScaffold
import com.android.calendar.chrome.compose.M3eScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ws.xsoh.etar.R
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** How many days ahead the screen loads. */
private const val AGENDA_DAYS = 90

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun M3eAgendaScreen(
    onOpenEvent: (AgendaEvent) -> Unit,
    onDeleteEvent: (AgendaEvent) -> Unit,
    /** Called when a row's notification lead time is changed from the list. */
    onReminderChanged: (AgendaEvent, List<Int>) -> Unit = { _, _ -> },
    /**
     * Bumped by the host whenever it comes back to the foreground. Editing an event from here goes
     * through another screen, and the provider's change notification arrives asynchronously, so the
     * list re-reads itself on return rather than trusting that it heard about the change.
     */
    refreshKey: Int = 0,
    /**
     * The term to search for, or null to browse.
     *
     * Searching reuses this screen rather than adding a second list: the rows, the day headers and the
     * delete behaviour are all the same thing, and a result is just an event found by name rather than
     * by date. The window buttons are replaced by a field while a search is open.
     */
    searchQuery: String? = null,
    /** Called as the search field is edited; null closes the search. */
    onSearchQueryChange: ((String?) -> Unit)? = null,
) {
    val context = LocalContext.current
    val zone = remember { ZoneId.systemDefault() }

    var loading by remember { mutableStateOf(true) }
    var events by remember { mutableStateOf<List<AgendaEvent>>(emptyList()) }

    // Deleting removes the row from this list straight away. The provider delete is what actually
    // removes the event, but CalendarProvider notifies observers asynchronously, so waiting for a
    // re-query made a deleted event sit there until the screen was reopened. Hiding it immediately
    // and re-querying in the background gives the list the truth without the wait.
    var hiddenIds by remember { mutableStateOf<Set<Long>>(emptySet()) }

    /**
     * The event whose delete confirmation is open.
     *
     * Only ever set for an event that has a countdown: an ordinary event is deleted by the swipe
     * alone, exactly as before, because asking on every row would be noise.
     */
    var confirmingDelete by remember { mutableStateOf<AgendaEvent?>(null) }

    /** The countdown paired with an event, looked up when the confirmation is built. */
    fun countdownFor(event: AgendaEvent): com.android.calendar.countdown.Countdown? =
        com.android.calendar.countdown.CountdownStore.forEventOrSimilar(
            context = context,
            eventId = event.id,
            eventTitle = event.title,
            eventStartMillis = event.begin,
            zone = zone,
        )

    // Start of the 90 day window that is currently loaded. The two buttons below shift it.
    var windowStart by remember { mutableStateOf(LocalDate.now(zone)) }

    LaunchedEffect(windowStart, refreshKey, searchQuery) {
        loading = true
        events = withContext(Dispatchers.IO) {
            val repository = AgendaRepository(context.applicationContext)
            val term = searchQuery?.trim()
            if (term.isNullOrEmpty()) {
                repository.load(
                    windowStart.atStartOfDay(zone).toInstant().toEpochMilli(),
                    windowStart.plusDays(AGENDA_DAYS.toLong())
                        .atStartOfDay(zone).toInstant().toEpochMilli(),
                )
            } else {
                repository.search(term, System.currentTimeMillis())
            }
        }
        loading = false
    }

    M3eChromeScaffold(
        selected = M3eScreen.AGENDA,
        title = { Text(stringResource(R.string.agenda_view)) },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (onSearchQueryChange != null && searchQuery != null) {
                // The search field, in place of the window controls: while a search is open the window
                // is not what is being looked at, and the results are not confined to it.
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { onSearchQueryChange(it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    singleLine = true,
                    label = { Text(stringResource(R.string.search)) },
                    leadingIcon = {
                        Icon(
                            painter = painterResource(R.drawable.ic_menu_search),
                            contentDescription = null,
                        )
                    },
                    trailingIcon = {
                        IconButton(onClick = { onSearchQueryChange(null) }) {
                            Icon(
                                painter = painterResource(R.drawable.ic_close),
                                contentDescription = stringResource(R.string.m3e_cancel),
                            )
                        }
                    },
                )
            } else {
                // Two explicit controls instead of an endless list: step the window back or forward
                // by the same 90 days the screen shows at a time.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FilledTonalButton(
                        onClick = { windowStart = windowStart.minusDays(AGENDA_DAYS.toLong()) },
                    ) {
                        Text(stringResource(R.string.m3e_agenda_prev90))
                    }
                    FilledTonalButton(
                        onClick = { windowStart = windowStart.plusDays(AGENDA_DAYS.toLong()) },
                    ) {
                        Text(stringResource(R.string.m3e_agenda_next90))
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = remember(windowStart) {
                            android.text.format.DateFormat.getDateFormat(context)
                                .format(Date(windowStart.atStartOfDay(zone)
                                    .toInstant().toEpochMilli()))
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            when {
            loading -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) { LoadingIndicator() }

            events.isEmpty() -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.m3e_agenda_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            else -> {
                val grouped = remember(events) {
                    events.groupBy {
                        Instant.ofEpochMilli(it.begin).atZone(zone).toLocalDate()
                    }.toSortedMap()
                }
                val visible = remember(grouped, hiddenIds) {
                    if (hiddenIds.isEmpty()) {
                        grouped
                    } else {
                        grouped.mapValues { (_, list) -> list.filterNot { it.id in hiddenIds } }
                            .filterValues { it.isNotEmpty() }
                    }
                }
                LazyColumn(
                    // No padding(padding) here: the scaffold's insets are already applied by the
                    // Column above, and applying them again is what left the gap between the toolbar
                    // and the first row.
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    visible.forEach { (date, dayEvents) ->
                        item(key = "header-$date") { DayHeader(date) }
                        items(dayEvents, key = { "${it.id}-${it.begin}" }) { event ->
                            AgendaRow(
                                event = event,
                                onClick = { onOpenEvent(event) },
                                onDelete = {
                                    // Every swipe asks first. The dialog carries a "also delete the
                                    // countdown" box only when there is a countdown to delete, which is
                                    // what keeps an ordinary event's confirmation to a plain yes/no.
                                    confirmingDelete = event
                                },
                                onReminderChanged = { minutes ->
                                    onReminderChanged(event, minutes)
                                },
                            )
                        }
                    }
                }
            }
            }
        }
    }

    // Asked on every swipe. The dialog shows a "also delete the countdown" box for an event that has
    // one, and is a plain confirmation for an event that does not.
    confirmingDelete?.let { event ->
        val countdown = countdownFor(event)
        com.android.calendar.countdown.compose.EventDeleteWithCountdownDialog(
            eventTitle = event.title,
            countdownName = countdown?.name,
            onDismiss = { confirmingDelete = null },
            onConfirm = { alsoDeleteCountdown ->
                if (countdown != null && alsoDeleteCountdown) {
                    com.android.calendar.countdown.CountdownStore.remove(context, countdown.id)
                    com.android.calendar.widget.CountdownWidgetUpdater.updateAll(context)
                } else if (countdown != null) {
                    // The countdown outlives the event, so the link is broken with it - otherwise
                    // the row would keep claiming an event that is gone.
                    com.android.calendar.countdown.CountdownStore.unlinkEvent(context, event.id)
                }
                confirmingDelete = null
                hiddenIds = hiddenIds + event.id
                onDeleteEvent(event)
            },
        )
    }
}

@Composable
private fun DayHeader(date: LocalDate) {
    val context = LocalContext.current
    val today = remember { LocalDate.now() }
    val weekday = remember(date) {
        SimpleDateFormat("EEEE", Locale.getDefault()).format(
            Date.from(date.atStartOfDay(ZoneId.systemDefault()).toInstant())
        )
    }
    val label = remember(date) {
        DateFormat.getMediumDateFormat(context).format(
            Date.from(date.atStartOfDay(ZoneId.systemDefault()).toInstant())
        )
    }

    Column(modifier = Modifier.padding(start = 8.dp, top = 4.dp, bottom = 4.dp)) {
        if (date == today) {
            Text(
                text = stringResource(R.string.m3e_agenda_today),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Text(
            text = "$weekday · $label",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (date == today) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
    }
}

/**
 * Fires the platform's short haptic tick.
 *
 * Kept behind a small wrapper because the Vibrator service is fetched differently before and after
 * API 31, and the swipe only needs "one short buzz".
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
                context.getSystemService(android.content.Context.VIBRATOR_SERVICE) as? android.os.Vibrator
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

/**
 * A single agenda row, swipeable to the left to delete.
 *
 * Swiping past the threshold vibrates once and removes the event. There is no confirm step: the
 * gesture is deliberate, and the removal is what the swipe is for.
 *
 * The leading colour bar carries the calendar colour, harmonised towards the wallpaper-derived
 * primary so that the list follows "monet" while the calendars stay distinguishable from each other
 * - the same rule the week/day grid uses.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AgendaRow(
    event: AgendaEvent,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onReminderChanged: (List<Int>) -> Unit,
) {
    val context = LocalContext.current
    val accent = harmonizedColor(event.calendarColor)
    val haptics = remember { HapticFeedbackCompat(context) }
    val dismissState = rememberSwipeToDismissBoxState(
        // The swipe asks; it never removes the row itself. For an event with no countdown the list
        // hides the row in onDelete, which looks the same to the user; for one with a countdown the
        // row springs back while the dialog is up, which is the honest picture - nothing is gone yet.
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                haptics.vibrate()
                onDelete()
            }
            false
        },
    )

    var reminderMenuOpen by remember(event.id) { mutableStateOf(false) }

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = true,
        backgroundContent = {
            // Revealed as the row slides away: a red panel with a bin, filling the space freed up.
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
                // The app's own bin vector: the Material icons artifact is not on the classpath, so
                // Icons.Default.Delete is not available.
                Icon(
                    painter = painterResource(R.drawable.ic_delete),
                    contentDescription = stringResource(R.string.m3e_event_delete),
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        },
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 5.dp, height = 44.dp)
                        .background(color = accent, shape = CircleShape)
                )
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = event.title.ifEmpty { stringResource(R.string.no_title_label) },
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = timeLabel(event),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    event.calendarName?.let { name ->
                        Text(
                            text = name,
                            style = MaterialTheme.typography.labelSmall,
                            color = accent,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    event.location?.takeIf { it.isNotBlank() }?.let { loc ->
                        Text(
                            text = loc,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                // The notification lead time, changeable right here - the classic agenda let the
                // user do this without opening the editor, and going through the editor for it is
                // more steps than the task deserves.
                Column(horizontalAlignment = Alignment.End) {
                    Box {
                        Text(
                            text = reminderLabel(event.remindersMinutes),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .clickable { reminderMenuOpen = true }
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                        )
                        DropdownMenu(
                            expanded = reminderMenuOpen,
                            onDismissRequest = { reminderMenuOpen = false },
                        ) {
                            REMINDER_CHOICES.forEach { choice ->
                                DropdownMenuItem(
                                    text = { Text(reminderLabel(choice)) },
                                    onClick = {
                                        reminderMenuOpen = false
                                        onReminderChanged(choice)
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

/** Lead times offered on an agenda row, matching the editor's list. */
private val REMINDER_CHOICES: List<List<Int>> = listOf(
    emptyList(),
    listOf(0),
    listOf(5),
    listOf(10),
    listOf(15),
    listOf(30),
    listOf(60),
    listOf(120),
    listOf(1440),
)

/** "通知 10 分钟前" style label, or that there is none. */
@Composable
private fun reminderLabel(minutes: List<Int>): String {
    if (minutes.isEmpty()) {
        return stringResource(R.string.m3e_no_reminders)
    }
    // The list only ever writes a single lead time, so showing the first is enough; an event with
    // several keeps them until it is edited here, which collapses them by design.
    val value = minutes.min()
    return when {
        value <= 0 -> stringResource(R.string.m3e_reminder_at_time)
        value % (24 * 60) == 0 -> stringResource(R.string.m3e_reminder_days, value / (24 * 60))
        value % 60 == 0 -> stringResource(R.string.m3e_reminder_hours, value / 60)
        else -> stringResource(R.string.m3e_reminder_minutes, value)
    }
}

@Composable
private fun timeLabel(event: AgendaEvent): String {
    val context = LocalContext.current
    if (event.allDay) {
        return stringResource(R.string.edit_event_all_day_label)
    }
    val zone = remember { ZoneId.systemDefault() }
    val pattern = if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm a"
    val fmt = remember(pattern) { SimpleDateFormat(pattern, Locale.getDefault()) }
    val sameDay = remember(event) {
        val s = Instant.ofEpochMilli(event.begin).atZone(zone).toLocalDate()
        val e = Instant.ofEpochMilli(event.end).atZone(zone).toLocalDate()
        s == e
    }
    val start = fmt.format(Date(event.begin))
    return if (sameDay) {
        "$start – ${fmt.format(Date(event.end))}"
    } else {
        start
    }
}

/** Harmonises a calendar colour with the current theme primary (monet) on Android 12+. */
@Composable
private fun harmonizedColor(calendarColor: Int): Color {
    val primary = MaterialTheme.colorScheme.primary.toArgb()
    return remember(calendarColor, primary) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Color(MaterialColors.harmonize(calendarColor, primary))
        } else {
            Color(calendarColor)
        }
    }
}

/** Unused placeholder kept so the classic agenda's rounded-corner metric stays discoverable. */
internal fun agendaCorner() = RoundedCornerShape(28.dp)

@Suppress("unused")
private val unusedCalendar = Calendar.getInstance()
