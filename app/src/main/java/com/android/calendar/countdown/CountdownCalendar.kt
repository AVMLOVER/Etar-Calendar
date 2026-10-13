/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The date grid behind the countdown screen's picker.
 *
 * Written here rather than taken from Material 3's DatePicker for two reasons: that picker's weekday
 * header renders every column as the first character of the name - "星" seven times over in Chinese -
 * and this screen also wants to start the week on the user's chosen day. The arithmetic is kept
 * separate from the drawing so it can be tested without a device.
 */
package com.android.calendar.countdown

import java.time.DayOfWeek
import java.time.LocalDate

/**
 * The six week rows a month view shows, each holding seven days.
 *
 * Leading and trailing slots are filled from the neighbouring months so every row is complete, which
 * is what keeps the columns aligned with the weekday header above them.
 */
object CountdownCalendar {

    const val WEEKS_SHOWN = 6

    /**
     * The grid for [month], with weeks starting on [firstDayOfWeek].
     *
     * Returned as a flat list of [WEEKS_SHOWN] * 7 days, so the caller can lay it out in rows of
     * seven without doing any date arithmetic of its own.
     */
    fun grid(month: LocalDate, firstDayOfWeek: DayOfWeek): List<LocalDate> {
        val first = month.withDayOfMonth(1)
        // How far back the first row reaches: 0 when the 1st already falls on the week's first day.
        val lead = (first.dayOfWeek.value - firstDayOfWeek.value + 7) % 7
        val start = first.minusDays(lead.toLong())
        return (0 until WEEKS_SHOWN * 7).map { start.plusDays(it.toLong()) }
    }

    /** The weekday names for the header, in the order the grid lays them out. */
    fun weekDays(firstDayOfWeek: DayOfWeek): List<DayOfWeek> =
        (0 until 7).map { firstDayOfWeek.plus(it.toLong()) }

    /** True when [day] belongs to [month] rather than to the padding around it. */
    fun isInMonth(day: LocalDate, month: LocalDate): Boolean =
        day.year == month.year && day.monthValue == month.monthValue

    /** The month [steps] months away from [month], always on the 1st so month lengths cannot bite. */
    fun shiftMonth(month: LocalDate, steps: Long): LocalDate =
        month.withDayOfMonth(1).plusMonths(steps)
}
