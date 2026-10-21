/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The editor's end-time fallback, checked without a device.
 *
 * The reported defect: an imported recurring event's editor showed an end of 1970-01-01 07:59 (the
 * model's -1 rendered in UTC+8) because the loader read DURATION instead of DTEND for rows with an
 * RRULE. These cases pin the arithmetic that replaces that -1: a stated end wins when it is after
 * the start, a stated duration is added when there is no usable end, and a missing duration falls
 * back to an hour or to the next UTC midnight for an all-day event. The duration parser is covered
 * on its own because RFC 5545's grammar has three component families.
 */
package com.android.calendar.composeui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EventEndTest {

    private val start = 1_700_000_000_000L // 2023-11-14T22:13:20Z, an ordinary timed start
    private val day = 24L * 60L * 60L * 1000L
    private val hour = 60L * 60L * 1000L

    /** A stated end after the start is what the editor shows: the fallback never overrides it. */
    @Test
    fun aValidEndIsLeftUntouched() {
        val end = start + 90L * 60L * 1000L
        assertEquals(end, eventEnd(start, end, duration = "PT1H", allDay = false))
        assertEquals(end, eventEnd(start, end, duration = null, allDay = false))
    }

    /** A missing DTEND with a DURATION is DTSTART + DURATION, as the provider computes it. */
    @Test
    fun aMissingEndUsesTheStatedDuration() {
        assertEquals(start + hour, eventEnd(start, end = -1, duration = "PT1H", allDay = false))
        assertEquals(start + 90L * 60L * 1000L, eventEnd(start, end = 0, duration = "PT1H30M", allDay = false))
        assertEquals(start + day, eventEnd(start, end = 0, duration = "P1D", allDay = true))
    }

    /** No end and no duration: a timed event is an hour long. */
    @Test
    fun aTimedEventWithoutEitherIsAnHourLong() {
        assertEquals(start + hour, eventEnd(start, end = -1, duration = null, allDay = false))
        // A zero or non-numeric duration is treated as absent.
        assertEquals(start + hour, eventEnd(start, end = -1, duration = "", allDay = false))
        assertEquals(start + hour, eventEnd(start, end = -1, duration = "nonsense", allDay = false))
    }

    /** No end and no duration: an all-day event runs to the next UTC midnight. */
    @Test
    fun anAllDayEventWithoutEitherEndsAtTheNextUtcMidnight() {
        val midnight = (start / day) * day
        val end = eventEnd(midnight, end = -1, duration = null, allDay = true)
        assertEquals(midnight + day, end)
        assertEquals(0L, end % day)

        // A start that is not on a midnight still ends on one.
        val endFromMidday = eventEnd(midnight + day / 2, end = 0, duration = null, allDay = true)
        assertEquals(midnight + day, endFromMidday)
    }

    /** An end that is before the start is as unusable as a missing one. */
    @Test
    fun anEndBeforeTheStartFallsBack() {
        assertEquals(start + hour, eventEnd(start, end = start - hour, duration = null, allDay = false))
        assertEquals(
            start + 30L * 60L * 1000L,
            eventEnd(start, end = start - hour, duration = "PT30M", allDay = false),
        )
    }

    /** The result is always after the start, which is what stops the editor rendering 1970. */
    @Test
    fun theFallbackIsAlwaysAfterTheStart() {
        assertTrue(eventEnd(start, -1, null, allDay = false) > start)
        assertTrue(eventEnd(start, 0, null, allDay = true) > start)
    }

    /** RFC 5545 duration forms: weeks, days, and the time components after the T. */
    @Test
    fun durationsAreParsedPerTheRfc() {
        assertEquals(7 * day, parseEventDurationMillis("P1W"))
        assertEquals(day, parseEventDurationMillis("P1D"))
        assertEquals(26 * hour, parseEventDurationMillis("P1DT2H"))
        assertEquals(hour + 30L * 60L * 1000L, parseEventDurationMillis("PT1H30M"))
        assertEquals(45L * 60L * 1000L, parseEventDurationMillis("PT45M"))
        assertEquals(30L * 1000L, parseEventDurationMillis("PT30S"))
        // Case-insensitive, as some exporters write it.
        assertEquals(hour, parseEventDurationMillis("pt1h"))
    }

    /** Malformed values are absent, never a wrong end: months, empty text, missing numbers. */
    @Test
    fun malformedDurationsAreRejected() {
        assertNull(parseEventDurationMillis(null))
        assertNull(parseEventDurationMillis(""))
        assertNull(parseEventDurationMillis("P"))
        assertNull(parseEventDurationMillis("PT"))
        assertNull(parseEventDurationMillis("1H"))
        assertNull(parseEventDurationMillis("P1M")) // months are not a duration
        assertNull(parseEventDurationMillis("PT1D")) // a day does not belong after the T
        assertNull(parseEventDurationMillis("P1H")) // an hour does not belong before the T
        assertNull(parseEventDurationMillis("P1X"))
    }
}
