/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * Material 3 Expressive month screen.
 *
 * A fixed weekday header over six rows of seven cells, each cell carrying the day number, the lunar
 * day when that setting is on, and a marker per event - the shape the classic month view has, drawn
 * with Compose so it follows the same Material 3 Expressive theme as the other new screens.
 *
 * Paging is horizontal and moves one month at a time. The grid keeps six rows even in months that
 * would fit in five, so the height of the screen does not jump as the user swipes from month to
 * month; the days of the neighbouring months that fill those rows are dimmed rather than removed.
 *
 * The events are read for the whole six week grid rather than for the month alone, because the cells
 * belonging to the next month are on screen and would otherwise be the only empty ones.
 */
package com.android.calendar.composeui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import android.content.Context
import android.content.res.Configuration
import com.android.calendar.LunarUtils
import com.android.calendar.Utils
import com.android.calendar.chrome.compose.M3eAddFabMenu
import com.android.calendar.chrome.compose.rememberAddMenuEntries
import com.android.calendar.chrome.compose.M3eChromeScaffold
import com.android.calendar.chrome.compose.M3eScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ws.xsoh.etar.R
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * How many months the pager holds, and how many stay composed either side.
 *
 * Same reasoning as the day grid's page count: the offset of a page is a Float over the whole range,
 * so a range long enough to be interesting is still kept short enough for the arithmetic to be
 * exact. 1200 months is a century each way from the middle.
 */
private const val MONTH_PAGE_COUNT = 1200
private const val MONTH_PAGE_MIDDLE = MONTH_PAGE_COUNT / 2
private const val MONTH_PAGE_BEYOND_VIEWPORT = 1

/** Six rows, always, so the grid does not change height between months. */
private const val MONTH_GRID_ROWS = 6
private const val MONTH_GRID_DAYS = MONTH_GRID_ROWS * 7

/**
 * How many event markers a cell shows.
 *
 * Three is what fits under the day number at the cell heights a phone gives this grid. A fourth bar
 * pushed the lunar day out of the cell, and the bar that was cut off said nothing anyway.
 */
private const val MAX_MARKERS = 3

/**
 * Width of the week-number column down the left of the grid.
 *
 * Narrow on purpose: it carries two digits, and every dp it takes comes off all seven day cells,
 * which are already the narrowest in the app. The classic month view has the same column when the
 * setting is on, and the numbers are read as a guide rather than as a row of their own.
 */
private val WEEK_NUMBER_WIDTH = 22.dp

/**
 * The corner of a day cell's pressed highlight.
 *
 * The same 12dp the cell's ripple was clipped to before the scrolling work removed the per-cell
 * ripple; the highlight is drawn as a rounded rectangle instead of being clipped to one.
 */
private val DAY_CELL_CORNER_RADIUS = 12.dp

/** Material's pressed state-layer opacity, over onSurface, for the day cell's highlight. */
private const val PRESSED_STATE_LAYER_ALPHA = 0.12f

/** Length of an event created by the add button, matching the other views' default. */
private const val DEFAULT_NEW_EVENT_MILLIS = 60L * 60L * 1000L

/**
 * How many months' events the pager cache keeps.
 *
 * The page on screen and its neighbours are the ones a swipe needs; the rest is history that makes a
 * quick back-and-forth free. Six is two screens' worth either side of the visible month.
 */
private const val MONTH_CACHE_MONTHS = 6

/** The month a cell belongs to, as a month index counted from January 1970. */
private fun monthIndexOf(month: YearMonth): Int =
    (month.year - 1970) * 12 + (month.monthValue - 1)

private fun monthAt(index: Int): YearMonth =
    YearMonth.of(1970 + Math.floorDiv(index, 12), Math.floorMod(index, 12) + 1)

/**
 * The month and year, written the way each language writes them: "October 2026", "2026年10月",
 * "Oktober 2026".
 *
 * Built from the platform's pattern rather than from a month name and a number, for the same reason
 * the three day heading is: the order of the two pieces and the glue between them belong to the
 * language, not to this screen.
 */
internal fun monthTitle(month: YearMonth): String {
    val millis = month.atDay(1)
        .atStartOfDay(ZoneId.systemDefault())
        .toInstant()
        .toEpochMilli()
    val locale = Locale.getDefault()
    val pattern = android.text.format.DateFormat.getBestDateTimePattern(locale, "yMMMM")
    return java.text.SimpleDateFormat(pattern, locale).format(java.util.Date(millis))
}

/**
 * The month grid.
 *
 * @param page the day to open on, counted in days from 1970-01-01; the month that contains it is the
 *   one the screen opens on.
 * @param firstDayOfWeek which day the weeks start on, from the app's own week start setting.
 * @param onVisibleMonthChanged called with the month on screen, so the host can set its heading.
 * @param onOpenDay called when a cell is tapped; the host opens the day view on that day.
 */
@Composable
fun M3eMonthScreen(
    page: Long,
    firstDayOfWeek: DayOfWeek,
    onVisibleMonthChanged: (YearMonth) -> Unit,
    onOpenDay: (LocalDate) -> Unit,
    onCreateEvent: (start: Long, end: Long) -> Unit,
    refreshKey: Int = 0,
    scrollToTodayKey: Int = 0,
    pickedDayKey: Long = 0,
) {
    val context = LocalContext.current
    val zone = remember { ZoneId.systemDefault() }
    val today = remember { LocalDate.now(zone) }

    // The add chooser is a sheet over this screen; false when it is closed.
    var showAddChooser by remember { mutableStateOf(false) }

    // Keyed on refreshKey, the host's per-resume bump, so turning the lunar line on or off in the
    // settings and coming back is noticed. A plain read would keep whatever the first composition
    // saw, which is what the three day grid did before it was keyed the same way.
    val showLunar = remember(refreshKey) { LunarUtils.showLunar(context) }

    // The week numbers down the left of the grid. Keyed on refreshKey for the same reason as the
    // lunar line: the switch lives on another screen and has no change notification of its own.
    val showWeekNumber = remember(refreshKey) { Utils.getShowWeekNumber(context) }

    val centreIndex = remember(page) { monthIndexOf(YearMonth.from(LocalDate.ofEpochDay(page))) }
    val pagerState = rememberPagerState(
        initialPage = MONTH_PAGE_MIDDLE,
        pageCount = { MONTH_PAGE_COUNT },
    )

    fun monthOfPage(index: Int): YearMonth = monthAt(centreIndex + index - MONTH_PAGE_MIDDLE)

    // The heading's month, read through derivedStateOf and only inside the title lambda. Reading
    // currentPage here, as a plain value, recomposed the whole screen - pager and all - every time a
    // swipe crossed a page boundary.
    val headingMonth by remember(pagerState, centreIndex) {
        derivedStateOf { monthOfPage(pagerState.currentPage) }
    }

    // A month's events, loaded once and kept. The grid never waits for them: a page draws its six
    // weeks from calendar arithmetic as soon as it composes and its markers are added when the query
    // lands, so a month that has not been read yet is still a complete month - just without bars.
    val eventsByMonth = remember(firstDayOfWeek) {
        mutableStateMapOf<YearMonth, List<CalendarDayEvent>>()
    }
    // The months a query is running for, so a second request cannot start another one for the same
    // month. Main-thread only: both the launch and the removal happen there.
    val inFlight = remember(firstDayOfWeek) { mutableSetOf<YearMonth>() }
    // Load order, oldest first, for the small cache eviction below. Kept apart from the map because
    // the map's iteration order is not a policy.
    val recentMonths = remember(firstDayOfWeek) { mutableListOf<YearMonth>() }
    val repository = remember(context) { DaysRepository(context.applicationContext) }
    val loaderScope = rememberCoroutineScope()

    /** Fetches one page's month unless it is cached, or already being fetched. */
    fun loadPage(index: Int, force: Boolean) {
        if (index !in 0 until MONTH_PAGE_COUNT) return
        val month = monthOfPage(index)
        if (!force && eventsByMonth.containsKey(month)) return
        if (!inFlight.add(month)) return
        loaderScope.launch {
            try {
                val gridStart = monthGridStart(month, firstDayOfWeek)
                val events = withContext(Dispatchers.IO) {
                    repository.load(
                        gridStart.atStartOfDay(zone).toInstant().toEpochMilli(),
                        gridStart.plusDays(MONTH_GRID_DAYS.toLong())
                            .atStartOfDay(zone).toInstant().toEpochMilli(),
                        // A six week window on a busy calendar: the default is enough for a day or a
                        // week, but a month of a shared calendar can hold more than that, and the
                        // markers that fall outside it would simply be missing.
                        limit = 2000,
                    )
                }
                eventsByMonth[month] = events
                recentMonths.remove(month)
                recentMonths.add(month)
                while (recentMonths.size > MONTH_CACHE_MONTHS) {
                    eventsByMonth.remove(recentMonths.removeAt(0))
                }
            } finally {
                inFlight.remove(month)
            }
        }
    }

    // The page on screen and one either side: the neighbours are what the pager composes, so a swipe
    // arrives at a page whose markers are already there. The first pass of a restarted effect - a
    // resume, or a changed week start - re-reads them even when they are cached.
    LaunchedEffect(pagerState, centreIndex, refreshKey, firstDayOfWeek) {
        var firstPass = true
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .collect { settled ->
                for (offset in -1..1) loadPage(settled + offset, force = firstPass)
                firstPass = false
            }
    }

    // The page being dragged towards, once the drag is a quarter of the way: waiting for the settle
    // would start the query only when the page is already on screen. snapshotFlow filters by value,
    // so this reacts to the threshold being crossed, not to every frame of the drag.
    LaunchedEffect(pagerState, centreIndex, refreshKey, firstDayOfWeek) {
        snapshotFlow {
            pageToPrefetch(pagerState.currentPage, pagerState.currentPageOffsetFraction)
        }
            .distinctUntilChanged()
            .collect { page -> if (page != null) loadPage(page, force = false) }
    }

    LaunchedEffect(pagerState, centreIndex) {
        snapshotFlow { monthOfPage(pagerState.currentPage) }
            .distinctUntilChanged()
            .collect { onVisibleMonthChanged(it) }
    }

    // Today, brought on screen when the host asks.
    LaunchedEffect(scrollToTodayKey) {
        if (scrollToTodayKey == 0) return@LaunchedEffect
        // Measured from the month on screen rather than from the month this screen opened on: after
        // a swipe the opening month is a stale reference and the distance would be wrong.
        val current = monthOfPage(pagerState.currentPage)
        val monthsAway = ChronoUnit.MONTHS.between(current, YearMonth.from(LocalDate.now(zone)))
        val targetPage = pagerState.currentPage + monthsAway.toInt()
        if (targetPage in 0 until MONTH_PAGE_COUNT) {
            pagerState.animateScrollToPage(targetPage)
        }
    }

    // A day chosen from the heading's picker, brought on screen - the month that contains it.
    LaunchedEffect(pickedDayKey) {
        if (pickedDayKey == 0L) return@LaunchedEffect
        val target = YearMonth.from(LocalDate.ofEpochDay(pickedDayKey))
        val current = monthOfPage(pagerState.currentPage)
        val monthsAway = ChronoUnit.MONTHS.between(current, target)
        val targetPage = pagerState.currentPage + monthsAway.toInt()
        if (targetPage in 0 until MONTH_PAGE_COUNT) {
            pagerState.animateScrollToPage(targetPage)
        }
    }

    M3eChromeScaffold(
        selected = M3eScreen.MONTH,
        title = {
            // Formatted once per month: the pattern lookup and the SimpleDateFormat both allocate,
            // and the old call ran again on every recomposition of the screen.
            val month = headingMonth
            Text(remember(month) { monthTitle(month) })
        },
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                beyondViewportPageCount = MONTH_PAGE_BEYOND_VIEWPORT,
            ) { index ->
                val month = monthOfPage(index)
                MonthPage(
                    month = month,
                    events = eventsByMonth[month],
                    today = today,
                    zone = zone,
                    showLunar = showLunar,
                    showWeekNumber = showWeekNumber,
                    firstDayOfWeek = firstDayOfWeek,
                    onOpenDay = onOpenDay,
                )
            }

            // The add button, with its three actions floating above it. The day they default to
            // follows the rule for the month on screen.
            val addEntries = rememberAddMenuEntries(
                onAddEvent = {
                    showAddChooser = false
                    // Read at the click: the month on screen is only needed here, and reading it in
                    // composition would make every page crossing recompose this screen.
                    val month = monthOfPage(pagerState.currentPage)
                    val todayInMonth = YearMonth.from(today) == month
                    val start = if (todayInMonth) {
                        Utils.getNextCreateEventStart(System.currentTimeMillis())
                    } else {
                        month.atDay(1).atTime(LocalTime.of(9, 0))
                            .atZone(zone)
                            .toInstant()
                            .toEpochMilli()
                    }
                    onCreateEvent(start, start + DEFAULT_NEW_EVENT_MILLIS)
                },
                onAddTodo = {
                    showAddChooser = false
                    val month = monthOfPage(pagerState.currentPage)
                    val addDate = addActionDate(
                        kind = AddViewKind.MONTH,
                        today = today,
                        firstVisibleDay = month.atDay(1),
                        visibleDays = month.lengthOfMonth(),
                    )
                    // The widget's own flow: open the editor on that day, not the list. The opener
                    // extras make back return here with the return slide.
                    context.startActivity(
                        com.android.calendar.todo.compose.ComposeTodoActivity.intent(context, addDate)
                            .putExtra(
                                com.android.calendar.todo.compose.ComposeTodoActivity.EXTRA_OPEN_EDITOR,
                                true,
                            )
                            .openedOver(context),
                    )
                },
                onAddCountdown = {
                    showAddChooser = false
                    val month = monthOfPage(pagerState.currentPage)
                    val addDate = addActionDate(
                        kind = AddViewKind.MONTH,
                        today = today,
                        firstVisibleDay = month.atDay(1),
                        visibleDays = month.lengthOfMonth(),
                    )
                    context.startActivity(
                        com.android.calendar.countdown.compose.ComposeCountdownActivity.intent(
                            context,
                            addDate,
                        ).openedOver(context),
                    )
                },
            )
            M3eAddFabMenu(
                expanded = showAddChooser,
                onToggle = { showAddChooser = !showAddChooser },
                entries = addEntries,
            )
        }
    }
}

/** One month: the weekday header and the six rows of cells, with that month's events loaded. */
@Composable
private fun MonthPage(
    month: YearMonth,
    events: List<CalendarDayEvent>?,
    today: LocalDate,
    zone: ZoneId,
    showLunar: Boolean,
    showWeekNumber: Boolean,
    firstDayOfWeek: DayOfWeek,
    onOpenDay: (LocalDate) -> Unit,
) {
    val context = LocalContext.current

    // A landscape window divides the same height into the same six rows, so a row is about half as
    // tall there. The cells lay themselves out differently for it - see DayCell.
    val compact = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    val gridStart = remember(month, firstDayOfWeek) {
        monthGridStart(month, firstDayOfWeek)
    }
    val cells = remember(gridStart) {
        (0L until MONTH_GRID_DAYS.toLong()).map { gridStart.plusDays(it) }
    }

    // No waiting state: the six weeks are complete without events, and a page whose query has not
    // returned is drawn with its day numbers and lunar line and gains its markers when it lands.
    val markersByDay = remember(events, gridStart, zone) {
        if (events == null) {
            emptyMap()
        } else {
            groupMarkersByDay(events, gridStart, MONTH_GRID_DAYS, zone)
        }
    }

    // The lunar line of each day, remembered apart from the markers: when a month's events land, the
    // cells are rebuilt to carry the new colours, and the 42 lunar conversions must not run again.
    val lunarByDay = remember(cells, showLunar) {
        if (!showLunar) emptyMap() else cells.associateWith { lunarShort(context, it) }
    }

    // Everything a cell draws, resolved once per load instead of once per cell per recomposition:
    // whether the day is in this month, its lunar line, and the already-coloured markers. This was
    // forty-two YearMonth conversions and forty-two marker-list allocations on every recomposition,
    // and a lambda per cell, which is what kept the grid from being skippable while the pager moved.
    val monthCells = remember(cells, month, markersByDay, lunarByDay) {
        cells.map { day ->
            MonthCell(
                day = day,
                dayNumber = day.dayOfMonth.toString(),
                inMonth = YearMonth.from(day) == month,
                lunar = lunarByDay[day],
                markers = markersByDay[day].orEmpty().take(MAX_MARKERS).map { event ->
                    // Declined events are drawn faint, as they are in the day grid.
                    Color(event.calendarColor).copy(
                        alpha = if (event.isDeclined) 0.25f else 0.9f,
                    )
                },
            )
        }
    }

    // The week each row falls in, read from the row's first day.
    //
    // Every day of a row is in the same week, and the first cell of the row is always the week's
    // first day, because leadingDays pads the grid back to it. Computed for the six rows at once
    // rather than inside the loop: the number is a function of the row, not of the cell.
    val weekNumbers = if (showWeekNumber) {
        remember(cells, firstDayOfWeek) {
            (0 until MONTH_GRID_ROWS).map { row ->
                Utils.getWeekNumberFromTime(
                    cells[row * 7].atStartOfDay(zone).toInstant().toEpochMilli(),
                    context,
                )
            }
        }
    } else {
        emptyList()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        WeekdayHeader(firstDayOfWeek, showWeekNumber)
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Column(modifier = Modifier.weight(1f).fillMaxWidth()) {
            for (row in 0 until MONTH_GRID_ROWS) {
                Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    if (showWeekNumber) {
                        WeekNumberCell(number = weekNumbers.getOrNull(row))
                    }
                    for (column in 0 until 7) {
                        DayCell(
                            cell = monthCells[row * 7 + column],
                            today = today,
                            compact = compact,
                            onOpenDay = onOpenDay,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                        )
                    }
                }
            }
        }
    }
}

/**
 * The first cell of a month's six week grid: the first day of the week that contains the 1st.
 *
 * Shared by the page and the loader, so the window the events are queried for is exactly the window
 * the cells cover.
 */
private fun monthGridStart(month: YearMonth, firstDayOfWeek: DayOfWeek): LocalDate {
    val firstOfMonth = month.atDay(1)
    val leadingDays = ((firstOfMonth.dayOfWeek.value - firstDayOfWeek.value) + 7) % 7
    return firstOfMonth.minusDays(leadingDays.toLong())
}

/** One day cell's data, resolved once per month load. */
private class MonthCell(
    val day: LocalDate,
    val dayNumber: String,
    val inMonth: Boolean,
    val lunar: String?,
    val markers: List<Color>,
)

/** The lunar day a cell shows, in the short form the grid uses, or null when there is none. */
private fun lunarShort(context: Context, day: LocalDate): String? =
    LunarUtils.get(
        context,
        day.year,
        day.monthValue - 1,
        day.dayOfMonth,
        LunarUtils.FORMAT_LUNAR_SHORT or LunarUtils.FORMAT_ONE_FESTIVAL,
        false,
        null,
    )

/**
 * The week number down the left of a row.
 *
 * Dimmed and small like the lunar line: it is a guide to the rows, not one of the days, and it is the
 * one cell of the grid that cannot be tapped to open a day.
 */
@Composable
private fun WeekNumberCell(number: Int?) {
    Box(
        modifier = Modifier
            .width(WEEK_NUMBER_WIDTH)
            .fillMaxHeight(),
        contentAlignment = Alignment.TopCenter,
    ) {
        if (number != null) {
            Text(
                text = number.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

/**
 * Groups the window's events by the days they cover.
 *
 * All-day events are stored at UTC midnight with an exclusive end - the day after the last one - so
 * their days are read in UTC and the last included day is the end minus a day. Timed events are read
 * in the device's zone, and an event ending exactly at midnight does not claim the day it ends on:
 * 23:00-24:00 belongs to one day, not two.
 *
 * Not private: the month widget draws the same window and has to read it the same way, all-day
 * events included - a second copy of this arithmetic is how the two would end up disagreeing about
 * which day a multi-day event is on.
 */
internal fun groupMarkersByDay(
    events: List<CalendarDayEvent>,
    gridStart: LocalDate,
    gridDays: Int,
    zone: ZoneId,
): Map<LocalDate, List<CalendarDayEvent>> {
    val gridEnd = gridStart.plusDays(gridDays.toLong()).minusDays(1)
    val result = HashMap<LocalDate, MutableList<CalendarDayEvent>>()
    for (event in events) {
        val first = if (event.allDay) {
            LocalDate.ofInstant(Instant.ofEpochMilli(event.begin), ZoneOffset.UTC)
        } else {
            LocalDate.ofInstant(Instant.ofEpochMilli(event.begin), zone)
        }
        var last = if (event.allDay) {
            LocalDate.ofInstant(
                Instant.ofEpochMilli((event.end - 1).coerceAtLeast(event.begin)),
                ZoneOffset.UTC,
            )
        } else {
            val endInstant = Instant.ofEpochMilli(event.end)
            val endDate = LocalDate.ofInstant(endInstant, zone)
            val endsAtMidnight = event.end > event.begin &&
                endInstant.atZone(zone).toLocalTime() == LocalTime.MIDNIGHT
            if (endsAtMidnight) endDate.minusDays(1) else endDate
        }
        if (last.isBefore(first)) {
            last = first
        }
        // A long event must not walk past the grid it is being drawn in.
        if (last.isAfter(gridEnd)) {
            last = gridEnd
        }
        var day = if (first.isBefore(gridStart)) gridStart else first
        while (!day.isAfter(last)) {
            result.getOrPut(day) { ArrayList() }.add(event)
            day = day.plusDays(1)
        }
    }
    return result
}

/** The seven weekday names, starting on the week's first day. */
@Composable
private fun WeekdayHeader(firstDayOfWeek: DayOfWeek, showWeekNumber: Boolean) {
    // The same builder the month widget uses, so the two headers cannot disagree about the order or
    // the source of the names. java.time's own text is not used: with core library desugaring its
    // narrow form is the first character of the full name.
    val labels = remember(firstDayOfWeek) {
        shortWeekdayLabels(firstDayOfWeek, Locale.getDefault())
    }
    Row(modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 4.dp)) {
        // The header of the week-number column: empty, but the same width as the cells below it, or
        // the weekday names would sit half a column off the days they name.
        if (showWeekNumber) {
            Spacer(Modifier.width(WEEK_NUMBER_WIDTH))
        }
        labels.forEach { label ->
            Text(
                text = label,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** One day of the grid: the day number, the lunar day, and up to three event markers. */
@Composable
private fun DayCell(
    cell: MonthCell,
    today: LocalDate,
    /**
     * True in a landscape window, where the six rows are about half as tall as they are upright.
     *
     * The cell then gives up the fixed 28dp circle and the gap under it - which is what pushed the
     * lunar line out of the cell - and draws its events as dots on one line under the lunar date
     * instead of as bars down the rest of the cell. Both changes exist to fit the same three pieces
     * into half the height.
     */
    compact: Boolean = false,
    onOpenDay: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val day = cell.day
    val isToday = day == today
    // One interaction source per cell: a source shared by all forty-two would report one cell's
    // press to every cell's highlight. The source is a small object; it is a ripple host, not this,
    // that used to be allocated per cell.
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val highlight = MaterialTheme.colorScheme.onSurface.copy(alpha = PRESSED_STATE_LAYER_ALPHA)

    Column(
        modifier = modifier
            // The tap still uses no indication - a ripple is rounded only through Modifier.clip,
            // which is a graphics layer for each of the forty-two cells - and the pressed state is
            // drawn below as a rounded tonal highlight in the cell's own pass. Same Material state
            // layer, no layer of any kind, and nothing per-frame unless a cell is being pressed.
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = { onOpenDay(day) },
            )
            .drawWithCache {
                val corner = CornerRadius(DAY_CELL_CORNER_RADIUS.toPx())
                val color = if (pressed) highlight else Color.Transparent
                onDrawBehind {
                    if (color.alpha > 0f) {
                        drawRoundRect(color = color, cornerRadius = corner)
                    }
                }
            }
            .padding(horizontal = 2.dp, vertical = if (compact) 0.dp else 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                // Two sizes: upright the circle is the 28dp the grid has always used; landscape has
                // to spend its height on the lunar line and the events, so it gets 20.
                .size(if (compact) 20.dp else 28.dp)
                // A shape on the background, not clip + background: clip makes a graphics layer, and
                // this is one of forty-two cells.
                .background(
                    color = if (isToday) MaterialTheme.colorScheme.primary else Color.Transparent,
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = cell.dayNumber,
                style = if (compact) {
                    MaterialTheme.typography.labelMedium
                } else {
                    MaterialTheme.typography.labelLarge
                },
                color = when {
                    isToday -> MaterialTheme.colorScheme.onPrimary
                    cell.inMonth -> MaterialTheme.colorScheme.onSurface
                    // The grid's tails: the days of the months on either side are there to keep the
                    // rows full, and reading them at full strength made it look as though the month
                    // had more days than it does.
                    else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                },
                maxLines = 1,
            )
        }
        val lunar = cell.lunar
        if (!lunar.isNullOrEmpty() && lunar.length <= 4) {
            // The lunar line is the first thing dropped when a cell is too small for it: the columns
            // of a month grid on a phone are about as narrow as the day number itself, and a clipped
            // "初二" tells less than no line at all. Longer values are the festivals, which the day
            // and three day views still show.
            Text(
                text = lunar,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (compact) {
            // The events, on one line under the lunar date: a dot in the calendar's colour per
            // event, which fits in four dp of height where a bar would need one row per event.
            EventDots(
                markers = cell.markers,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )
        } else {
            Spacer(Modifier.height(2.dp))
            // Weighted, so the markers take whatever the day number and the lunar line left rather
            // than pushing the cell past its row.
            EventBars(
                markers = cell.markers,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )
        }
    }
}

/**
 * The event bars, drawn into the cell's own canvas instead of as one Box per bar.
 *
 * Three Boxes with a clip and a background each were three more layers per cell; this draws the same
 * rounded bars in a single cached behind-the-content pass, and stops drawing once the cell's height
 * runs out, which is what the old clipToBounds was for.
 */
@Composable
private fun EventBars(markers: List<Color>, modifier: Modifier) {
    Box(
        modifier = modifier.drawWithCache {
            val barHeight = 4.dp.toPx()
            val gap = 2.dp.toPx()
            val corner = CornerRadius(2.dp.toPx())
            onDrawBehind {
                var y = 0f
                for (color in markers) {
                    if (y + barHeight > size.height) break
                    drawRoundRect(
                        color = color,
                        topLeft = Offset(0f, y),
                        size = Size(size.width, barHeight),
                        cornerRadius = corner,
                    )
                    y += barHeight + gap
                }
            }
        },
    )
}

/** The compact form of the markers: one dot per event, centred on a single line. */
@Composable
private fun EventDots(markers: List<Color>, modifier: Modifier) {
    Box(
        modifier = modifier.drawWithCache {
            val dot = 4.dp.toPx()
            val gap = 3.dp.toPx()
            val top = 1.dp.toPx()
            onDrawBehind {
                if (markers.isEmpty()) return@onDrawBehind
                val width = markers.size * dot + (markers.size - 1) * gap
                var x = (size.width - width) / 2f
                for (color in markers) {
                    drawCircle(
                        color = color,
                        radius = dot / 2f,
                        center = Offset(x + dot / 2f, top + dot / 2f),
                    )
                    x += dot + gap
                }
            }
        },
    )
}
