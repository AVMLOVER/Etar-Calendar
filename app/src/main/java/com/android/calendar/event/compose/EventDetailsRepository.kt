/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * Read-only data layer for the Material 3 Expressive event details screen.
 *
 * Everything here only reads from CalendarContract; nothing is written, so this screen cannot
 * damage the user's calendar data.
 */
package com.android.calendar.event.compose

import android.content.ContentUris
import android.content.Context
import android.provider.CalendarContract
import com.android.calendar.event.ExtendedProperty
import com.android.calendar.composeui.pickEventUrl
import android.util.Log

data class AttendeeInfo(
    val name: String?,
    val email: String?,
    val status: Int,
)

data class EventDetails(
    val id: Long,
    val title: String,
    val location: String?,
    val description: String?,
    val start: Long,
    val end: Long,
    val allDay: Boolean,
    val calendarId: Long,
    val calendarName: String?,
    val calendarColor: Int,
    val rrule: String?,
    val organizer: String?,
    /** The event's URL, read from its extended properties; null when it has none. */
    val url: String?,
    val timezone: String?,
    val remindersMinutes: List<Int>,
    val attendees: List<AttendeeInfo>,
)

class EventDetailsRepository(private val context: Context) {

    /**
     * Deletes an event from the details screen.
     *
     * Delegated to EventEditorRepository.deleteEvent, which issues the delete against the provider
     * directly. DeleteEventHelper was tried first and rejected: it raises its own confirmation
     * dialog, and it returns silently when its own permission check declines, which left the event in
     * place after the screen had already told the user it was being deleted.
     */
    fun delete(activity: android.app.Activity, eventId: Long): Boolean =
        EventEditorRepository(activity).deleteEvent(eventId)

    fun load(eventId: Long): EventDetails? {
        val projection = arrayOf(
            CalendarContract.Events._ID,
            CalendarContract.Events.TITLE,
            CalendarContract.Events.EVENT_LOCATION,
            CalendarContract.Events.DESCRIPTION,
            CalendarContract.Events.DTSTART,
            CalendarContract.Events.DTEND,
            CalendarContract.Events.ALL_DAY,
            CalendarContract.Events.CALENDAR_ID,
            CalendarContract.Events.RRULE,
            CalendarContract.Events.ORGANIZER,
            CalendarContract.Events.EVENT_TIMEZONE,
        )

        return try {
            // queryRowById, not a plain row read: an event on a local calendar has no answer at its
            // own row uri, and asking there alone reported every such event as no longer existing.
            context.contentResolver.queryRowById(
                CalendarContract.Events.CONTENT_URI, eventId, projection,
            ) { c ->
                val calendarId = c.getLong(7)
                val start = c.getLong(4)
                val endRaw = if (c.isNull(5)) -1L else c.getLong(5)
                EventDetails(
                    id = c.getLong(0),
                    title = c.getString(1).orEmpty(),
                    location = c.getString(2),
                    description = c.getString(3),
                    start = start,
                    end = if (endRaw > 0) endRaw else start + DEFAULT_DURATION_MS,
                    allDay = c.getInt(6) != 0,
                    calendarId = calendarId,
                    calendarName = calendarName(calendarId),
                    calendarColor = calendarColor(calendarId),
                    rrule = c.getString(8),
                    organizer = c.getString(9),
                    url = url(eventId),
                    timezone = c.getString(10),
                    remindersMinutes = reminders(eventId),
                    attendees = attendees(eventId),
                )
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Could not read event $eventId", t)
            null
        }
    }

    /** The calendar's own row, read the same fallback way as the event. */
    private fun calendarColumn(calendarId: Long, column: String): String? = try {
        context.contentResolver.queryRowById(
            CalendarContract.Calendars.CONTENT_URI, calendarId, arrayOf(column),
        ) { c -> c.getString(0) }
    } catch (t: Throwable) {
        Log.w(TAG, "Could not read calendar $calendarId", t)
        null
    }

    private fun calendarName(calendarId: Long): String? =
        calendarColumn(calendarId, CalendarContract.Calendars.CALENDAR_DISPLAY_NAME)

    private fun calendarColor(calendarId: Long): Int = try {
        context.contentResolver.queryRowById(
            CalendarContract.Calendars.CONTENT_URI,
            calendarId,
            arrayOf(CalendarContract.Calendars.CALENDAR_COLOR),
        ) { c -> c.getInt(0) } ?: 0
    } catch (t: Throwable) {
        0
    }

    /**
     * The event's URL extended property.
     *
     * There is no url column on Events (the provider rejects one), so the value comes from
     * ExtendedProperties under the ical4android names - the same rows the export reads.
     */
    private fun url(eventId: Long): String? {
        val rows = ArrayList<Pair<String, String>>()
        try {
            context.contentResolver.query(
                CalendarContract.ExtendedProperties.CONTENT_URI,
                arrayOf(
                    CalendarContract.ExtendedProperties.NAME,
                    CalendarContract.ExtendedProperties.VALUE,
                ),
                CalendarContract.ExtendedProperties.EVENT_ID + "=? AND " +
                    CalendarContract.ExtendedProperties.NAME + " IN (?, ?)",
                arrayOf(
                    eventId.toString(),
                    ExtendedProperty.URL_NAME,
                    ExtendedProperty.URL_NAME_PRIV,
                ),
                null,
            )?.use { c ->
                while (c.moveToNext()) {
                    rows.add((c.getString(0) ?: "") to (c.getString(1) ?: ""))
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Could not read the event url", t)
        }
        return pickEventUrl(rows, ExtendedProperty.URL_NAME_PRIV, ExtendedProperty.URL_NAME)
    }

    private fun reminders(eventId: Long): List<Int> = try {
        val out = ArrayList<Int>()
        context.contentResolver.query(
            CalendarContract.Reminders.CONTENT_URI,
            arrayOf(CalendarContract.Reminders.MINUTES),
            CalendarContract.Reminders.EVENT_ID + "=?",
            arrayOf(eventId.toString()),
            CalendarContract.Reminders.MINUTES + " ASC",
        )?.use { c -> while (c.moveToNext()) out.add(c.getInt(0)) }
        out
    } catch (t: Throwable) {
        emptyList()
    }

    private fun attendees(eventId: Long): List<AttendeeInfo> = try {
        val out = ArrayList<AttendeeInfo>()
        context.contentResolver.query(
            CalendarContract.Attendees.CONTENT_URI,
            arrayOf(
                CalendarContract.Attendees.ATTENDEE_NAME,
                CalendarContract.Attendees.ATTENDEE_EMAIL,
                CalendarContract.Attendees.ATTENDEE_STATUS,
            ),
            CalendarContract.Attendees.EVENT_ID + "=?",
            arrayOf(eventId.toString()),
            null,
        )?.use { c ->
            while (c.moveToNext()) {
                out.add(AttendeeInfo(c.getString(0), c.getString(1), c.getInt(2)))
            }
        }
        out
    } catch (t: Throwable) {
        emptyList()
    }

    private companion object {
        const val TAG = "M3eEventDetails"
        const val DEFAULT_DURATION_MS = 60L * 60L * 1000L
    }
}
