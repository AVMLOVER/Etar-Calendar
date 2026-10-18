/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * Material 3 Expressive three day screen ("三日").
 *
 * The grid itself lives in M3eDaysScreen, which the day and week views are built from as well: the
 * three of them are one screen with a different number of days in a page. This file keeps the name
 * and the signature the three day activity was written against, so that screen still composes
 * exactly what it did before the grid was shared - three days, no day header taps, and the same
 * heading and paging.
 *
 * See M3eDaysScreen for the layout itself: a fixed header naming the days, one shared vertical time
 * grid holding a column per day, and a line marking the current time on today's column.
 */
package com.android.calendar.threeday.compose

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import com.android.calendar.chrome.compose.M3eScreen
import com.android.calendar.composeui.M3eDaysScreen
import java.time.LocalDate

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
     * Bumped by the host whenever the screen comes back to the foreground, which is what makes the
     * grid re-read the provider after an event was created or deleted on another screen.
     */
    refreshKey: Int = 0,
    /**
     * Bumped when the host wants today brought on screen.
     *
     * The pager keeps its own scroll position, so the page it was opened on cannot also be a
     * command to move; this is that command.
     */
    scrollToTodayKey: Int = 0,
    /** Epoch day chosen from the heading's date picker, or 0 when none has been chosen. */
    pickedDayKey: Long = 0,
) {
    M3eDaysScreen(
        days = 3,
        page = page,
        onVisibleRangeChanged = onVisibleRangeChanged,
        onOpenEvent = onOpenEvent,
        onCreateEvent = onCreateEvent,
        selectedScreen = M3eScreen.DAY,
        refreshKey = refreshKey,
        scrollToTodayKey = scrollToTodayKey,
        pickedDayKey = pickedDayKey,
    )
}
