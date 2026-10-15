/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * How far away an event's day is, for the title bar of the screens that show one.
 *
 * Counted in whole days and ignoring the time of day: an event at nine tonight is today, not tomorrow,
 * and one at one in the morning is still today. That is the question the heading answers - which day
 * it falls on - so the comparison is between calendar dates in the device's zone, not between
 * instants.
 *
 * The count is live. It is derived from a value that is refreshed when the date rolls over, so a
 * screen left open overnight changes "Today" to "1 day ago" by itself rather than keeping yesterday's
 * answer until it is reopened.
 */
package com.android.calendar.event.compose

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import ws.xsoh.etar.R

/**
 * Today's date, re-emitted shortly after each midnight.
 *
 * A screen that only read the date once would be right until the day changed and wrong afterwards,
 * which for a screen left open overnight is the whole of the following day.
 */
@Composable
fun rememberToday(): State<LocalDate> = produceState(LocalDate.now()) {
    while (true) {
        val zone = ZoneId.systemDefault()
        val now = Instant.now()
        val nextMidnight = LocalDate.now(zone).plusDays(1).atStartOfDay(zone).toInstant()
        // A second past midnight, so the new date is certainly the one in effect.
        val wait = Duration.between(now, nextMidnight).toMillis().coerceAtLeast(1_000L) + 1_000L
        delay(wait)
        value = LocalDate.now(zone)
    }
}

/**
 * The event's day measured from today: 0 for today, positive for days still to come, negative for
 * days already past.
 *
 * The instant is resolved in the device's zone and truncated to a date, so the time of day has no
 * effect on the answer.
 */
fun daysFromToday(start: Long, today: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Long =
    ChronoUnit.DAYS.between(today, Instant.ofEpochMilli(start).atZone(zone).toLocalDate())

/**
 * The parenthesised note the title bar carries, or an empty string when there is no date to describe.
 *
 * "Today", "in x days", "x days ago" - the wording the settings screen uses for its own countdown
 * preview, so the two read the same.
 */
@Composable
fun dayDistanceLabel(start: Long?): String {
    if (start == null || start <= 0L) {
        return ""
    }
    val today = rememberToday().value
    return when (val days = daysFromToday(start, today)) {
        0L -> stringResource(R.string.m3e_day_today)
        in 1L..Long.MAX_VALUE -> stringResource(R.string.m3e_day_in_future, days)
        else -> stringResource(R.string.m3e_day_in_past, -days)
    }
}

/**
 * The heading these screens share: what the screen is, and how far off the event's day is.
 *
 * One line, always. The note pushed the heading onto a second line on a phone, and a title bar that
 * reflows stops looking like a title bar - so the text is bounded to a single line and scaled down,
 * which keeps the whole heading readable in the width there is.
 *
 * [heading] is passed in because the editor and the viewer name themselves differently while
 * sharing everything else about the heading.
 */
@Composable
fun EventTitleWithDistance(
    start: Long?,
    heading: String = stringResource(R.string.m3e_event_edit_title),
) {
    val distance = dayDistanceLabel(start)
    val text = if (distance.isEmpty()) {
        heading
    } else {
        val (open, close) = dayNoteBrackets()
        heading + open + distance + close
    }
    Text(
        text = text,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        style = MaterialTheme.typography.titleMedium,
    )
}

/**
 * The brackets the note is wrapped in, chosen for the script the app is running in.
 *
 * Chinese, Japanese and Korean set their punctuation full width, and a half-width bracket beside
 * their characters looks like a gap; Latin scripts are the other way round, where a full-width
 * bracket leaves an obvious hole. The app has no locale of its own, so the system language decides.
 */
@Composable
fun dayNoteBrackets(): Pair<String, String> {
    val language = androidx.compose.ui.platform.LocalConfiguration.current.locales[0].language
    return if (language in CJK_LANGUAGES) "（" to "）" else " (" to ")"
}

private val CJK_LANGUAGES = setOf("zh", "ja", "ko")
