/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * Host for the Material 3 Expressive agenda screen.
 *
 * It is a standalone Compose screen hosted in the calendar's own drawer and top bar rather than a
 * rewrite of the in-place AgendaWindowAdapter, so the classic agenda stays intact.
 */
package com.android.calendar.agenda.compose

import android.content.Intent
import android.os.Bundle
import android.provider.CalendarContract
import com.android.calendar.CalendarController
import com.android.calendar.chrome.M3eHostActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.time.Instant
import java.time.ZoneId
import ws.xsoh.etar.R

class ComposeAgendaActivity : M3eHostActivity() {

    override val checkedMenuItemId: Int = R.id.agenda_menu_item
    override val currentScreenName: String = "AGENDA"

    /**
     * The agenda is one of the views the "Default view" setting names, so a landing agenda screen
     * checks on resume whether that setting has changed and hands over if it has. See
     * M3eHostActivity.applyConfiguredLandingView.
     *
     * This says nothing about how the screen stacks: it is still a tool screen rather than one of
     * the drawer's calendar views, so nothing is ever revealed over it and it keeps its own place
     * under whatever it opens. When it is the landing screen and the setting still names Agenda the
     * comparison matches, so it never routes to itself.
     */
    override val landingViewType: Int = CalendarController.ViewType.AGENDA

    /**
     * Bumped every time the screen is resumed, which is what makes the list re-read itself after
     * coming back from the editor or the details screen. Relying on the provider's change
     * notification alone left an edited event showing its old text until the screen was reopened.
     */
    private var refreshKey by mutableStateOf(0)

    /**
     * The heading and "go to" choose a day, so the loaded window moves to it.
     *
     * The screen is not a pager: it loads a window of days starting at one date, so a chosen date is
     * the start of the window - the same thing the previous/next 90 days buttons change. A month
     * picker, which the host would otherwise give this screen, could not do that: its answer was a
     * month, and the only way to show one is to leave for the month view.
     */
    override val pickerChoosesDate: Boolean = true

    /** The day chosen from the picker, in epoch days, or 0 before one has been. */
    private var jumpedDay by mutableStateOf(0L)

    override fun onDatePicked(date: java.time.LocalDate) {
        // Nothing is started: this screen is already the one showing the events.
        jumpedDay = date.toEpochDay()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // The day the list is centred on, handed over by the view the agenda was opened from.
        // Nothing arrives when there is no view behind it, and the screen then centres on today.
        val initialDate = intent
            .getLongExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, 0L)
            .takeIf { it > 0L }
            ?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate() }

        setM3eContent {
            M3eAgendaScreen(
                initialDate = initialDate,
                onOpenEvent = { event -> openEvent(event) },
                onDeleteEvent = { event -> deleteEvent(event) },
                onReminderChanged = { event, minutes -> setReminder(event, minutes) },
                refreshKey = refreshKey,
                jumpToDayKey = jumpedDay,
            )
        }
    }

    override fun onResume() {
        super.onResume()
        refreshKey++
    }

    /** Re-reads the loaded window now, as a resume does, when the title bar's refresh is used. */
    override fun onRefresh() {
        refreshKey++
    }

    /**
     * Writes a new notification lead time for one event.
     *
     * The list is made to re-read afterwards, because the row shows the reminder it was given rather
     * than the one the provider reports, and the two must not drift apart.
     */
    private fun setReminder(event: AgendaEvent, minutes: List<Int>) {
        val saved = AgendaRepository(applicationContext).setReminderMinutes(event.id, minutes)
        if (!saved) {
            android.widget.Toast.makeText(
                this,
                R.string.m3e_reminder_save_failed,
                android.widget.Toast.LENGTH_SHORT,
            ).show()
        }
        refreshKey++
    }

    /** Deletes the event a row was swiped away. The list has already hidden the row. */
    private fun deleteEvent(event: AgendaEvent) {
        val deleted = AgendaRepository(applicationContext).delete(this, event.id)
        if (!deleted) {
            android.widget.Toast.makeText(
                this,
                R.string.m3e_event_delete_failed,
                android.widget.Toast.LENGTH_SHORT,
            ).show()
            // The row was hidden optimistically; put it back by making the list re-read.
            refreshKey++
        }
    }

    /** Opens the Material 3 Expressive details screen, which in turn can hand off to the editor. */
    private fun openEvent(event: AgendaEvent) {
        // A to-do is listed here like any other all-day event, but it opens its own screen: it is a
        // checklist, not an event with guests and a location.
        if (com.android.calendar.todo.openTodoForEvent(this, event.id)) {
            return
        }
        startActivity(
            Intent(this, com.android.calendar.event.compose.ComposeEventDetailsActivity::class.java)
                .putExtra(
                    com.android.calendar.event.compose.ComposeEventDetailsActivity.EXTRA_EVENT_ID,
                    event.id,
                )
        )
    }
}
