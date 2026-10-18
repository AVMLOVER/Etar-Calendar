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
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import android.content.res.Configuration
import com.android.calendar.LunarUtils
import com.android.calendar.Utils
import com.android.calendar.chrome.compose.M3eChromeScaffold
import com.android.calendar.chrome.compose.M3eScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ws.xsoh.etar.R
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.TextStyle
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
 * The shape a day cell's touch feedback takes.
 *
 * A cell is one of forty-two rectangles laid edge to edge, and an unclipped ripple lit the whole
 * rectangle - a hard square sliding under the finger. Clipping the cell's indication to a gently
 * rounded tile is what makes tapping a date read the way the rest of this app's surfaces do.
 */
private val DAY_CELL_SHAPE = RoundedCornerShape(12.dp)

/** Length of an event created by the add button, matching the other views' default. */
private const val DEFAULT_NEW_EVENT_MILLIS = 60L * 60L * 1000L

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
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
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

    val month = remember(pagerState.currentPage, centreIndex) { monthOfPage(pagerState.currentPage) }

    LaunchedEffect(month) { onVisibleMonthChanged(month) }

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
        title = { Text(monthTitle(month)) },
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                beyondViewportPageCount = MONTH_PAGE_BEYOND_VIEWPORT,
            ) { index ->
                MonthPage(
                    month = monthOfPage(index),
                    today = today,
                    zone = zone,
                    showLunar = showLunar,
                    showWeekNumber = showWeekNumber,
                    firstDayOfWeek = firstDayOfWeek,
                    onOpenDay = onOpenDay,
                    refreshKey = refreshKey,
                )
            }

            // The add button, the same one the other views carry: today at the shared next-slot time
            // while today is in the month on screen, and nine in the morning on the first of the
            // month otherwise - the slot the month view gives a day tapped in another month.
            androidx.compose.material3.FloatingActionButton(
                onClick = {
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
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ) {
                androidx.compose.material3.Icon(
                    painter = painterResource(R.drawable.ic_add_event),
                    contentDescription = stringResource(R.string.event_create),
                )
            }
        }
    }
}

/** One month: the weekday header and the six rows of cells, with that month's events loaded. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MonthPage(
    month: YearMonth,
    today: LocalDate,
    zone: ZoneId,
    showLunar: Boolean,
    showWeekNumber: Boolean,
    firstDayOfWeek: DayOfWeek,
    onOpenDay: (LocalDate) -> Unit,
    refreshKey: Int,
) {
    val context = LocalContext.current

    // A landscape window divides the same height into the same six rows, so a row is about half as
    // tall there. The cells lay themselves out differently for it - see DayCell.
    val compact = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    // The grid always starts on the first day of the week that contains the 1st, and always runs for
    // six weeks: the cells before and after the month belong to the months on either side, and are
    // drawn dimmed.
    val firstOfMonth = remember(month) { month.atDay(1) }
    val leadingDays = remember(firstOfMonth, firstDayOfWeek) {
        ((firstOfMonth.dayOfWeek.value - firstDayOfWeek.value) + 7) % 7
    }
    val gridStart = remember(firstOfMonth, leadingDays) {
        firstOfMonth.minusDays(leadingDays.toLong())
    }
    val cells = remember(gridStart) {
        (0L until MONTH_GRID_DAYS.toLong()).map { gridStart.plusDays(it) }
    }

    var events by remember(month) { mutableStateOf<List<CalendarDayEvent>?>(null) }
    LaunchedEffect(month, refreshKey) {
        val loaded = withContext(Dispatchers.IO) {
            DaysRepository(context.applicationContext).load(
                gridStart.atStartOfDay(zone).toInstant().toEpochMilli(),
                gridStart.plusDays(MONTH_GRID_DAYS.toLong())
                    .atStartOfDay(zone).toInstant().toEpochMilli(),
                // A six week window on a busy calendar: the default is enough for a day or a week,
                // but a month of a shared calendar can hold more than that, and the markers that fall
                // outside the limit would simply be missing.
                limit = 2000,
            )
        }
        events = loaded
    }

    val loaded = events
    if (loaded == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            LoadingIndicator()
        }
        return
    }

    val markersByDay = remember(loaded, gridStart, zone) {
        groupMarkersByDay(loaded, gridStart, MONTH_GRID_DAYS, zone)
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
                        val day = cells[row * 7 + column]
                        DayCell(
                            day = day,
                            today = today,
                            inMonth = YearMonth.from(day) == month,
                            showLunar = showLunar,
                            markers = markersByDay[day].orEmpty(),
                            onClick = { onOpenDay(day) },
                            compact = compact,
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
    val labels = remember(firstDayOfWeek) {
        (0L until 7L).map { offset ->
            firstDayOfWeek.plus(offset).getDisplayName(TextStyle.SHORT, Locale.getDefault())
        }
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
    day: LocalDate,
    today: LocalDate,
    inMonth: Boolean,
    showLunar: Boolean,
    markers: List<CalendarDayEvent>,
    onClick: () -> Unit,
    /**
     * True in a landscape window, where the six rows are about half as tall as they are upright.
     *
     * The cell then gives up the fixed 28dp circle and the gap under it - which is what pushed the
     * lunar line out of the cell - and draws its events as dots on one line under the lunar date
     * instead of as bars down the rest of the cell. Both changes exist to fit the same three pieces
     * into half the height.
     */
    compact: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val isToday = day == today
    val lunar = if (showLunar) {
        remember(day) {
            LunarUtils.get(
                context,
                day.year,
                day.monthValue - 1,
                day.dayOfMonth,
                LunarUtils.FORMAT_LUNAR_SHORT or LunarUtils.FORMAT_ONE_FESTIVAL,
                false,
                null,
            )
        }
    } else {
        null
    }

    Column(
        modifier = modifier
            // The corner is applied before the clickable so the ripple itself is rounded: clipping
            // afterwards would only cut the content, which already fits.
            .clip(DAY_CELL_SHAPE)
            .clickable(onClick = onClick)
            .padding(horizontal = 2.dp, vertical = if (compact) 0.dp else 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                // Two sizes: upright the circle is the 28dp the grid has always used; landscape has
                // to spend its height on the lunar line and the events, so it gets 20.
                .size(if (compact) 20.dp else 28.dp)
                .clip(CircleShape)
                .background(if (isToday) MaterialTheme.colorScheme.primary else Color.Transparent),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = day.dayOfMonth.toString(),
                style = if (compact) {
                    MaterialTheme.typography.labelMedium
                } else {
                    MaterialTheme.typography.labelLarge
                },
                color = when {
                    isToday -> MaterialTheme.colorScheme.onPrimary
                    inMonth -> MaterialTheme.colorScheme.onSurface
                    // The grid's tails: the days of the months on either side are there to keep the
                    // rows full, and reading them at full strength made it look as though the month
                    // had more days than it does.
                    else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                },
                maxLines = 1,
            )
        }
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
            //
            // The line is weighted, so a row too short for all three pieces loses the dots rather
            // than the lunar date - the date is what the cell was asked to show, and the dots are
            // the summary under it.
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clipToBounds(),
                contentAlignment = Alignment.TopCenter,
            ) {
                if (markers.isNotEmpty()) {
                    Row(
                        modifier = Modifier.padding(top = 1.dp),
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        markers.take(MAX_MARKERS).forEach { event ->
                            Box(
                                modifier = Modifier
                                    .size(4.dp)
                                    .clip(CircleShape)
                                    .background(
                                        // Declined events are drawn faint, as they are everywhere else.
                                        Color(event.calendarColor).copy(
                                            alpha = if (event.isDeclined) 0.25f else 0.9f
                                        )
                                    ),
                            )
                        }
                    }
                }
            }
        } else {
            Spacer(Modifier.height(2.dp))
            // Weighted, so the markers take whatever the day number and the lunar line left rather than
            // pushing the cell past its row. In a landscape window six rows are short enough that three
            // bars do not fit under the number, and a cell that overflowed drew its bars across the row
            // below; clipped, the third bar is simply not shown.
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clipToBounds(),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                markers.take(MAX_MARKERS).forEach { event ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(
                                // Declined events are drawn faint, as they are in the day grid.
                                Color(event.calendarColor).copy(
                                    alpha = if (event.isDeclined) 0.25f else 0.9f
                                )
                            ),
                    )
                }
            }
        }
    }
}
