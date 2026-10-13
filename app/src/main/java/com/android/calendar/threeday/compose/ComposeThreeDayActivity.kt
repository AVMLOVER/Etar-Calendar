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
    override val currentScreenName: String = "THREEDAY"

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
        val day = LocalDate.ofInstant(
            java.time.Instant.ofEpochMilli(
                intent.getLongExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, System.currentTimeMillis())
            ),
            ZoneId.systemDefault(),
        )

        setM3eContent {
            M3eThreeDayScreen(
                page = day.toEpochDay(),
                onVisibleRangeChanged = { first ->
                    setPickerTitle(rangeTitle(first))
                },
                onOpenEvent = { event -> openEvent(event.id) },
                onCreateEvent = { start, end -> createEvent(start, end) },
                refreshKey = refreshKey,
                scrollToTodayKey = scrollToTodayKey,
            )
        }
    }

    override fun onResume() {
        super.onResume()
        refreshKey++
    }

    override fun onToday() {
        scrollToTodayKey++
    }

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

    /**
     * "10月13–15日" within one month, "10月30日 – 11月1日" across two.
     *
     * The month is not repeated when the range stays inside it: the title sits beside the search and
     * today icons, and the longer form left no room for them on a narrow screen.
     */
    private fun rangeTitle(first: LocalDate): String {
        val last = first.plusDays(2)
        return if (first.monthValue == last.monthValue && first.year == last.year) {
            first.monthValue.toString() + "月" + first.dayOfMonth + "–" + last.dayOfMonth + "日"
        } else {
            first.monthValue.toString() + "月" + first.dayOfMonth + "日–" +
                last.monthValue.toString() + "月" + last.dayOfMonth + "日"
        }
    }

    /** Opens the Material 3 Expressive details screen, which can hand off to the editor. */
    private fun openEvent(eventId: Long) {
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
