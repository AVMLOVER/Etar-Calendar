/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The week start the widgets draw with.
 *
 * The preference is stored in the platform calendar's numbering, where Sunday is 1; Utils converts
 * that to Time's, where Sunday is 0 - the same conversion the calendar's own views make. It lives
 * here rather than inside a provider because the widgets have to agree with each other about it.
 */
package com.android.calendar.widget

import android.content.Context
import com.android.calendar.Utils
import java.time.DayOfWeek

internal object WeekStart {

    fun dayOfWeek(context: Context): DayOfWeek {
        val timeDay = Utils.getFirstDayOfWeek(context)
        return if (timeDay == android.text.format.Time.SUNDAY) {
            DayOfWeek.SUNDAY
        } else {
            DayOfWeek.of(timeDay)
        }
    }
}
