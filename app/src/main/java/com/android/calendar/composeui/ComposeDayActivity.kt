/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * Host for the Material 3 Expressive day view.
 *
 * The drawer and top bar come from M3eHostActivity, which uses the same layouts and menus the
 * calendar itself does; the day grid is the shared one from M3eDaysScreen, asked for a single day.
 *
 * The heading is a date, as the day view's own heading is - yesterday / today / tomorrow, the
 * weekday and the lunar date on the second line - so a day here reads the same as a day anywhere
 * else in the app.
 */
package com.android.calendar.composeui

import android.content.Intent
import android.os.Bundle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.android.calendar.CalendarController
import com.android.calendar.chrome.DayHeading
import com.android.calendar.chrome.compose.M3eScreen
import java.time.LocalDate
import ws.xsoh.etar.R

class ComposeDayActivity : ComposeCalendarViewActivity() {

    override val checkedMenuItemId: Int = R.id.day_menu_item
    override val currentScreenName: String = "DAY"

    /**
     * The day view is one of the views the "Default view" setting names, so a landing day screen
     * checks on resume whether that setting has changed and hands over if it has. See
     * M3eHostActivity.applyConfiguredLandingView.
     */
    override val landingViewType: Int = CalendarController.ViewType.DAY

    /**
     * The heading is a date, so the picker under it chooses a day.
     *
     * The host's default picker chooses a month, and on this screen that would leave for the month
     * view - which is not what a heading showing a single date is offering to do.
     */
    override val pickerChoosesDate: Boolean = true

    /** The day chosen from the heading's picker, or 0 before one has been. */
    private var pickedDay by mutableStateOf(0L)

    /** The day on screen, and the millis the drawer's other entries hand on. */
    private var visibleDayMillis = 0L

    /** Bumped on every resume, which is what makes the grid re-read after an edit elsewhere. */
    private var refreshKey by mutableStateOf(0)

    /** Bumped by the today button, which brings today back on screen. */
    private var scrollToTodayKey by mutableStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Opened on the day the caller asked for, or on today. A day tapped in the month view hands
        // its own day over, so the day view opens where the user was looking.
        val day = requestedDay()

        setM3eContent {
            M3eDaysScreen(
                days = 1,
                page = day.toEpochDay(),
                onVisibleRangeChanged = { first ->
                    visibleDayMillis = dayStartMillis(first)
                    setPickerTitle(DayHeading.title(this@ComposeDayActivity, first))
                    setPickerSubtitle(DayHeading.subtitle(this@ComposeDayActivity, first))
                },
                onOpenEvent = { event -> openEvent(event.id) },
                onCreateEvent = { start, end -> createEvent(start, end) },
                selectedScreen = M3eScreen.DAY,
                // The header is the way out of a single day: tapping it opens the three day view on
                // the block that contains the day, which is how the user sees the days around it.
                onOpenDay = { picked -> openThreeDay(picked) },
                refreshKey = refreshKey,
                scrollToTodayKey = scrollToTodayKey,
                pickedDayKey = pickedDay,
            )
        }
    }

    override fun onResume() {
        super.onResume()
        refreshKey++
        // The heading's second line carries the lunar date when that setting is on, and the setting
        // has no change notification: rebuilding both lines as the screen resumes is what makes a
        // change in the settings show up without leaving the day view. The day on screen is read
        // from the grid's last report, and falls back to the requested day before it has one.
        val day = if (visibleDayMillis > 0) {
            java.time.Instant.ofEpochMilli(visibleDayMillis)
                .atZone(java.time.ZoneId.systemDefault())
                .toLocalDate()
        } else {
            requestedDay()
        }
        setPickerTitle(DayHeading.title(this, day))
        setPickerSubtitle(DayHeading.subtitle(this, day))
    }

    /**
     * A day named by a new intent, when this screen is reused rather than started afresh.
     *
     * The launcher is what reuses it: tapping the icon brings the running task forward with today,
     * and the day has to be read from that intent or the screen would stay on the day it was left
     * on. The drawer no longer comes through here - it starts or reveals screens instead of
     * reordering a running one - so today is the day that arrives in practice.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        requestedDayFrom(intent)?.let { pickedDay = it.toEpochDay() }
    }

    override fun onDatePicked(date: LocalDate) {
        // The screen moves to the day; nothing is started, because this screen is already the one
        // on top.
        pickedDay = date.toEpochDay()
    }

    override fun onToday() {
        scrollToTodayKey++
    }

    /** Re-reads the day's events now, as a resume does, when the title bar's refresh is used. */
    override fun onRefresh() {
        refreshKey++
    }

    /**
     * The day the calendar views open on when the drawer sends the user to one of them: the day on
     * screen. Null until the grid has reported one, which leaves the calendar on its own date.
     */
    override fun calendarViewDateMillis(): Long? =
        if (visibleDayMillis > 0) visibleDayMillis else null
}
