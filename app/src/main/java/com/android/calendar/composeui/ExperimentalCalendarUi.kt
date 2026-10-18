/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The single answer to "which calendar views does a navigation request mean".
 *
 * The day, week and month screens are the all-Compose ones. They started life behind an
 * experimental switch in the settings; that switch has been retired and these screens are the
 * calendar now, so the answer is yes for everything that asks.
 *
 * Kept as one object rather than a bare `true` at each call site: the question is asked from three
 * places - the Compose host's drawer, the classic calendar's drawer and the launcher redirect - and
 * the reason the old switch worked everywhere at once is that all three read the same answer here.
 */
package com.android.calendar.composeui

import android.content.Context

object ExperimentalCalendarUi {

    /**
     * True: the day, week and month views are the all-Compose screens.
     *
     * The signature still takes a [Context] because the three call sites hand over their activity,
     * and the value used to be read from it. Nothing is read any more - the preference behind this
     * was removed from the settings screen along with the switch, and the app opens the new screens
     * whether or not that preference is still set on a device.
     */
    @JvmStatic
    @Suppress("UNUSED_PARAMETER")
    fun enabled(context: Context): Boolean = true
}
