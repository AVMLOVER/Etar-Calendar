/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * Data layer for the Material 3 Expressive three day screen.
 *
 * This is deliberately just the day range of AgendaRepository: the window is three days wide, so
 * the same Instances query is enough and there is no windowing to do.
 */
package com.android.calendar.threeday.compose

import android.content.ContentUris
import android.content.Context
import android.provider.CalendarContract
import android.util.Log

/** One occurrence of an event inside the three day window. */
data class ThreeDayEvent(
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

class ThreeDayRepository(private val context: Context) {

    fun load(fromMillis: Long, toMillis: Long, limit: Int = 500): List<ThreeDayEvent> {
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

        val result = ArrayList<ThreeDayEvent>()
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
                        ThreeDayEvent(
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
            Log.w(TAG, "Could not read the three day window", t)
        }
        return result
    }

    private companion object {
        const val TAG = "M3eThreeDay"
    }
}
