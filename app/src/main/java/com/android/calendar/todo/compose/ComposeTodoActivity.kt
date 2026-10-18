/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * Host for the Material 3 Expressive to-do screen.
 *
 * A screen of its own, opened from the drawer, from the three dot menu and from a to-do tapped in
 * any of the calendar views. It shows one day at a time, so its heading is a date that can be picked
 * and its today button comes back to today rather than leaving the screen.
 */
package com.android.calendar.todo.compose

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.android.calendar.chrome.DayHeading
import com.android.calendar.chrome.M3eHostActivity
import com.android.calendar.todo.TodoReminder
import ws.xsoh.etar.R
import java.time.LocalDate
import java.time.ZoneId

class ComposeTodoActivity : M3eHostActivity() {

    override val checkedMenuItemId: Int = R.id.action_todo
    override val currentScreenName: String = "TODO"
    /** The heading is a date, so tapping it picks a date. */
    override val pickerChoosesDate: Boolean = true

    /** Today is a day here, as it is in the day view. */
    override val showTodayAction: Boolean = true

    /** Search has no place on a checklist, and it used to lead out of this screen. */
    override val showSearchAction: Boolean = false

    /** The day on screen, as an epoch day. */
    private var day by mutableStateOf(LocalDate.now().toEpochDay())

    /** Bumped on every resume so the screen re-reads after an edit elsewhere. */
    private var refreshKey by mutableStateOf(0)

    /** Bumped by the today button, which brings today back on screen. */
    private var todayKey by mutableStateOf(0)

    /** The day chosen from the heading's picker, brought on screen when it changes. */
    private var pickedDayKey by mutableStateOf(0L)

    /** Bumped by the title bar's edit action, which opens the day's to-do in the editor. */
    private var editRequestKey by mutableStateOf(0)

    /** Whether the day on screen has a to-do, which is what the title bar's edit action needs. */
    private var hasTodo by mutableStateOf(false)

    /**
     * True when the screen was opened straight into the editor.
     *
     * The to-do widget's header asks for this: a tap there means "work on today's to-do", which is
     * adding one when the day has none and editing it when it has. Whether that is an add or an edit
     * is decided from the day's own to-do, so the intent only has to ask for the editor.
     */
    private var openEditor by mutableStateOf(false)

    /**
     * The edit action is offered only while the day on screen has something to edit; the screen
     * reports that as the user pages from day to day, and the menu is rebuilt when it changes.
     */
    override val showTodoEditAction: Boolean get() = hasTodo

    override fun onTodoEdit() {
        editRequestKey++
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Opened on the day the caller asked for - a to-do tapped in another view hands its own day
        // over - or on today.
        val requested = intent.getLongExtra(EXTRA_EPOCH_DAY, Long.MIN_VALUE)
        if (requested != Long.MIN_VALUE) {
            day = requested
        }
        openEditor = intent.getBooleanExtra(EXTRA_OPEN_EDITOR, false)

        // Alarms do not survive a restart; opening this screen is one of the moments to put the
        // reminders that are still to come back on the clock.
        TodoReminder.rescheduleAll(this)

        setM3eContent {
            M3eTodoScreen(
                dayEpoch = day,
                refreshKey = refreshKey,
                todayKey = todayKey,
                pickedDayKey = pickedDayKey,
                editRequestKey = editRequestKey,
                openEditorOnStart = openEditor,
                onDayChanged = { visible ->
                    day = visible.toEpochDay()
                    setDayHeading(visible)
                },
                onHasTodoChanged = { present ->
                    if (present != hasTodo) {
                        hasTodo = present
                        refreshTitleBarMenu()
                    }
                },
            )
        }
        setDayHeading(LocalDate.ofEpochDay(day))
    }

    override fun onResume() {
        super.onResume()
        refreshKey++
        // A screen opened for another day - from a to-do tapped in the calendar - arrives through
        // onNewIntent, which is where the day it asks for is read.
        val requested = intent.getLongExtra(EXTRA_EPOCH_DAY, Long.MIN_VALUE)
        if (requested != Long.MIN_VALUE) {
            day = requested
            setDayHeading(LocalDate.ofEpochDay(requested))
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    override fun onDatePicked(date: LocalDate) {
        day = date.toEpochDay()
        pickedDayKey = date.toEpochDay()
        setDayHeading(date)
    }

    override fun onToday() {
        day = LocalDate.now().toEpochDay()
        todayKey++
        setDayHeading(LocalDate.now())
    }

    /** The day the calendar views open on when the drawer sends the user to one of them. */
    override fun calendarViewDateMillis(): Long? =
        LocalDate.ofEpochDay(day).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun dayTitle(date: LocalDate): String = DayHeading.title(this, date)

    /**
     * The second line of the heading: yesterday / today / tomorrow, the weekday, and the lunar date
     * when that setting is on - the day view's own second line, so this screen reads like it.
     */
    private fun daySubtitle(date: LocalDate): String = DayHeading.subtitle(this, date)

    /** Both heading lines at once, which is what every place that changes the day calls. */
    private fun setDayHeading(date: LocalDate) {
        setPickerTitle(dayTitle(date))
        setPickerSubtitle(daySubtitle(date))
    }

    companion object {
        /** The day the screen should open on, as an epoch day. */
        const val EXTRA_EPOCH_DAY = "m3e_todo_day"

        /** Open straight into the editor for the day: adding when it has no to-do, editing when it has. */
        const val EXTRA_OPEN_EDITOR = "m3e_todo_open_editor"

        fun intent(context: Context, date: LocalDate): Intent =
            Intent(context, ComposeTodoActivity::class.java)
                .putExtra(EXTRA_EPOCH_DAY, date.toEpochDay())
    }
}
