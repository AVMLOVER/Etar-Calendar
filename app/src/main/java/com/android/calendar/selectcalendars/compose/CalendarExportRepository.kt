/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * Reads one calendar's events for the ICS export.
 *
 * One Events query for the whole calendar - recurring rows included, because an exported event is the
 * rule, not the occurrences - plus one ExtendedProperties query for the URLs. The URL is not a column
 * on Events (the provider rejects it), so it has to come from the properties, the same way the grids
 * read it.
 */
package com.android.calendar.selectcalendars.compose

import android.content.Context
import android.provider.CalendarContract
import android.provider.CalendarContract.ExtendedProperties
import android.util.Log
import com.android.calendar.composeui.IcsEvent
import com.android.calendar.event.ExtendedProperty

class CalendarExportRepository(private val context: Context) {

    /** Every event of [calendarId], or an empty list when the calendar has none or cannot be read. */
    fun load(calendarId: Long): List<IcsEvent> {
        val projection = arrayOf(
            CalendarContract.Events._ID,
            CalendarContract.Events.TITLE,
            CalendarContract.Events.DESCRIPTION,
            CalendarContract.Events.EVENT_LOCATION,
            CalendarContract.Events.DTSTART,
            CalendarContract.Events.DTEND,
            CalendarContract.Events.ALL_DAY,
            CalendarContract.Events.RRULE,
            CalendarContract.Events.DURATION,
        )
        val selection =
            "${CalendarContract.Events.CALENDAR_ID}=? AND ${CalendarContract.Events.DELETED}=0"
        val events = ArrayList<IcsEvent>()
        try {
            context.contentResolver.query(
                CalendarContract.Events.CONTENT_URI,
                projection,
                selection,
                arrayOf(calendarId.toString()),
                "${CalendarContract.Events.DTSTART} ASC",
            )?.use { c ->
                while (c.moveToNext()) {
                    val start = c.getLong(4)
                    val end = c.getLong(5).takeIf { it > start } ?: start
                    events.add(
                        IcsEvent(
                            id = c.getLong(0),
                            title = c.getString(1),
                            description = c.getString(2),
                            location = c.getString(3),
                            url = null,
                            startMillis = start,
                            endMillis = end,
                            allDay = c.getInt(6) != 0,
                            rrule = c.getString(7),
                            duration = c.getString(8),
                        )
                    )
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Could not read the calendar for export", t)
            return emptyList()
        }
        if (events.isEmpty()) {
            return events
        }
        val urls = loadUrls(events.mapTo(HashSet()) { it.id })
        return events.map { event -> event.copy(url = urls[event.id]) }
    }

    private fun loadUrls(eventIds: Set<Long>): Map<Long, String> {
        val selection = buildString {
            append(ExtendedProperties.EVENT_ID)
            append(" IN (")
            append(eventIds.joinToString(",") { "?" })
            append(") AND ")
            append(ExtendedProperties.NAME)
            append(" IN (?, ?)")
        }
        val args = eventIds.map { it.toString() } +
            listOf(ExtendedProperty.URL_NAME, ExtendedProperty.URL_NAME_PRIV)
        val urls = HashMap<Long, String>()
        try {
            context.contentResolver.query(
                ExtendedProperties.CONTENT_URI,
                arrayOf(ExtendedProperties.EVENT_ID, ExtendedProperties.VALUE),
                selection,
                args.toTypedArray(),
                null,
            )?.use { c ->
                while (c.moveToNext()) {
                    val value = c.getString(1)?.trim().orEmpty()
                    if (value.isNotEmpty()) {
                        urls[c.getLong(0)] = value
                    }
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Could not read the event urls for export", t)
        }
        return urls
    }

    private companion object {
        const val TAG = "CalendarExport"
    }
}
