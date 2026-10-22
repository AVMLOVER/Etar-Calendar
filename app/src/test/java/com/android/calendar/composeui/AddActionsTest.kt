/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The add actions' default day, checked without a device.
 *
 * One rule per view: the day view is the day on screen, the three day and week blocks are today when
 * it is inside them and their first day otherwise, the month is today in the current month and its
 * 1st otherwise, and the year is always today. These cases pin each rule and its boundaries.
 */
package com.android.calendar.composeui

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class AddActionsTest {

    private val today = LocalDate.of(2026, 10, 22)

    /** The year view has no day to point at, so it always uses today. */
    @Test
    fun theYearViewUsesToday() {
        assertEquals(
            today,
            addActionDate(AddViewKind.YEAR, today, LocalDate.of(2026, 1, 1), visibleDays = 365),
        )
    }

    /** The month view uses today when it is in the displayed month and the 1st otherwise. */
    @Test
    fun theMonthViewUsesTodayOrTheFirstOfTheMonth() {
        val october = LocalDate.of(2026, 10, 1)
        assertEquals(today, addActionDate(AddViewKind.MONTH, today, october, visibleDays = 31))
        assertEquals(
            LocalDate.of(2026, 11, 1),
            addActionDate(AddViewKind.MONTH, today, LocalDate.of(2026, 11, 1), visibleDays = 30),
        )
    }

    /** The week view uses today when it is in the displayed week, the week's first day otherwise. */
    @Test
    fun theWeekViewUsesTodayOrTheFirstDayOfTheWeek() {
        val weekStart = LocalDate.of(2026, 10, 19)
        assertEquals(today, addActionDate(AddViewKind.WEEK, today, weekStart, visibleDays = 7))
        val otherWeek = LocalDate.of(2026, 10, 26)
        assertEquals(
            otherWeek,
            addActionDate(AddViewKind.WEEK, today, otherWeek, visibleDays = 7),
        )
    }

    /** The three day block behaves the same way over its three days. */
    @Test
    fun theThreeDayViewUsesTodayOrTheFirstOfTheThreeDays() {
        val blockStart = LocalDate.of(2026, 10, 21)
        assertEquals(today, addActionDate(AddViewKind.THREE_DAY, today, blockStart, visibleDays = 3))
        val otherBlock = LocalDate.of(2026, 10, 23)
        assertEquals(
            otherBlock,
            addActionDate(AddViewKind.THREE_DAY, today, otherBlock, visibleDays = 3),
        )
    }

    /** The day view is the day on screen. */
    @Test
    fun theDayViewUsesTheDayOnScreen() {
        assertEquals(today, addActionDate(AddViewKind.DAY, today, today, visibleDays = 1))
        val otherDay = LocalDate.of(2026, 10, 25)
        assertEquals(
            otherDay,
            addActionDate(AddViewKind.DAY, today, otherDay, visibleDays = 1),
        )
    }

    /** The last day of a block counts as inside it; the day after does not. */
    @Test
    fun theBlockBoundariesAreInclusiveOfTheirLastDay() {
        val weekStart = LocalDate.of(2026, 10, 16)
        assertEquals(today, addActionDate(AddViewKind.WEEK, today, weekStart, visibleDays = 7))
        val justBefore = LocalDate.of(2026, 10, 23)
        assertEquals(
            justBefore,
            addActionDate(AddViewKind.WEEK, today, justBefore, visibleDays = 7),
        )
    }

    /** A period with no days falls back to its first day rather than claiming today is inside. */
    @Test
    fun anEmptyPeriodFallsBackToItsFirstDay() {
        assertEquals(
            LocalDate.of(2026, 11, 1),
            addActionDate(AddViewKind.MONTH, today, LocalDate.of(2026, 11, 1), visibleDays = 0),
        )
    }
}
