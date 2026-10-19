/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * Data layer for the Material 3 Expressive "calendars to display" screen.
 */
package com.android.calendar.selectcalendars.compose

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.provider.CalendarContract
import android.util.Log

/** One row of the Calendars table that carries events. */
data class CalendarEntry(
    val id: Long,
    val displayName: String,
    val accountName: String?,
    val ownerAccount: String?,
    val color: Int,
    val visible: Boolean,
    val accountType: String? = null,
) {
    /** True when this is the account's own calendar rather than a shared/subscribed one. */
    val isPrimary: Boolean
        get() = accountName != null && accountName == ownerAccount

    /** True when this app created the calendar itself and may delete it. */
    val isOffline: Boolean
        get() = accountType == CalendarContract.ACCOUNT_TYPE_LOCAL
}

/**
 * True when this screen may offer to delete the calendar.
 *
 * Only calendars this app created can be deleted; every other calendar is owned by its account and
 * stays. The to-do calendar is one of the app's own but is not offered: every to-do lives in it, so
 * deleting it would take them all with it, and nothing in the list says so.
 *
 * Shared with the per-calendar screen rather than repeated there, so the rule has one definition and
 * the two screens cannot disagree about which calendar is safe to remove.
 */
val CalendarEntry.canBeDeleted: Boolean
    get() = isOffline && accountName != com.android.calendar.todo.TodoStore.ACCOUNT_NAME

/**
 * Reads and updates the calendars the user can show.
 *
 * This mirrors exactly what the classic SelectVisibleCalendarsFragment does: it queries the
 * Calendars table restricted to SYNC_EVENTS=1 and writes the VISIBLE flag back through
 * Calendars.CONTENT_URI, so both screens stay consistent with each other.
 */
class CalendarsRepository(private val context: Context) {

    fun load(): List<CalendarEntry> {
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.OWNER_ACCOUNT,
            CalendarContract.Calendars.CALENDAR_COLOR,
            CalendarContract.Calendars.VISIBLE,
            CalendarContract.Calendars.ACCOUNT_TYPE,
        )

        val result = ArrayList<CalendarEntry>()
        try {
            context.contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                projection,
                CalendarContract.Calendars.SYNC_EVENTS + "=1",
                null,
                CalendarContract.Calendars.ACCOUNT_NAME + " ASC",
            )?.use { c ->
                while (c.moveToNext()) {
                    result.add(
                        CalendarEntry(
                            id = c.getLong(0),
                            displayName = c.getString(1).orEmpty(),
                            accountName = c.getString(2),
                            ownerAccount = c.getString(3),
                            color = c.getInt(4),
                            visible = c.getInt(5) != 0,
                            accountType = c.getString(6),
                        )
                    )
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Could not read the calendars", t)
        }
        return result
    }

    fun setVisible(calendarId: Long, visible: Boolean): Boolean {
        return update(
            calendarId,
            ContentValues().apply {
                put(CalendarContract.Calendars.VISIBLE, if (visible) 1 else 0)
            },
        )
    }

    /**
     * The one calendar with [calendarId], or null when it is no longer in the list.
     *
     * Read through [load] rather than with a query of its own: the per-calendar screen has to see
     * exactly the rows the list showed, including the SYNC_EVENTS filter, or a calendar hidden from
     * the list would still be editable by id from a stale intent.
     */
    fun loadOne(calendarId: Long): CalendarEntry? = load().firstOrNull { it.id == calendarId }

    fun setColor(calendarId: Long, color: Int): Boolean {
        return update(
            calendarId,
            ContentValues().apply {
                put(CalendarContract.Calendars.CALENDAR_COLOR, color)
            },
        )
    }

    /**
     * Renames a calendar.
     *
     * The same Calendars.CONTENT_URI update the two other setters use, so the name the list reads,
     * the name the calendar views title themselves with and the name the next sync sees are all the
     * one column. Only the display name changes: NAME is the account's own key for the calendar and
     * is not ours to rewrite.
     */
    fun setDisplayName(calendarId: Long, displayName: String): Boolean {
        return update(
            calendarId,
            ContentValues().apply {
                put(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, displayName)
            },
        )
    }

    private fun update(calendarId: Long, values: ContentValues): Boolean {
        return try {
            val uri = ContentUris.withAppendedId(
                CalendarContract.Calendars.CONTENT_URI, calendarId
            )
            context.contentResolver.update(uri, values, null, null) > 0
        } catch (t: Throwable) {
            Log.w(TAG, "Could not update calendar $calendarId", t)
            false
        }
    }

    private companion object {
        const val TAG = "M3eCalendars"
    }
}
