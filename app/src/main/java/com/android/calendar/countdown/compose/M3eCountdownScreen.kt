/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The Material 3 Expressive countdown ("倒数日") screen.
 */
package com.android.calendar.countdown.compose

import androidx.compose.material3.IconButton
import androidx.compose.material3.FilledTonalIconButton
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import com.android.calendar.chrome.compose.CountdownBackdrop
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.android.calendar.chrome.compose.ColorPaletteGrid
import com.android.calendar.chrome.compose.HsvColorPicker
import com.android.calendar.chrome.compose.M3eDatePickerDialog
import com.android.calendar.chrome.compose.M3eEdgeToEdgeContent
import com.android.calendar.chrome.compose.onColorFor
import com.android.calendar.countdown.Countdown
import com.android.calendar.countdown.CountdownState
import com.android.calendar.countdown.CountdownStore
import com.android.calendar.countdown.CountdownUnit
import com.android.calendar.settings.compose.SettingsRepository
import ws.xsoh.etar.R
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** Settings keys, shared with the settings screen. */
const val KEY_UNIT = "pref_countdown_unit"
const val KEY_DECIMALS = "pref_countdown_decimals"
const val KEY_COUNT_TODAY = "pref_countdown_count_today"
const val KEY_DYNAMIC = "pref_countdown_dynamic"
const val KEY_SORT = "pref_countdown_sort"

/** How the settings tell this screen to draw a countdown. */
private data class DisplaySettings(
    val unit: CountdownUnit,
    val allowDecimals: Boolean,
    val countToday: Boolean,
    val dynamic: Boolean,
    val sort: CountdownStore.SortOrder,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun M3eCountdownScreen() {
    val context = LocalContext.current
    val repo = remember { SettingsRepository(context) }
    val zone = remember { ZoneId.systemDefault() }

    // The stored list is read off the main thread.
    //
    // It used to be read during the first composition, which put a SharedPreferences load and a JSON
    // parse in the middle of the frame that has to appear. The first frame now draws the empty state
    // and the list arrives a moment later, which reads as the screen opening rather than hesitating.
    var items by remember { mutableStateOf(emptyList<Countdown>()) }
    LaunchedEffect(Unit) {
        items = withContext(Dispatchers.IO) { CountdownStore.all(context) }
    }
    var adding by remember { mutableStateOf(false) }

    /** The countdown the edit dialog is working on; null when it is closed. */
    var editing by remember { mutableStateOf<Countdown?>(null) }

    /** The countdown whose detail sheet is open, by id. */
    var detailOf by remember { mutableStateOf<String?>(null) }

    /** The countdown whose delete confirmation is open, by id. */
    var deleting by remember { mutableStateOf<String?>(null) }

    /** The countdown whose long-press action sheet is open, by id. */
    var actionsOf by remember { mutableStateOf<String?>(null) }

    // Remembered rather than rebuilt each recomposition: it is a key of the remember below, and a
    // fresh instance every frame would invalidate that cache every frame.
    val settings = remember {
        DisplaySettings(
            unit = CountdownUnit.fromKey(repo.string(KEY_UNIT, CountdownUnit.DEFAULT.name)),
            allowDecimals = repo.bool(KEY_DECIMALS, false),
            countToday = repo.bool(KEY_COUNT_TODAY, false),
            dynamic = repo.bool(KEY_DYNAMIC, false),
            sort = CountdownStore.SortOrder.fromKey(
                repo.string(KEY_SORT, CountdownStore.SortOrder.DEFAULT.name)
            ),
        )
    }

    // The clock, and how fast it needs to tick.
    //
    // "Dynamic" refreshes every second, which is only worth doing for the units that move that fast.
    // The decision is made from the units actually being *shown*, not the configured one: a countdown
    // set to days steps down to hours once it is close, and that display needs the same per-second
    // refresh as one configured in hours.
    var now by remember { mutableStateOf(System.currentTimeMillis()) }

    /**
     * The filter term, and whether the field for it is open.
     *
     * Search is a filter over this list rather than a screen of its own: a countdown is already on
     * this screen, and the only thing wanted is the one being looked for. It matches the name and,
     * because a countdown is a date too, the date as it is written.
     */
    var searchOpen by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    val visibleItems = remember(items, searchQuery, zone) {
        val term = searchQuery.trim()
        if (term.isEmpty()) {
            items
        } else {
            items.filter { countdown ->
                countdown.name.contains(term, ignoreCase = true) ||
                    countdown.date.toString().contains(term) ||
                    java.time.format.DateTimeFormatter
                        .ofPattern("M月d日")
                        .withLocale(java.util.Locale.getDefault())
                        .format(countdown.date)
                        .contains(term)
            }
        }
    }

    val ordered = remember(visibleItems, settings.sort, now) {
        CountdownStore.sorted(visibleItems, settings.sort, zone, now)
    }
    val shown = remember(ordered, settings, now) {
        ordered.map { countdown ->
            CountdownStore.describe(
                countdown = countdown,
                zone = zone,
                now = now,
                unit = settings.unit,
                allowDecimals = settings.allowDecimals,
                countToday = settings.countToday,
            )
        }
    }
    val tightestShown = shown.minByOrNull { it.unit.ordinal }?.unit
    val live = settings.dynamic &&
        (tightestShown == CountdownUnit.MINUTES || tightestShown == CountdownUnit.HOURS)

    LaunchedEffect(settings.dynamic, live) {
        val period = if (live) 1_000L else 60_000L
        while (true) {
            now = System.currentTimeMillis()
            kotlinx.coroutines.delay(period)
        }
    }

    fun reload() {
        items = CountdownStore.all(context)
        com.android.calendar.widget.CountdownWidgetUpdater.updateAll(context)
    }

    M3eEdgeToEdgeContent {
        // A plain Box, not a Material Scaffold.
        //
        // A Scaffold brings its own contentWindowInsets, defaulting to the safe-drawing set, and the
        // host has not consumed the status bar - so it added a second helping of that inset and the
        // result was an empty band between the toolbar and the first card. The other screens here
        // avoid this by using M3eChromeScaffold, which is a background and nothing else; this screen
        // only needed the floating action button on top, which a Box gives directly.
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                // The filter field, shown only while it is wanted. It is not a screen of its own:
                // there is nothing to search but this list.
                if (searchOpen) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, top = 8.dp),
                        singleLine = true,
                        label = { Text(stringResource(R.string.search)) },
                        leadingIcon = {
                            Icon(
                                painter = painterResource(R.drawable.ic_menu_search),
                                contentDescription = null,
                            )
                        },
                        trailingIcon = {
                            IconButton(onClick = {
                                searchQuery = ""
                                searchOpen = false
                            }) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_close),
                                    contentDescription = stringResource(R.string.m3e_cancel),
                                )
                            }
                        },
                    )
                }

            if (items.isEmpty()) {                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.m3e_countdown_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    // The navigation bar inset lives in the content padding, so the list scrolls
                    // behind the gesture bar but its last card comes to rest above it. The extra room
                    // at the bottom keeps the last card clear of the add button.
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 8.dp,
                        bottom = 96.dp + WindowInsets.navigationBars.asPaddingValues()
                            .calculateBottomPadding(),
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(shown, key = { it.countdown.id }) { state ->
                        CountdownRow(
                            state = state,
                            onOpen = { detailOf = state.countdown.id },
                            onLongPress = { actionsOf = state.countdown.id },
                            onDelete = { deleting = state.countdown.id },
                        )
                    }
                }
            }   // end of the empty/list branch
            }   // end of the Column

            // The search toggle: a plain icon button above the add button, so the field can be
            // brought up without leaving the screen. Outside the Column, so it floats like the add
            // button rather than sitting in the list's flow.
            FilledTonalIconButton(
                onClick = {
                    if (searchOpen) {
                        searchQuery = ""
                        searchOpen = false
                    } else {
                        searchOpen = true
                    }
                },
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(
                        end = 16.dp,
                        bottom = 88.dp + WindowInsets.navigationBars.asPaddingValues()
                            .calculateBottomPadding(),
                    ),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_menu_search),
                    contentDescription = stringResource(R.string.search),
                )
            }

            // The add button, deliberately not a Scaffold's floatingActionButton so that no Scaffold
            // has to exist for it.
            FloatingActionButton(
                onClick = { adding = true },
                // A squarer corner than a circle, which is what the rest of this app's primary
                // actions use.
                shape = RoundedCornerShape(20.dp),
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(
                        end = 16.dp,
                        bottom = 16.dp + WindowInsets.navigationBars.asPaddingValues()
                            .calculateBottomPadding(),
                    ),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_add),
                    contentDescription = stringResource(R.string.m3e_countdown_add),
                )
            }
        }
    }

    // ------------------------------------------------------------------------------- add / edit
    val editingCountdown = editing
    if (adding || editingCountdown != null) {
        CountdownEditorDialog(
            repo = repo,
            existing = editingCountdown,
            onDismiss = { adding = false; editing = null },
            onSave = { countdown, addToCalendar ->
                if (editingCountdown == null) {
                    // The event is written first so the countdown can record which one it made; that
                    // link is what lets a later delete offer to remove both.
                    val eventId = if (addToCalendar) createEvent(context, countdown, zone) else -1L
                    if (addToCalendar && eventId <= 0L) {
                        // Said out loud rather than swallowed: a ticked box that quietly did nothing
                        // reads as the feature being broken.
                        android.widget.Toast.makeText(
                            context,
                            R.string.m3e_countdown_event_failed,
                            android.widget.Toast.LENGTH_LONG,
                        ).show()
                    }
                    CountdownStore.add(context, countdown.copy(eventId = eventId.takeIf { it > 0L }))
                } else {
                    // Editing never re-creates the event: the link stays as it was, and a changed date
                    // is the user's business rather than a reason to move their calendar entry.
                    CountdownStore.update(context, countdown)
                }
                adding = false
                editing = null
                reload()
            },
        )
    }

    // ------------------------------------------------------------------------------------ detail
    val detailState = shown.firstOrNull { it.countdown.id == detailOf }
    if (detailState != null) {
        CountdownDetailSheet(
            countdown = detailState.countdown,
            state = detailState,
            onDismiss = { detailOf = null },
            onEdit = {
                detailOf = null
                editing = detailState.countdown
            },
            onDelete = {
                detailOf = null
                deleting = detailState.countdown.id
            },
        )
    }

    // ---------------------------------------------------------------------------- long press
    val actionsCountdown = items.firstOrNull { it.id == actionsOf }
    if (actionsCountdown != null) {
        CountdownActionsSheet(
            countdown = actionsCountdown,
            onDismiss = { actionsOf = null },
            onEdit = {
                actionsOf = null
                editing = actionsCountdown
            },
            onSaveImage = {
                // The image is the card as shown, so this routes through the detail sheet rather than
                // rendering a second copy here that could drift from it.
                actionsOf = null
                detailOf = actionsCountdown.id
            },
            onDelete = {
                actionsOf = null
                deleting = actionsCountdown.id
            },
        )
    }

    // ------------------------------------------------------------------------------------ delete
    //
    // One dialog for the card, the sheet and the left swipe, so the question is always the same.
    // The event is looked up rather than taken from the stored id alone: a countdown written before
    // the pairing was recorded has no id, and its owner would otherwise never be offered the chance
    // to remove the event it plainly belongs with.
    val deletingCountdown = items.firstOrNull { it.id == deleting }
    if (deletingCountdown != null) {
        val pairedEventId = deletingCountdown.eventId
            ?: pairedEventIdFor(context, deletingCountdown, zone)

        CountdownDeleteDialog(
            countdown = deletingCountdown.copy(eventId = pairedEventId),
            onDismiss = { deleting = null },
            onConfirm = { alsoDeleteEvent ->
                when {
                    alsoDeleteEvent && pairedEventId != null ->
                        com.android.calendar.event.compose.EventEditorRepository(context)
                            .deleteEvent(pairedEventId)
                    // The event stays, so the link must not: it would outlive the countdown it points
                    // at and the calendar would keep claiming a countdown that is gone.
                    pairedEventId != null ->
                        CountdownStore.unlinkEvent(context, pairedEventId)
                }
                CountdownStore.remove(context, deletingCountdown.id)
                deleting = null
                reload()
            },
        )
    }
}

/**
 * The calendar event a countdown belongs with, found by name and day when it has no recorded id.
 *
 * Only consulted for a countdown that is missing its id, and only ever used to *offer* deleting the
 * event - the user can clear the box - so a wrong guess costs nothing.
 */
private fun pairedEventIdFor(
    context: android.content.Context,
    countdown: Countdown,
    zone: ZoneId,
): Long? {
    val dayStart = countdown.date.atStartOfDay(zone).toInstant().toEpochMilli()
    return try {
        context.contentResolver.query(
            android.provider.CalendarContract.Instances.CONTENT_URI.buildUpon()
                .appendPath(dayStart.toString())
                .appendPath((dayStart + CountdownStore.DAY_MILLIS).toString())
                .build(),
            arrayOf(android.provider.CalendarContract.Instances.EVENT_ID),
            android.provider.CalendarContract.Instances.TITLE + "=?",
            arrayOf(countdown.name),
            null,
        )?.use { c -> if (c.moveToFirst()) c.getLong(0) else null }
    } catch (t: Throwable) {
        null
    }
}

/**
 * One countdown in the list.
 *
 * A left swipe asks before deleting rather than removing outright: the row stays until the dialog is
 * confirmed. A tap opens the detail sheet and a long press opens the editor, which keeps the three
 * actions apart without adding a menu.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CountdownRow(
    state: CountdownState,
    onOpen: () -> Unit,
    onLongPress: () -> Unit,
    onDelete: () -> Unit,
) {
    val dismissState = rememberSwipeToDismissBoxState(
        // Never dismissed on its own - returning false leaves the row where it was - because the
        // swipe is a request to delete, not the delete itself.
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
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
                        shape = RoundedCornerShape(24.dp),
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
        CountdownCard(state = state, onOpen = onOpen, onLongPress = onLongPress)
    }
}

/**
 * The countdown itself.
 *
 * A rounded card filled with the countdown's own colour: the point of choosing a colour is that the
 * entry is recognisable at a glance. The text is white or black depending on how light that colour
 * is, so a pale choice stays readable.
 */
@Composable
private fun CountdownCard(
    state: CountdownState,
    onOpen: () -> Unit,
    onLongPress: () -> Unit,
) {
    val countdown = state.countdown
    val onColor = onColorFor(countdown.color)

    CountdownBackdrop(
        color = countdown.color,
        backgroundImageUri = countdown.backgroundImageUri,
        cornerRadius = 24.dp,
        modifier = Modifier
            .fillMaxWidth()
            // Not the full width of a wide screen. The card holds one number, and a card stretched
            // across a tablet reads as mostly empty.
            .widthIn(max = 420.dp)
            .pointerInput(countdown.id) {
                detectTapGestures(
                    onTap = { onOpen() },
                    onLongPress = { onLongPress() },
                )
            },
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            // The name is small; the number is the point.
            Text(
                text = countdown.name,
                style = MaterialTheme.typography.labelLarge,
                color = onColor.copy(alpha = 0.85f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = countdown.date.format(CountdownStore.DATE_FORMAT) +
                    (countdown.time?.let { " " + it.toString() } ?: ""),
                style = MaterialTheme.typography.labelSmall,
                color = onColor.copy(alpha = 0.7f),
            )
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = if (state.passed) {
                        stringResource(R.string.m3e_countdown_passed)
                    } else {
                        stringResource(R.string.m3e_countdown_remaining)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = onColor.copy(alpha = 0.85f),
                    modifier = Modifier.padding(bottom = 4.dp, end = 6.dp),
                )
                Text(
                    text = state.value,
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = onColor,
                )
                Text(
                    text = " " + unitLabel(state.unit),
                    style = MaterialTheme.typography.bodyMedium,
                    color = onColor.copy(alpha = 0.85f),
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }
            if (state.repeated) {
                Text(
                    text = stringResource(R.string.m3e_countdown_next_year),
                    style = MaterialTheme.typography.labelSmall,
                    color = onColor.copy(alpha = 0.7f),
                )
            }
        }
    }
}

@Composable
internal fun unitLabel(unit: CountdownUnit): String = stringResource(
    when (unit) {
        CountdownUnit.MINUTES -> R.string.m3e_countdown_unit_minutes
        CountdownUnit.HOURS -> R.string.m3e_countdown_unit_hours
        CountdownUnit.DAYS -> R.string.m3e_countdown_unit_days
        CountdownUnit.MONTHS -> R.string.m3e_countdown_unit_months
        CountdownUnit.YEARS -> R.string.m3e_countdown_unit_years
    }
)

/**
 * Adds a countdown, or edits one.
 *
 * @param existing the countdown being edited, or null when this is a new one
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CountdownEditorDialog(
    repo: SettingsRepository,
    existing: Countdown?,
    onDismiss: () -> Unit,
    onSave: (Countdown, addToCalendar: Boolean) -> Unit,
) {
    val context = LocalContext.current
    val isEdit = existing != null

    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var color by remember { mutableStateOf(existing?.color ?: CountdownStore.nextColor(context)) }
    var date by remember { mutableStateOf(existing?.date ?: LocalDate.now()) }
    var useTime by remember { mutableStateOf(existing?.time != null) }
    var time by remember { mutableStateOf(existing?.time ?: LocalTime.of(9, 0)) }
    var repeatYearly by remember { mutableStateOf(existing?.repeatYearly ?: false) }
    // Only a new countdown offers to make an event; an edit leaves the existing link alone.
    var addToCalendar by remember { mutableStateOf(false) }

    var pickingDate by remember { mutableStateOf(false) }
    var pickingTime by remember { mutableStateOf(false) }
    var pickingUnit by remember { mutableStateOf(false) }
    var customColor by remember { mutableStateOf(false) }
    var backgroundImageUri by remember { mutableStateOf(existing?.backgroundImageUri) }
    var transparentBackground by remember { mutableStateOf(existing?.transparentBackground ?: true) }
    var unit by remember {
        mutableStateOf(CountdownUnit.fromKey(repo.string(KEY_UNIT, CountdownUnit.DEFAULT.name)))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.extraLarge,
        title = {
            Text(
                stringResource(
                    if (isEdit) R.string.m3e_countdown_edit else R.string.m3e_countdown_add
                )
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.m3e_countdown_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.m3e_countdown_color),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ColorPaletteGrid(
                    colors = CountdownStore.PALETTE,
                    selected = color,
                    onSelect = { color = it },
                    perRow = 6,
                )
                TextButton(onClick = { customColor = true }) {
                    Text(stringResource(R.string.m3e_event_color_custom))
                }

                // A picture behind the card, for the saved image as well as the sheet. Shared with the
                // event editor, which offers the same two settings when it creates a countdown.
                CountdownBackgroundRows(
                    imageUri = backgroundImageUri,
                    transparent = transparentBackground,
                    onImageChange = { backgroundImageUri = it },
                    onTransparentChange = { transparentBackground = it },
                )

                Spacer(Modifier.height(4.dp))
                CheckRow(
                    label = stringResource(R.string.m3e_countdown_use_time),
                    checked = useTime,
                    onChange = { useTime = it },
                )

                PickerRow(
                    label = stringResource(R.string.m3e_countdown_date),
                    value = date.format(CountdownStore.DATE_FORMAT),
                    onClick = { pickingDate = true },
                )
                if (useTime) {
                    PickerRow(
                        label = stringResource(R.string.m3e_countdown_time),
                        value = String.format(
                            java.util.Locale.US, "%02d:%02d", time.hour, time.minute
                        ),
                        onClick = { pickingTime = true },
                    )
                }
                Box {
                    PickerRow(
                        label = stringResource(R.string.m3e_countdown_unit),
                        value = unitLabel(unit),
                        onClick = { pickingUnit = true },
                    )
                    DropdownMenu(
                        expanded = pickingUnit,
                        onDismissRequest = { pickingUnit = false },
                    ) {
                        CountdownUnit.entries.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(unitLabel(option)) },
                                onClick = { unit = option; pickingUnit = false },
                            )
                        }
                    }
                }

                // Only offered for a date still ahead: repeating something already past is a
                // different intention from what "every year" means here.
                if (date.isAfter(LocalDate.now())) {
                    CheckRow(
                        label = stringResource(R.string.m3e_countdown_repeat),
                        checked = repeatYearly,
                        onChange = { repeatYearly = it },
                    )
                }

                if (!isEdit) {
                    CheckRow(
                        label = stringResource(R.string.m3e_countdown_add_to_calendar),
                        checked = addToCalendar,
                        onChange = { addToCalendar = it },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        Countdown(
                            // An edit keeps its identity, and so its link to any calendar event.
                            id = existing?.id ?: CountdownStore.newId(),
                            name = name.trim().ifEmpty {
                                context.getString(R.string.m3e_countdown_default_name)
                            },
                            color = color,
                            dateIso = date.toString(),
                            timeIso = if (useTime) {
                                String.format(
                                    java.util.Locale.US, "%02d:%02d", time.hour, time.minute
                                )
                            } else {
                                null
                            },
                            repeatYearly = repeatYearly,
                            eventId = existing?.eventId,
                            backgroundImageUri = backgroundImageUri,
                            transparentBackground = transparentBackground,
                        ),
                        addToCalendar,
                    )
                },
                enabled = name.isNotBlank(),
            ) { Text(stringResource(R.string.m3e_ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.m3e_cancel)) }
        },
    )

    if (pickingDate) {
        M3eDatePickerDialog(
            initial = date,
            // Monday, matching the app's own month view.
            firstDayOfWeek = DayOfWeek.MONDAY,
            onDismiss = { pickingDate = false },
            onPick = { date = it; pickingDate = false },
        )
    }

    if (pickingTime) {
        val timeState = rememberTimePickerState(initialHour = time.hour, initialMinute = time.minute)
        AlertDialog(
            onDismissRequest = { pickingTime = false },
            shape = MaterialTheme.shapes.extraLarge,
            title = { Text(stringResource(R.string.m3e_countdown_time)) },
            text = { TimePicker(state = timeState) },
            confirmButton = {
                TextButton(onClick = {
                    time = LocalTime.of(timeState.hour, timeState.minute)
                    pickingTime = false
                }) { Text(stringResource(R.string.m3e_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { pickingTime = false }) {
                    Text(stringResource(R.string.m3e_cancel))
                }
            },
        )
    }

    if (customColor) {
        AlertDialog(
            onDismissRequest = { customColor = false },
            shape = MaterialTheme.shapes.extraLarge,
            title = { Text(stringResource(R.string.m3e_event_color_custom)) },
            text = { HsvColorPicker(initial = color, onColor = { color = it }) },
            confirmButton = {
                TextButton(onClick = { customColor = false }) {
                    Text(stringResource(R.string.m3e_ok))
                }
            },
        )
    }
}

@Composable
private fun PickerRow(label: String, value: String, onClick: () -> Unit) {
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
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/**
 * Writes the countdown into the calendar and returns the new event's id, or -1 when it could not be
 * created.
 *
 * This goes through the app's own event writing rather than an ACTION_INSERT intent. The intent only
 * creates the event once the user confirms it in the editor, so no id is available here - and the
 * countdown has to remember which event it produced, so that deleting one can offer to delete the
 * other. With no time the event is all-day on the countdown's date.
 */
private fun createEvent(
    context: android.content.Context,
    countdown: Countdown,
    zone: ZoneId,
): Long {
    val start = countdown.targetMillis(zone)
    val end = if (countdown.time == null) {
        start + CountdownStore.DAY_MILLIS
    } else {
        start + 60L * 60L * 1000L
    }
    return com.android.calendar.event.compose.EventEditorRepository(context).createSimpleEvent(
        title = countdown.name,
        startMillis = start,
        endMillis = end,
        allDay = countdown.time == null,
        color = countdown.color,
    )
}
