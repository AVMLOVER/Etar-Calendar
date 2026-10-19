/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * Tapping a to-do from one of the other screens.
 *
 * To-dos are all-day events in a calendar of their own, which is what puts them in the all-day strip
 * of the day, three day, week and month views. A tap there is an ordinary view-event request, so the
 * screens that open event details themselves - rather than going through the calendar activity -
 * ask here first whether the event is a to-do.
 */
package com.android.calendar.todo

import android.content.Context
import com.android.calendar.composeui.ComposeCalendarViewActivity
import com.android.calendar.todo.compose.ComposeTodoActivity

/**
 * Opens the to-do screen for the day [eventId] belongs to, and reports whether the event is a to-do.
 *
 * Returns false for an ordinary event, which the caller then opens as it always would.
 */
fun openTodoForEvent(context: Context, eventId: Long): Boolean {
    val date = TodoStore.todoDateForEvent(context, eventId) ?: return false
    // The screen that showed the to-do, when the caller is one: named along with the flag so the
    // to-do screen's own menu entry for it is a step back - the same finish and return slide as the
    // back gesture - rather than a fresh push of that screen over the to-do list.
    val openerClass = (context as? android.app.Activity)?.javaClass?.name
    context.startActivity(
        ComposeTodoActivity.intent(context, date)
            // Opened from the screen that showed the to-do, so back returns to that screen -
            // finishing here and letting the platform reveal it, the reverse of the way in. Without
            // this, back stepped to a freshly started month view, which plays the forward slide, so
            // the return went left a second time.
            .putExtra(ComposeCalendarViewActivity.EXTRA_RETURN_TO_OPENER, true)
            .apply {
                if (openerClass != null) {
                    putExtra(ComposeCalendarViewActivity.EXTRA_OPENER_CLASS, openerClass)
                }
            }
    )
    return true
}
