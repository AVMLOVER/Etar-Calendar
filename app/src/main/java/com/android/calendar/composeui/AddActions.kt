/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The day the three "add" actions default to, decided by the calendar view that is showing.
 *
 * A view knows the period it is showing, and "add" has to land inside it: today when today is part of
 * what is on screen, and otherwise the first day of it. The year view is the exception - a year is too
 * long a span for "the first day of it" to mean anything, so it always uses today.
 *
 * One function for all three actions and every view, so the event, the to-do and the countdown cannot
 * end up on different days from one tap. Pure because it is only dates, and checked without a device.
 */
package com.android.calendar.composeui

import java.time.LocalDate

/** The calendar view a chooser was opened from. */
enum class AddViewKind {
    YEAR,
    MONTH,
    WEEK,
    THREE_DAY,
    DAY,
}

/**
 * The day the add actions default to.
 *
 * @param kind the view the chooser is open over.
 * @param today the device's today.
 * @param firstVisibleDay the first day of the period on screen: the 1st of the displayed month, the
 *   first day of the displayed week or three day block, or the day of the day view.
 * @param visibleDays how many days that period spans; 0 for the year view.
 */
fun addActionDate(
    kind: AddViewKind,
    today: LocalDate,
    firstVisibleDay: LocalDate,
    visibleDays: Int,
): LocalDate {
    // A year is too long a span for its first day to mean anything, so it always uses today.
    if (kind == AddViewKind.YEAR) {
        return today
    }
    val inside = visibleDays > 0 &&
        !today.isBefore(firstVisibleDay) &&
        today.isBefore(firstVisibleDay.plusDays(visibleDays.toLong()))
    return if (inside) today else firstVisibleDay
}
