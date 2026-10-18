/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * Host for the Material 3 Expressive countdown screen.
 *
 * A tool you dip into, so like the date calculator back returns to the screen you came from rather
 * than leaving the app.
 */
package com.android.calendar.countdown.compose

import android.os.Bundle
import com.android.calendar.chrome.M3eHostActivity
import ws.xsoh.etar.R

class ComposeCountdownActivity : M3eHostActivity() {

    override val checkedMenuItemId: Int = R.id.action_countdown
    override val currentScreenName: String = "COUNTDOWN"
    /** The heading is the screen name, not a period, so it does not open the month picker. */
    override val titleOpensPicker: Boolean = false

    /** Only the three dot menu belongs here; there is nothing to go "today" to. */
    override val showTodayAction: Boolean = false

    /** No jump-to-date and no search: the list is its own thing, not a period being browsed. */
    override val showGotoAction: Boolean = false

    /** Search was removed with the rest of the old calendar chrome; it led out of this screen. */
    override val showSearchAction: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setPickerTitle(getString(R.string.m3e_countdown_title))
        setM3eContent { M3eCountdownScreen() }
    }
}
