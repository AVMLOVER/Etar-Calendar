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
import com.android.calendar.todo.compose.ComposeTodoActivity

/**
 * Opens the to-do screen for the day [eventId] belongs to, and reports whether the event is a to-do.
 *
 * Returns false for an ordinary event, which the caller then opens as it always would.
 */
fun openTodoForEvent(context: Context, eventId: Long): Boolean {
    val date = TodoStore.todoDateForEvent(context, eventId) ?: return false
    context.startActivity(ComposeTodoActivity.intent(context, date))
    return true
}
