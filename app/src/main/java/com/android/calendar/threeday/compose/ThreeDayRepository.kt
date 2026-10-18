/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * Data layer for the Material 3 Expressive three day screen.
 *
 * The query itself moved to DaysRepository when the week and day views came along: all three look
 * at an Instances window and differ only in how wide it is. This is kept as the name the three day
 * screen knows, so that screen's own code did not have to change with the move.
 */
package com.android.calendar.threeday.compose

import android.content.Context
import com.android.calendar.composeui.CalendarDayEvent
import com.android.calendar.composeui.DaysRepository

/**
 * One occurrence of an event inside the three day window.
 *
 * An alias rather than a copy: the value is the same row whichever screen read it, and a second data
 * class would have meant converting between two identical types at every boundary.
 */
typealias ThreeDayEvent = CalendarDayEvent

class ThreeDayRepository(context: Context) {

    private val delegate = DaysRepository(context)

    fun load(fromMillis: Long, toMillis: Long, limit: Int = 500): List<CalendarDayEvent> =
        delegate.load(fromMillis, toMillis, limit)
}
