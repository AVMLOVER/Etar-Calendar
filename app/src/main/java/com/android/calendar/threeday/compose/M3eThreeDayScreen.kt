/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * Material 3 Expressive three day screen ("三日").
 *
 * Layout follows Google Calendar: a fixed header naming the three days, then one shared vertical
 * time grid holding a column per day, with a line marking the current time on today's column.
 *
 * Paging is horizontal and moves three days at a time, so a flick always lands on the next or
 * previous block of three days rather than somewhere inside one.
 */
package com.android.calendar.threeday.compose

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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.android.calendar.LunarUtils
import com.android.calendar.chrome.compose.M3eChromeScaffold
import com.android.calendar.chrome.compose.M3eScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** How tall one hour of the grid is. */
private val HOUR_HEIGHT = 60.dp

/** Width of the gutter that carries the hour labels. */
private val GUTTER_WIDTH = 44.dp

/**
 * How many three day pages the pager holds, and which one it opens on, plus how many pages either
 * side of the visible one stay composed.
 *
 * The count is deliberately small. A pager tracks scroll offset as a Float over the whole page
 * range, so a long range costs precision: at a few thousand pages the offsets of neighbouring pages
 * stop being distinguishable and the pager settles between two of them instead of on one. 1200 pages
 * is about ten years of three day blocks in each direction from the middle, and keeps the offset
 * arithmetic exact.
 *
 * [BEYOND_VIEWPORT] keeps the neighbouring pages laid out so a swipe has the next block ready
 * instead of composing it mid-gesture, which is what made paging stutter.
 */
private const val PAGE_COUNT = 1200
private const val PAGE_MIDDLE = PAGE_COUNT / 2
private const val BEYOND_VIEWPORT = 2

/** Length of an event created by tapping an empty slot, matching the day view's default. */
private const val DEFAULT_NEW_EVENT_MILLIS = 60L * 60L * 1000L

private val RANGE_FORMAT = DateTimeFormatter.ofPattern("M月d日", Locale.getDefault())
private val WEEKDAY_FORMAT = DateTimeFormatter.ofPattern("EEE", Locale.getDefault())

/**
 * @param page the day to open on, counted in days from 1970-01-01. The host passes the day the
 *   calendar is currently on, so opening the view lands on the block that contains it.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun M3eThreeDayScreen(
    page: Long,
    onVisibleRangeChanged: (LocalDate) -> Unit,
    onOpenEvent: (ThreeDayEvent) -> Unit,
    onCreateEvent: (start: Long, end: Long) -> Unit,
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
) {
    val context = LocalContext.current
    val zone = remember { ZoneId.systemDefault() }
    val today = remember { LocalDate.now(zone) }

    // Same switch the week and month views read, so the lunar line appears in all of them together.
    val showLunar = remember { LunarUtils.showLunar(context) }

    // The block the host asked for sits at the middle of the range, so there is room to swipe either
    // way without ever reaching an end.
    val centrePage = (page / 3L).toInt()
    val pagerState = rememberPagerState(
        initialPage = PAGE_MIDDLE,
        pageCount = { PAGE_COUNT },
    )

    // Absolute day number of the first of the three days on the current page.
    val firstDay = remember(pagerState.currentPage) {
        LocalDate.ofEpochDay((centrePage + pagerState.currentPage - PAGE_MIDDLE).toLong() * 3L)
    }

    LaunchedEffect(firstDay) { onVisibleRangeChanged(firstDay) }

    // Today, brought on screen when the host asks.
    LaunchedEffect(scrollToTodayKey) {
        if (scrollToTodayKey == 0) return@LaunchedEffect

        // Found by difference from the page on screen, not from the page this screen opened on.
        //
        // The two are the same until the user swipes, after which the opening page is a stale
        // reference and the distance to today has to be measured from where the pager actually is.
        // In days rather than in blocks, so a page whose three day window straddles today still lands
        // on the block that contains it.
        val currentFirstDay = LocalDate.ofEpochDay(
            (centrePage + pagerState.currentPage - PAGE_MIDDLE).toLong() * 3L
        )
        val daysAway = today.toEpochDay() - currentFirstDay.toEpochDay()
        val targetPage = pagerState.currentPage + Math.floorDiv(daysAway, 3L).toInt()

        if (targetPage in 0 until PAGE_COUNT) {
            pagerState.animateScrollToPage(targetPage)
        }
    }

    M3eChromeScaffold(selected = M3eScreen.DAY, title = { Text(titleOf(firstDay)) }) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            // Keep the neighbouring blocks composed so a swipe shows the next three days straight
            // away instead of composing them mid-gesture.
            beyondViewportPageCount = BEYOND_VIEWPORT,
        ) { index ->
            ThreeDayPage(
                firstDay = LocalDate.ofEpochDay((centrePage + index - PAGE_MIDDLE).toLong() * 3L),
                today = today,
                zone = zone,
                showLunar = showLunar,
                onOpenEvent = onOpenEvent,
                onCreateEvent = onCreateEvent,
                refreshKey = refreshKey,
            )
        }
    }
}

/** "10月16日 – 10月18日", the same heading the host puts in the toolbar. */
private fun titleOf(firstDay: LocalDate): String {
    val last = firstDay.plusDays(2)
    return firstDay.format(RANGE_FORMAT) + " – " + last.format(RANGE_FORMAT)
}

/**
 * One three day block.
 *
 * The data is loaded per page rather than once for whichever page is current. Loading centrally
 * meant every page shared one loading flag, so each swipe swapped the whole pager for a spinner and
 * back; that is what made paging stutter.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ThreeDayPage(
    firstDay: LocalDate,
    today: LocalDate,
    zone: ZoneId,
    showLunar: Boolean,
    onOpenEvent: (ThreeDayEvent) -> Unit,
    onCreateEvent: (start: Long, end: Long) -> Unit,
    refreshKey: Int,
) {
    val context = LocalContext.current
    val days = remember(firstDay) { (0L..2L).map { firstDay.plusDays(it) } }

    var events by remember(firstDay) { mutableStateOf<List<ThreeDayEvent>?>(null) }
    // Re-reads on a refresh without dropping back to the spinner: the page keeps showing what it has
    // while the query runs, so returning from the editor does not flash.
    LaunchedEffect(firstDay, refreshKey) {
        val loaded = withContext(Dispatchers.IO) {
            ThreeDayRepository(context.applicationContext).load(
                firstDay.atStartOfDay(zone).toInstant().toEpochMilli(),
                firstDay.plusDays(3).atStartOfDay(zone).toInstant().toEpochMilli(),
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

    val timedByDay = remember(loaded, days, zone) {
        days.associateWith { day ->
            loaded.filter { event ->
                !event.allDay && LocalDate.ofInstant(Instant.ofEpochMilli(event.begin), zone) == day
            }
        }
    }
    val allDayByDay = remember(loaded, days, zone) {
        days.associateWith { day ->
            loaded.filter { event ->
                event.allDay && LocalDate.ofInstant(Instant.ofEpochMilli(event.begin), zone) == day
            }
        }
    }

    ThreeDayBlock(
        days = days,
        today = today,
        zone = zone,
        showLunar = showLunar,
        timedByDay = timedByDay,
        allDayByDay = allDayByDay,
        onOpenEvent = onOpenEvent,
        onCreateEvent = onCreateEvent,
    )
}

@Composable
private fun ThreeDayBlock(
    days: List<LocalDate>,
    today: LocalDate,
    zone: ZoneId,
    showLunar: Boolean,
    timedByDay: Map<LocalDate, List<ThreeDayEvent>>,
    allDayByDay: Map<LocalDate, List<ThreeDayEvent>>,
    onOpenEvent: (ThreeDayEvent) -> Unit,
    onCreateEvent: (start: Long, end: Long) -> Unit,
) {
    // The colour the hour and column rules are drawn in.
    //
    // outlineVariant is the app's separator colour, but as a one pixel line it is too close to the
    // background in light mode - Material's light outline variant is a very pale grey, and the grid
    // disappeared entirely. outline is a clear step darker, which is what a grid rule needs, and it
    // still follows the theme in both modes.
    val ruleColor = MaterialTheme.colorScheme.outline
    val columnRuleColor = MaterialTheme.colorScheme.outline

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
        DayHeaderRow(days = days, today = today, showLunar = showLunar)

        // All day events sit above the scrolling grid so they never scroll away.
        val hasAllDay = days.any { !allDayByDay[it].isNullOrEmpty() }
        if (hasAllDay) {
            Row(modifier = Modifier.fillMaxWidth().padding(start = GUTTER_WIDTH)) {
                days.forEach { day ->
                    Column(modifier = Modifier.weight(1f).padding(horizontal = 2.dp)) {
                        allDayByDay[day].orEmpty().forEach { event ->
                            AllDayChip(event = event, onClick = { onOpenEvent(event) })
                        }
                    }
                }
            }
        }

        HorizontalDivider(color = ruleColor)

        // The grid, and the column dividers drawn over it.
        //
        // The dividers have to be a sibling of the scrolling Row inside a Box, not a child of it. A
        // Row lays its children out left to right, so a fillMaxWidth() Canvas inside it is treated as
        // one more column: it took horizontal space, squeezed the three days narrower, and the hour
        // rules drawn inside those columns were squeezed with them. As an overlay in the Box it covers
        // the grid without taking part in its layout.
        Box(modifier = Modifier.fillMaxSize()) {
            // Tapping an empty slot starts a new event there.
            //
            // The tap detector is attached to the scrolling container itself rather than to an overlay
            // on top of it. An overlay sized fillMaxSize() only ever covers the part of the grid that
            // is currently on screen, and - worse - competing with the scroll for the same drags
            // stopped the grid from scrolling at all. On this modifier the drag is consumed by the
            // scroll before the tap gesture can act on it, so a swipe scrolls and a tap creates.
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scroll)
                    .pointerInput(days.first()) {
                        detectTapGestures { offset ->
                            val columnWidth = (size.width - GUTTER_WIDTH.toPx()) / days.size
                            val column = ((offset.x - GUTTER_WIDTH.toPx()) / columnWidth)
                                .toInt()
                                .coerceIn(0, days.size - 1)
                            val minuteOfDay = (offset.y / HOUR_HEIGHT.toPx() * 60f).toInt()
                                .coerceIn(0, 24 * 60 - 1)
                            // Snap to the half hour, which is the granularity the day view uses.
                            val snapped = (minuteOfDay / 30) * 30
                            val dayStart = days[column]
                                .atStartOfDay(zone)
                                .plusMinutes(snapped.toLong())
                                .toInstant()
                                .toEpochMilli()
                            onCreateEvent(dayStart, dayStart + DEFAULT_NEW_EVENT_MILLIS)
                        }
                    },
            ) {
                HourGutter()
                days.forEach { day ->
                    DayColumn(
                        day = day,
                        isToday = day == today,
                        zone = zone,
                        nowMinutes = if (day == today) nowMinutes else -1,
                        events = timedByDay[day].orEmpty(),
                        onOpenEvent = onOpenEvent,
                        ruleColor = ruleColor,
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
                val gutter = GUTTER_WIDTH.toPx()
                val columnWidth = (size.width - gutter) / days.size
                for (i in 1 until days.size) {
                    val x = gutter + columnWidth * i
                    drawLine(
                        color = columnRuleColor,
                        start = Offset(x, 0f),
                        end = Offset(x, size.height),
                        strokeWidth = 1.dp.toPx(),
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
 */
@Composable
private fun DayHeaderRow(days: List<LocalDate>, today: LocalDate, showLunar: Boolean) {
    val context = LocalContext.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = GUTTER_WIDTH, top = 8.dp, bottom = 6.dp),
        verticalAlignment = Alignment.Top,
    ) {
        days.forEach { day ->
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
                modifier = Modifier.weight(1f),
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

/** The 00:00 … 23:00 labels down the left edge; each label sits at the top of its hour. */
@Composable
private fun HourGutter() {
    Column(modifier = Modifier.width(GUTTER_WIDTH)) {
        for (hour in 0..23) {
            Box(modifier = Modifier.height(HOUR_HEIGHT), contentAlignment = Alignment.TopEnd) {
                Text(
                    text = String.format(Locale.getDefault(), "%02d:00", hour),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 6.dp),
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
    events: List<ThreeDayEvent>,
    onOpenEvent: (ThreeDayEvent) -> Unit,
    /** The colour the hour rules are drawn in, so the whole grid shares one. */
    ruleColor: Color,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxHeight()) {
        // The grid is one box of a known height - 24 hours - and the events are positioned inside it.
        //
        // Two things had to be right for the hour rules to show. They must be drawn against a
        // definite height, because this column only gets an unbounded one from the scrolling parent;
        // and they must not be drawn in a near-transparent white. A bare 0x1FFFFFFF is about 12%
        // white, which is invisible as a one pixel line on a dark background.
        Box(modifier = Modifier.height(HOUR_HEIGHT * 24)) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val stroke = 1.dp.toPx()
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
    event: ThreeDayEvent,
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
private fun AllDayChip(event: ThreeDayEvent, onClick: () -> Unit) {
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
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun minutesNow(zone: ZoneId): Int {
    val now = java.time.LocalTime.now(zone)
    return now.hour * 60 + now.minute
}
