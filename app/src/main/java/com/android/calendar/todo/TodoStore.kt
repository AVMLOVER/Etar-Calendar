/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The to-do ("待办") records behind the to-do screen.
 *
 * A to-do is stored twice, on purpose, because the two halves are needed in different places:
 *
 *  - As an all-day event in a local calendar of its own. That is what puts it in the all-day strip
 *    of the day, three day, week and month views, without any of those views knowing what a to-do
 *    is, and what makes tapping one there lead back to this screen.
 *  - Its items - the checklist, and whether and when to remind - in SharedPreferences, keyed by the
 *    day. The calendar provider has no column for a list of things, and the event's description is
 *    shown to the user elsewhere, so the checklist must not live there.
 *
 * One to-do per day. The day is the key for both halves, so a to-do cannot be saved twice into the
 * same day, and the two cannot drift apart without the other noticing.
 */
package com.android.calendar.todo

import android.app.Application
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.provider.CalendarContract
import android.util.Log
import com.android.calendar.persistence.CalendarRepository
import org.json.JSONArray
import org.json.JSONObject
import ws.xsoh.etar.R
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID

/** One line of a to-do: what is to be done, and whether it is done. */
data class TodoItem(
    val id: String,
    val text: String,
    val done: Boolean = false,
)

/** A day's to-do. */
data class Todo(
    val date: LocalDate,
    val name: String,
    val items: List<TodoItem> = emptyList(),
    /** A free form note for the day, shown under the checklist. */
    val note: String = "",
    val remind: Boolean = false,
    /** Minutes past local midnight at which to remind, when [remind] is set. */
    val remindMinuteOfDay: Int = DEFAULT_REMIND_MINUTE,
    /** The all-day event that carries this to-do, or -1 while it has none. */
    val eventId: Long = -1L,
) {
    val doneCount: Int get() = items.count { it.done }

    /** The day's instant, at UTC midnight - how an all-day event's day is stored. */
    val utcMidnight: Long
        get() = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    /** When to remind, in the given zone, or null when no reminder is set. */
    fun remindAtMillis(zone: ZoneId): Long? {
        if (!remind) return null
        return LocalDateTime.of(date, LocalTime.of(remindMinuteOfDay / 60, remindMinuteOfDay % 60))
            .atZone(zone).toInstant().toEpochMilli()
    }

    companion object {
        /** 09:00, which is when a reminder for a day is usually wanted. */
        const val DEFAULT_REMIND_MINUTE = 9 * 60
    }
}

object TodoStore {

    private const val TAG = "TodoStore"

    /**
     * The account the to-do calendar belongs to.
     *
     * Deliberately not the name the user's own offline calendars use: those are found and listed by
     * their account, and the to-do calendar is not one of them - it is not a calendar to write
     * events into, it is where the to-dos live. Its own name also keeps it out of reach of the
     * delete-a-calendar gesture, which would take the to-dos with it.
     */
    const val ACCOUNT_NAME = "etar_todo"

    private const val PREFS = "m3e_todos"
    private const val KEY_ITEMS = "items"
    private const val KEY_REMIND = "remind"
    private const val KEY_MINUTE = "minute"
    private const val KEY_NOTE = "note"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun key(date: LocalDate) = "todo_" + date.toEpochDay()

    // ------------------------------------------------------------------ the calendar

    /**
     * The to-do calendar, created the first time it is needed.
     *
     * Returns -1 when there is no calendar provider to write to, which is the case on a device whose
     * calendar storage is still being set up; every caller treats that as "nothing to show" rather
     * than failing.
     */
    fun calendarId(context: Context): Long {
        queryCalendarId(context)?.let { return it }
        return try {
            val app = context.applicationContext as Application
            val name = context.getString(R.string.m3e_todo_calendar_name)
            val uri = CalendarRepository(app).addLocalCalendar(ACCOUNT_NAME, name)
            ContentUris.parseId(uri)
        } catch (t: Throwable) {
            Log.w(TAG, "the to-do calendar could not be created", t)
            -1L
        }
    }

    private fun queryCalendarId(context: Context): Long? = try {
        context.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            arrayOf(CalendarContract.Calendars._ID),
            CalendarContract.Calendars.ACCOUNT_NAME + "=? AND " +
                CalendarContract.Calendars.ACCOUNT_TYPE + "=?",
            arrayOf(ACCOUNT_NAME, CalendarContract.ACCOUNT_TYPE_LOCAL),
            null,
        )?.use { c -> if (c.moveToFirst()) c.getLong(0) else null }
    } catch (t: Throwable) {
        Log.w(TAG, "the to-do calendar could not be read", t)
        null
    }

    // ------------------------------------------------------------------ reading

    /** The day's to-do, or null when the day has none. */
    fun forDate(context: Context, date: LocalDate): Todo? {
        val calendarId = calendarId(context)
        if (calendarId < 0) return null
        val utcMidnight = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        var eventId = -1L
        var name = ""
        try {
            context.contentResolver.query(
                CalendarContract.Events.CONTENT_URI,
                arrayOf(CalendarContract.Events._ID, CalendarContract.Events.TITLE),
                CalendarContract.Events.CALENDAR_ID + "=? AND " +
                    CalendarContract.Events.DTSTART + "=? AND " +
                    CalendarContract.Events.DELETED + "=0",
                arrayOf(calendarId.toString(), utcMidnight.toString()),
                null,
            )?.use { c ->
                if (c.moveToFirst()) {
                    eventId = c.getLong(0)
                    name = c.getString(1).orEmpty()
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "the to-do could not be read", t)
            return null
        }
        if (eventId < 0) return null
        val stored = prefs(context)
        return Todo(
            date = date,
            name = name,
            items = readItems(stored.getString(key(date), null)),
            note = stored.getString(key(date) + "_" + KEY_NOTE, null).orEmpty(),
            remind = stored.getBoolean(key(date) + "_" + KEY_REMIND, false),
            remindMinuteOfDay = stored.getInt(
                key(date) + "_" + KEY_MINUTE, Todo.DEFAULT_REMIND_MINUTE,
            ),
            eventId = eventId,
        )
    }

    /** The day of the to-do an event carries, or null when the event is not a to-do. */
    fun todoDateForEvent(context: Context, eventId: Long): LocalDate? {
        if (eventId < 0) return null
        val calendarId = queryCalendarId(context) ?: return null
        return try {
            context.contentResolver.query(
                CalendarContract.Events.CONTENT_URI,
                arrayOf(CalendarContract.Events.DTSTART),
                CalendarContract.Events._ID + "=? AND " + CalendarContract.Events.CALENDAR_ID + "=?",
                arrayOf(eventId.toString(), calendarId.toString()),
                null,
            )?.use { c ->
                if (c.moveToFirst()) {
                    Instant.ofEpochMilli(c.getLong(0)).atZone(ZoneOffset.UTC).toLocalDate()
                } else {
                    null
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "the event could not be checked", t)
            null
        }
    }

    /** Every day that has a to-do, for the widget-free views that ask which days carry one. */
    fun datesWithTodo(context: Context): Set<LocalDate> =
        prefs(context).all.keys
            .filter {
                it.startsWith("todo_") &&
                    !it.endsWith("_$KEY_REMIND") &&
                    !it.endsWith("_$KEY_MINUTE") &&
                    !it.endsWith("_$KEY_NOTE")
            }
            .mapNotNull { runCatching { LocalDate.ofEpochDay(it.removePrefix("todo_").toLong()) }.getOrNull() }
            .toSet()

    // ------------------------------------------------------------------ writing

    /**
     * Writes the to-do for its day: the event first, then the checklist beside it.
     *
     * The event is updated in place when the day already had one, so its id - which the other views
     * and any reminder already point at - does not change.
     */
    fun save(context: Context, todo: Todo): Boolean {
        val calendarId = calendarId(context)
        if (calendarId < 0) return false

        // A to-do with nothing on it is not a to-do: a title alone would sit in the all-day strip
        // and on the to-do screen with no way to complete anything, and it would hide the add
        // button, so the day could never be given a real one. Saving such a thing deletes the day's
        // to-do instead - which is also what removing the last line of an existing one means.
        if (todo.items.none { it.text.isNotBlank() }) {
            return delete(context, todo.date)
        }

        val values = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calendarId)
            put(CalendarContract.Events.TITLE, todo.name)
            put(CalendarContract.Events.DTSTART, todo.utcMidnight)
            put(CalendarContract.Events.DTEND, todo.utcMidnight + DAY_MILLIS)
            put(CalendarContract.Events.ALL_DAY, 1)
            put(CalendarContract.Events.EVENT_TIMEZONE, "UTC")
            put(CalendarContract.Events.EVENT_END_TIMEZONE, "UTC")
            put(CalendarContract.Events.HAS_ALARM, 0)
            put(CalendarContract.Events.ACCESS_LEVEL, CalendarContract.Events.ACCESS_DEFAULT)
            put(CalendarContract.Events.AVAILABILITY, CalendarContract.Events.AVAILABILITY_FREE)
        }

        val existing = forDate(context, todo.date)
        val ok = try {
            if (existing != null) {
                // The row's own uri refuses writes on a local calendar, so the collection uri with
                // the id in the selection is what an edit goes through.
                context.contentResolver.update(
                    CalendarContract.Events.CONTENT_URI,
                    values,
                    CalendarContract.Events._ID + "=?",
                    arrayOf(existing.eventId.toString()),
                )
                true
            } else {
                context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values) != null
            }
        } catch (t: Throwable) {
            Log.w(TAG, "the to-do could not be saved", t)
            false
        }
        if (!ok) return false

        prefs(context).edit()
            .putString(key(todo.date), writeItems(todo.items))
            .putString(key(todo.date) + "_" + KEY_NOTE, todo.note)
            .putBoolean(key(todo.date) + "_" + KEY_REMIND, todo.remind)
            .putInt(key(todo.date) + "_" + KEY_MINUTE, todo.remindMinuteOfDay)
            .apply()

        TodoReminder.schedule(context, todo)
        // The home screen widget shows the same checklist, and this is the one place every change to
        // a to-do goes through - the screen, the widget's own boxes, a delete that empties the day.
        com.android.calendar.widget.TodoWidgetUpdater.updateAll(context)
        return true
    }

    /** Removes the day's to-do, event and checklist together. */
    fun delete(context: Context, date: LocalDate): Boolean {
        val existing = forDate(context, date) ?: return false
        val ok = try {
            context.contentResolver.delete(
                CalendarContract.Events.CONTENT_URI,
                CalendarContract.Events._ID + "=?",
                arrayOf(existing.eventId.toString()),
            ) >= 0
        } catch (t: Throwable) {
            Log.w(TAG, "the to-do could not be deleted", t)
            false
        }
        prefs(context).edit()
            .remove(key(date))
            .remove(key(date) + "_" + KEY_NOTE)
            .remove(key(date) + "_" + KEY_REMIND)
            .remove(key(date) + "_" + KEY_MINUTE)
            .apply()
        TodoReminder.cancel(context, date)
        com.android.calendar.widget.TodoWidgetUpdater.updateAll(context)
        return ok
    }

    // ------------------------------------------------------------------ the checklist

    /**
     * A fresh item.
     *
     * Items are identified by a string rather than by their position, so reordering and completing
     * them does not make the list treat one as another.
     */
    fun newItem(text: String = "", done: Boolean = false): TodoItem =
        TodoItem(id = UUID.randomUUID().toString(), text = text, done = done)

    private fun writeItems(items: List<TodoItem>): String {
        val array = JSONArray()
        items.forEach { item ->
            array.put(
                JSONObject().apply {
                    put("id", item.id)
                    put("t", item.text)
                    put("d", item.done)
                }
            )
        }
        return array.toString()
    }

    private fun readItems(json: String?): List<TodoItem> {
        if (json.isNullOrEmpty()) return emptyList()
        return try {
            val array = JSONArray(json)
            (0 until array.length()).mapNotNull { i ->
                val o = array.optJSONObject(i) ?: return@mapNotNull null
                TodoItem(
                    id = o.optString("id").ifEmpty { UUID.randomUUID().toString() },
                    text = o.optString("t"),
                    done = o.optBoolean("d"),
                )
            }
        } catch (t: Throwable) {
            Log.w(TAG, "the checklist could not be read", t)
            emptyList()
        }
    }

    /** One day, which is also the length of the all-day event a to-do is stored as. */
    const val DAY_MILLIS = 24L * 60L * 60L * 1000L
}
