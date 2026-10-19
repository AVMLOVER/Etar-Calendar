/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * Host for the Material 3 Expressive per-calendar screen.
 *
 * This is the page the calendars list opens when a calendar is tapped: the classic calendar had one
 * behind the same tap - show the events, manage the notifications, change the colour, rename it,
 * delete it, and the calendar's own information underneath. It is a screen rather than a dialog
 * because it is a place the user goes and comes back from, and because what it edits is a calendar,
 * not a single value that a dialog could ask for.
 *
 * The chrome - toolbar, drawer, edge-to-edge insets and the sideways transitions - comes from
 * M3eHostActivity, the same base the year view, the agenda and the settings screens use, so this
 * screen arrives and leaves the way they do.
 */
package com.android.calendar.selectcalendars.compose

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.android.calendar.chrome.M3eHostActivity
import com.android.calendar.composeui.ComposeCalendarViewActivity
import ws.xsoh.etar.R

class ComposeCalendarEditActivity : M3eHostActivity() {

    /** Not a drawer entry: this screen is opened from the calendars list, not switched to. */
    override val checkedMenuItemId: Int = 0
    override val currentScreenName: String = "CALENDAR_EDIT"

    /** The heading is the screen's name, so there is no period for a picker to open. */
    override val titleOpensPicker: Boolean = false

    /** A calendar's settings have no dates in them: no today, nothing to jump to, nothing to find. */
    override val showTodayAction: Boolean = false
    override val showGotoAction: Boolean = false
    override val showSearchAction: Boolean = false

    /**
     * No title bar menu at all - not even the three dots.
     *
     * This screen is about one calendar, and it offers everything it can do as rows on the screen.
     * The overflow would be a list of ways out of it - the other views, the calendars list, the
     * settings - which is not what a page about one calendar should put in reach of the delete row.
     */
    override val showsTitleBarMenu: Boolean = false

    /**
     * Back returns to the calendars list, never out of the app.
     *
     * The screen is only ever reached from that list, and the list is where the changes made here
     * are seen - a delete, a rename, a colour - so back has to reveal it. It is also the direction
     * the screen's transitions were declared for: the list is revealed from the left while this
     * screen leaves to the right, the reverse of the way in.
     */
    override val exitOnBack: Boolean = false

    /** Arrived at rather than switched to: a back arrow where the drawer button would be. */
    override val navigationIconShowsBack: Boolean = true

    /** Read from the intent in onCreate; the calendar the screen is about. */
    private var calendarId: Long = -1L

    /**
     * Bumped on every resume.
     *
     * The list underneath - or the colour dialog, or the system's own notification settings screen -
     * can leave this calendar changed while this screen is stopped, so the values it draws are
     * re-read on the way back rather than kept from the first load. It is a Compose state because
     * the content is composed with it: bumping it re-runs the screen's read.
     */
    private var reloadKey by mutableStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        calendarId = intent.getLongExtra(EXTRA_CALENDAR_ID, -1L)
        setPickerTitle(getString(R.string.m3e_calendar_edit_title))
        if (calendarId <= 0L) {
            // Started without a calendar. Nothing here can be edited, and the list it would return
            // to is not on screen, so there is nothing to show.
            finish()
            return
        }

        setM3eContent {
            M3eCalendarEditScreen(
                calendarId = calendarId,
                reloadKey = reloadKey,
                // The heading names the screen; the calendar's own name is the second line, so the
                // user can see which calendar they opened without losing the screen's name.
                onCalendarName = { name -> setPickerSubtitle(name) },
                onFinished = { finish() },
            )
        }
    }

    override fun onResume() {
        super.onResume()
        reloadKey++
    }

    companion object {
        /** The Calendars._ID the screen edits. */
        const val EXTRA_CALENDAR_ID = "com.android.calendar.extra.CALENDAR_ID"

        /**
         * The intent the calendars list starts this screen with.
         *
         * [ComposeCalendarViewActivity.EXTRA_RETURN_TO_OPENER] says the list stays underneath: back
         * - the toolbar arrow, the system gesture and the button all take the same path - finishes
         * this screen, and the platform plays that as the reverse of the way in, revealing the list
         * from the left.
         */
        fun intent(context: Context, calendarId: Long): Intent =
            Intent(context, ComposeCalendarEditActivity::class.java)
                .putExtra(EXTRA_CALENDAR_ID, calendarId)
                .putExtra(ComposeCalendarViewActivity.EXTRA_RETURN_TO_OPENER, true)
    }
}
