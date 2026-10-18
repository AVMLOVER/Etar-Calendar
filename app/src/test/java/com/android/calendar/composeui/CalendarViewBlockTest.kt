/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The day-grid paging arithmetic, which is what decides whether a screen opened on a day really
 * shows that day.
 *
 * The week view hands a tapped day to the three day view, and the three day view shows the block
 * that contains it. That only holds if the block a page index maps to is the block the day falls
 * in, so the two directions - day to page, page to day - are checked against each other here rather
 * than only on a device.
 */
package com.android.calendar.composeui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class CalendarViewBlockTest {

    private val monday = DayOfWeek.MONDAY
    private val sunday = DayOfWeek.SUNDAY

    /** The first day of a page, computed the way the screen computes it. */
    private fun firstOfPage(anchor: LocalDate, index: Int, days: Int): LocalDate =
        anchor.plusDays(index.toLong() * days)

    /** Every page a date can fall in has to hold that date; checked over a decade of dates. */
    @Test
    fun aThreeDayBlockContainsTheDayItWasFoundFrom() {
        val anchor = blockStartOf(LocalDate.ofEpochDay(0L), 3, monday)
        var day = LocalDate.of(1970, 1, 1)
        while (day.year < 2035) {
            val index = blockIndex(anchor, day, 3, monday)
            val first = firstOfPage(anchor, index, 3)
            assertFalse("$day is before the block starting $first", day.isBefore(first))
            assertFalse("$day is after the block starting $first", day.isAfter(first.plusDays(2)))
            day = day.plusDays(1)
        }
    }

    /** A week page has to start on the week's first day, whichever day that is. */
    @Test
    fun weekPagesStartOnTheConfiguredFirstDay() {
        for (firstDayOfWeek in DayOfWeek.entries) {
            val anchor = blockStartOf(LocalDate.ofEpochDay(0L), 7, firstDayOfWeek)
            assertEquals(
                "the anchor is not a ${firstDayOfWeek}",
                firstDayOfWeek,
                anchor.dayOfWeek,
            )
            var day = LocalDate.of(2024, 1, 1)
            while (day.year < 2027) {
                val index = blockIndex(anchor, day, 7, firstDayOfWeek)
                val first = firstOfPage(anchor, index, 7)
                assertEquals(
                    "the page for $day does not start on ${firstDayOfWeek}",
                    firstDayOfWeek,
                    first.dayOfWeek,
                )
                assertTrue(
                    "the page starting $first does not hold $day",
                    !day.isBefore(first) && day.isBefore(first.plusDays(7)),
                )
                day = day.plusDays(1)
            }
        }
    }

    /** A page of one day is that day, so the day view's page index is its epoch day. */
    @Test
    fun aOneDayPageIsTheDayItself() {
        val anchor = blockStartOf(LocalDate.ofEpochDay(0L), 1, monday)
        val day = LocalDate.of(2026, 10, 18)
        val index = blockIndex(anchor, day, 1, monday)
        assertEquals(day, firstOfPage(anchor, index, 1))
        assertEquals(day.toEpochDay(), index.toLong())
    }

    /** The week's first day is taken from the setting, not from the locale's default. */
    @Test
    fun aSundayWeekStartsOnSunday() {
        val day = LocalDate.of(2026, 10, 18) // a Sunday
        assertEquals(day, blockStartOf(day, 7, sunday))
        assertEquals(day.minusDays(6), blockStartOf(day, 7, monday))
    }
}
