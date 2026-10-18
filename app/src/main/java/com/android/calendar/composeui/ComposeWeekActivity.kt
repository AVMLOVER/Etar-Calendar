/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * Host for the Material 3 Expressive week view.
 *
 * The drawer and top bar come from M3eHostActivity; the grid is the shared one from M3eDaysScreen,
 * asked for seven days. A week page starts on the week's first day - the app's own week start
 * setting, not a fixed weekday - so the columns are the week the user means by "this week".
 *
 * The heading is a range of days, like the three day view's, so the picker under it chooses a day
 * and the screen moves to the week that contains it.
 */
package com.android.calendar.composeui

import android.content.Intent
import android.os.Bundle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.android.calendar.chrome.compose.M3eScreen
import com.android.calendar.threeday.compose.rangeTitleOf
import java.time.LocalDate
import ws.xsoh.etar.R

class ComposeWeekActivity : ComposeCalendarViewActivity() {

    override val checkedMenuItemId: Int = R.id.week_menu_item
    override val currentScreenName: String = "WEEK"

    /** The heading is a range of days, so the picker under it chooses a day. */
    override val pickerChoosesDate: Boolean = true

    /** The day chosen from the heading's picker, or 0 before one has been. */
    private var pickedDay by mutableStateOf(0L)

    /** First day of the week on screen, in epoch millis, or 0 before the grid has reported one. */
    private var visibleFirstDayMillis = 0L

    /** Bumped on every resume, which is what makes the grid re-read after an edit elsewhere. */
    private var refreshKey by mutableStateOf(0)

    /** Bumped by the today button, which brings today's week back on screen. */
    private var scrollToTodayKey by mutableStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Opened on the week that contains the day the caller named, or today's week.
        val day = requestedDay()

        setM3eContent {
            // Re-read as the screen resumes: the week start is a setting, and changing it has to move
            // the columns to the new week rather than wait for the screen to be opened again.
            val firstDayOfWeek = remember(refreshKey) { firstDayOfWeek() }
            M3eDaysScreen(
                days = 7,
                page = day.toEpochDay(),
                onVisibleRangeChanged = { first ->
                    // Kept for the drawer: the day, three day and month entries hand this day on, so
                    // they open where this screen is rather than on today.
                    visibleFirstDayMillis = dayStartMillis(first)
                    setPickerTitle(rangeTitleOf(first, first.plusDays(6)))
                },
                onOpenEvent = { event -> openEvent(event.id) },
                onCreateEvent = { start, end -> createEvent(start, end) },
                selectedScreen = M3eScreen.WEEK,
                firstDayOfWeek = firstDayOfWeek,
                // A day tapped in the header opens the three day view on the block that contains it:
                // the week says which days are busy, and the three day view is where one of them is
                // read in detail.
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
    }

    /** A day named by a new intent, for a copy that is brought forward instead of started afresh. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val requested = requestedDayFrom(intent)
        if (requested != null) {
            pickedDay = requested.toEpochDay()
            // The launcher's own intent names today, which is also what the title bar's today button
            // means. The picked-day key alone would not move the screen when it already held today's
            // value - the key would not change - so re-entering the app after paging away would stay
            // on the week the user had swiped to instead of coming back to today's.
            if (requested == LocalDate.now()) {
                scrollToTodayKey++
            }
        }
    }

    override fun onDatePicked(date: LocalDate) {
        pickedDay = date.toEpochDay()
    }

    override fun onToday() {
        scrollToTodayKey++
    }

    /**
     * The week on screen is seven days, so a screen opened from here - the three day view, the to-do
     * list - lands on today while today is one of them and on the first day of the week otherwise.
     */
    override val visibleDaySpan: Int = 7

    /**
     * The day the calendar views open on when this screen sends the user to one of them: the first
     * day of the week on screen. Null until the grid has reported one.
     */
    override fun calendarViewDateMillis(): Long? =
        if (visibleFirstDayMillis > 0) visibleFirstDayMillis else null
}
