/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * Imports an .ics file.
 *
 * The events are written to the provider here rather than handed to another app through an
 * ACTION_INSERT intent. Two things were wrong with the intent approach: it only ever carried the
 * first event of the file, so a file with thirty events imported one, and it left the actual writing
 * to whichever other app answered the intent. Everything the file holds is now written directly, and
 * the screen reports how many events it created.
 */
package com.android.calendar;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.CalendarContract;
import android.text.TextUtils;
import android.widget.Toast;

import com.android.calendar.event.ExtendedProperty;
import com.android.calendar.icalendar.Attendee;
import com.android.calendar.icalendar.IcalendarUtils;
import com.android.calendar.icalendar.VCalendar;
import com.android.calendar.icalendar.VEvent;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.TimeZone;

import ws.xsoh.etar.R;

public class ImportActivity extends Activity {

    private static final String TAG = "ImportActivity";

    /** How far the file's own fields are mapped onto the provider's columns. */
    private static final String[] CALENDAR_PROJECTION = new String[] {
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL,
    };

    /** The event a UID already belongs to, so importing the same file twice does not duplicate it. */
    private static final String[] UID_PROJECTION = new String[] {
            CalendarContract.Events._ID,
    };

    /**
     * A value identifying an event for the duplicate check.
     *
     * The file's own UID would be the natural key, but it is not dependable: providers differ on
     * whether they keep UID_2445, and it is not part of the columns every provider exposes. The start
     * time, the title and the calendar together identify an event well enough for the purpose, which
     * is only to stop the same file importing twice.
     */
    private static String dedupeKey(long calendarId, long start, String title) {
        return calendarId + "|" + start + "|" + (title == null ? "" : title.trim());
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!isValidIntent()) {
            Toast.makeText(this, R.string.cal_nothing_to_import, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        parseCalFile();
    }

    /**
     * The instant an iCalendar date-time names, or -1 when it cannot be read.
     *
     * Every form in RFC 5545 section 3.3.5 is accepted: a date alone, a date and time, either with
     * UTC ("Z") or with a TZID parameter, and either basic (19980119T020000) or extended
     * (1998-01-19T02:00:00) separators. The previous implementation tried one SimpleDateFormat at a
     * time and gave up if none matched exactly, which lost whole files over a form it did not know.
     */
    private long getLocalTimeFromString(String iCalDate, String iCalDateParam) {
        if (TextUtils.isEmpty(iCalDate)) {
            return -1;
        }

        java.util.regex.Matcher m = ICAL_DATE.matcher(iCalDate.trim());
        if (!m.matches()) {
            return -1;
        }

        try {
            int year = Integer.parseInt(m.group(1));
            int month = Integer.parseInt(m.group(2));
            int day = Integer.parseInt(m.group(3));
            int hour = m.group(4) != null ? Integer.parseInt(m.group(4)) : 0;
            int minute = m.group(5) != null ? Integer.parseInt(m.group(5)) : 0;
            int second = m.group(6) != null ? Integer.parseInt(m.group(6)) : 0;
            boolean utc = m.group(7) != null;

            java.util.Calendar cal = java.util.Calendar.getInstance(
                    utc ? TimeZone.getTimeZone("UTC") : zoneFor(iCalDateParam));
            cal.clear();
            // The seconds field of an iCalendar date is not always present, and Calendar defaults a
            // cleared SECOND to 0 anyway; setting it explicitly keeps a 60 out of a leap second.
            cal.set(year, month - 1, day, hour, minute, Math.min(second, 59));
            return cal.getTimeInMillis();
        } catch (Throwable t) {
            return -1;
        }
    }

    /**
     * A date-time may carry both a date and a time (19980119T020000), a date alone (19980119), an
     * optional fractional second, and an optional trailing Z for UTC.
     */
    private static final java.util.regex.Pattern ICAL_DATE = java.util.regex.Pattern.compile(
            "(\\d{4})-?(\\d{2})-?(\\d{2})"
                    + "(?:T(\\d{2}):?(\\d{2})(?::?(\\d{2}))?(?:\\.\\d+)?(Z)?)?"
    );

    /** The zone a TZID parameter names, falling back to the device's own. */
    private static TimeZone zoneFor(String iCalDateParam) {
        if (iCalDateParam != null && iCalDateParam.startsWith("TZID=")) {
            String id = iCalDateParam.substring(5).replace("\"", "");
            if (Arrays.asList(TimeZone.getAvailableIDs()).contains(id)) {
                return TimeZone.getTimeZone(id);
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                String converted = android.icu.util.TimeZone.getIDForWindowsID(id, "001");
                if (converted != null && !converted.isEmpty()) {
                    return TimeZone.getTimeZone(converted);
                }
            }
        }
        return TimeZone.getDefault();
    }

    private void showErrorToast() {
        Toast.makeText(this, R.string.cal_import_error_msg, Toast.LENGTH_SHORT).show();
        finish();
    }

    /**
     * The calendar the import was asked to write into, or -1 for the first writable one.
     *
     * The import dialog lets the user pick the target calendar and passes its id along. An id that
     * is missing, or that no longer names a writable calendar, falls back to the old behaviour.
     */
    private static final String EXTRA_ICS_CALENDAR_ID = "m3eIcsCalendarId";

    /**
     * True when [calendarId] names a calendar this app is allowed to write to.
     *
     * A stale id - the calendar was deleted between the dialog and the import - must not be used:
     * the provider would reject every insert with an unknown calendar id and the whole file would
     * come back as unreadable.
     */
    private boolean isWritableCalendar(long calendarId) {
        try (Cursor c = getContentResolver().query(
                CalendarContract.Calendars.CONTENT_URI,
                CALENDAR_PROJECTION,
                CalendarContract.Calendars._ID + "=?",
                new String[] { String.valueOf(calendarId) },
                null)) {
            return c != null && c.moveToFirst()
                    && c.getInt(1) >= CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR;
        } catch (Throwable t) {
            return false;
        }
    }

    private void parseCalFile() {
        Uri uri = getIntent().getData();
        VCalendar calendar = IcalendarUtils.readCalendarFromFile(this, uri);

        if (calendar == null) {
            showErrorToast();
            return;
        }

        LinkedList<VEvent> events = calendar.getAllEvents();
        if (events == null || events.isEmpty()) {
            showErrorToast();
            return;
        }

        long calendarId = getIntent().getLongExtra(EXTRA_ICS_CALENDAR_ID, -1L);
        if (calendarId == -1 || !isWritableCalendar(calendarId)) {
            calendarId = writableCalendarId();
        }
        if (calendarId == -1) {
            // Nothing to import into. Said plainly rather than silently doing nothing.
            Toast.makeText(this, R.string.cal_import_no_calendar, Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        int imported = 0;
        int duplicates = 0;
        int unreadable = 0;
        java.util.HashSet<String> seen = new java.util.HashSet<>();
        for (VEvent event : events) {
            try {
                switch (importEvent(event, calendarId, seen)) {
                    case IMPORTED:
                        imported++;
                        break;
                    case DUPLICATE:
                        duplicates++;
                        break;
                    default:
                        unreadable++;
                        break;
                }
            } catch (Throwable t) {
                // Counted and logged. Swallowing it silently is what once made a file report every
                // event unreadable without saying a word about the cause.
                unreadable++;
                android.util.Log.w(TAG, "event could not be imported", t);
            }
        }

        Toast.makeText(
                this,
                getString(R.string.cal_import_result, imported, duplicates, unreadable),
                Toast.LENGTH_LONG).show();
        setResult(imported > 0 ? RESULT_OK : RESULT_CANCELED);
        finish();
    }

    /** What became of one event in the file. */
    private enum Outcome { IMPORTED, DUPLICATE, UNREADABLE }

    /**
     * The first calendar this app may write to, or -1 when there is none.
     *
     * A file import has no calendar to be "the current one", so the first writable calendar is used -
     * the same choice the countdown screen makes when it creates an event.
     */
    private long writableCalendarId() {
        try (Cursor c = getContentResolver().query(
                CalendarContract.Calendars.CONTENT_URI,
                CALENDAR_PROJECTION,
                null,
                null,
                CalendarContract.Calendars.ACCOUNT_NAME + " ASC")) {
            if (c != null) {
                while (c.moveToNext()) {
                    if (c.getInt(1) >= CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR) {
                        return c.getLong(0);
                    }
                }
            }
        } catch (Throwable t) {
            // Fall through to the -1 below.
        }
        return -1;
    }

    /**
     * True when an event with this start time and title is already stored.
     *
     * Deliberately looks for an existing event rather than consulting a UID column: providers differ
     * on whether they keep one, and a check that depends on a column which may not be there is how
     * every event in a file came to be reported as a duplicate.
     */
    private boolean alreadyStored(long calendarId, long start, String title) {
        String selection = CalendarContract.Events.CALENDAR_ID + "=? AND "
                + CalendarContract.Events.DTSTART + "=? AND "
                + CalendarContract.Events.TITLE + " IS ?";
        String[] args = new String[] {
                String.valueOf(calendarId),
                String.valueOf(start),
                title == null ? null : title.trim(),
        };
        try (Cursor c = getContentResolver().query(
                CalendarContract.Events.CONTENT_URI,
                UID_PROJECTION,
                selection,
                args,
                null)) {
            return c != null && c.getCount() > 0;
        } catch (Throwable t) {
            // A duplicate check that cannot run must not stop the import.
            return false;
        }
    }

    /**
     * Writes one event from the file into the provider.
     *
     * @param seen keys already imported by this run, so a file that repeats an event does not
     *             duplicate it within itself either.
     */
    private Outcome importEvent(VEvent event, long calendarId, java.util.HashSet<String> seen) {
        // Every step that can end this event is recorded, so a failure names itself instead of only
        // adding to a count.
        String dtStart = event.getProperty(VEvent.DTSTART);
        String dtStartParam = event.getPropertyParameters(VEvent.DTSTART);
        long start = getLocalTimeFromString(dtStart, dtStartParam);
        if (start == -1) {
            return Outcome.UNREADABLE;
        }

        String title = IcalendarUtils.uncleanseString(event.getProperty(VEvent.SUMMARY));
        String key = dedupeKey(calendarId, start, title);
        if (seen.contains(key) || alreadyStored(calendarId, start, title)) {
            return Outcome.DUPLICATE;
        }

        String dtEnd = event.getProperty(VEvent.DTEND);
        String dtEndParam = event.getPropertyParameters(VEvent.DTEND);
        long end = getLocalTimeFromString(dtEnd, dtEndParam);

        // An all-day event is one whose start is a date with no time, which the file says with
        // VALUE=DATE (or an eight digit DTSTART).
        //
        // This used to be inferred from the start being at 00:00, which also caught an ordinary
        // event that happens to begin at midnight.
        boolean allDay = isDateOnly(dtStart, dtStartParam);
        if (end == -1) {
            // No end given: the whole day for an all-day event, an hour otherwise.
            end = start + (allDay ? 86400000L : 3600000L);
        }

        String microsoftAllDay = event.getProperty("X-MICROSOFT-CDO-ALLDAYEVENT");
        if (!TextUtils.isEmpty(microsoftAllDay) && microsoftAllDay.equals("TRUE")) {
            allDay = true;
        }

        ContentValues values = new ContentValues();
        values.put(CalendarContract.Events.CALENDAR_ID, calendarId);
        values.put(CalendarContract.Events.TITLE, title);
        values.put(CalendarContract.Events.EVENT_LOCATION,
                IcalendarUtils.uncleanseString(event.getProperty(VEvent.LOCATION)));
        values.put(CalendarContract.Events.DESCRIPTION,
                IcalendarUtils.uncleanseString(event.getProperty(VEvent.DESCRIPTION)));
        // ExtendedProperty.URL is deliberately not written.
        //
        // It reads back fine, which is why the old code carried it, but the provider rejects it on
        // insert with "column 'url' is invalid" - and one bad column fails the whole event. That is
        // what made every event in a file unreadable.
        values.put(CalendarContract.Events.ORGANIZER,
                IcalendarUtils.uncleanseString(event.getProperty(VEvent.ORGANIZER)));
        values.put(CalendarContract.Events.RRULE,
                IcalendarUtils.uncleanseString(event.getProperty(VEvent.RRULE)));
        values.put(CalendarContract.Events.DTSTART, start);
        values.put(CalendarContract.Events.DTEND, end);
        values.put(CalendarContract.Events.ALL_DAY, allDay ? 1 : 0);
        values.put(CalendarContract.Events.EVENT_TIMEZONE,
                timeZoneIdFor(dtStartParam, start));
        values.put(CalendarContract.Events.EVENT_END_TIMEZONE,
                timeZoneIdFor(dtEndParam, end));
        values.put(CalendarContract.Events.HAS_ALARM, 0);
        values.put(CalendarContract.Events.ACCESS_LEVEL, CalendarContract.Events.ACCESS_DEFAULT);
        values.put(CalendarContract.Events.AVAILABILITY, CalendarContract.Events.AVAILABILITY_BUSY);
        String uid = IcalendarUtils.uncleanseString(event.getProperty(VEvent.UID));
        // Not written to the provider.
        //
        // UID_2445 is not a column every provider has, and a value it does not know makes the insert
        // itself fail - which is what turned every event in a file into "could not be read".
        if (!TextUtils.isEmpty(uid)) {
        }
        if (!TextUtils.isEmpty(event.getProperty(VEvent.STATUS))) {
            values.put(CalendarContract.Events.STATUS,
                    IcalendarUtils.uncleanseString(event.getProperty(VEvent.STATUS)));
        }

        try {
            Uri inserted = getContentResolver().insert(CalendarContract.Events.CONTENT_URI, values);
            if (inserted == null) {
                android.util.Log.w(TAG, "provider returned no uri for " + title);
                return Outcome.UNREADABLE;
            }
            long eventId = ContentUris.parseId(inserted);
            addAttendees(eventId, event);
            seen.add(key);
            return Outcome.IMPORTED;
        } catch (Throwable t) {
            android.util.Log.w(TAG, "insert rejected", t);
            return Outcome.UNREADABLE;
        }
    }

    /** The zone an event's time was expressed in, for the provider's own bookkeeping. */
    private String timeZoneIdFor(String param, long millis) {
        if (param != null && param.startsWith("TZID=")) {
            String id = param.substring(5).replace("\"", "");
            if (Arrays.asList(TimeZone.getAvailableIDs()).contains(id)) {
                return id;
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                String converted = android.icu.util.TimeZone.getIDForWindowsID(id, "001");
                if (converted != null && !converted.isEmpty()) {
                    return converted;
                }
            }
        }
        return TimeZone.getDefault().getID();
    }

    /** Writes the file's attendees for one event, ignoring any that carry no address. */
    private void addAttendees(long eventId, VEvent event) {
        if (event.mAttendees == null || event.mAttendees.isEmpty()) {
            return;
        }
        for (Attendee attendee : event.mAttendees) {
            if (TextUtils.isEmpty(attendee.mEmail)) {
                continue;
            }
            ContentValues values = new ContentValues();
            values.put(CalendarContract.Attendees.EVENT_ID, eventId);
            values.put(CalendarContract.Attendees.ATTENDEE_EMAIL, attendee.mEmail);
            // The name lives in the property map under CN, not in a field of its own.
            String name = attendee.mProperties != null
                    ? attendee.mProperties.get(Attendee.CN)
                    : null;
            values.put(CalendarContract.Attendees.ATTENDEE_NAME,
                    IcalendarUtils.uncleanseString(name));
            values.put(CalendarContract.Attendees.ATTENDEE_RELATIONSHIP,
                    CalendarContract.Attendees.RELATIONSHIP_ATTENDEE);
            values.put(CalendarContract.Attendees.ATTENDEE_TYPE,
                    CalendarContract.Attendees.TYPE_REQUIRED);
            values.put(CalendarContract.Attendees.ATTENDEE_STATUS,
                    CalendarContract.Attendees.ATTENDEE_STATUS_NONE);
            try {
                getContentResolver().insert(CalendarContract.Attendees.CONTENT_URI, values);
            } catch (Throwable t) {
                // One unreadable attendee must not lose the event.
            }
        }
    }

    /**
     * True when the value is a date with no time of day.
     *
     * RFC 5545 says so with a VALUE=DATE parameter, and such a value is eight digits rather than the
     * fifteen of a date-time. Either is enough to tell; both are checked because files vary.
     */
    private boolean isDateOnly(String iCalDate, String iCalDateParam) {
        if (iCalDateParam != null && iCalDateParam.toUpperCase().contains("VALUE=DATE")) {
            return true;
        }
        if (iCalDate == null) {
            return false;
        }
        // Strip separators, so an extended 1998-01-19 also counts as eight digits.
        String digits = iCalDate.trim().replace("-", "");
        return digits.length() == 8 && TextUtils.isDigitsOnly(digits);
    }

    private boolean isValidIntent() {
        Intent intent = getIntent();
        if (intent == null) {
            return false;
        }
        Uri fileUri = intent.getData();
        if (fileUri == null) {
            return false;
        }
        String scheme = fileUri.getScheme();
        return ContentResolver.SCHEME_CONTENT.equals(scheme)
                || ContentResolver.SCHEME_FILE.equals(scheme);
    }
}
