/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * Host for the Material 3 Expressive three day view.
 *
 * The drawer and top bar come from M3eHostActivity, which uses the same layouts and menus the
 * calendar itself does; the three day grid is the only part written here.
 */
package com.android.calendar.threeday.compose

import android.content.Intent
import android.os.Bundle
import android.provider.CalendarContract
import com.android.calendar.AllInOneActivity
import com.android.calendar.CalendarController
import com.android.calendar.Utils
import com.android.calendar.chrome.M3eHostActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.time.LocalDate
import java.time.ZoneId
import ws.xsoh.etar.R

class ComposeThreeDayActivity : M3eHostActivity() {

    override val checkedMenuItemId: Int = R.id.threeday_menu_item

    /**
     * The three day view is one of the drawer's calendar views, so it moves through the same rule
     * as the day, week and month screens: it opens over the landing screen and replaces a screen
     * that was itself opened over one, which keeps the stack two deep. See M3eHostActivity's
     * isCalendarViewScreen and the calendarViewStep rule.
     */
    override val isCalendarViewScreen: Boolean = true

    /**
     * The three day view is one of the views the "Default view" setting names, so a landing three
     * day screen checks on resume whether that setting has changed and hands over if it has. As
     * with the other views, a three day screen that *is* the configured default is the landing
     * screen wherever it is opened: back from it leaves the app. See
     * M3eHostActivity.applyConfiguredLandingView and isEffectivelyLanding.
     */
    override val landingViewType: Int = CalendarController.ViewType.THREE_DAY

    /**
     * The name the drawer knows this screen by.
     *
     * The drawer uses it to recognise the screen it is already on, so choosing "3 days" while this
     * screen is on top closes the drawer instead of launching the screen again - a relaunch arrived
     * here as onNewIntent with a fresh day, which moved the pager off the block the user was reading.
     */
    override val currentScreenName: String = "THREE_DAY"

    /**
     * The heading is a range of days, so the picker under it chooses a day.
     *
     * It chose a month until now, by inheriting the host's month picker - and choosing a month
     * left this screen for the month view, which is not what a heading showing "13-15 Okt." is
     * offering to do. See M3eHostActivity.pickerChoosesDate.
     */
    override val pickerChoosesDate: Boolean = true

    /** The day chosen from the heading's picker, or 0 before one has been. */
    private var pickedDay by androidx.compose.runtime.mutableStateOf(0L)

    /**
     * First day of the three day block on screen, in epoch millis, or 0 before the screen has
     * reported one.
     *
     * Read through calendarViewDateMillis() when the drawer opens the day, week or month view, so
     * those open on the days this screen is showing instead of today.
     */
    private var visibleFirstDayMillis = 0L

    override fun onDatePicked(date: java.time.LocalDate) {
        // The screen moves to the block holding the day; nothing is started, because this screen
        // is already the one on top.
        pickedDay = date.toEpochDay()
    }

    /**
     * Bumped on every resume, which is what makes the grid re-read after returning from the editor
     * that created or deleted an event.
     */
    private var refreshKey by mutableStateOf(0)

    /** Bumped by the today button, which brings today's block on screen.
     *
     * The three day view shows dates, so "today" means today here rather than "go back to the
     * month view" - the button used to leave the screen, which is not what it says.
     */
    private var scrollToTodayKey by mutableStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // The host may be opened either from the drawer (today) or from a date handed over in the
        // intent, so the starting three day block follows whatever day was asked for.
        //
        // That date is how the week and day views say "show the three days that contain this one":
        // the grid centres its pager on the three day block holding the day, and a block is three
        // days counted from the epoch, so the day is one of the three shown however it aligns. No
        // extra is needed to name the block - only the day the user actually tapped.
        val requestedMillis = intent.getLongExtra(
            CalendarContract.EXTRA_EVENT_BEGIN_TIME,
            System.currentTimeMillis(),
        )
        val day = LocalDate.ofInstant(
            java.time.Instant.ofEpochMilli(requestedMillis),
            ZoneId.systemDefault(),
        )

        setM3eContent {
            M3eThreeDayScreen(
                page = day.toEpochDay(),
                onVisibleRangeChanged = { first ->
                    // Kept for the drawer: the day, week and month entries hand this day to the
                    // calendar, so they open where the three day view is, not on today.
                    visibleFirstDayMillis = first
                        .atStartOfDay(ZoneId.systemDefault())
                        .toInstant()
                        .toEpochMilli()
                    setPickerTitle(rangeTitleOf(first))
                },
                onOpenEvent = { event -> openEvent(event.id) },
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
     * A day handed over by a new intent, when this screen is reused rather than started afresh.
     *
     * The view that opens it starts a new instance, so this is reached when something brings the
     * running task back - the launcher - and the day it names is turned into a move through
     * pickedDay, the same route the heading's date picker uses. The block that contains that day
     * comes on screen instead of the block the user was on last time.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val millis = intent.getLongExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, 0L)
        if (millis > 0) {
            pickedDay = LocalDate.ofInstant(
                java.time.Instant.ofEpochMilli(millis),
                ZoneId.systemDefault(),
            ).toEpochDay()
        }
    }

    override fun onToday() {
        scrollToTodayKey++
    }

    /** Re-reads the block's events now, as a resume does, when the title bar's refresh is used. */
    override fun onRefresh() {
        refreshKey++
    }

    /**
     * The three days on screen: three, so a screen opened from here - the to-do list - lands on today
     * while today is one of them and on the first day of the block otherwise.
     */
    override val visibleDaySpan: Int = 3

    /**
     * The day the calendar views open on when this screen sends the user to one of them: the first
     * day of the three day block on screen. Null until the screen has reported a block, which
     * leaves the calendar on its own date.
     */
    override fun calendarViewDateMillis(): Long? =
        if (visibleFirstDayMillis > 0) visibleFirstDayMillis else null

    /**
     * Tapping an empty slot in the grid opens the Material 3 editor on a new event at that time.
     *
     * The intent is built by CalendarController so a new event started here carries exactly the
     * extras the editor expects, rather than a second set invented at this call site.
     */
    private fun createEvent(start: Long, end: Long) {
        startActivity(
            CalendarController.getInstance(this).generateCreateEventIntent(
                start,
                end,
                false,
                null,
                -1L,
            )
        )
    }

    /** Opens the Material 3 Expressive details screen, which can hand off to the editor. */
    private fun openEvent(eventId: Long) {
        // A to-do sits in the all-day strip like any other all-day event; tapping it opens the to-do
        // screen for its day rather than the event details.
        if (com.android.calendar.todo.openTodoForEvent(this, eventId)) {
            return
        }
        startActivity(
            Intent(this, com.android.calendar.event.compose.ComposeEventDetailsActivity::class.java)
                .putExtra(
                    com.android.calendar.event.compose.ComposeEventDetailsActivity.EXTRA_EVENT_ID,
                    eventId,
                )
        )
    }

    /** Opens the month view on the given day, reusing the running calendar activity. */
    @Suppress("unused")
    private fun openMonth(day: LocalDate) {
        val millis = day.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        startActivity(
            Intent(this, AllInOneActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                putExtra(Utils.INTENT_KEY_VIEW_TYPE, Utils.INTENT_VALUE_VIEW_TYPE_MONTH)
                putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, millis)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
        )
    }
}
