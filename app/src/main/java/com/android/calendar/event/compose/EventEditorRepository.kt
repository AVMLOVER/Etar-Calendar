/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * Load/save layer for the Material 3 Expressive event editor.
 *
 * SAFETY: EditEventHelper.saveEvent() rewrites the attendees and reminders tables from the
 * model it is given. If the model were only partially loaded, saving would silently DELETE the
 * guest list or the reminders. This loader therefore mirrors the classic editor's five queries
 * (event, attendees, reminders, calendars) and refuses to hand back a model unless every piece
 * it depends on could actually be read.
 */
package com.android.calendar.event.compose

import android.content.ContentValues
import android.content.ContentUris
import android.content.Context
import android.provider.CalendarContract
import android.util.Log
import com.android.calendar.CalendarEventModel
import com.android.calendar.Utils
import com.android.calendar.composeui.pickEventUrl
import com.android.calendar.event.EditEventHelper
import com.android.calendar.event.ExtendedProperty
import com.android.calendar.persistence.CalendarRepository
import com.android.calendar.todo.TodoStore

data class CalendarOption(
    val id: Long,
    val name: String,
    val color: Int,
    /** The calendar's account type; the colour table is keyed by name and type. */
    val accountType: String,
    /**
     * The calendar's owner account. A new event needs this: CalendarEventModel.isValid() refuses a
     * model without an owner account, and EditEventHelper.saveEvent() returns false for an invalid
     * model, which surfaces as "could not save the event".
     */
    val ownerAccount: String,
)

class EventEditorRepository(private val context: Context) {

    /** A fully loaded model pair plus the calendars the event may be moved to. */
    class Snapshot(
        val model: CalendarEventModel,
        val original: CalendarEventModel,
        val calendars: List<CalendarOption>,
        /**
         * The event colours this account actually offers, as ARGB values.
         *
         * Empty when the account has no colour table of its own, in which case the picker falls back
         * to [DEFAULT_EVENT_COLORS].
         */
        val eventColors: List<Int>,
    )

    /**
     * @return a fully populated snapshot, or null when anything could not be read. A null result
     *         must be treated as "do not edit" - never as "start from an empty model".
     */
    fun load(eventId: Long): Snapshot? {
        val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
        val model = CalendarEventModel(context)
        val original = CalendarEventModel()

        var found = false
        try {
            // queryRowById rather than a plain row read, for the same reason the calendar read uses
            // one: an event on a local calendar has no answer at its own row uri, and reading only
            // there refused to open every such event.
            found = context.contentResolver.queryRowById(
                CalendarContract.Events.CONTENT_URI, eventId, EditEventHelper.EVENT_PROJECTION,
            ) { c ->
                EditEventHelper.setModelFromCursor(original, c, context)
                EditEventHelper.setModelFromCursor(model, c, context)
                true
            } ?: false
        } catch (t: Throwable) {
            Log.w(TAG, "event query failed", t)
        }
        if (!found) {
            return null
        }

        if (model.mCalendarId == -1L) {
            Log.w(TAG, "event has no calendar id; refusing to edit")
            return null
        }

        model.mUri = uri.toString()
        original.mUri = uri.toString()

        // Same safety net as the create path: a model without an owner account is rejected by
        // saveEvent as invalid, which would make an otherwise fine edit fail to save.
        if (model.mOwnerAccount.isNullOrEmpty()) {
            val option = loadCalendars().firstOrNull { it.id == model.mCalendarId }
            if (option != null) {
                model.mOwnerAccount = option.ownerAccount.ifEmpty { option.name }
                original.mOwnerAccount = model.mOwnerAccount
            }
        }
        // Opening the editor from a details screen always edits the series as a whole.
        model.mOriginalStart = model.mStart
        model.mOriginalEnd = model.mEnd
        model.mIsFirstEventInSeries = true

        // Attendees are mandatory: saveEvent deletes and re-inserts the guest list.
        model.mAttendeesList.clear()
        original.mAttendeesList.clear()
        if (model.mHasAttendeeData && !loadAttendees(eventId, model, original)) {
            Log.w(TAG, "attendees could not be read; refusing to edit")
            return null
        }

        // Reminders are mandatory for the same reason.
        model.mReminders = ArrayList()
        original.mReminders = ArrayList()
        if (!loadReminders(eventId, model, original)) {
            Log.w(TAG, "reminders could not be read; refusing to edit")
            return null
        }

        // The URL is not on the event row - it lives in ExtendedProperties under the ical4android
        // name, which setModelFromCursor cannot see. Without this the editor opened with an empty
        // field, so saving anything else wrote that emptiness back and dropped a stored URL. Both
        // models get the value, so an untouched URL is not treated as a change.
        val url = loadUrl(eventId)
        model.mUrl = url
        original.mUrl = url

        return Snapshot(model, original, loadCalendars(), eventColorsFor(model))
    }

    /** The event's URL extended property; blank and missing rows both read as null. */
    private fun loadUrl(eventId: Long): String? {
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
            Log.w(TAG, "url query failed", t)
        }
        return pickEventUrl(rows, ExtendedProperty.URL_NAME_PRIV, ExtendedProperty.URL_NAME)
    }

    /** Queues the update through the same helper the classic editor uses. */
    fun save(model: CalendarEventModel, original: CalendarEventModel): Boolean {
        return try {
            val helper = EditEventHelper(context)
            helper.saveEvent(model, original, Utils.MODIFY_ALL)
        } catch (t: Throwable) {
            Log.w(TAG, "save failed", t)
            false
        }
    }

    /**
     * Deletes an event.
     *
     * This deliberately does NOT go through DeleteEventHelper. That class is written for the classic
     * UI: it raises its own confirmation dialog (which would be a second one, on top of the
     * screen's), and when its own permission check declines it simply returns without doing anything
     * and without reporting anything - which is exactly "it said it would delete and the event is
     * still there".
     *
     * The delete is issued directly against the provider instead. The plain app delete is tried
     * first even for a local calendar, because that is the path that makes CalendarProvider notify
     * the Instances table the agenda reads - a sync adapter delete skips it, which is why a deleted
     * event kept showing up until the screen was reopened. Only if the plain delete removes nothing
     * is the sync adapter form used, which is needed to remove the row of a local calendar outright.
     *
     * @return true when the provider actually removed the event.
     */
    fun deleteEvent(eventId: Long): Boolean {
        val (accountName, accountType) = syncAccountOf(eventId)

        val plain = CalendarContract.Events.CONTENT_URI
        if (deleteAt(ContentUris.withAppendedId(plain, eventId))) {
            return true
        }

        // Plain delete removed nothing: fall back to the sync adapter form for a local calendar.
        val isLocal = CalendarContract.ACCOUNT_TYPE_LOCAL == accountType
        if (!isLocal || accountName == null) {
            Log.w(TAG, "delete removed no rows for event $eventId")
            return false
        }
        val local = CalendarRepository.asLocalCalendarSyncAdapter(
            accountName,
            CalendarContract.Events.CONTENT_URI,
        )
        val removed = deleteAt(ContentUris.withAppendedId(local, eventId))
        if (!removed) {
            Log.w(TAG, "sync adapter delete removed no rows for event $eventId")
        }
        return removed
    }

    private fun deleteAt(uri: android.net.Uri): Boolean = try {
        context.contentResolver.delete(uri, null, null) > 0
    } catch (t: Throwable) {
        Log.w(TAG, "delete failed for $uri", t)
        false
    }

    /**
     * The account a calendar belongs to, as (name, type).
     *
     * Read from the calendar rather than from a fully loaded model, so deleting does not depend on
     * the editor being able to load the event first.
     */
    private fun syncAccountOf(eventId: Long): Pair<String?, String?> {
        var calendarId = -1L
        try {
            // queryRowById: the event's calendar has to be readable before it is saved, and an event
            // on a local calendar does not answer at its own row uri.
            calendarId = context.contentResolver.queryRowById(
                CalendarContract.Events.CONTENT_URI,
                eventId,
                arrayOf(CalendarContract.Events.CALENDAR_ID),
            ) { c -> c.getLong(0) } ?: -1L
        } catch (t: Throwable) {
            Log.w(TAG, "could not read the event's calendar", t)
        }
        if (calendarId == -1L) return null to null

        return try {
            var name: String? = null
            var type: String? = null
            context.contentResolver.query(
                ContentUris.withAppendedId(CalendarContract.Calendars.CONTENT_URI, calendarId),
                arrayOf(
                    CalendarContract.Calendars.ACCOUNT_NAME,
                    CalendarContract.Calendars.ACCOUNT_TYPE,
                ),
                null, null, null,
            )?.use { c ->
                if (c.moveToFirst()) {
                    name = c.getString(0)
                    type = c.getString(1)
                }
            }
            name to type
        } catch (t: Throwable) {
            Log.w(TAG, "could not read the calendar account", t)
            null to null
        }
    }

    // ------------------------------------------------------------------------------- new events

    // ------------------------------------------------------------------------------- delete
    /**
     * Builds the model for a brand new event from the intent that asked for it.
     *
     * This mirrors what the classic editor does when it is launched with no event id: the model is
     * seeded from the intent extras, the calendar is chosen (the one the intent asked for, or the
     * first writable one), and [EditEventHelper.setModelFromCalendarCursor] fills in everything the
     * save path reads off the calendar - access level, maximum reminders, allowed reminder methods.
     *
     * @return a snapshot ready to edit, or null when there is no calendar the event could be written
     *         to. A null result means "do not open the editor" rather than "start from an empty
     *         model", because a model with no calendar cannot be saved.
     */
    fun create(intent: android.content.Intent): Snapshot? {
        val calendars = loadCalendars()
        if (calendars.isEmpty()) {
            return null
        }

        // CalendarEventModel(context, intent) picks up the title, location, description, url,
        // availability, visibility and recurrence rule from the intent - but not the times. Those
        // are read from the CalendarContract extras here, the same way EditEventActivity does it.
        // Without this the model keeps its -1 default and a new event lands at the epoch.
        val model = CalendarEventModel(context, intent)
        val original = CalendarEventModel(context)

        val begin = intent.getLongExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, -1L)
        val endExtra = intent.getLongExtra(CalendarContract.EXTRA_EVENT_END_TIME, -1L)
        val allDay = model.mAllDay

        val requested = intent.getLongExtra(CalendarContract.Events.CALENDAR_ID, -1L)
        val calendar = calendars.firstOrNull { it.id == requested } ?: calendars.first()
        model.mCalendarId = calendar.id
        original.mCalendarId = calendar.id

        // The owner account and organiser are what make the model valid, exactly as the classic
        // editor sets them when the calendar is chosen. Without them saveEvent rejects the model.
        val owner = calendar.ownerAccount.ifEmpty { calendar.name }
        model.mOwnerAccount = owner
        model.mOrganizer = owner
        original.mOwnerAccount = owner
        original.mOrganizer = owner

        // The time the caller asked for. A tap on the three day grid passes the slot that was
        // tapped; anything else that opens a new event without a time falls back to now.
        val start = if (begin > 0L) begin else System.currentTimeMillis()
        model.mStart = start
        model.mEnd = when {
            endExtra > start -> endExtra
            allDay -> start + ALL_DAY_MILLIS
            else -> start + DEFAULT_DURATION_MILLIS
        }
        model.mOriginalStart = model.mStart
        model.mOriginalEnd = model.mEnd
        model.mIsFirstEventInSeries = true

        if (!applyCalendar(model, calendar.id)) {
            return null
        }
        applyCalendar(original, calendar.id)

        // applyCalendar fills the calendar-derived fields, but the owner account is what makes the
        // model valid; if the calendar row did not carry one, fall back to the account name so a
        // save is still possible.
        if (model.mOwnerAccount.isNullOrEmpty()) {
            model.mOwnerAccount = calendar.ownerAccount.ifEmpty { calendar.name }
        }
        if (original.mOwnerAccount.isNullOrEmpty()) {
            original.mOwnerAccount = model.mOwnerAccount
        }

        // A new event has no guests; the list has to exist because saveEvent rewrites the table.
        model.mAttendeesList.clear()
        original.mAttendeesList.clear()

        model.mReminders = ArrayList()
        original.mReminders = ArrayList()
        if (EditEventHelper.canAddReminders(model)) {
            // A caller that knows the reminders to start from wins over the default: this is how
            // duplicating an event carries the original's notifications across.
            val preferred = intent.getIntArrayExtra(REMINDER_EXTRA_KEY)?.toList()
            if (preferred != null) {
                val method = reminderMethod(model)
                preferred.sortedDescending().forEach { minutes ->
                    val entry = CalendarEventModel.ReminderEntry.valueOf(minutes, method)
                    model.mReminders.add(entry)
                    original.mReminders.add(entry)
                }
            } else {
                // The defaults for a brand new event: the user's chosen default lead time (ten
                // minutes, unless the setting says otherwise) and a reminder at the start. The two
                // are what the checkbox menu shows ticked when a new event is opened, and what gets
                // saved when the user does not touch them. Only a caller that seeds the event with
                // its own reminders - a duplicated event - overrides this pair.
                val prefs = android.preference.PreferenceManager.getDefaultSharedPreferences(context)
                val minutes = prefs.getString(
                    KEY_DEFAULT_REMINDER, DEFAULT_REMINDER_MINUTES.toString()
                )?.toIntOrNull() ?: DEFAULT_REMINDER_MINUTES
                val method = reminderMethod(model)
                listOf(minutes, AT_START_REMINDER_MINUTES)
                    .filter { it != NO_REMINDER }
                    .distinct()
                    .sortedDescending()
                    .forEach { lead ->
                        val entry = CalendarEventModel.ReminderEntry.valueOf(lead, method)
                        model.mReminders.add(entry)
                        original.mReminders.add(entry)
                    }
            }
        }
        model.mHasAlarm = model.mReminders.isNotEmpty()

        // An explicit colour for the new event, when the caller supplied one.
        val requestedColor = intent.getIntExtra(COLOR_EXTRA_KEY, 0)
        if (requestedColor != 0) {
            model.setEventColor(requestedColor)
        }

        return Snapshot(model, original, calendars, eventColorsFor(model))
    }

    /**
     * The event colours the event's account offers.
     *
     * Read from the Colors table the same way the classic editor did - by account, restricted to
     * TYPE_EVENT - so the editor shows the colours the account really has rather than a palette
     * invented here.
     */
    private fun eventColorsFor(model: CalendarEventModel): List<Int> = try {
        val accountName = model.mCalendarAccountName
        val accountType = model.mCalendarAccountType
        if (accountName == null || accountType == null) {
            emptyList()
        } else {
            val out = ArrayList<Int>()
            context.contentResolver.query(
                CalendarContract.Colors.CONTENT_URI,
                arrayOf(CalendarContract.Colors.COLOR),
                CalendarContract.Colors.ACCOUNT_NAME + "=? AND " +
                    CalendarContract.Colors.ACCOUNT_TYPE + "=? AND " +
                    CalendarContract.Colors.COLOR_TYPE + "=" + CalendarContract.Colors.TYPE_EVENT,
                arrayOf(accountName, accountType),
                null,
            )?.use { c ->
                while (c.moveToNext()) {
                    out.add(Utils.getDisplayColorFromColor(context, c.getInt(0)))
                }
            }
            out
        }
    } catch (t: Throwable) {
        Log.w(TAG, "colour table could not be read", t)
        emptyList()
    }

    /**
     * Creates a plain calendar event and returns its id, or -1 when it could not be saved.
     *
     * Used by the countdown screen instead of an ACTION_INSERT intent. The intent only creates the
     * event once the user confirms it in the editor, so the id is not known here - and the countdown
     * has to remember which event it made so the two can be deleted together.
     */
    fun createSimpleEvent(
        title: String,
        startMillis: Long,
        endMillis: Long,
        allDay: Boolean,
        color: Int,
        description: String? = null,
    ): Long {
        val calendars = loadCalendars()
        if (calendars.isEmpty()) {
            Log.w(TAG, "no writable calendar; refusing to create an event")
            return -1L
        }
        val calendar = calendars.first()

        // Written straight to the provider rather than through EditEventHelper.
        //
        // saveEvent runs a series of internal consistency checks that exist for the editor's long
        // lived model - mEventOk, the original-model comparison, isUnchanged - and a model built from
        // scratch here failed one of them while returning nothing but a bare false. An insert of the
        // columns this event actually needs has no such state to get wrong, and it hands back the new
        // row's id directly, which is what the countdown has to record.
        val values = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calendar.id)
            put(CalendarContract.Events.TITLE, title)
            if (!description.isNullOrEmpty()) {
                put(CalendarContract.Events.DESCRIPTION, description)
            }
            if (allDay) {
                // An all-day event is stored in UTC, and its start must be UTC midnight.
                //
                // CalendarProvider builds its Instances rows only for events that follow that rule, so
                // an all-day event written with local midnight and a local time zone gets no Instances
                // row at all - it is absent from the month and three-day views, which read Instances,
                // while still showing in the agenda, which reads Events. The local midnight the caller
                // passes is shifted to the UTC midnight of the same calendar day.
                put(CalendarContract.Events.DTSTART, utcMidnight(startMillis))
                put(CalendarContract.Events.DTEND, utcMidnight(endMillis))
                put(CalendarContract.Events.EVENT_TIMEZONE, UTC)
                put(CalendarContract.Events.EVENT_END_TIMEZONE, UTC)
            } else {
                put(CalendarContract.Events.DTSTART, startMillis)
                put(CalendarContract.Events.DTEND, endMillis)
                put(CalendarContract.Events.EVENT_TIMEZONE, java.util.TimeZone.getDefault().id)
                put(CalendarContract.Events.EVENT_END_TIMEZONE, java.util.TimeZone.getDefault().id)
            }
            put(CalendarContract.Events.ALL_DAY, if (allDay) 1 else 0)
            put(CalendarContract.Events.HAS_ALARM, 0)
            put(CalendarContract.Events.ACCESS_LEVEL, CalendarContract.Events.ACCESS_DEFAULT)
            put(CalendarContract.Events.AVAILABILITY, CalendarContract.Events.AVAILABILITY_BUSY)
            if (color != 0) {
                // CalendarProvider does not accept an arbitrary colour: it resolves the display colour
                // through the color key, and a row written with EVENT_COLOR alone comes back as the
                // calendar's own colour. The key is looked up in the account's colour table, and when
                // the account has no entry for this colour it is left off - a keyless colour would be
                // discarded anyway, so writing one only looks like it worked.
                colorKeyFor(calendar, color)?.let { key ->
                    put(CalendarContract.Events.EVENT_COLOR_KEY, key)
                }
            }
        }

        return try {
            val uri = context.contentResolver.insert(
                CalendarContract.Events.CONTENT_URI,
                values,
            ) ?: return -1L
            val id = ContentUris.parseId(uri)
            Log.i(TAG, "created event $id for a countdown")
            id
        } catch (t: Throwable) {
            Log.w(TAG, "the event could not be inserted", t)
            -1L
        }
    }

    /**
     * The UTC midnight of the calendar day that [localMidnight] falls on.
     *
     * All-day events are stored as UTC midnight. A caller naturally works in local time, so its
     * midnight sits at an offset from UTC that changes with the date - summer time means the offset
     * for the day in question, not today's.
     */
    private fun utcMidnight(localMidnight: Long): Long {
        val zone = java.time.ZoneId.systemDefault()
        val date = java.time.Instant.ofEpochMilli(localMidnight).atZone(zone).toLocalDate()
        return date.atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
    }

    /**
     * The Colors-table key for [color] on [calendar]'s account, or null when the account has none.
     *
     * The query the classic editor's colour cache was filled from: by account, restricted to
     * TYPE_EVENT. Colours are compared as display colours, because that is what the palette the user
     * picked from holds.
     */
    private fun colorKeyFor(calendar: CalendarOption, color: Int): String? = try {
        context.contentResolver.query(
            CalendarContract.Colors.CONTENT_URI,
            arrayOf(CalendarContract.Colors.COLOR, CalendarContract.Colors.COLOR_KEY),
            CalendarContract.Colors.ACCOUNT_NAME + "=? AND " +
                CalendarContract.Colors.ACCOUNT_TYPE + "=? AND " +
                CalendarContract.Colors.COLOR_TYPE + "=" + CalendarContract.Colors.TYPE_EVENT,
            arrayOf(calendar.ownerAccount, calendar.accountType),
            null,
        )?.use { c ->
            var found: String? = null
            while (c.moveToNext()) {
                if (Utils.getDisplayColorFromColor(context, c.getInt(0)) == color) {
                    found = c.getString(1)
                    break
                }
            }
            found
        }
    } catch (t: Throwable) {
        Log.w(TAG, "the colour table could not be read", t)
        null
    }
    /** Fills the model's calendar-derived fields from the calendar row, exactly as the UI does. */
    private fun applyCalendar(model: CalendarEventModel, calendarId: Long): Boolean {
        // By id first, then by scanning the list.
        //
        // A local calendar - the offline kind this app creates, with a LOCAL account - is not always
        // readable through its own row uri, and the query comes back with no rows rather than an
        // error. The same calendar is readable through the collection uri, which is how it appears in
        // the calendar list, so falling back to that is what lets an event be created on it. Without
        // the fallback the editor refused with "the event could not be loaded" for the only calendar
        // the user had.
        if (applyCalendarById(model, calendarId)) {
            return true
        }
        return applyCalendarFromList(model, calendarId)
    }

    private fun applyCalendarById(model: CalendarEventModel, calendarId: Long): Boolean = try {
        var applied = false
        context.contentResolver.query(
            ContentUris.withAppendedId(CalendarContract.Calendars.CONTENT_URI, calendarId),
            EditEventHelper.CALENDARS_PROJECTION,
            null,
            null,
            null,
        )?.use { c ->
            if (c.moveToFirst()) {
                EditEventHelper.setModelFromCalendarCursor(model, c, context)
                applied = true
            }
        }
        applied
    } catch (t: Throwable) {
        Log.w(TAG, "calendar row could not be read", t)
        false
    }

    /** The same read, through the collection uri this time. */
    private fun applyCalendarFromList(model: CalendarEventModel, calendarId: Long): Boolean = try {
        var applied = false
        context.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            EditEventHelper.CALENDARS_PROJECTION,
            CalendarContract.Calendars._ID + "=?",
            arrayOf(calendarId.toString()),
            null,
        )?.use { c ->
            if (c.moveToFirst()) {
                EditEventHelper.setModelFromCalendarCursor(model, c, context)
                applied = true
            }
        }
        applied
    } catch (t: Throwable) {
        Log.w(TAG, "calendar row could not be read from the list either", t)
        false
    }

    /**
     * The first reminder method the calendar allows.
     *
     * The allowed set arrives as the calendar's ALLOWED_REMINDERS string, so it is put through the
     * same helper the classic editor uses rather than parsed here; that also covers a calendar that
     * allows nothing, in which case the default method is kept.
     */
    private fun reminderMethod(model: CalendarEventModel): Int {
        val allowed = model.mCalendarAllowedReminders ?: return CalendarContract.Reminders.METHOD_ALERT

        val values = ArrayList<Int>()
        val labels = ArrayList<String>()
        for (method in intArrayOf(
            CalendarContract.Reminders.METHOD_ALERT,
            CalendarContract.Reminders.METHOD_EMAIL,
            CalendarContract.Reminders.METHOD_SMS,
        )) {
            values.add(method)
            labels.add(method.toString())
        }
        com.android.calendar.event.EventViewUtils.reduceMethodList(values, labels, allowed)
        return values.firstOrNull() ?: CalendarContract.Reminders.METHOD_DEFAULT
    }

    // ------------------------------------------------------------------------------- internals

    private fun loadAttendees(
        eventId: Long,
        model: CalendarEventModel,
        original: CalendarEventModel,
    ): Boolean = try {
        context.contentResolver.query(
            CalendarContract.Attendees.CONTENT_URI,
            ATTENDEES_PROJECTION,
            CalendarContract.Attendees.EVENT_ID + "=? AND "
                    + CalendarContract.Attendees.ATTENDEE_EMAIL + " IS NOT NULL",
            arrayOf(eventId.toString()),
            null,
        )?.use { c ->
            while (c.moveToNext()) {
                val id = c.getInt(0)
                val name = c.getString(1)
                val email = c.getString(2)
                val status = c.getInt(3)
                val relationship = c.getInt(4)

                if (relationship == CalendarContract.Attendees.RELATIONSHIP_ORGANIZER
                    && email != null
                ) {
                    val organizerName = if (name.isNullOrEmpty()) email else name
                    model.mOrganizer = email
                    model.mIsOrganizer = model.mOwnerAccount?.equals(email, true) ?: false
                    model.mOrganizerDisplayName = organizerName
                    original.mOrganizer = email
                    original.mIsOrganizer = original.mOwnerAccount?.equals(email, true) ?: false
                    original.mOrganizerDisplayName = organizerName
                }

                // The owner is not stored in the attendee list; the id is kept separately.
                if (email != null && model.mOwnerAccount?.equals(email, true) == true) {
                    model.mOwnerAttendeeId = id
                    model.mSelfAttendeeStatus = status
                    original.mOwnerAttendeeId = id
                    original.mSelfAttendeeStatus = status
                    continue
                }

                val attendee = CalendarEventModel.Attendee(name, email)
                attendee.mStatus = status
                model.addAttendee(attendee)
                original.addAttendee(attendee)
            }
            true
        } ?: false
    } catch (t: Throwable) {
        Log.w(TAG, "attendee query failed", t)
        false
    }

    private fun loadReminders(
        eventId: Long,
        model: CalendarEventModel,
        original: CalendarEventModel,
    ): Boolean = try {
        context.contentResolver.query(
            CalendarContract.Reminders.CONTENT_URI,
            REMINDERS_PROJECTION,
            CalendarContract.Reminders.EVENT_ID + "=?",
            arrayOf(eventId.toString()),
            null,
        )?.use { c ->
            while (c.moveToNext()) {
                val entry = CalendarEventModel.ReminderEntry.valueOf(c.getInt(0), c.getInt(1))
                model.mReminders.add(entry)
                original.mReminders.add(entry)
            }
            true
        } ?: false
    } catch (t: Throwable) {
        Log.w(TAG, "reminder query failed", t)
        false
    }

    /**
     * The calendars this app may write to, without the to-do list's own calendar.
     *
     * To-dos are all-day events in a local calendar of their own (see TodoStore), which is what puts
     * them in every view's all-day strip. That calendar is not a place to write a normal event into:
     * it holds one entry per day, its events are read as a checklist, and picking it here produced an
     * event the to-do screen would list as a to-do. The account is the way to recognise it - the
     * display name is localised and the user may rename nothing, but the account name is fixed.
     *
     * Exposed so the editor can tell "there is no calendar" apart from "this event would
     * not load": the first has an answer the user can act on, the second does not.
     */
    fun writableCalendars(): List<CalendarOption> = loadCalendars()

    private fun loadCalendars(): List<CalendarOption> = try {
        val out = ArrayList<CalendarOption>()
        context.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            CALENDARS_PROJECTION,
            null,            null,
            CalendarContract.Calendars.ACCOUNT_NAME + " ASC",
        )?.use { c ->
            while (c.moveToNext()) {
                // Only calendars this app is allowed to write to.
                if (c.getInt(3) < CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR) continue
                // The to-do calendar is written only by TodoStore, never by the event editor.
                if (c.getString(5) == TodoStore.ACCOUNT_NAME) continue
                out.add(
                    CalendarOption(
                        id = c.getLong(0),
                        name = c.getString(1).orEmpty(),
                        color = c.getInt(2),
                        ownerAccount = c.getString(4).orEmpty(),
                        accountType = c.getString(6).orEmpty(),
                    )
                )
            }
        }
        out
    } catch (t: Throwable) {
        Log.w(TAG, "calendar query failed", t)
        emptyList()
    }

    companion object {
        const val TAG = "M3eEventEditor"

        /** All-day events are stored against this zone, per CalendarProvider. */
        private const val UTC = "UTC"

        /**
         * Offered when the account's own colour table is empty.
         *
         * Android's calendar palette, the same ten the app's classic picker falls back to - not a
         * palette invented here.
         */
        val DEFAULT_EVENT_COLORS = listOf(
            0xFFE57373.toInt(), 0xFFF06292.toInt(), 0xFFBA68C8.toInt(), 0xFF9575CD.toInt(),
            0xFF7986CB.toInt(), 0xFF64B5F6.toInt(), 0xFF4FC3F7.toInt(), 0xFF4DD0E1.toInt(),
            0xFF4DB6AC.toInt(), 0xFF81C784.toInt(),
        )
        /** Matches GeneralPreferences.REMINDER_DEFAULT_TIME. */
        const val DEFAULT_REMINDER_MINUTES = 10

        /** Matches GeneralPreferences.NO_REMINDER. */
        const val NO_REMINDER = -1

        /** The second default reminder: at the event's start. */
        const val AT_START_REMINDER_MINUTES = 0

        /** Same preference key the classic editor reads for its default reminder. */
        const val KEY_DEFAULT_REMINDER = "preferences_default_reminder"

        /**
         * Intent extra keys for the values a caller can seed a new event with. They match the ones
         * ComposeEventEditorActivity publishes; kept as plain strings here because the repository does
         * not depend on that activity.
         */
        const val REMINDER_EXTRA_KEY = "m3eReminderMinutes"
        const val COLOR_EXTRA_KEY = "m3eEventColor"

        const val ALL_DAY_MILLIS = 24L * 60L * 60L * 1000L

        /** Matches GeneralPreferences.DEFAULT_EVENT_DURATION_MINUTES. */
        const val DEFAULT_DURATION_MILLIS = 60L * 60L * 1000L

        // Defined locally: EditEventHelper's own versions are package private.
        val ATTENDEES_PROJECTION = arrayOf(
            CalendarContract.Attendees._ID,                     // 0
            CalendarContract.Attendees.ATTENDEE_NAME,           // 1
            CalendarContract.Attendees.ATTENDEE_EMAIL,          // 2
            CalendarContract.Attendees.ATTENDEE_STATUS,         // 3
            CalendarContract.Attendees.ATTENDEE_RELATIONSHIP,   // 4
        )

        val REMINDERS_PROJECTION = arrayOf(
            CalendarContract.Reminders.MINUTES,                 // 0
            CalendarContract.Reminders.METHOD,                  // 1
        )

        val CALENDARS_PROJECTION = arrayOf(            CalendarContract.Calendars._ID,                     // 0
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,   // 1
            CalendarContract.Calendars.CALENDAR_COLOR,          // 2
            CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL,   // 3
            // 4 and on are only read by loadCalendars; setModelFromCalendarCursor uses its own
            // projection, which is EditEventHelper.CALENDARS_PROJECTION.
            CalendarContract.Calendars.OWNER_ACCOUNT,           // 4
            CalendarContract.Calendars.ACCOUNT_NAME,            // 5
            CalendarContract.Calendars.ACCOUNT_TYPE,            // 6
        )
    }
}
