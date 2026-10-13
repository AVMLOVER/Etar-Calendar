/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * Data layer for the Material 3 Expressive agenda screen.
 */
package com.android.calendar.agenda.compose

import android.content.ContentUris
import android.content.Context
import android.provider.CalendarContract
import android.util.Log
import com.android.calendar.event.compose.EventDetailsRepository

/** One occurrence of an event, as returned by the Instances table. */
data class AgendaEvent(
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
    /**
     * Lead times of the event's notifications, in minutes before the start.
     *
     * Carried on the row so the notification lead time can be changed from the list itself, which is
     * what the classic agenda allowed and what the editor-only flow lost.
     */
    val remindersMinutes: List<Int> = emptyList(),
) {
    val isDeclined: Boolean
        get() = selfAttendeeStatus == CalendarContract.Attendees.ATTENDEE_STATUS_DECLINED
}

/**
 * Reads upcoming event occurrences straight from CalendarContract.Instances.
 *
 * Unlike the classic agenda this does no cursor windowing: the range is bounded by the screen
 * (see [com.android.calendar.agenda.compose.M3eAgendaScreen]) so a single query is enough.
 */
class AgendaRepository(private val context: Context) {

    fun load(fromMillis: Long, toMillis: Long, limit: Int = 500): List<AgendaEvent> {
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

        val result = ArrayList<AgendaEvent>()
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
                        AgendaEvent(
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
                            remindersMinutes = remindersFor(c.getLong(0)),
                        )
                    )
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Could not read the agenda", t)
        }
        return result
    }

    /**
     * Event occurrences whose title, location or description contains [query].
     *
     * Searched over a bounded window rather than all of time, for the same reason the agenda is: an
     * Instances query needs a range. A year either side of [aroundMillis] covers what someone is
     * looking for in practice, and the count is capped.
     */
    fun search(
        query: String,
        aroundMillis: Long,
        plusMinusDays: Long = 365L,
        limit: Int = 200,
    ): List<AgendaEvent> {
        val term = query.trim()
        if (term.isEmpty()) {
            return emptyList()
        }
        val day = 24L * 60L * 60L * 1000L
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().apply {
            ContentUris.appendId(this, aroundMillis - plusMinusDays * day)
            ContentUris.appendId(this, aroundMillis + plusMinusDays * day)
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

        // LIKE with a wildcard on both sides, escaped so a % or _ the user typed is a literal.
        val like = "%" + term.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"
        val selection = buildString {
            append("(").append(CalendarContract.Instances.VISIBLE).append("=1) AND (")
            append(CalendarContract.Instances.TITLE).append(" LIKE ? ESCAPE '\\' OR ")
            append(CalendarContract.Instances.EVENT_LOCATION).append(" LIKE ? ESCAPE '\\' OR ")
            append(CalendarContract.Instances.DESCRIPTION).append(" LIKE ? ESCAPE '\\')")
        }

        val result = ArrayList<AgendaEvent>()
        try {
            context.contentResolver.query(
                uri,
                projection,
                selection,
                arrayOf(like, like, like),
                CalendarContract.Instances.BEGIN + " ASC",
            )?.use { c ->
                while (c.moveToNext() && result.size < limit) {
                    result.add(
                        AgendaEvent(
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
                            // Not loaded for results: a search list is for finding an event, not for
                            // editing its reminders, and one query per row would make it slow.
                            remindersMinutes = emptyList(),
                        )
                    )
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Could not search", t)
        }
        return result
    }

    /**
     * The event's notification lead times.
     *
     * Read per event rather than joined into the Instances query, because a reminder belongs to the
     * event and one query per event keeps the Instances projection as it is. The agenda loads at most
     * a few hundred rows and only the events that have reminders cost a query that returns rows.
     */
    private fun remindersFor(eventId: Long): List<Int> = try {
        val out = ArrayList<Int>()
        context.contentResolver.query(
            CalendarContract.Reminders.CONTENT_URI,
            arrayOf(CalendarContract.Reminders.MINUTES),
            CalendarContract.Reminders.EVENT_ID + "=?",
            arrayOf(eventId.toString()),
            null,
        )?.use { c ->
            while (c.moveToNext()) out.add(c.getInt(0))
        }
        out
    } catch (t: Throwable) {
        Log.w(TAG, "could not read reminders for event $eventId", t)
        emptyList()
    }

    /**
     * Replaces one event's notification lead times.
     *
     * The rows are rewritten rather than updated in place, which is what the editor's save path does
     * too: a reminder has no stable identity across an edit, so replacing the set is the honest
     * operation. The method is kept as an alert because the list only ever offers the lead time.
     *
     * @return true when the write went through.
     */
    fun setReminderMinutes(eventId: Long, minutes: List<Int>): Boolean = try {
        val resolver = context.contentResolver
        resolver.delete(
            CalendarContract.Reminders.CONTENT_URI,
            CalendarContract.Reminders.EVENT_ID + "=?",
            arrayOf(eventId.toString()),
        )
        minutes.sortedDescending().forEach { value ->
            resolver.insert(
                CalendarContract.Reminders.CONTENT_URI,
                android.content.ContentValues().apply {
                    put(CalendarContract.Reminders.EVENT_ID, eventId)
                    put(CalendarContract.Reminders.MINUTES, value)
                    put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
                },
            )
        }
        // Keep the event's own flag in step, so other screens show the bell correctly.
        resolver.update(
            ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId),
            android.content.ContentValues().apply {
                put(CalendarContract.Events.HAS_ALARM, if (minutes.isEmpty()) 0 else 1)
            },
            null,
            null,
        )
        true
    } catch (t: Throwable) {
        Log.w(TAG, "could not write reminders for event $eventId", t)
        false
    }

    /**
     * Deletes one occurrence's event.
     *
     * Delegated to the editor repository so the swipe uses exactly the same path as deleting from the
     * details screen or the editor - it supplies the full model DeleteEventHelper needs, checks the
     * calendar is writable and routes through the classic helper, keeping the undo snackbar.
     */
    fun delete(activity: android.app.Activity, eventId: Long): Boolean =
        EventDetailsRepository(activity).delete(activity, eventId)

    private companion object {
        const val TAG = "M3eAgenda"
    }
}
