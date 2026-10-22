/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * Host for the Material 3 Expressive countdown screen.
 *
 * A tool you dip into, so like the date calculator back returns to the screen you came from rather
 * than leaving the app.
 */
package com.android.calendar.countdown.compose

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.android.calendar.chrome.M3eHostActivity
import ws.xsoh.etar.R
import java.time.LocalDate

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

    /**
     * Incremented each time an intent asks for the add dialog.
     *
     * A request is a counter rather than a flag because this screen is single-top: a second request
     * arrives through onNewIntent while the screen is already composed, and the flag would have
     * nothing new to react to.
     */
    private var addRequest by mutableLongStateOf(0L)

    /** The day the requested add dialog should start on. */
    private var addDate by mutableStateOf<LocalDate?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        consumeAddIntent(intent)
        setPickerTitle(getString(R.string.m3e_countdown_title))
        setM3eContent {
            M3eCountdownScreen(addRequest = addRequest, addDate = addDate)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        consumeAddIntent(intent)
    }

    private fun consumeAddIntent(intent: Intent?) {
        val epochDay = intent?.getLongExtra(EXTRA_ADD_DATE, Long.MIN_VALUE) ?: return
        if (epochDay == Long.MIN_VALUE) {
            return
        }
        addDate = LocalDate.ofEpochDay(epochDay)
        addRequest++
    }

    companion object {
        /** The day the add dialog should open on, as an epoch day. */
        const val EXTRA_ADD_DATE = "m3e_countdown_add_date"

        /**
         * Opens this screen straight into its add dialog on [date].
         *
         * The add dialog is the screen's own entry point - the same one its add button opens - so the
         * chooser reuses it rather than writing a countdown of its own.
         */
        fun intent(context: Context, date: LocalDate): Intent =
            Intent(context, ComposeCountdownActivity::class.java)
                .putExtra(EXTRA_ADD_DATE, date.toEpochDay())
    }
}
