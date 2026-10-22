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
import android.provider.CalendarContract.ExtendedProperties
import android.util.Log
import com.android.calendar.event.ExtendedProperty

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
    /**
     * The event's description, when it has one.
     *
     * Carried for the day and week grids, which show it under the title inside the event block.
     * The month grid and the widget ignore it: their cells have room for the title only.
     */
    val description: String?,
    /**
     * The event's URL, when it has one.
     *
     * The provider has no url column on Events or Instances, so it is read from the extended
     * properties (see [loadUrls]) and carried here for the grid block to show under the description.
     */
    val url: String?,
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
            CalendarContract.Instances.DESCRIPTION,
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
                            description = c.getString(10),
                            url = null,
                        )
                    )
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Could not read the calendar window", t)
        }
        if (result.isEmpty()) {
            return result
        }
        // The URLs are a second query because the provider has no url column to join on; it is still
        // one query for the whole window rather than one per event.
        val urls = loadUrls(result.mapTo(HashSet()) { it.id })
        return result.map { event -> event.copy(url = urls[event.id]) }
    }

    /**
     * The URL extended property of every event in [eventIds].
     *
     * The URL is stored in ExtendedProperties, not on the event row: writing "url" on Events is
     * rejected by the provider ("column 'url' is invalid"), which is why the importer does not write
     * it. The name is the ical4android one, private form included because a synced calendar may store
     * either.
     */
    private fun loadUrls(eventIds: Set<Long>): Map<Long, String> {
        if (eventIds.isEmpty()) {
            return emptyMap()
        }
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
        val projection = arrayOf(ExtendedProperties.EVENT_ID, ExtendedProperties.VALUE)
        val urls = HashMap<Long, String>()
        try {
            context.contentResolver.query(
                ExtendedProperties.CONTENT_URI,
                projection,
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
            Log.w(TAG, "Could not read the event urls", t)
        }
        return urls
    }

    private companion object {
        const val TAG = "M3eDays"
    }
}
