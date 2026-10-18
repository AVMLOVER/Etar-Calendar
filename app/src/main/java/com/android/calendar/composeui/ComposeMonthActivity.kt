/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * Host for the Material 3 Expressive month view.
 *
 * The drawer and top bar come from M3eHostActivity; the grid is M3eMonthScreen - a weekday header
 * over six rows of seven cells, with a marker per event and the lunar day when that setting is on.
 *
 * The heading is a month, so the picker under it chooses a day and the screen moves to the month
 * that contains it. It cannot use the host's own month picker: that one jumps to the month view by
 * starting the calendar activity, and this screen is the month view - it has to move itself.
 *
 * Back leaves the app. This is the view the day, three day and week screens step back to, so there
 * is no further screen of the sequence behind it; the platform's own back - the one that carries the
 * predictive animation - is handed the gesture rather than being intercepted to start another view.
 */
package com.android.calendar.composeui

import android.content.Intent
import android.os.Bundle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.android.calendar.chrome.compose.M3eScreen
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import ws.xsoh.etar.R

class ComposeMonthActivity : ComposeCalendarViewActivity() {

    override val checkedMenuItemId: Int = R.id.month_menu_item
    override val currentScreenName: String = "MONTH"

    /** Back returns to whatever opened this screen instead of stepping to another calendar view. */
    override val exitOnBack: Boolean = false

    /** The heading is a month, so tapping it asks for a day and moves to that day's month. */
    override val pickerChoosesDate: Boolean = true

    /** The month on screen, for the drawer's other entries and for the anchor-day rule. */
    private var visibleMonth by mutableStateOf(YearMonth.now())

    /** First day of the month on screen, in epoch millis, or 0 before the grid has reported one. */
    private var visibleFirstDayMillis = 0L

    /** The day chosen from the heading's picker, or 0 before one has been. */
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

    /** A day named by a new intent, for a copy that is brought forward instead of started afresh. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        requestedDayFrom(intent)?.let { pickedDay = it.toEpochDay() }
    }

    override fun onDatePicked(date: LocalDate) {
        pickedDay = date.toEpochDay()
    }

    override fun onToday() {
        scrollToTodayKey++
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

