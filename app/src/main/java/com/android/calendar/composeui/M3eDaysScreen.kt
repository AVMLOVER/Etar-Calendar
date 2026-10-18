/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The shared Material 3 Expressive day grid: the day, three day and week views.
 *
 * Layout follows Google Calendar: a fixed header naming the days, then one shared vertical time
 * grid holding a column per day, with a line marking the current time on today's column.
 *
 * The three views are the same screen with a different number of days in a page, so they are the
 * same code with a different [M3eDaysScreen.days]. Writing the week view a second time would have
 * meant two grids that drift apart - the three day one already carried fixes (the column rules as
 * an overlay rather than a row child, the selection kept across a trip to the editor) that would
 * have had to be found and made again.
 *
 * Paging is horizontal and moves a whole page at a time, so a flick always lands on the next or
 * previous block of days rather than somewhere inside one.
 */
package com.android.calendar.composeui

import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.android.calendar.LunarUtils
import com.android.calendar.Utils
import com.android.calendar.chrome.DayHeading
import com.android.calendar.chrome.compose.M3eChromeScaffold
import com.android.calendar.chrome.compose.M3eScreen
import com.android.calendar.threeday.compose.rangeTitleOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ws.xsoh.etar.R
import kotlin.math.ceil
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/** How tall one hour of the grid is. */
private val HOUR_HEIGHT = 60.dp

/**
 * Starting width of the gutter that carries the hour labels.
 *
 * Two sizes: a week has seven columns on a phone, and giving the gutter the same width it has on a
 * three day page leaves each of them too narrow to show a time and an event title. This is only the
 * floor, though - the gutter actually used is whichever of it and the measured "00:00" is wider, see
 * [DaysBlock]. A gutter narrower than the label it has to hold is exactly what wrapped the hour
 * labels of the week view onto two lines.
 */
private val GUTTER_WIDTH = 44.dp
private val NARROW_GUTTER_WIDTH = 32.dp

/**
 * Space between an hour label and the first day column.
 *
 * Part of the measured gutter rather than a fixed inset on top of it, so the width the day columns
 * are measured from is the width the labels are actually laid out in.
 */
private val HOUR_LABEL_END_PADDING = 6.dp

/**
 * How many pages the pager holds, and which one it opens on, plus how many pages either side of the
 * visible one stay composed.
 *
 * The count is deliberately small. A pager tracks scroll offset as a Float over the whole page
 * range, so a long range costs precision: at a few thousand pages the offsets of neighbouring pages
 * stop being distinguishable and the pager settles between two of them instead of on one. 1200 pages
 * is about three years of single days, ten years of three day blocks or twenty three years of weeks
 * altogether - half of that either side of the page a screen opens on, which is more than a user
 * swipes through - and keeps the offset arithmetic exact.
 *
 * [BEYOND_VIEWPORT] keeps the neighbouring pages laid out so a swipe has the next block ready
 * instead of composing it mid-gesture, which is what made paging stutter.
 */
private const val PAGE_COUNT = 1200
private const val PAGE_MIDDLE = PAGE_COUNT / 2

/**
 * How many blocks either side of the visible one stay composed.
 *
 * One rather than two. Each page is a full day grid - seven columns of twenty four hour canvases on
 * the week view - and two either side meant five of them alive at once, which is where the three day
 * screen's start-up cost and its memory went. One still brings the next block in before a swipe
 * reaches it.
 */
private const val BEYOND_VIEWPORT = 1

/** Length of an event created by tapping an empty slot, matching the day view's default. */
private const val DEFAULT_NEW_EVENT_MILLIS = 60L * 60L * 1000L

private val WEEKDAY_FORMAT = DateTimeFormatter.ofPattern("EEE", Locale.getDefault())

/** One physical pixel. */
@Composable
private fun hairlinePx(): Float = with(LocalDensity.current) {
    1.dp.toPx() / density
}

/**
 * The plus drawn on the picked slot, matching the day and week views' own.
 *
 * Those are Canvas views working in raw pixels: a two pixel stroke and a sixteen pixel arm. A dp
 * is the same measure at a typical density and keeps the proportion on a denser screen.
 */
private val PLUS_STROKE = 2.dp
private val PLUS_MAX_LENGTH = 16.dp
private val PLUS_MARGIN = 8.dp
private val PLUS_MIN_LENGTH = 6.dp

/**
 * The day grid.
 *
 * @param days how many days one page shows: 1 for the day view, 3 for the three day view, 7 for the
 *   week.
 * @param page the day to open on, counted in days from 1970-01-01. The host passes the day the
 *   calendar is currently on, so opening the view lands on the block that contains it.
 * @param selectedScreen the chrome entry this screen belongs to; only the drawer's bookkeeping uses
 *   it, the hosting activity is what displays the title.
 * @param firstDayOfWeek which day a page starts on. Only consulted for the week view, whose pages
 *   have to start on the week's first day; the day and three day views are aligned to fixed blocks
 *   counted from the epoch, so a given day always lands on the same page however it was reached.
 * @param onOpenDay called when a day in the header is tapped, when the screen offers that. The day
 *   and week views open the three day view there; the three day view passes null, because it is
 *   already showing the neighbouring days.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun M3eDaysScreen(
    days: Int,
    page: Long,
    onVisibleRangeChanged: (LocalDate) -> Unit,
    onOpenEvent: (CalendarDayEvent) -> Unit,
    onCreateEvent: (start: Long, end: Long) -> Unit,
    selectedScreen: M3eScreen = M3eScreen.DAY,
    firstDayOfWeek: DayOfWeek = java.time.temporal.WeekFields.of(Locale.getDefault()).firstDayOfWeek,
    onOpenDay: ((LocalDate) -> Unit)? = null,
    /**
     * Bumped by the host whenever the screen comes back to the foreground.
     *
     * Creating or deleting an event happens on another screen, and the provider's change
     * notification arrives asynchronously - and not at all to a screen that was paused at the time.
     * Without this the grid kept showing its old contents until the app was restarted, which is what
     * made a newly created event invisible and a deleted one still present.
     */
    refreshKey: Int = 0,
    /**
     * Bumped when the host wants today brought on screen.
     *
     * The pager keeps its own scroll position, so the page it was opened on cannot also be a
     * command to move; this is that command.
     */
    scrollToTodayKey: Int = 0,
    /** Epoch day chosen from the heading's date picker, or 0 when none has been chosen. */
    pickedDayKey: Long = 0,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val zone = remember { ZoneId.systemDefault() }
    val today = remember { LocalDate.now(zone) }

    // Same switch the month view reads, so the lunar line appears in all of them together.
    //
    // Keyed on refreshKey, which the host bumps on every resume. Without a key this was computed once
    // - on the first composition - and never again, so turning the lunar line on or off in the
    // settings and coming back left this screen showing what it had read the first time.
    val showLunar = remember(refreshKey) { LunarUtils.showLunar(context) }

    // The first day of the block that contains the day the host asked for.
    //
    // Pages are counted from a fixed anchor rather than from the day on screen: the same day then
    // always lands on the same page, which is what lets the pager keep a stable position while the
    // user swipes away and back. The anchor for a week is the start of the week the epoch fell in,
    // so week pages line up with weeks; for the day and three day views it is simply the epoch.
    val anchor = remember(days, firstDayOfWeek) {
        blockStartOf(LocalDate.ofEpochDay(0L), days, firstDayOfWeek)
    }
    val centreIndex = remember(page, anchor, days, firstDayOfWeek) {
        blockIndex(anchor, LocalDate.ofEpochDay(page), days, firstDayOfWeek)
    }
    val pagerState = rememberPagerState(
        initialPage = PAGE_MIDDLE,
        pageCount = { PAGE_COUNT },
    )

    fun firstDayOfPage(index: Int): LocalDate = anchor.plusDays(index.toLong() * days)

    // Absolute day number of the first of the days on the current page.
    val firstDay = remember(pagerState.currentPage, anchor) {
        firstDayOfPage(centreIndex + pagerState.currentPage - PAGE_MIDDLE)
    }

    LaunchedEffect(firstDay) { onVisibleRangeChanged(firstDay) }

    // Today, brought on screen when the host asks.
    LaunchedEffect(scrollToTodayKey) {
        if (scrollToTodayKey == 0) return@LaunchedEffect

        // Found by difference from the page on screen, not from the page this screen opened on.
        //
        // The two are the same until the user swipes, after which the opening page is a stale
        // reference and the distance to today has to be measured from where the pager actually is.
        // In days rather than in pages, so a page whose window straddles today still lands on the
        // page that contains it.
        val currentFirstDay = firstDayOfPage(centreIndex + pagerState.currentPage - PAGE_MIDDLE)
        val daysAway = today.toEpochDay() - currentFirstDay.toEpochDay()
        val targetPage = pagerState.currentPage + Math.floorDiv(daysAway, days.toLong()).toInt()

        if (targetPage in 0 until PAGE_COUNT) {
            pagerState.animateScrollToPage(targetPage)
        }
    }

    // A day chosen from the heading's picker, brought on screen.
    //
    // A date rather than a month, because the heading is a range of days. The page the day falls in
    // is the one to show, so the distance is measured in days and divided by the page length, the
    // same arithmetic the today button uses.
    LaunchedEffect(pickedDayKey) {
        if (pickedDayKey == 0L) return@LaunchedEffect
        val target = LocalDate.ofEpochDay(pickedDayKey)
        val currentFirstDay = firstDayOfPage(centreIndex + pagerState.currentPage - PAGE_MIDDLE)
        val daysAway = target.toEpochDay() - currentFirstDay.toEpochDay()
        val targetPage = pagerState.currentPage + Math.floorDiv(daysAway, days.toLong()).toInt()
        if (targetPage in 0 until PAGE_COUNT) {
            pagerState.animateScrollToPage(targetPage)
        }
    }

    M3eChromeScaffold(selected = selectedScreen, title = { Text(daysTitle(context, firstDay, days)) }) {
        androidx.compose.foundation.layout.Box(modifier = Modifier.fillMaxSize()) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                // Keep the neighbouring blocks composed so a swipe shows the next days straight away
                // instead of composing them mid-gesture.
                beyondViewportPageCount = BEYOND_VIEWPORT,
            ) { index ->
                DaysPage(
                    firstDay = firstDayOfPage(centreIndex + index - PAGE_MIDDLE),
                    days = days,
                    today = today,
                    zone = zone,
                    showLunar = showLunar,
                    onOpenEvent = onOpenEvent,
                    onCreateEvent = onCreateEvent,
                    onOpenDay = onOpenDay,
                    refreshKey = refreshKey,
                )
            }

            // The add button, the same one every calendar view carries.
            //
            // The new event starts today at the shared "next slot" time when today is inside the
            // range - the next half hour, at least 30 and at most 60 minutes away, like every other
            // view's add button. With today outside the range the event goes on the first day shown,
            // at nine in the morning - the same 9:00-10:00 slot the month view gives a day the user
            // tapped in another month. It used to take the current clock time onto that day, which is
            // not a time the user chose.
            androidx.compose.material3.FloatingActionButton(
                onClick = {
                    val now = java.time.ZonedDateTime.now(zone)
                    val start = if (!today.isBefore(firstDay) &&
                        !today.isAfter(firstDay.plusDays((days - 1).toLong()))
                    ) {
                        com.android.calendar.Utils.getNextCreateEventStart(now.toInstant().toEpochMilli())
                    } else {
                        firstDay.atTime(java.time.LocalTime.of(9, 0))
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
                    // The project's own add icon, not Icons.Filled.Add: the material-icons artifact is
                    // not a dependency here, and the screens that need an icon all use painterResource
                    // for that reason.
                    painter = painterResource(R.drawable.ic_add_event),
                    contentDescription = stringResource(R.string.event_create),
                )
            }
        }
    }

}

/**
 * The heading for the days on screen; the day view names one day, the others a range.
 *
 * The hosting activity is what actually displays this - it owns the real title bar - but every screen
 * declares its own heading, and the day view should read as a date while the others read as a range.
 */
private fun daysTitle(context: android.content.Context, first: LocalDate, count: Int): String =
    if (count == 1) {
        DayHeading.title(context, first)
    } else {
        rangeTitleOf(first, first.plusDays((count - 1).toLong()))
    }

/**
 * The first day of the block that contains [date].
 *
 * The day and three day views divide time into fixed blocks counted from the epoch, so a date
 * always belongs to the same block - which is what keeps a page's days the same however the user
 * got there. The week is aligned to the week's first day instead: a week view whose columns start
 * on an arbitrary weekday is not a week.
 *
 * The day view is the degenerate case, one day per page.
 */
internal fun blockStartOf(date: LocalDate, days: Int, firstDayOfWeek: DayOfWeek): LocalDate =
    when {
        days <= 1 -> date
        days == 7 -> date.minusDays(
            ((date.dayOfWeek.value - firstDayOfWeek.value + 7) % 7).toLong()
        )
        else -> LocalDate.ofEpochDay(Math.floorDiv(date.toEpochDay(), days.toLong()) * days)
    }

/**
 * Which page, counted from [anchor], holds [date].
 *
 * Floor division rather than truncation, so the arithmetic keeps working for a date before the
 * anchor - the epoch is the anchor for the day and three day views, so any date in the current
 * decade is after it, but a week anchor can sit just before the epoch for a year or two of very
 * old events.
 */
internal fun blockIndex(
    anchor: LocalDate,
    date: LocalDate,
    days: Int,
    firstDayOfWeek: DayOfWeek,
): Int {
    val start = blockStartOf(date, days, firstDayOfWeek)
    return Math.floorDiv(
        ChronoUnit.DAYS.between(anchor, start),
        days.toLong(),
    ).toInt()
}

/**
 * One page: the data for its days, loaded on its own.
 *
 * The data is loaded per page rather than once for whichever page is current. Loading centrally
 * meant every page shared one loading flag, so each swipe swapped the whole pager for a spinner and
 * back; that is what made paging stutter.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DaysPage(
    firstDay: LocalDate,
    days: Int,
    today: LocalDate,
    zone: ZoneId,
    showLunar: Boolean,
    onOpenEvent: (CalendarDayEvent) -> Unit,
    onCreateEvent: (start: Long, end: Long) -> Unit,
    onOpenDay: ((LocalDate) -> Unit)?,
    refreshKey: Int,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val dates = remember(firstDay, days) { (0L until days.toLong()).map { firstDay.plusDays(it) } }

    var events by remember(firstDay) { mutableStateOf<List<CalendarDayEvent>?>(null) }
    // Re-reads on a refresh without dropping back to the spinner: the page keeps showing what it has
    // while the query runs, so returning from the editor does not flash.
    LaunchedEffect(firstDay, refreshKey) {
        val loaded = withContext(Dispatchers.IO) {
            DaysRepository(context.applicationContext).load(
                firstDay.atStartOfDay(zone).toInstant().toEpochMilli(),
                firstDay.plusDays(days.toLong()).atStartOfDay(zone).toInstant().toEpochMilli(),
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

    val timedByDay = remember(loaded, dates, zone) {
        dates.associateWith { day ->
            loaded.filter { event ->
                !event.allDay && LocalDate.ofInstant(Instant.ofEpochMilli(event.begin), zone) == day
            }
        }
    }
    val allDayByDay = remember(loaded, dates) {
        dates.associateWith { day ->
            loaded.filter { event -> event.allDay && allDayCovers(event, day) }
        }
    }

    DaysBlock(
        dates = dates,
        today = today,
        zone = zone,
        showLunar = showLunar,
        // Keyed on refreshKey like the lunar switch: the setting is changed on another screen, and
        // a value read once would keep the corner empty (or filled) until the app restarted.
        showWeekNumber = remember(refreshKey) { Utils.getShowWeekNumber(context) },
        timedByDay = timedByDay,
        allDayByDay = allDayByDay,
        onOpenEvent = onOpenEvent,
        onCreateEvent = onCreateEvent,
        onOpenDay = onOpenDay,
    )
}

@Composable
private fun DaysBlock(
    dates: List<LocalDate>,
    today: LocalDate,
    zone: ZoneId,
    showLunar: Boolean,
    showWeekNumber: Boolean,
    timedByDay: Map<LocalDate, List<CalendarDayEvent>>,
    allDayByDay: Map<LocalDate, List<CalendarDayEvent>>,
    onOpenEvent: (CalendarDayEvent) -> Unit,
    onCreateEvent: (start: Long, end: Long) -> Unit,
    onOpenDay: ((LocalDate) -> Unit)?,
) {
    // The colour the hour and column rules are drawn in.
    //
    // outlineVariant, the role the day and week grids resolve to. These rules were previously drawn
    // in outline - a clear step darker - and at a full dp. Against the Canvas-drawn week view, one
    // physical pixel of outlineVariant, they read as heavy bars across the grid. Matching both the
    // role and the width is what brings the two grids to the same weight.
    val ruleColor = MaterialTheme.colorScheme.outlineVariant
    val columnRuleColor = MaterialTheme.colorScheme.outlineVariant
    // Read here rather than in the Canvas lambda: LocalDensity is a composition local, so it
    // can only be read from a composable scope.
    val hairline = hairlinePx()

    // The hour gutter is measured from the labels it has to hold.
    //
    // Those labels are this grid's own strings ("00:00"), so how wide the gutter must be is known
    // before anything is laid out. A fixed gutter narrow enough to leave a week's seven columns
    // usable was narrower than the label itself, and the time then broke onto two lines - "00:" over
    // "00" - which is what the week view showed on a phone. Measured with the style the labels are
    // drawn in, the gutter is exactly as wide as what it holds, in any language and at any font
    // scale, and the day columns get all the remaining width. On the day and three day views the
    // measured width stays below the width they have always had, so nothing about them changes.
    val hourLabels = remember { (0..23).map { String.format(Locale.getDefault(), "%02d:00", it) } }
    val hourLabelStyle = MaterialTheme.typography.labelSmall
    val textMeasurer = rememberTextMeasurer()
    val hourLabelWidth = remember(textMeasurer, hourLabelStyle, hourLabels) {
        hourLabels.maxOf { label ->
            textMeasurer.measure(AnnotatedString(label), hourLabelStyle).size.width
        }
    }
    // Rounded up to whole dp, plus one more for the layout's own rounding: a label measured to a
    // fraction of a pixel must not lose that fraction - or the right edge of its last glyph - to a
    // fractional density. At the usual 2.75x density a 30dp label is 82.5 pixels, and an exact fit
    // there is no fit at all.
    val measuredGutter = with(LocalDensity.current) {
        ceil(hourLabelWidth.toDp().value).dp
    } + HOUR_LABEL_END_PADDING + 1.dp
    val gutterWidth = maxOf(
        if (dates.size >= 7) NARROW_GUTTER_WIDTH else GUTTER_WIDTH,
        measuredGutter,
    )

    // The slot the user has picked out but not yet created an event in, as (column, hour).
    //
    // The week view works this way: the first tap marks the slot and shows what tapping it again
    // would do, and only the second tap opens the editor. This grid used to create on the first tap,
    // which meant a misplaced finger started an event and there was nothing to see before committing.
    // Kept across a trip to the editor, because the week view keeps its selection too.
    var selection by rememberSaveable { mutableStateOf<Pair<Int, Int>?>(null) }

    val scroll = rememberScrollState()

    // A hair taller than the grid, so the dividers are not clipped by the exact-height boundary.
    val columnRuleInset = 1.dp

    // Today's column carries a line at the current time; it refreshes once a minute.
    var nowMinutes by remember { mutableStateOf(minutesNow(zone)) }
    LaunchedEffect(Unit) {
        while (true) {
            nowMinutes = minutesNow(zone)
            kotlinx.coroutines.delay(60_000)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // The date row, only where it names more than one day.
        //
        // The day view shows one day, and its heading - the toolbar - already names that day: the row
        // repeating the weekday, the number and the lunar date under it was a second copy of the
        // heading, and the week-number corner it left behind was a strip of nothing above the hour
        // labels. So a page of one day starts straight into the grid; the number itself still has its
        // corner on the three day and week pages, and its column in the month grid.
        val showHeader = dates.size > 1
        if (showHeader) {
            DayHeaderRow(
                dates = dates,
                today = today,
                showLunar = showLunar,
                showWeekNumber = showWeekNumber,
                gutterWidth = gutterWidth,
                onOpenDay = onOpenDay,
            )
        }

        // All day events sit above the scrolling grid so they never scroll away.
        val hasAllDay = dates.any { !allDayByDay[it].isNullOrEmpty() }
        if (hasAllDay) {
            Row(modifier = Modifier.fillMaxWidth().padding(start = gutterWidth)) {
                dates.forEach { day ->
                    Column(modifier = Modifier.weight(1f).padding(horizontal = 2.dp)) {
                        allDayByDay[day].orEmpty().forEach { event ->
                            AllDayChip(event = event, onClick = { onOpenEvent(event) })
                        }
                    }
                }
            }
        }

        // Only where there is something above it to separate: with no header row and no all-day
        // strip, a rule here would be a line drawn under the toolbar for its own sake.
        if (showHeader || hasAllDay) {
            HorizontalDivider(color = ruleColor)
        }

        // The grid, and the column dividers drawn over it.
        //
        // The dividers have to be a sibling of the scrolling Row inside a Box, not a child of it. A
        // Row lays its children out left to right, so a fillMaxWidth() Canvas inside it is treated as
        // one more column: it took horizontal space, squeezed the days narrower, and the hour rules
        // drawn inside those columns were squeezed with them. As an overlay in the Box it covers the
        // grid without taking part in its layout.
        Box(modifier = Modifier.fillMaxSize()) {
            // Tapping an empty slot marks it, and tapping the marked slot starts an event there.
            //
            // Two taps, as the week view does it: the first shows which slot was picked and what
            // tapping again means, the second commits. The tap detector is attached to the scrolling
            // container itself rather than to an overlay on top of it. An overlay sized fillMaxSize()
            // only ever covers the part of the grid that is currently on screen, and - worse -
            // competing with the scroll for the same drags stopped the grid from scrolling at all. On
            // this modifier the drag is consumed by the scroll before the tap gesture can act on it,
            // so a swipe scrolls and a tap selects.
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scroll)
                    .pointerInput(dates.first()) {
                        detectTapGestures { offset ->
                            val columnWidth = (size.width - gutterWidth.toPx()) / dates.size
                            val column = ((offset.x - gutterWidth.toPx()) / columnWidth)
                                .toInt()
                                .coerceIn(0, dates.size - 1)
                            val minuteOfDay = (offset.y / HOUR_HEIGHT.toPx() * 60f).toInt()
                                .coerceIn(0, 24 * 60 - 1)
                            // Whole hours, so the marked slot is one row of the grid. It used to snap
                            // to the half hour, which put the mark across two half rows: the plus was
                            // drawn in the lower half of the hour it belonged to, and the event it
                            // created covered only that half. A row of this grid is an hour, so that
                            // is what a tap on it should mean.
                            val hour = minuteOfDay / 60
                            val picked = column to hour

                            if (selection == picked) {
                                selection = null
                                val dayStart = dates[column]
                                    .atStartOfDay(zone)
                                    .plusHours(hour.toLong())
                                    .toInstant()
                                    .toEpochMilli()
                                onCreateEvent(dayStart, dayStart + DEFAULT_NEW_EVENT_MILLIS)
                            } else {
                                selection = picked
                            }
                        }
                    },
            ) {
                HourGutter(width = gutterWidth, labels = hourLabels, labelStyle = hourLabelStyle)
                dates.forEachIndexed { column, day ->
                    DayColumn(
                        day = day,
                        isToday = day == today,
                        zone = zone,
                        nowMinutes = if (day == today) nowMinutes else -1,
                        events = timedByDay[day].orEmpty(),
                        onOpenEvent = onOpenEvent,
                        ruleColor = ruleColor,
                        // The picked slot, if it is in this column: the hour it starts at.
                        selectedSlot = selection?.takeIf { it.first == column }?.second,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            // Column dividers, drawn over the grid at its exact height.
            //
            // They were a fillMaxHeight() Canvas per column before that. A column in this scrolling
            // row has no definite height to fill, so that Canvas measured zero and drew nothing -
            // which is why the grid had hour rules but no lines between the days. Anchored to the
            // grid's 24 hour height, and positioned from the measured width, they land on the column
            // edges.
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(HOUR_HEIGHT * 24 + columnRuleInset),
            ) {
                val gutter = gutterWidth.toPx()
                val columnWidth = (size.width - gutter) / dates.size
                for (i in 1 until dates.size) {
                    val x = gutter + columnWidth * i
                    drawLine(
                        color = columnRuleColor,
                        start = Offset(x, 0f),
                        end = Offset(x, size.height),
                        strokeWidth = hairline,
                    )
                }
            }
        }
    }
}

/**
 * The date row: weekday above, day number below, then the lunar date when the lunar line is on -
 * the same three pieces the week view's header shows, and the same shape the toolbar handler uses.
 * Today's day number is circled.
 *
 * Only drawn for a page of more than one day: one day is already named by the toolbar, and a row of
 * one date under it is that heading said twice.
 *
 * [onOpenDay] makes each column a control: the day and week views open the three day view on the day
 * tapped. The three day view leaves it null, because its header is a label rather than a way out of
 * the screen.
 *
 * [showWeekNumber] fills the corner to the left of the first day - the gutter over the hour labels -
 * with the week the row falls in, the place the classic day and week views draw it and the only space
 * this row has that is otherwise empty.
 */
@Composable
private fun DayHeaderRow(
    dates: List<LocalDate>,
    today: LocalDate,
    showLunar: Boolean,
    showWeekNumber: Boolean,
    gutterWidth: Dp,
    onOpenDay: ((LocalDate) -> Unit)?,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val zone = ZoneId.systemDefault()
    // One day rather than the range is what the number names: any day of the row gives the same
    // week number, and the first one is always present.
    val weekNumber = if (showWeekNumber) {
        remember(dates.first()) {
            Utils.getWeekNumberFromTime(
                dates.first().atStartOfDay(zone).toInstant().toEpochMilli(),
                context,
            )
        }
    } else {
        0
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            // The corner is a cell of this row rather than an overlay, so the day columns are
            // measured against exactly the width they had before - the gutter width is the same
            // either way, only the way it is taken differs.
            .padding(
                start = if (showWeekNumber) 0.dp else gutterWidth,
                // A lone corner does not need the headroom the weekday-and-day stack takes, so the
                // day view's strip stays a strip rather than a band under the toolbar.
                top = if (dates.size > 1) 8.dp else 4.dp,
                bottom = if (dates.size > 1) 6.dp else 2.dp,
            ),
        verticalAlignment = Alignment.Top,
    ) {
        if (showWeekNumber) {
            Box(
                modifier = Modifier.width(gutterWidth),
                contentAlignment = Alignment.TopEnd,
            ) {
                Text(
                    text = weekNumber.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    // The gutter is only as wide as an hour label; a week number that needs more room
                    // must not push the columns sideways.
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.padding(end = HOUR_LABEL_END_PADDING),
                )
            }
        }
        // Only the days: with one day on the page the row is the week-number corner and nothing else,
        // because the toolbar already names that day. Without this the day view drew a second copy of
        // the heading under the title bar - the weekday, the number and the lunar date.
        if (dates.size > 1) {
            dates.forEach { day ->
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
                    modifier = Modifier
                        .weight(1f)
                        .then(
                            if (onOpenDay == null) {
                                Modifier
                            } else {
                                Modifier.clickable { onOpenDay(day) }
                            }
                        ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = day.format(WEEKDAY_FORMAT),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isToday) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        // A week puts seven of these on one row, so a language whose weekday
                        // abbreviation is longer than "Mon" would otherwise break it in two and push
                        // the day number down; cut at the end instead.
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(2.dp))
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(
                                if (isToday) MaterialTheme.colorScheme.primary else Color.Transparent
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = day.dayOfMonth.toString(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                            color = if (isToday) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            maxLines = 1,
                        )
                    }
                    if (!lunar.isNullOrEmpty()) {
                        Text(
                            text = lunar,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

/**
 * The 00:00 … 23:00 labels down the left edge; each label sits at the top of its hour.
 *
 * The labels and their style are handed in rather than rebuilt here: the gutter was measured from
 * exactly these strings in exactly this style (see [DaysBlock]), and formatting a second copy would
 * leave the drawn text and the width it was given able to disagree.
 */
@Composable
private fun HourGutter(width: Dp, labels: List<String>, labelStyle: TextStyle) {
    Column(modifier = Modifier.width(width)) {
        for (label in labels) {
            Box(modifier = Modifier.height(HOUR_HEIGHT), contentAlignment = Alignment.TopEnd) {
                Text(
                    text = label,
                    style = labelStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    // One line, never broken and never abbreviated: the gutter around this label was
                    // measured from it, so it fits, and softWrap = false is the guarantee that a
                    // rounding difference cannot split "00:00" into "00:" over "00" again. The
                    // default clip is deliberate - an ellipsis would turn a time into "00:…", which
                    // says less than the time itself.
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.padding(end = HOUR_LABEL_END_PADDING),
                )
            }
        }
    }
}

@Composable
private fun DayColumn(
    day: LocalDate,
    isToday: Boolean,
    zone: ZoneId,
    nowMinutes: Int,
    events: List<CalendarDayEvent>,
    onOpenEvent: (CalendarDayEvent) -> Unit,
    /** The colour the hour rules are drawn in, so the whole grid shares one. */
    ruleColor: Color,
    /**
     * The hour of the slot picked out in this column, or null.
     *
     * It is drawn as a filled hour with a plus, which is what the week view shows on its selected
     * slot and what tells the user a second tap is what creates the event.
     */
    selectedSlot: Int? = null,
    modifier: Modifier = Modifier,
) {
    // Read in the composable scope; the Canvas below cannot call LocalDensity itself.
    val hairline = hairlinePx()
    Box(modifier = modifier.fillMaxHeight()) {
        // The grid is one box of a known height - 24 hours - and the events are positioned inside it.
        //
        // Two things had to be right for the hour rules to show. They must be drawn against a
        // definite height, because this column only gets an unbounded one from the scrolling parent;
        // and they must not be drawn in a near-transparent white. A bare 0x1FFFFFFF is about 12%
        // white, which is invisible as a one pixel line on a dark background.
        Box(modifier = Modifier.height(HOUR_HEIGHT * 24)) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val stroke = hairline
                for (hour in 1 until 24) {
                    val y = HOUR_HEIGHT.toPx() * hour
                    drawLine(
                        color = ruleColor,
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = stroke,
                    )
                }
            }

            events.forEach { event ->
                EventBlock(event = event, zone = zone, onClick = { onOpenEvent(event) })
            }

            // The picked slot, and the plus that says what picking it again will do.
            //
            // Drawn above the events but below the current time marker, in the same place the week
            // view puts its own: a filled hour block with a plus centred in it.
            if (selectedSlot != null) {
                val highlight = MaterialTheme.colorScheme.secondaryContainer
                val plusColor = MaterialTheme.colorScheme.onSecondaryContainer
                // Converted here, in the composable scope: the Canvas lambda below draws in pixels
                // and has no Density of its own to convert with.
                val plusStroke = with(LocalDensity.current) { PLUS_STROKE.toPx() }
                val plusMaxLength = with(LocalDensity.current) { PLUS_MAX_LENGTH.toPx() }
                val plusMargin = with(LocalDensity.current) { PLUS_MARGIN.toPx() }
                val plusMinLength = with(LocalDensity.current) { PLUS_MIN_LENGTH.toPx() }
                // One hour, which is what a row of this grid is.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset(y = HOUR_HEIGHT * selectedSlot)
                        .height(HOUR_HEIGHT)
                        .background(highlight),
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        // Sized and stroked like the day and week views' own plus.
                        //
                        // Those draw it as two lines of a fixed length - NEW_EVENT_MAX_LENGTH, 16
                        // pixels - with a two pixel stroke, shrinking for a slot too small to hold
                        // that. Drawn here at a fraction of the slot instead, it came out far larger
                        // than the mark the other two views show for the same gesture, and at one
                        // physical pixel it was too thin to match them.
                        val length = minOf(
                            minOf(size.height, size.width) - plusMargin,
                            plusMaxLength,
                        ).coerceAtLeast(plusMinLength)
                        val half = length / 2f
                        val midX = size.width / 2f
                        val midY = size.height / 2f
                        drawLine(
                            color = plusColor,
                            start = Offset(midX - half, midY),
                            end = Offset(midX + half, midY),
                            strokeWidth = plusStroke,
                        )
                        drawLine(
                            color = plusColor,
                            start = Offset(midX, midY - half),
                            end = Offset(midX, midY + half),
                            strokeWidth = plusStroke,
                        )
                    }
                }
            }
        }

        if (nowMinutes >= 0) {
            val fraction = nowMinutes / (24f * 60f)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset(y = HOUR_HEIGHT * 24 * fraction)
                    .height(2.dp)
                    .background(MaterialTheme.colorScheme.error)
            )
        } else if (isToday) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.06f))
            )
        }
    }
}

@Composable
private fun EventBlock(
    event: CalendarDayEvent,
    zone: ZoneId,
    onClick: () -> Unit,
) {
    val start = remember(event.begin, zone) {
        java.time.LocalDateTime.ofInstant(Instant.ofEpochMilli(event.begin), zone)
    }
    val end = remember(event.end, zone) {
        java.time.LocalDateTime.ofInstant(Instant.ofEpochMilli(event.end), zone)
    }

    val startMinutes = start.hour * 60 + start.minute
    // Clamp to the day so an event running past midnight does not overflow the grid.
    val endMinutes = if (end.toLocalDate() > start.toLocalDate()) 24 * 60 else end.hour * 60 + end.minute
    val durationMinutes = (endMinutes - startMinutes).coerceAtLeast(20)

    val top = HOUR_HEIGHT * (startMinutes / 60f)
    val height = HOUR_HEIGHT * (durationMinutes / 60f)

    val accent = remember(event.calendarColor) { Color(event.calendarColor) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp)
            .offset(y = top)
            .height(height)
            .clip(RoundedCornerShape(6.dp))
            .background(accent.copy(alpha = if (event.isDeclined) 0.25f else 0.85f))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 2.dp),
    ) {
        Text(
            text = event.title,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun AllDayChip(event: CalendarDayEvent, onClick: () -> Unit) {
    val accent = remember(event.calendarColor) { Color(event.calendarColor) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(accent.copy(alpha = 0.85f))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 2.dp),
    ) {
        Text(
            text = event.title,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            // One line with the cut at the end. The chip of a week column is about forty dp wide, so
            // a title that wrapped took the second line from the chip below it in the strip; a single
            // ellipsized line says as much and keeps every chip the same height.
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun minutesNow(zone: ZoneId): Int {
    val now = java.time.LocalTime.now(zone)
    return now.hour * 60 + now.minute
}

/**
 * True when an all-day occurrence covers [day].
 *
 * All-day events are stored at UTC midnight with an exclusive end - the day after the last one - so
 * the days they cover are read in UTC rather than in the device's zone. Read in the device's zone,
 * an event stored for the 1st fell on the 31st in every zone behind UTC.
 *
 * Covering the whole range rather than only its first day is what makes a multi-day event appear in
 * each of its columns. Drawn on the first day alone, a three day conference read as three separate
 * one day entries that had somehow lost two of their days.
 */
private fun allDayCovers(event: CalendarDayEvent, day: LocalDate): Boolean {
    val first = LocalDate.ofInstant(Instant.ofEpochMilli(event.begin), ZoneOffset.UTC)
    val last = LocalDate.ofInstant(
        Instant.ofEpochMilli((event.end - 1).coerceAtLeast(event.begin)),
        ZoneOffset.UTC,
    )
    return !day.isBefore(first) && !day.isAfter(last)
}
