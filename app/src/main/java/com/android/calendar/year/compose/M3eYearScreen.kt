/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * Material 3 Expressive year view.
 *
 * Layout: one section per Gregorian year, scrollable from year to year. Each section has the
 * year on the left of its header row, the lunar underline legend on the right, a thin divider,
 * and then the twelve months as a 3 x 4 grid (which is also what the drawer icon depicts).
 *
 * Unchanged from the rest of the app: a thick underline marks 正月初一 (the lunar new year day)
 * and a thin underline marks the first day of every other lunar month.
 *
 * Performance: the per-year grid is built once and cached, the weeks are pre-chunked so
 * composition allocates nothing, and the underline box is only emitted for the handful of days
 * that actually carry a lunar month start.
 */
package com.android.calendar.year.compose

import android.content.res.Configuration
import androidx.compose.runtime.key
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.calendar.chrome.compose.M3eAddFabMenu
import com.android.calendar.composeui.openedOver
import com.android.calendar.chrome.compose.rememberAddMenuEntries
import com.android.calendar.LunarCalendar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ws.xsoh.etar.R
import java.text.DateFormatSymbols
import java.time.LocalDate
import java.time.ZoneId
import java.time.YearMonth
import java.util.concurrent.ConcurrentHashMap

/** Years the lunar table can resolve. */
private const val FIRST_YEAR = 1900
private const val LAST_YEAR = 2100
/**
 * Months across, for the width the screen actually has.
 *
 * Three by four on a phone held upright, which is what this was fixed at. A phone on its side has the
 * width for four, and a tablet - which this is told about separately, because a phone on its side is
 * also wide - has the width for six, which brings the year down to two rows and removes most of the
 * scrolling.
 *
 * The tablet test is on the shortest side rather than the current one: a phone turned sideways
 * reports a wide screen, and picking the layout from that alone would give a phone the tablet grid.
 */
private fun monthColumns(configuration: Configuration): Int = when {
    configuration.smallestScreenWidthDp >= 600 -> 6
    configuration.orientation == Configuration.ORIENTATION_LANDSCAPE -> 4
    else -> 3
}

private enum class Underline { NONE, THIN, THICK }

private data class DayCell(val day: Int, val underline: Underline)

private data class MonthGrid(val month: Int, val weeks: List<List<DayCell?>>)

/**
 * Built years are cached: scrolling away and back must not redo ~366 lunar conversions.
 * Keyed by year and week start because both change the result.
 */
private val yearCache = ConcurrentHashMap<Int, List<MonthGrid>>()

private val monthNames = DateFormatSymbols().months

@Composable
fun M3eYearScreen(
    initialYear: Int,
    firstDayOfWeek: Int,
    showLunar: Boolean,
    horizontal: Boolean,
    onVisibleYearChanged: (Int) -> Unit,
    onOpenMonth: (year: Int, month: Int) -> Unit,
    onCreateEvent: () -> Unit = {},
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    // The year view adds on today, the same day its event button has always used.
    val addDate = remember { LocalDate.now(ZoneId.systemDefault()) }
    var showAddChooser by remember { mutableStateOf(false) }

    // The add button floats over both modes, so it sits outside whichever one is composed.
    androidx.compose.foundation.layout.Box(modifier = Modifier.fillMaxSize()) {
        // Keyed on the mode and the year it is asked to show, not on the resume count.
        //
        // The mode is part of the key so switching between sideways and up-and-down builds the
        // other one from scratch: without it the two share a composition slot and the pager's
        // remembered state survives the switch, leaving the new mode looking half applied.
        //
        // The year is part of the key so a year picked from the heading rebuilds the list at that
        // year. The resume count used to be a third key, and that was the scroll bug: every resume
        // - coming back from a month, from an event, from anywhere - disposed the list and built it
        // again at its initial year, so the year view jumped back instead of staying where the user
        // had scrolled to.
        key(horizontal, initialYear) {
            if (horizontal) {
                HorizontalYearPager(
                    initialYear = initialYear,
                    firstDayOfWeek = firstDayOfWeek,
                    showLunar = showLunar,
                    onVisibleYearChanged = onVisibleYearChanged,
                    onOpenMonth = onOpenMonth,
                )
            } else {
                VerticalYearList(
                    initialYear = initialYear,
                    firstDayOfWeek = firstDayOfWeek,
                    showLunar = showLunar,
                    onVisibleYearChanged = onVisibleYearChanged,
                    onOpenMonth = onOpenMonth,
                )
            }
        }

        // The same add button the other views carry, with its actions floating above it. A year is a
        // long way from a day, so there is no "first day of the view" to put anything on: all three
        // actions land on today.
        val addEntries = rememberAddMenuEntries(
            onAddEvent = {
                showAddChooser = false
                onCreateEvent()
            },
            onAddTodo = {
                showAddChooser = false
                // The widget's flow: the to-do editor opens on that day, not the list. The opener
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

/** Settings choice "sideways": one year per page, paged left and right. */
@Composable
private fun HorizontalYearPager(
    initialYear: Int,
    firstDayOfWeek: Int,
    showLunar: Boolean,
    onVisibleYearChanged: (Int) -> Unit,
    onOpenMonth: (year: Int, month: Int) -> Unit,
) {
    val today = remember { LocalDate.now() }
    val pageCount = LAST_YEAR - FIRST_YEAR + 1
    val pagerState = rememberPagerState(
        initialPage = (initialYear - FIRST_YEAR).coerceIn(0, pageCount - 1),
        pageCount = { pageCount },
    )

    // The host's toolbar shows the year the user is actually looking at. derivedStateOf keeps the
    // work scoped to the title instead of the whole screen.
    val visibleYear by remember {
        derivedStateOf { FIRST_YEAR + pagerState.currentPage }
    }
    LaunchedEffect(visibleYear) { onVisibleYearChanged(visibleYear) }

    HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
        // A page can be taller than the screen (landscape, short displays), so each page scrolls
        // vertically on its own; the pager keeps the horizontal axis.
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            YearSection(
                year = FIRST_YEAR + page,
                firstDayOfWeek = firstDayOfWeek,
                showLunar = showLunar,
                today = today,
                onOpenMonth = onOpenMonth,
            )
        }
    }
}

/** Settings choice "up and down": the years in one continuous list. */
@Composable
private fun VerticalYearList(
    initialYear: Int,
    firstDayOfWeek: Int,
    showLunar: Boolean,
    onVisibleYearChanged: (Int) -> Unit,
    onOpenMonth: (year: Int, month: Int) -> Unit,
) {
    val today = remember { LocalDate.now() }
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = (initialYear - FIRST_YEAR).coerceIn(0, LAST_YEAR - FIRST_YEAR)
    )

    val visibleYear by remember {
        derivedStateOf { FIRST_YEAR + listState.firstVisibleItemIndex }
    }
    LaunchedEffect(visibleYear) { onVisibleYearChanged(visibleYear) }

    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
        items(LAST_YEAR - FIRST_YEAR + 1, key = { it }) { index ->
            YearSection(
                year = FIRST_YEAR + index,
                firstDayOfWeek = firstDayOfWeek,
                showLunar = showLunar,
                today = today,
                onOpenMonth = onOpenMonth,
            )
        }
    }
}

/**
 * Lays out one year: header row, divider and the twelve months.
 *
 * Nothing here scrolls. Whichever mode is active owns the scrolling: the vertical list scrolls all
 * the years, and the horizontal pager gives each page its own vertical scroll so a tall grid is
 * still reachable in landscape.
 */
@Composable
private fun YearSection(
    year: Int,
    firstDayOfWeek: Int,
    showLunar: Boolean,
    today: LocalDate,
    onOpenMonth: (Int, Int) -> Unit,
) {
    // Built synchronously: with monthStartMarks() a year costs a walk of ~13 lunar months rather
    // than 366 per-day conversions, so there is nothing left worth pushing to another thread.
    val months = remember(year, firstDayOfWeek, showLunar) {
        cachedYear(year, firstDayOfWeek, showLunar)
    }
    // Read here rather than passed in, so a rotation rebuilds the grid with the new count: the
    // composition is already keyed on the screen, and LocalConfiguration changes with it.
    val columns = monthColumns(LocalConfiguration.current)
    val monthRows = remember(months, columns) { months.chunked(columns) }
    val isCurrentYear = year == today.year

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, top = 14.dp, bottom = 12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = year.toString(),
                // The year being shown stands out; the others stay quiet so the eye lands on it.
                style = if (isCurrentYear) {
                    MaterialTheme.typography.headlineSmall
                } else {
                    MaterialTheme.typography.titleMedium
                },
                fontWeight = if (isCurrentYear) FontWeight.Bold else FontWeight.Normal,
                color = if (isCurrentYear) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.weight(1f),
            )
            if (showLunar) {
                Legend(year = year)
            }
        }

        // Thin rule separating the year from its months.
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant,
        )

        // Months across by four down, in the count the width calls for.
        monthRows.forEach { row ->
            Row(modifier = Modifier.fillMaxWidth()) {
                row.forEach { grid ->
                    Box(modifier = Modifier.weight(1f).padding(3.dp)) {
                        MonthCard(
                            grid = grid,
                            title = monthNames[grid.month - 1],
                            year = year,
                            today = today,
                            onClick = { onOpenMonth(year, grid.month) },
                        )
                    }
                }
                repeat(columns - row.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

/** Thick rule + "乙巳蛇年", thin rule + "农历初一". */
@Composable
private fun Legend(year: Int) {
    val label = remember(year) {
        LunarCalendar.ganZhi(year) + LunarCalendar.animal(year)
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        RuleSample(thick = true)
        Spacer(Modifier.width(4.dp))
        Text(
            text = stringResource(R.string.m3e_year_legend_new_year, label),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(10.dp))
        RuleSample(thick = false)
        Spacer(Modifier.width(4.dp))
        Text(
            text = stringResource(R.string.m3e_year_legend_lunar_first),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun RuleSample(thick: Boolean) {
    Box(
        modifier = Modifier
            .width(16.dp)
            .height(if (thick) 3.dp else 1.dp)
            .background(
                color = if (thick) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outline
                }
            )
    )
}

/**
 * One mini-month, drawn as a single Canvas.
 *
 * A composable per day cell cost roughly a hundred nodes per month - well over a thousand per
 * year - and composing them as they scrolled into view was what made the list stutter. Drawing
 * the month instead leaves one node per month, and because every day number shares one text
 * style the measurer only ever measures "1".."31" once and reuses those layouts.
 */
@Composable
private fun MonthCard(
    grid: MonthGrid,
    title: String,
    year: Int,
    today: LocalDate,
    onClick: () -> Unit,
) {
    val measurer = rememberTextMeasurer(cacheSize = 64)
    val titleStyle = MaterialTheme.typography.labelMedium.copy(
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
    )
    val dayStyle = TextStyle(
        fontSize = 9.sp,
        color = MaterialTheme.colorScheme.onSurface,
    )
    val todayStyle = dayStyle.copy(
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
    )
    val thickColor = MaterialTheme.colorScheme.primary
    val thinColor = MaterialTheme.colorScheme.outline
    val cardColor = MaterialTheme.colorScheme.surfaceContainerHigh

    val sidePad = 4.dp
    val topPad = 6.dp
    val titleHeight = 16.dp
    val titleGap = 4.dp
    val rowHeight = 13.dp
    val bottomPad = 6.dp

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(topPad + titleHeight + titleGap + rowHeight * grid.weeks.size + bottomPad)
            .clip(RoundedCornerShape(12.dp))
            .background(cardColor)
            .clickable(onClick = onClick),
    ) {
        val side = sidePad.toPx()
        drawText(measurer.measure(title, titleStyle), topLeft = Offset(side, topPad.toPx()))

        val columnWidth = (size.width - 2 * side) / 7f
        var rowTop = topPad.toPx() + titleHeight.toPx() + titleGap.toPx()

        grid.weeks.forEach { week ->
            week.forEachIndexed { column, cell ->
                if (cell != null) {
                    val isToday = today.year == year && today.monthValue == grid.month &&
                            today.dayOfMonth == cell.day
                    val layout = measurer.measure(
                        cell.day.toString(),
                        if (isToday) todayStyle else dayStyle,
                    )
                    val left = side + column * columnWidth
                    drawText(
                        textLayoutResult = layout,
                        topLeft = Offset(left + (columnWidth - layout.size.width) / 2f, rowTop),
                    )
                    if (cell.underline != Underline.NONE) {
                        val barWidth = columnWidth * 0.8f
                        drawRect(
                            color = if (cell.underline == Underline.THICK) thickColor else thinColor,
                            topLeft = Offset(
                                left + (columnWidth - barWidth) / 2f,
                                rowTop + layout.size.height,
                            ),
                            size = Size(
                                barWidth,
                                if (cell.underline == Underline.THICK) 2.dp.toPx() else 1.dp.toPx(),
                            ),
                        )
                    }
                }
            }
            rowTop += rowHeight.toPx()
        }
    }
}

// ---------------------------------------------------------------------------------- data build

private fun cachedYear(year: Int, firstDayOfWeek: Int, showLunar: Boolean): List<MonthGrid> {
    val key = (year * 10 + firstDayOfWeek) * 2 + if (showLunar) 1 else 0
    yearCache[key]?.let { return it }
    val built = buildYear(year, firstDayOfWeek, showLunar)
    yearCache[key] = built
    return built
}

private fun buildYear(year: Int, firstDayOfWeek: Int, showLunar: Boolean): List<MonthGrid> {
    // The lunar month starts for the whole Gregorian year come from one cheap walk of the ~13
    // lunar months that overlap it, instead of converting all 365 days.
    val marks = if (showLunar) {
        ByteArray(366).also { LunarCalendar.monthStartMarks(year, it) }
    } else {
        null
    }

    var dayOfYearBase = 0
    return (1..12).map { month ->
        val length = YearMonth.of(year, month).lengthOfMonth()
        val first = LocalDate.of(year, month, 1)
        val leading = (first.dayOfWeek.value % 7 - firstDayOfWeek + 7) % 7

        val slots = ArrayList<DayCell?>(leading + length)
        repeat(leading) { slots.add(null) }
        for (day in 1..length) {
            val mark = marks?.get(dayOfYearBase + day - 1) ?: LunarCalendar.MARK_NONE
            slots.add(
                DayCell(
                    day = day,
                    underline = when (mark) {
                        LunarCalendar.MARK_NEW_YEAR -> Underline.THICK
                        LunarCalendar.MARK_MONTH_START -> Underline.THIN
                        else -> Underline.NONE
                    },
                )
            )
        }
        dayOfYearBase += length

        // Pre-chunk into weeks so composition never allocates.
        val weeks = ArrayList<List<DayCell?>>(6)
        var i = 0
        while (i < slots.size) {
            val end = minOf(i + 7, slots.size)
            val week = ArrayList<DayCell?>(7)
            for (j in i until end) week.add(slots[j])
            while (week.size < 7) week.add(null)
            weeks.add(week)
            i = end
        }
        MonthGrid(month = month, weeks = weeks)
    }
}
