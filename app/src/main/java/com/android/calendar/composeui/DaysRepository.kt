/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * Data layer for the all-Compose day grids.
 *
 * One Instances query serves all three of them: the day, three day and week screens only differ in
 * how many days a page holds, so the window is the same query with a wider range and there is no
 * windowing to do.
 *
 * Instances, not Events: an event is a rule, and the grid needs the occurrences the rule produces
 * inside the window, with the calendar it belongs to already joined for its colour and visibility.
 * Reading Events instead meant a recurring event appeared once, on the day it happens to start.
 */
package com.android.calendar.composeui

import android.content.ContentUris
import android.content.Context
import android.provider.CalendarContract
import android.util.Log

/** One occurrence of an event inside the window that was asked for. */
data class CalendarDayEvent(
    val id: Long,
    val title: String,
    val begin: Long,
    val end: Long,
    val allDay: Boolean,
    val location: String?,
    val calendarId: Long,
    val calendarName: String?,
    val calendarColor: Int,
    val selfAttendeeStatus: Int,
) {
    val isDeclined: Boolean
        get() = selfAttendeeStatus == CalendarContract.Attendees.ATTENDEE_STATUS_DECLINED
}

class DaysRepository(private val context: Context) {

    fun load(fromMillis: Long, toMillis: Long, limit: Int = 500): List<CalendarDayEvent> {
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().apply {
            ContentUris.appendId(this, fromMillis)
            ContentUris.appendId(this, toMillis)
        }.build()

        val projection = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.EVENT_LOCATION,
            CalendarContract.Instances.CALENDAR_ID,
            CalendarContract.Instances.CALENDAR_DISPLAY_NAME,
            CalendarContract.Instances.CALENDAR_COLOR,
            CalendarContract.Instances.SELF_ATTENDEE_STATUS,
        )

        val result = ArrayList<CalendarDayEvent>()
        try {
            context.contentResolver.query(
                uri,
                projection,
                CalendarContract.Instances.VISIBLE + "=1",
                null,
                CalendarContract.Instances.BEGIN + " ASC",
            )?.use { c ->
                while (c.moveToNext() && result.size < limit) {
                    result.add(
                        CalendarDayEvent(
                            id = c.getLong(0),
                            title = c.getString(1).orEmpty(),
                            begin = c.getLong(2),
                            end = c.getLong(3),
                            allDay = c.getInt(4) != 0,
                            location = c.getString(5),
                            calendarId = c.getLong(6),
                            calendarName = c.getString(7),
                            calendarColor = c.getInt(8),
                            selfAttendeeStatus = c.getInt(9),
                        )
                    )
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Could not read the calendar window", t)
        }
        return result
    }

    private companion object {
        const val TAG = "M3eDays"
    }
}
