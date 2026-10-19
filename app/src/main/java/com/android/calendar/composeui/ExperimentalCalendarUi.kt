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

import android.app.Activity
import android.content.Context
import com.android.calendar.CalendarController
import com.android.calendar.Utils

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

    /**
     * The Compose screen that shows [viewType], or null when that view has no Compose screen.
     *
     * One mapping, read from two places: the launcher redirect decides what the launcher opens with
     * it, and a landing screen that is already running uses it to hand over when the "Default view"
     * setting has changed underneath it. A second copy of this list is how the redirect and the
     * screens would end up disagreeing about what "the default view" opens.
     *
     * @param viewType one of [CalendarController.ViewType], as returned by
     *   [com.android.calendar.Utils.getViewTypeFromIntentAndSharedPref].
     */
    @JvmStatic
    fun screenForViewType(viewType: Int): Class<out Activity>? = when (viewType) {
        CalendarController.ViewType.DAY -> ComposeDayActivity::class.java
        CalendarController.ViewType.WEEK -> ComposeWeekActivity::class.java
        CalendarController.ViewType.MONTH -> ComposeMonthActivity::class.java
        CalendarController.ViewType.THREE_DAY ->
            com.android.calendar.threeday.compose.ComposeThreeDayActivity::class.java
        CalendarController.ViewType.AGENDA ->
            com.android.calendar.agenda.compose.ComposeAgendaActivity::class.java
        else -> null
    }

    /**
     * The view type a screen shows, or null when it is not one of the calendar views.
     *
     * The reverse of [screenForViewType]. Used where the screen is held as a class - the drawer's
     * entries, the classic host's - and the question is whether the view it opens is the one the
     * settings name as the app's default.
     */
    @JvmStatic
    fun viewTypeForScreen(screen: Class<out Activity>): Int? = when (screen) {
        ComposeDayActivity::class.java -> CalendarController.ViewType.DAY
        ComposeWeekActivity::class.java -> CalendarController.ViewType.WEEK
        ComposeMonthActivity::class.java -> CalendarController.ViewType.MONTH
        com.android.calendar.threeday.compose.ComposeThreeDayActivity::class.java ->
            CalendarController.ViewType.THREE_DAY
        com.android.calendar.agenda.compose.ComposeAgendaActivity::class.java ->
            CalendarController.ViewType.AGENDA
        else -> null
    }

    /**
     * True when [viewType] is the view the "Default view" setting names.
     *
     * Resolved through [Utils.getViewTypeFromIntentAndSharedPref] - the same call the launcher
     * redirect makes - so a screen comparing itself with the setting cannot disagree with the
     * screen the launcher would have opened.
     */
    @JvmStatic
    fun isConfiguredDefault(activity: Activity, viewType: Int): Boolean =
        Utils.getViewTypeFromIntentAndSharedPref(activity) == viewType
}
