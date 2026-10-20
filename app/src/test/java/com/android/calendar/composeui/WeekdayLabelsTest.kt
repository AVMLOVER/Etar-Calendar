/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The weekday header builder, checked without a device.
 *
 * The widget's own bug - Chinese narrow names coming out as 星 星 星 ... - came from the platform's
 * desugared java.time narrow text; these cases pin the two things this file decides: that the widget
 * asks for the narrow field width, and that the labels follow the app's first day of the week rather
 * than the locale's default. The platform's locale data itself is exercised by the widget at runtime.
 */
package com.android.calendar.composeui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek

class WeekdayLabelsTest {

    /** A name source that labels each day by its ISO number, so the order is easy to read. */
    private val numbered = WeekdayNameSource { day -> "d${day.value}" }

    /** Monday first: the week runs 1..7. */
    @Test
    fun mondayFirstRunsMondayToSunday() {
        assertEquals(
            listOf("d1", "d2", "d3", "d4", "d5", "d6", "d7"),
            weekdayLabels(DayOfWeek.MONDAY, numbered),
        )
    }

    /** Sunday first: 7, then 1..6 - what the widget shows on a Sunday-first locale. */
    @Test
    fun sundayFirstRunsSundayToSaturday() {
        assertEquals(
            listOf("d7", "d1", "d2", "d3", "d4", "d5", "d6"),
            weekdayLabels(DayOfWeek.SUNDAY, numbered),
        )
    }

    /** Every first-day setting gives seven labels, starting on that day, and nothing else. */
    @Test
    fun everyFirstDayGivesSevenLabelsStartingOnIt() {
        for (first in DayOfWeek.entries) {
            val labels = weekdayLabels(first, numbered)
            assertEquals("labels for first day $first", 7, labels.size)
            assertEquals("first label for $first", "d${first.value}", labels.first())
            assertEquals("all days for $first", 7, labels.toSet().size)
        }
    }

    /**
     * The narrow labels are single characters, and there are still seven of them in order: the
     * Chinese week the bug report named, modelled as the hand-written narrow source it should
     * produce. Duplicates (German M/D/M/D/F/S/S) are allowed - they are what the locale defines.
     */
    @Test
    fun narrowLabelsAreSingleCharactersForASingleCharacterLocale() {
        val chineseNarrow = mapOf(
            DayOfWeek.MONDAY to "一",
            DayOfWeek.TUESDAY to "二",
            DayOfWeek.WEDNESDAY to "三",
            DayOfWeek.THURSDAY to "四",
            DayOfWeek.FRIDAY to "五",
            DayOfWeek.SATURDAY to "六",
            DayOfWeek.SUNDAY to "日",
        )
        val labels = weekdayLabels(DayOfWeek.MONDAY, WeekdayNameSource { chineseNarrow.getValue(it) })
        assertEquals(listOf("一", "二", "三", "四", "五", "六", "日"), labels)
        assertTrue("every label is one character", labels.all { it.length == 1 })
    }

    /** German narrow names repeat letters, and the builder must not deduplicate them. */
    @Test
    fun duplicateNarrowLabelsAreKept() {
        val german = listOf("M", "D", "M", "D", "F", "S", "S")
        val names = mapOf(
            DayOfWeek.MONDAY to "M",
            DayOfWeek.TUESDAY to "D",
            DayOfWeek.WEDNESDAY to "M",
            DayOfWeek.THURSDAY to "D",
            DayOfWeek.FRIDAY to "F",
            DayOfWeek.SATURDAY to "S",
            DayOfWeek.SUNDAY to "S",
        )
        assertEquals(
            german,
            weekdayLabels(DayOfWeek.MONDAY, WeekdayNameSource { names.getValue(it) }),
        )
    }

    /**
     * The widget asks for the narrow field width. Pinning it here means a future change back to a
     * full or short name - the bug's shape - has to change this constant first.
     */
    @Test
    fun theWidgetAsksForTheNarrowFieldWidth() {
        assertEquals("EEEEE", NARROW_WEEKDAY_SKELETON)
    }
}
