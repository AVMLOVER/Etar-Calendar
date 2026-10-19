/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * Host for the Material 3 Expressive year view.
 *
 * The drawer and top bar come from M3eHostActivity, which uses the same layouts and menus the
 * calendar itself does.
 */
package com.android.calendar.year.compose

import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import android.content.Intent
import android.os.Bundle
import android.provider.CalendarContract
import com.android.calendar.AllInOneActivity
import com.android.calendar.CalendarController
import com.android.calendar.LunarUtils
import com.android.calendar.Utils
import com.android.calendar.chrome.M3eHostActivity
import com.android.calendar.composeui.ComposeCalendarViewActivity
import com.android.calendar.composeui.ComposeMonthActivity
import com.android.calendar.composeui.ExperimentalCalendarUi
import com.android.calendar.settings.compose.SettingsRepository
import com.android.calendar.settings.compose.YEAR_SCROLL_VERTICAL
import com.android.calendar.settings.compose.yearScrollMode
import java.time.LocalDate
import java.time.ZoneId
import java.util.Calendar
import ws.xsoh.etar.R

class ComposeYearActivity : M3eHostActivity() {

    override val checkedMenuItemId: Int = R.id.year_menu_item
    override val currentScreenName: String = "YEAR"

    /**
     * Bumped on every resume.
     *
     * The year view has no change notification to listen to for its settings, so this is what
     * makes it re-read them when the screen comes back to the front - which is how a scroll
     * mode changed in the settings takes effect without reopening the view.
     */
    private var refreshKey by androidx.compose.runtime.mutableStateOf(0)

    /** Year the screen is showing, from its own title updates. The year picker is centred on it. */
    private var visibleYear = 0

    /**
     * The year the list is built on. Picking a year from the heading's picker sets it and bumps
     * refreshKey: the list is keyed on refreshKey, so that re-key is what moves it to that year.
     */
    private var initialYear by androidx.compose.runtime.mutableStateOf(LocalDate.now().year)

    /**
     * The year view's heading is a year, so its picker chooses a whole year. A month is not a
     * destination this screen has: the row of twelve months would either leave for the month view
     * or mean nothing here.
     */
    override val pickerChoosesYear: Boolean = true

    override fun pickerYear(): Int = if (visibleYear > 0) visibleYear else initialYear

    override fun onYearPicked(year: Int) {
        initialYear = year
        refreshKey++
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val repo = SettingsRepository(this)

        // preferences_week_start_day: -1 means "follow the locale".
        val weekStartPref = repo.string(KEY_WEEK_START, "-1").toIntOrNull() ?: -1
        val firstDayOfWeek = if (weekStartPref == -1) {
            Calendar.getInstance().firstDayOfWeek
        } else {
            weekStartPref
        }
        // The year the caller named, when it named one: the month view passes the year it is
        // showing, so opening the year view lands there instead of jumping back to the current
        // year. With no extra - the other screens, which have no month to name - today's year
        // is the answer.
        val requestedYear = intent.getIntExtra(EXTRA_INITIAL_YEAR, 0)
        initialYear = if (requestedYear > 0) requestedYear else LocalDate.now().year
        val showLunar = LunarUtils.showLunar(this)

        setM3eContent {
            val context = LocalContext.current

            // The scroll mode is read here, inside the composition, and re-read on every resume.
            //
            // It used to be read once in onCreate, so changing it in the settings and coming back
            // left the year view scrolling the old way until it was left and opened again. The
            // preference is a plain value with no change notification, so the refresh key is what
            // makes the screen notice: bumping it recomposes this block and the new mode is read.
            M3eYearScreen(
                initialYear = initialYear,
                firstDayOfWeek = firstDayOfWeek,
                showLunar = showLunar,
                horizontal = yearScrollMode(SettingsRepository(context)) != YEAR_SCROLL_VERTICAL,
                // Bumped on resume, and by a year picked from the heading's picker, which is what
                // re-reads the mode after the settings screen and rebuilds the list at that year.
                screenKey = refreshKey,
                // The year with its unit, as each language writes it: the unit follows the number in
                // Chinese and leads it in German and English, and the string carries the spacing so
                // that "2026年" has none and "Jahr 2026" does.
                onVisibleYearChanged = { year ->
                    visibleYear = year
                    setPickerTitle(getString(R.string.m3e_year_title, year.toString()))
                },
                onOpenMonth = { year, month -> openMonth(year, month) },
                // The add button: an event on today, at the next half-hour slot. A year view has
                // no "first day of the view" that means anything, so today is always the answer.
                onCreateEvent = { createEventToday() },
            )
        }
    }

    /** Starts the editor for a new event today, at the shared next-slot time. */
    private fun createEventToday() {
        val start = Utils.getNextCreateEventStart(System.currentTimeMillis())
        startActivity(
            com.android.calendar.CalendarController.getInstance(this).generateCreateEventIntent(
                start,
                start + 60L * 60L * 1000L,
                false,
                null,
                -1L,
            )
        )
    }

    override fun onResume() {
        super.onResume()
        refreshKey++
    }

    /** Re-reads the year's event markers now, as a resume does, when the title bar's refresh is used. */
    override fun onRefresh() {
        refreshKey++
    }

    /**
     * Opens the month view on the given month.
     *
     * This screen stays underneath, so the month is opened *over* it and back returns here. The one
     * exception is when the month is the configured default view: it is then the landing screen
     * wherever it is opened, so it is started without the opener extras and its back leaves the app
     * rather than walking into the year view.
     */
    private fun openMonth(year: Int, month: Int) {
        val millis = LocalDate.of(year, month, 1)
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

        // With the experimental UI on, "the month view" is the all-Compose screen. Opening the
        // classic one here left a month pick from this screen on the old View-based grid while every
        // other way into the month view - the drawer, the start view - went to the new one.
        if (ExperimentalCalendarUi.enabled(this)) {
            val opensOverThisScreen = !ExperimentalCalendarUi.isConfiguredDefault(
                this,
                CalendarController.ViewType.MONTH,
            )
            startActivity(
                Intent(this, ComposeMonthActivity::class.java)
                    // The month that contains this day is the month opened, the same extra the
                    // classic intent below carries.
                    .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, millis)
                    .apply {
                        // This screen stays underneath, so the month view is opened *over* it: back
                        // returns here, and the month's own drawer keeps the two-screen shape.
                        // Without these the month read itself as a landing screen, which also made
                        // it hand over to the default view as soon as it resumed if that was not the
                        // month.
                        if (opensOverThisScreen) {
                            putExtra(ComposeCalendarViewActivity.EXTRA_RETURN_TO_OPENER, true)
                            putExtra(ComposeCalendarViewActivity.EXTRA_OPENER_CLASS, this@ComposeYearActivity::class.java.name)
                        }
                    }
                    .addFlags(
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                    )
            )
            // This screen is left underneath rather than finished: back from the month view then
            // comes back here, which is the way the user arrived, and the return plays the reverse
            // of the way in instead of stepping on to whatever was behind this screen.
            return
        }

        startActivity(
            Intent(this, AllInOneActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                putExtra(Utils.INTENT_KEY_VIEW_TYPE, Utils.INTENT_VALUE_VIEW_TYPE_MONTH)
                putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, millis)
                // Marks this as a destination the user named, not a tap on a grid cell.
                //
                // The month view normally holds still when it is sent to a month other than the one on
                // screen, so that tapping a day belonging to the next month does not move the whole
                // page. A month picked here is the user choosing where to go, and without this the
                // request was treated as one of those taps: the month view returned to where it had
                // been, which is what "it does not jump" looked like.
                putExtra(EXTRA_GOTO_EXPLICIT, true)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
        )
        finish()
    }

    private companion object {
        const val KEY_WEEK_START = "preferences_week_start_day"

        /** Read by AllInOneActivity and passed on as CalendarController.EXTRA_GOTO_EXPLICIT. */
        const val EXTRA_GOTO_EXPLICIT = "com.android.calendar.extra.GOTO_EXPLICIT"

        /** Read by AllInOneActivity as the year the year view should open on. */
        const val EXTRA_INITIAL_YEAR = "com.android.calendar.extra.INITIAL_YEAR"
    }
}
