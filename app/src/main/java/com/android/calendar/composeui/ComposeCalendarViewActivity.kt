/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The pieces the three all-Compose calendar views share.
 *
 * The day, week and month screens are separate activities - each is a destination the drawer knows
 * about, and each has its own heading and paging - but they open events, create events and hand days
 * to one another in exactly the same way. Writing that three times would have meant three copies of
 * the to-do interception, and a to-do tapped in one view would have opened its details in the others.
 */
package com.android.calendar.composeui

import android.content.Intent
import android.provider.CalendarContract
import com.android.calendar.CalendarController
import com.android.calendar.Utils
import com.android.calendar.chrome.M3eHostActivity
import com.android.calendar.event.compose.ComposeEventDetailsActivity
import com.android.calendar.threeday.compose.ComposeThreeDayActivity
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

abstract class ComposeCalendarViewActivity : M3eHostActivity() {

    /** The day handed over in the intent, or today when the caller named none. */
    protected fun requestedDay(): LocalDate =
        requestedDayFrom(intent) ?: LocalDate.now(ZoneId.systemDefault())

    /**
     * The day a later intent asks for, or null when it names none.
     *
     * A screen that is brought forward with REORDER_TO_FRONT keeps its pager where it was, so a day
     * named in the new intent has to be turned into a visible move by the screen itself. That is
     * what the picked-day key is for.
     */
    protected fun requestedDayFrom(intent: Intent): LocalDate? {
        val millis = intent.getLongExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, 0L)
        if (millis <= 0) {
            return null
        }
        return Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
    }

    /**
     * First day of the week, from the app's own week start setting.
     *
     * The preference stores the platform's day numbers, where Sunday is 1; Utils turns that into
     * the Time class's numbering, where Sunday is 0 - the same conversion the calendar activity
     * makes for its own month grid.
     */
    protected fun firstDayOfWeek(): DayOfWeek {
        val timeDay = Utils.getFirstDayOfWeek(this)
        return if (timeDay == android.text.format.Time.SUNDAY) {
            DayOfWeek.SUNDAY
        } else {
            DayOfWeek.of(timeDay)
        }
    }

    /** Midnight at the start of [day], in the device's zone, as epoch millis. */
    protected fun dayStartMillis(day: LocalDate): Long =
        day.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    /**
     * Tapping an empty slot in a grid opens the Material 3 editor on a new event at that time.
     *
     * The intent is built by CalendarController so a new event started here carries exactly the
     * extras the editor expects, rather than a second set invented at this call site.
     */
    protected fun createEvent(start: Long, end: Long) {
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
    protected fun openEvent(eventId: Long) {
        // A to-do sits in the all-day strip like any other all-day event; tapping it opens the to-do
        // screen for its day rather than the event details. Checked here, in the one place all three
        // calendar views go through, so the three of them behave the same.
        if (com.android.calendar.todo.openTodoForEvent(this, eventId)) {
            return
        }
        startActivity(
            Intent(this, ComposeEventDetailsActivity::class.java)
                .putExtra(ComposeEventDetailsActivity.EXTRA_EVENT_ID, eventId)
        )
    }

    /**
     * Opens the three day view on the block that contains [day].
     *
     * The day travels as EXTRA_EVENT_BEGIN_TIME, which is the extra the three day screen already
     * reads: it centres its pager on the block holding that day, so the three days shown are the
     * ones the tapped day belongs to. SINGLE_TOP and REORDER_TO_FRONT bring an instance that is
     * already running forward instead of stacking a second copy; the day is re-read there from
     * onNewIntent, because a reused instance keeps its pager where it was.
     *
     * This screen is not finished: back from the three day view returns to the week or the day the
     * user came from, which is where they were. That is what [EXTRA_RETURN_TO_OPENER] tells it.
     */
    protected fun openThreeDay(day: LocalDate) {
        startActivity(
            Intent(this, ComposeThreeDayActivity::class.java)
                .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, dayStartMillis(day))
                // Back from the three day view returns to this screen rather than stepping on to
                // the month view. Opened from the drawer there is no screen of the sequence to
                // return to, and the three day view keeps its own back - which is why this is an
                // extra rather than a change to that screen.
                .putExtra(EXTRA_RETURN_TO_OPENER, true)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        )
    }

    /**
     * Opens the day view on [day], the same way and with the same flags as [openThreeDay].
     *
     * The month view opens a day over itself rather than switching to it, so back has to finish the
     * day view and reveal the month where it was left. Without this extra the day view stepped on to
     * the month view instead - starting it at the tapped day, which moved a month grid the user had
     * not asked to move whenever the tapped day belonged to the previous or next month.
     */
    protected fun openDay(day: LocalDate) {
        startActivity(
            Intent(this, ComposeDayActivity::class.java)
                .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, dayStartMillis(day))
                .putExtra(EXTRA_RETURN_TO_OPENER, true)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        )
    }

    companion object {
        /**
         * Set on an intent that opens a screen over one that stays underneath it.
         *
         * The calendar views of the sequence - day, three day, week - replace one another when they
         * are reached from the drawer: the screen behind is finished and back steps on to the month
         * view. The month and the week also open the day and three day views *over* themselves, and
         * back there has to return to the screen that opened them rather than start another one: it
         * is revealed exactly as it was left, pager included.
         */
        const val EXTRA_RETURN_TO_OPENER = "m3eReturnToOpener"
    }
}
