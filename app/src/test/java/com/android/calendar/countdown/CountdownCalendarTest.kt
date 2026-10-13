/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The date grid behind the countdown screen's picker.
 */
package com.android.calendar.countdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class CountdownCalendarTest {

    @Test
    fun `the grid is always six full weeks`() {
        val grid = CountdownCalendar.grid(LocalDate.of(2026, 10, 1), DayOfWeek.MONDAY)
        assertEquals(CountdownCalendar.WEEKS_SHOWN * 7, grid.size)
    }

    @Test
    fun `every row is a whole week starting on the chosen day`() {
        for (first in DayOfWeek.entries) {
            val grid = CountdownCalendar.grid(LocalDate.of(2026, 10, 1), first)
            grid.chunked(7).forEach { row ->
                assertEquals("row should start on $first", first, row.first().dayOfWeek)
                assertEquals("row should end before $first", first, row.last().plusDays(1).dayOfWeek)
            }
        }
    }

    @Test
    fun `the grid starts on or before the first of the month`() {
        val grid = CountdownCalendar.grid(LocalDate.of(2026, 10, 1), DayOfWeek.MONDAY)
        assertTrue(grid.first().isBefore(LocalDate.of(2026, 10, 1)) || grid.first() == LocalDate.of(2026, 10, 1))
    }

    @Test
    fun `the whole month fits inside the grid`() {
        // Every month of a leap year, so February's 29 days is covered too.
        var month = LocalDate.of(2024, 1, 1)
        repeat(12) {
            val grid = CountdownCalendar.grid(month, DayOfWeek.MONDAY)
            val last = month.withDayOfMonth(month.lengthOfMonth())
            assertTrue("$month: first day missing", grid.contains(month))
            assertTrue("$month: last day missing", grid.contains(last))
            // And it is contiguous, so no day is skipped in the middle.
            grid.zipWithNext().forEach { (a, b) ->
                assertEquals("gap between $a and $b", a.plusDays(1), b)
            }
            month = month.plusMonths(1)
        }
    }

    @Test
    fun `the first of the month on the week start needs no leading padding`() {
        // 1 June 2026 is a Monday.
        val grid = CountdownCalendar.grid(LocalDate.of(2026, 6, 1), DayOfWeek.MONDAY)
        assertEquals(LocalDate.of(2026, 6, 1), grid.first())
    }

    @Test
    fun `a sunday start shifts the whole layout`() {
        // 1 November 2026 is a Sunday.
        val sundayFirst = CountdownCalendar.grid(LocalDate.of(2026, 11, 1), DayOfWeek.SUNDAY)
        assertEquals(LocalDate.of(2026, 11, 1), sundayFirst.first())
        val mondayFirst = CountdownCalendar.grid(LocalDate.of(2026, 11, 1), DayOfWeek.MONDAY)
        assertEquals(LocalDate.of(2026, 10, 26), mondayFirst.first())
    }

    @Test
    fun `weekday names follow the chosen start`() {
        val monday = CountdownCalendar.weekDays(DayOfWeek.MONDAY)
        assertEquals(DayOfWeek.MONDAY, monday.first())
        assertEquals(DayOfWeek.SUNDAY, monday.last())
        val sunday = CountdownCalendar.weekDays(DayOfWeek.SUNDAY)
        assertEquals(DayOfWeek.SUNDAY, sunday.first())
        assertEquals(DayOfWeek.SATURDAY, sunday.last())
    }

    @Test
    fun `padding days are not in the month`() {
        val october = LocalDate.of(2026, 10, 1)
        val grid = CountdownCalendar.grid(october, DayOfWeek.MONDAY)
        assertTrue(CountdownCalendar.isInMonth(LocalDate.of(2026, 10, 31), october))
        assertFalse(CountdownCalendar.isInMonth(LocalDate.of(2026, 9, 30), october))
        assertFalse(CountdownCalendar.isInMonth(LocalDate.of(2026, 11, 1), october))
        // The grid does contain those neighbours, which is the point of the padding.
        assertTrue(grid.contains(LocalDate.of(2026, 9, 30)))
        assertTrue(grid.contains(LocalDate.of(2026, 11, 1)))
    }

    @Test
    fun `shifting a month lands on the first and survives a year boundary`() {
        val december = LocalDate.of(2026, 12, 15)
        assertEquals(LocalDate.of(2027, 1, 1), CountdownCalendar.shiftMonth(december, 1))
        assertEquals(LocalDate.of(2026, 11, 1), CountdownCalendar.shiftMonth(december, -1))
        val january = LocalDate.of(2026, 1, 31)
        assertEquals(LocalDate.of(2025, 12, 1), CountdownCalendar.shiftMonth(january, -1))
        assertEquals(LocalDate.of(2026, 2, 1), CountdownCalendar.shiftMonth(january, 1))
    }
}
