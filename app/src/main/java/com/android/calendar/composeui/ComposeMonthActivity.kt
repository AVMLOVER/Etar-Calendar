/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * Host for the Material 3 Expressive month view.
 *
 * The drawer and top bar come from M3eHostActivity; the grid is M3eMonthScreen - a weekday header
 * over six rows of seven cells, with a marker per event and the lunar day when that setting is on.
 *
 * The heading names a month, so the picker under it is the app's month picker - the same chip row
 * the year view's month selection and the classic month view's heading use - and the screen moves
 * its own grid to the chosen month. It used to open a date picker: a heading saying "October 2026"
 * asking for a day was a question the screen did not need answered, and the day only served to pick
 * the month it fell in. The pick itself is handed to this screen through the host's onMonthPicked
 * rather than turned into an intent, because this screen *is* the month view - starting another
 * copy of it would stack a screen the user did not ask for.
 *
 * Back leaves the app: this screen is a destination of its own, and the month it lands on was named
 * by the user rather than reached from another view. Nothing is started to "return" anywhere; the
 * host's back callback finishes the screen, which is what goes back to the launcher.
 */
package com.android.calendar.composeui

import android.content.Intent
import android.os.Bundle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.android.calendar.CalendarController
import com.android.calendar.chrome.compose.M3eScreen
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import ws.xsoh.etar.R

class ComposeMonthActivity : ComposeCalendarViewActivity() {

    override val checkedMenuItemId: Int = R.id.month_menu_item
    override val currentScreenName: String = "MONTH"

    /**
     * The month view is one of the views the "Default view" setting names, so a landing month screen
     * checks on resume whether that setting has changed and hands over if it has. See
     * M3eHostActivity.applyConfiguredLandingView.
     */
    override val landingViewType: Int = CalendarController.ViewType.MONTH

    /** The month on screen, for the drawer's other entries and for the anchor-day rule. */
    private var visibleMonth by mutableStateOf(YearMonth.now())

    /** First day of the month on screen, in epoch millis, or 0 before the grid has reported one. */
    private var visibleFirstDayMillis = 0L

    /**
     * The first day of the month chosen from the heading's picker, or 0 before one has been.
     *
     * The grid moves on a day key: a picked day means "show the month this day falls in", so the
     * first of the chosen month is what moves it there - the month itself is the destination.
     */
    private var pickedDay by mutableStateOf(0L)

    /** Bumped on every resume, which is what makes the grid re-read after an edit elsewhere. */
    private var refreshKey by mutableStateOf(0)

    /** Bumped by the today button, which brings today's month back on screen. */
    private var scrollToTodayKey by mutableStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Opened on the month that contains the day the caller named, or the current month.
        val day = requestedDay()

        setM3eContent {
            // Re-read as the screen resumes: the week start is a setting, and changing it has to
            // move the grid's columns rather than wait for the screen to be opened again.
            val firstDayOfWeek = remember(refreshKey) { firstDayOfWeek() }
            M3eMonthScreen(
                page = day.toEpochDay(),
                firstDayOfWeek = firstDayOfWeek,
                onVisibleMonthChanged = { month ->
                    visibleMonth = month
                    visibleFirstDayMillis = month.atDay(1)
                        .atStartOfDay(ZoneId.systemDefault())
                        .toInstant()
                        .toEpochMilli()
                    // The heading is written by the same builder the grid uses for its own, so the
                    // title bar and the month on screen cannot disagree about the year either.
                    setPickerTitle(monthTitleOf(month))
                },
                onOpenDay = { picked -> openDay(picked) },
                onCreateEvent = { start, end -> createEvent(start, end) },
                refreshKey = refreshKey,
                scrollToTodayKey = scrollToTodayKey,
                pickedDayKey = pickedDay,
            )
        }
    }

    override fun onResume() {
        super.onResume()
        refreshKey++
    }

    /**
     * A day named by a new intent, when this screen is reused rather than started afresh.
     *
     * The launcher is what reuses it: tapping the icon brings the running task forward with today,
     * and the month that contains it is what should come back on screen.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        requestedDayFrom(intent)?.let { pickedDay = it.toEpochDay() }
    }

    /**
     * A month chosen from the heading's picker, brought on screen.
     *
     * The choice stays inside this screen. The host's default would open the month view for that
     * month, which is this very screen - starting a second copy of it would stack a screen the user
     * did not ask for and leave the one they were reading underneath. The first of the month is
     * handed to the grid as a picked day, which is the key that moves the pager to that month.
     */
    override fun onMonthPicked(year: Int, month: Int) {
        pickedDay = YearMonth.of(year, month).atDay(1).toEpochDay()
    }

    /** The heading's month picker opens on the month this screen is showing, not on today's. */
    override fun pickerYear(): Int = visibleMonth.year

    /** See [pickerYear]; the picker counts months from 1. */
    override fun pickerMonth(): Int = visibleMonth.monthValue

    override fun onToday() {
        scrollToTodayKey++
    }

    /** Re-reads the month's events now, as a resume does, when the title bar's refresh is used. */
    override fun onRefresh() {
        refreshKey++
    }

    /**
     * How many days the month on screen holds.
     *
     * The host uses this to decide which day a screen opened from here should land on: today while
     * today is part of the month on screen, the first of the month otherwise.
     */
    override val visibleDaySpan: Int get() = visibleMonth.lengthOfMonth()

    /**
     * The day the other views open on when this screen sends the user to one of them.
     *
     * Today while the month on screen is the one today falls in, and the first of the month
     * otherwise. A step from the month grid into the day view is a step into "now" when the user is
     * looking at the current month - the day they are most likely to want - and into the month they
     * are reading when they are not: the 1st of it. It used to be the first of the month in both
     * cases, so going from the current month to the day view always landed on the 1st.
     *
     * Null until the grid has reported a month, which leaves the calendar on its own date.
     */
    override fun calendarViewDateMillis(): Long? {
        if (visibleFirstDayMillis <= 0) {
            return null
        }
        val today = LocalDate.now(ZoneId.systemDefault())
        return if (YearMonth.from(today) == visibleMonth) {
            dayStartMillis(today)
        } else {
            visibleFirstDayMillis
        }
    }

    /**
     * The month and year for the heading.
     *
     * The grid builds the same string for its own title, and it is the only one: a second pattern
     * here would let the title bar and the screen drift into two different wordings of the same
     * month.
     */
    private fun monthTitleOf(month: YearMonth): String = monthTitle(month)
}

