/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * Host for the Material 3 Expressive date calculator.
 *
 * Unlike the year view and the agenda this is a tool you dip into, so back returns to the screen
 * you came from rather than leaving the app.
 */
package com.android.calendar.datecalc.compose

import android.os.Bundle
import com.android.calendar.chrome.M3eHostActivity
import ws.xsoh.etar.R

class ComposeDateCalculatorActivity : M3eHostActivity() {

    override val checkedMenuItemId: Int = R.id.action_date_calculator
    override val currentScreenName: String = "DATE_CALCULATOR"
    /** Back returns to the calendar, as it does from the other drawer entries. */
    override val exitOnBack: Boolean = true

    /** The heading is the screen's name, not a period, so there is no picker for it to open. */
    override val titleOpensPicker: Boolean = false

    /** Nothing to go "today" to, and nowhere to jump to. */
    override val showTodayAction: Boolean = false

    /** No jump-to-date and no search: a calculator works on the dates typed into it. */
    override val showGotoAction: Boolean = false
    override val showSearchAction: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setPickerTitle(getString(R.string.m3e_date_calculator_title))
        setM3eContent { M3eDateCalculatorScreen() }
    }
}
