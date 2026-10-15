/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * A one-off repair of all-day events written by an earlier version.
 *
 * CalendarProvider stores an all-day event in UTC: DTSTART at UTC midnight, and EVENT_TIMEZONE as
 * "UTC". An earlier version of the countdown feature wrote the caller's local midnight together with
 * the device's own zone. The provider builds its Instances rows only for events that follow the UTC
 * rule, so those events existed - they were visible in the agenda, which reads Events - but had no
 * Instances row, and so were missing from the month, week, day and three-day views.
 *
 * The events are corrected in place rather than left for the user to recreate: the ones already in
 * their calendar are the ones they care about. Only events this app's countdown feature produced are
 * touched, identified by the countdown that still points at them, so nothing the user made by hand is
 * rewritten.
 */
package com.android.calendar.countdown

import com.android.calendar.event.compose.queryRowById
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.provider.CalendarContract
import android.util.Log
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

object CountdownAllDayMigration {

    private const val TAG = "CountdownMigration"

    /** Recorded once the repair has run, so it never runs twice. */
    private const val PREF_DONE = "pref_countdown_allday_migrated_version"

    /**
     * Corrects every countdown-linked all-day event that is not stored as UTC.
     *
     * Runs on whatever thread the caller uses; callers should use a background one.
     *
     * @return how many events were corrected.
     */
    fun run(context: Context): Int {
        val prefs = context.getSharedPreferences("com.android.calendar.prefs", Context.MODE_PRIVATE)

        // Keyed by the app version, not a plain flag.
        //
        // A plain flag meant an install that had already run it never ran it again - including after
        // this repair itself was corrected. The check is cheap and does nothing when there is nothing
        // to fix, so re-running it once per version is the safe side to be on.
        val version = versionKey(context)
        if (prefs.getString(PREF_DONE, null) == version) {
            return 0
        }

        var repaired = 0
        val zone = ZoneId.systemDefault()
        try {
            for (countdown in CountdownStore.all(context)) {
                if (countdown.time != null) {
                    // A countdown with a time of day is a timed event, which belongs in local time.
                    continue
                }
                val eventId = countdown.eventId ?: continue
                if (repairEvent(context, eventId, zone)) {
                    repaired++
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "the all-day repair could not finish", t)
            // Not marked done: a failure part way through should be retried on the next start.
            return repaired
        }

        prefs.edit().putString(PREF_DONE, version).apply()
        if (repaired > 0) {
            Log.i(TAG, "repaired $repaired all-day event(s)")
        }
        return repaired
    }

    /** The version this run belongs to. */
    private fun versionKey(context: Context): String = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"
    } catch (t: Throwable) {
        "?"
    }

    /**
     * True when the event needed correcting and was corrected.
     *
     * Left alone unless the event is all-day and its stored zone is not already UTC - a correct event
     * is not touched, so running this twice would be harmless even without the flag.
     */
    private fun repairEvent(context: Context, eventId: Long, zone: ZoneId): Boolean {
        val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
        val projection = arrayOf(
            CalendarContract.Events.DTSTART,
            CalendarContract.Events.DTEND,
            CalendarContract.Events.ALL_DAY,
            CalendarContract.Events.EVENT_TIMEZONE,
        )
        var start = 0L
        var end = 0L
        var allDay = false
        var timeZone: String? = null
        try {
            context.contentResolver.query(uri, projection, null, null, null)?.use { c ->
                if (!c.moveToFirst()) {
                    return false
                }
                start = c.getLong(0)
                end = c.getLong(1)
                allDay = c.getInt(2) == 1
                timeZone = c.getString(3)
            } ?: return false
        } catch (t: Throwable) {
            Log.w(TAG, "could not read event $eventId", t)
            return false
        }

        if (!allDay || "UTC".equals(timeZone, ignoreCase = true)) {
            return false
        }

        // The stored start is local midnight of the intended day. Its date is what matters; the UTC
        // midnight of that same date is what the provider wants.
        val day = Instant.ofEpochMilli(start).atZone(zone).toLocalDate()
        val utcStart = day.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        // The end is one day later, or however many days the event spanned.
        val endDay = Instant.ofEpochMilli(end).atZone(zone).toLocalDate()
        val utcEnd = endDay.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

        val values = ContentValues().apply {
            put(CalendarContract.Events.DTSTART, utcStart)
            put(CalendarContract.Events.DTEND, utcEnd)
            put(CalendarContract.Events.EVENT_TIMEZONE, "UTC")
            put(CalendarContract.Events.EVENT_END_TIMEZONE, "UTC")
        }
        return try {
            // asSyncAdapter is not used: this is the app writing its own row, and the provider allows
            // a plain update for a calendar the app may modify.
            context.contentResolver.update(uri, values, null, null) > 0
        } catch (t: Throwable) {
            Log.w(TAG, "could not repair event $eventId", t)
            false
        }
    }

    /** Exposed for tests: the UTC midnight of the day [localMidnight] falls on. */
    fun utcMidnightFor(localMidnight: Long, zone: ZoneId = ZoneId.systemDefault()): Long {
        val day: LocalDate = Instant.ofEpochMilli(localMidnight).atZone(zone).toLocalDate()
        return day.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    }
}
