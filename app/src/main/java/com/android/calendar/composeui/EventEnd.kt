/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The end time an event editor should show when the row's DTEND is unusable.
 *
 * Android's provider allows either DTEND or DURATION on an event row - never both - and every reader
 * except the editor's loader gets the end from the provider's Instances table, which the provider
 * computes from whichever one is stored. The editor reads the raw row, so it has to cope with both
 * forms itself. It did not: setModelFromCursor read only DURATION when an event was recurring and
 * only DTEND otherwise, so a row carrying RRULE + DTEND but no DURATION - which is exactly what the
 * ICS importer writes, and what Google Calendar exports - left the model's end at its default -1.
 * The editor then rendered 1969-12-31 23:59:59.999 UTC, which is 1970-01-01 07:59 in UTC+8: the
 * reported "1970-01-01 07:59" end date, and one that a save would write back as the event's end.
 *
 * The arithmetic is pure: a start, an end and an RFC 5545 duration string, and the same rules the
 * provider applies - the stated end when it is usable, otherwise start + duration, otherwise an hour
 * for a timed event and the next UTC midnight for an all-day one (all-day rows are stored at UTC
 * midnight by this app's other code paths).
 */
package com.android.calendar.composeui

/** One day in milliseconds. */
private const val DAY_MILLIS = 24L * 60L * 60L * 1000L

/** One hour in milliseconds, the fallback length of a timed event with no stated duration. */
private const val HOUR_MILLIS = 60L * 60L * 1000L

/**
 * The end an editor should use for a row.
 *
 * @param start DTSTART in epoch millis.
 * @param end DTEND in epoch millis, or -1/0 when the row has none.
 * @param duration the row's RFC 5545 DURATION ("P1D", "PT1H30M"), or null.
 * @param allDay whether the row's ALL_DAY flag is set.
 * @return a time strictly after [start]: the stated end when it is after the start, otherwise the
 *   derived duration, otherwise an hour (timed) or the next UTC midnight (all-day).
 */
fun eventEnd(
    start: Long,
    end: Long,
    duration: String?,
    allDay: Boolean,
): Long {
    if (end > start) {
        return end
    }
    val statedDuration = parseEventDurationMillis(duration)
    if (statedDuration != null && statedDuration > 0L) {
        return start + statedDuration
    }
    return if (allDay) nextUtcMidnight(start) else start + HOUR_MILLIS
}

/**
 * Parses an RFC 5545 DURATION value into milliseconds, or null when it is absent or malformed.
 *
 * Grammar: `P[nW][nD][T[nH][nM][nS]]`; weeks and days are date components, hours, minutes and
 * seconds come after the `T`. An "M" before the `T` would be months, which a duration may not
 * contain, so it is rejected rather than read as minutes.
 */
fun parseEventDurationMillis(duration: String?): Long? {
    val text = duration?.trim()?.uppercase() ?: return null
    if (text.length < 3 || text[0] != 'P') {
        return null
    }
    var index = 1
    var inTime = false
    var total = 0L
    var matched = false
    while (index < text.length) {
        val c = text[index]
        if (c == 'T') {
            if (inTime) {
                return null
            }
            inTime = true
            index++
            continue
        }
        var number = 0L
        var digits = 0
        while (index < text.length && text[index].isDigit()) {
            number = number * 10 + (text[index] - '0')
            index++
            digits++
        }
        if (digits == 0 || index >= text.length) {
            return null
        }
        val factor = when (val unit = text[index]) {
            'W' -> 7 * DAY_MILLIS
            'D' -> DAY_MILLIS
            'H' -> HOUR_MILLIS
            'M' -> 60_000L
            'S' -> 1_000L
            else -> return null
        }
        val timeUnit = text[index] == 'H' || text[index] == 'M' || text[index] == 'S'
        // Hours, minutes and seconds belong after the T; weeks and days before it.
        if (timeUnit != inTime) {
            return null
        }
        if (number > Long.MAX_VALUE / factor) {
            return null
        }
        total += number * factor
        matched = true
        index++
    }
    return if (matched && total > 0L) total else null
}

/** The first UTC midnight strictly after [millis]. All-day rows are stored at UTC midnight. */
fun nextUtcMidnight(millis: Long): Long {
    val day = Math.floorDiv(millis, DAY_MILLIS)
    return (day + 1) * DAY_MILLIS
}
