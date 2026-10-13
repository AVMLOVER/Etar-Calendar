/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * Host for the Material 3 Expressive event editor.
 *
 * It must extend AbstractCalendarActivity: EditEventHelper's constructor casts its Context to
 * that type in order to reach the AsyncQueryService used for the batched write.
 */
package com.android.calendar.event.compose

import androidx.compose.ui.res.stringResource
import androidx.compose.material3.Text
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Button
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Column
import com.android.calendar.Utils
import com.android.calendar.AllInOneActivity
import android.content.Intent
import android.os.Bundle
import android.provider.CalendarContract
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.android.calendar.AbstractCalendarActivity
import com.android.calendar.CalendarEventModel
import com.android.calendar.settings.compose.EtarM3ETheme
import com.android.calendar.settings.compose.SettingsRepository
import ws.xsoh.etar.R

class ComposeEventEditorActivity : AbstractCalendarActivity() {

    private var repository: EventEditorRepository? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val eventId = intent.getLongExtra(EXTRA_EVENT_ID, -1L)
        val readOnly = intent.getBooleanExtra(EXTRA_READ_ONLY, false)
        val repo = SettingsRepository(this)
        // The repository is built with the Activity (not the application context) because
        // EditEventHelper requires an AbstractCalendarActivity.
        val editorRepo = EventEditorRepository(this)
        repository = editorRepo

        setContent {
            val themeMode = remember { repo.string(KEY_THEME, "system") }
            val dark = when (themeMode) {
                "light" -> false
                "dark", "black" -> true
                else -> isSystemInDarkTheme()
            }
            

            EtarM3ETheme(darkTheme = dark) {
                EditorHost(
                    eventId = eventId,
                    intent = intent,
                    repository = editorRepo,
                    readOnly = readOnly,
                )
            }
        }
    }

    private fun applyAndSave(snapshot: EventEditorRepository.Snapshot, edited: EditedEvent) {
        val model = snapshot.model
        model.mTitle = edited.title
        model.mLocation = edited.location
        model.mDescription = edited.description
        model.mUrl = edited.url
        model.mAllDay = edited.allDay
        model.mStart = edited.start
        model.mEnd = edited.end
        model.mCalendarId = edited.calendarId
        model.mCalendarDisplayName = edited.calendarName

        // Properties the form used to drop on the floor. Leaving them alone keeps whatever was
        // loaded; the sentinels mean "the user did not change this".
        model.mRrule = edited.rrule
        if (edited.availability != NOT_SET) {
            model.mAvailability = edited.availability
            model.mAvailabilityExplicitlySet = true
        }
        if (edited.accessLevel != NOT_SET) {
            model.mAccessLevel = edited.accessLevel
        }
        if (edited.eventColor != 0) {
            // setEventColor also marks the colour as explicitly chosen, which is what makes it
            // survive the calendar-change handling in the save path.
            model.setEventColor(edited.eventColor)
        }
        if (edited.selfAttendeeStatus != NOT_SET) {
            model.mSelfAttendeeStatus = edited.selfAttendeeStatus
        }

        // Reminders are rewritten from the list, so an event that had none keeps none and one the
        // user edited gets exactly what the form shows. The method is the one already on each
        // existing reminder, or an alert for a reminder the user just added.
        val methods = snapshot.model.mReminders.associate { it.minutes to it.method }
        model.mReminders = ArrayList(
            edited.reminderMinutes.sortedDescending().map { minutes ->
                CalendarEventModel.ReminderEntry.valueOf(
                    minutes,
                    methods[minutes] ?: CalendarContract.Reminders.METHOD_ALERT,
                )
            }
        )
        model.mHasAlarm = model.mReminders.isNotEmpty()

        // The event's own zone, which the classic editor's "time zone" button set.
        model.mTimezone = edited.timezone

        // Guests. EditEventHelper.saveEvent rewrites the attendee table from this map, so it has to
        // hold exactly what the form shows - an empty map means no guests, which is the intent when
        // the user removed them all.
        model.mAttendeesList.clear()
        edited.attendees.distinct().forEach { email ->
            model.mAttendeesList[email] = CalendarEventModel.Attendee("", email)
        }
        // Adding or removing guests is only meaningful for an event that carries attendee data.
        if (edited.attendees.isNotEmpty()) {
            model.mHasAttendeeData = true
        }

        val ok = repository?.save(model, snapshot.original) ?: false
        Toast.makeText(
            this,
            if (ok) R.string.m3e_event_saved else R.string.m3e_event_save_failed,
            Toast.LENGTH_SHORT,
        ).show()
        if (ok) {
            // The countdown is written only once the event itself saved: a countdown for an event
            // that failed to save would point at nothing.
            if (edited.addToCountdown) {
                createCountdown(edited, eventId = model.mId, snapshot = snapshot)
            }
            setResult(RESULT_OK)
            finish()
        }
    }

    /**
     * Adds a countdown for the event that was just saved, using the event's title and start date.
     *
     * Only the date is carried over, not the time: a countdown counts towards a day, and the screen
     * treats an entry without a time as midnight anyway.
     *
     * @param eventId the event's row id, recorded on the countdown so the pair can be found from
     *                either side. Without it the agenda cannot tell that an event has a countdown,
     *                so its swipe-to-delete never offers to remove one - which is exactly what
     *                happened while this was omitted.
     */
    private fun createCountdown(
        edited: EditedEvent,
        eventId: Long,
        snapshot: EventEditorRepository.Snapshot,
    ) {
        val zone = java.time.ZoneId.systemDefault()
        val startInstant = java.time.Instant.ofEpochMilli(edited.start).atZone(zone)
        val date = startInstant.toLocalDate()

        // A timed event's start time becomes the countdown's time.
        //
        // Only the date used to be carried over, on the reasoning that a countdown counts towards a
        // day - but a countdown made from a 09:30 meeting then counted to midnight, which is not the
        // moment of the event it was made from. An all-day event has no time of day to carry, so it
        // stays date-only, which is also what marks the countdown as all-day.
        val timeIso = if (edited.allDay) {
            null
        } else {
            startInstant.toLocalTime()
                .truncatedTo(java.time.temporal.ChronoUnit.MINUTES)
                .toString()
        }

        // The countdown takes the event's own colour when the event has one, and the colour picked in
        // this dialog otherwise.
        //
        // The pairing is meant to be recognisable, and the event's colour is the one that will
        // actually be stored: a custom EVENT_COLOR is only honoured when the account's colour table
        // has a key for it, and without one the event keeps its calendar's colour. Matching the
        // countdown to what the event really looks like is therefore the only way the two can agree.
        // The picked colour is the fallback, and it is what the countdown screen itself would use.
        com.android.calendar.countdown.CountdownStore.add(
            this,
            com.android.calendar.countdown.Countdown(
                id = com.android.calendar.countdown.CountdownStore.newId(),
                name = edited.title.ifBlank { getString(R.string.m3e_countdown_default_name) },
                color = eventDisplayColor(snapshot, edited.countdownColor),
                dateIso = date.toString(),
                timeIso = timeIso,
                repeatYearly = false,
                eventId = eventId.takeIf { it > 0L },
                // The same defaults the countdown screen creates with, so a countdown made from here
                // is the same object as one made there and can be edited there afterwards - including
                // its background picture and the transparent-background switch.
                backgroundImageUri = edited.countdownBackgroundImage,
                transparentBackground = edited.countdownTransparent,
            ),
        )
        com.android.calendar.widget.CountdownWidgetUpdater.updateAll(this)
    }

    /** The colour the saved event is actually shown in, falling back to [fallback]. */
    private fun eventDisplayColor(
        snapshot: EventEditorRepository.Snapshot,
        fallback: Int,
    ): Int {
        val model = snapshot.model
        return if (model.isEventColorInitialized) model.eventColor else model.calendarColor
    }

    /**
     * Deletes the event and leaves the screen.
     *
     * The screen has already confirmed with the user, so this goes straight to the provider - see
     * EventEditorRepository.deleteEvent for why DeleteEventHelper is not used.
     */
    private fun deleteEvent(snapshot: EventEditorRepository.Snapshot) {
        val done = repository?.deleteEvent(snapshot.model.mId) ?: false
        if (!done) {
            Toast.makeText(this, R.string.m3e_event_delete_failed, Toast.LENGTH_SHORT).show()
            return
        }

        // Back to the calendar itself, not to whatever opened this editor.
        //
        // The editor is reached through the agenda or the event details screen, and simply finishing
        // here handed control back to that middle screen - which was still showing the event that had
        // just been deleted. CLEAR_TOP takes the whole chain with it (the agenda and details screens
        // sit above AllInOneActivity in the stack), and SINGLE_TOP reuses the calendar instance
        // instead of stacking a second one, so it also picks up the change.
        startActivity(
            Intent(this, AllInOneActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                putExtra(Utils.INTENT_KEY_VIEW_TYPE, Utils.INTENT_VALUE_VIEW_TYPE_MONTH)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
        )
        setResult(RESULT_OK)
        finish()
    }

    @OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
    @Composable
    private fun EditorHost(
        eventId: Long,
        intent: android.content.Intent,
        repository: EventEditorRepository,
        readOnly: Boolean,
    ) {
        var loading by remember { mutableStateOf(true) }
        var snapshot by remember { mutableStateOf<EventEditorRepository.Snapshot?>(null) }

        /**
         * True when the only reason there is no editor is that the app has no calendar to write to.
         *
         * A new event needs somewhere to go, and with no calendar at all the editor cannot offer one.
         * That is a different situation from an event that failed to load, and it has a different
         * answer: add a calendar first. Showing the "could not be loaded" message there told the user
         * their event was broken when the calendar was simply missing.
         */
        var needsCalendar by remember { mutableStateOf(false) }

        LaunchedEffect(eventId) {
            snapshot = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                // No event id means "new event": build the model from the intent that asked for it
                // rather than reading one out of the database.
                if (eventId == -1L) {
                    val created = repository.create(intent)
                    // Only for a new event: an existing one that will not load is a different fault.
                    needsCalendar = created == null && repository.writableCalendars().isEmpty()
                    created
                } else {
                    repository.load(eventId)
                }
            }
            loading = false
        }

        val current = snapshot
        when {
            loading -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) { LoadingIndicator() }

            current == null && needsCalendar -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(32.dp),
                ) {
                    androidx.compose.material3.Text(
                        text = androidx.compose.ui.res.stringResource(
                            R.string.m3e_event_needs_calendar
                        ),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(16.dp))
                    androidx.compose.material3.Button(
                        onClick = {
                            // The calendar screen, which is where a calendar is added - offline,
                            // CalDAV or through another app. The editor closes: once a calendar
                            // exists, creating the event is a fresh tap.
                            startActivity(
                                Intent(
                                    this@ComposeEventEditorActivity,
                                    com.android.calendar.selectcalendars.compose
                                        .ComposeCalendarsActivity::class.java,
                                )
                            )
                            finish()
                        },
                    ) {
                        Text(stringResource(R.string.m3e_event_add_calendar))
                    }
                }
            }

            current == null -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                // The safety gate refused: better to show this than to risk dropping the guest
                // list or the reminders of an event that could not be read completely.
                androidx.compose.material3.Text(
                    text = androidx.compose.ui.res.stringResource(
                        R.string.m3e_event_editor_unavailable
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            else -> M3eEventEditorScreen(
                initialTitle = current.model.mTitle.orEmpty(),
                initialLocation = current.model.mLocation.orEmpty(),
                initialDescription = current.model.mDescription.orEmpty(),
                initialUrl = current.model.mUrl.orEmpty(),
                initialAllDay = current.model.mAllDay,
                initialStart = current.model.mStart,
                initialEnd = current.model.mEnd,
                initialCalendarId = current.model.mCalendarId,
                initialRrule = current.model.mRrule,
                initialAvailability = current.model.mAvailability,
                initialAccessLevel = current.model.mAccessLevel,
                initialEventColor = if (current.model.isEventColorInitialized) {
                    current.model.eventColor
                } else {
                    0
                },
                // Read back from the model, which the repository already seeded from the intent when
                // this is a new event, so the copy opens showing what it will be saved with.
                initialReminders = current.model.mReminders.orEmpty().map { it.minutes },
                initialHasAttendeeData = current.model.mHasAttendeeData,
                initialSelfAttendeeStatus = current.model.mSelfAttendeeStatus,
                initialTimezone = current.model.mTimezone ?: java.util.TimeZone.getDefault().id,
                initialAttendees = current.model.mAttendeesList.values
                    .mapNotNull { it.mEmail }
                    .filter { it.isNotBlank() }
                    .sorted(),
                initialEventColors = current.eventColors,
                canDelete = !readOnly && current.model.mUri != null &&
                    com.android.calendar.event.EditEventHelper.canModifyCalendar(current.model),
                eventId = current.model.mId,
                readOnly = readOnly,
                calendars = current.calendars,
                onBack = { finish() },
                onSave = { edited -> applyAndSave(current, edited) },
                onDelete = { deleteEvent(current) },
            )
        }
    }

    companion object {
        const val EXTRA_EVENT_ID = "m3eEditEventId"

        /**
         * Opens the editor without offering to save or delete.
         *
         * The classic editor had the same flag and the import flow used it, because an event that
         * was just imported from an .ics file is not something this screen can safely rewrite.
         */
        const val EXTRA_READ_ONLY = "read_only"

        /**
         * Notification lead times for a brand new event, in minutes before the start.
         *
         * Only read when there is no event id: for an existing event the reminders come from the
         * provider and these would be a second, competing source. They exist so that duplicating an
         * event carries the original's notifications over, which is what the classic editor did.
         */
        const val EXTRA_REMINDERS = "m3eReminderMinutes"

        /**
         * An explicit colour for a brand new event, or 0 to follow the calendar.
         *
         * Read under the same condition as [EXTRA_REMINDERS], and for the same reason: duplicating an
         * event should not drop its colour.
         */
        const val EXTRA_EVENT_COLOR = "m3eEventColor"

        /** Sentinel for "the form did not change this property". */
        private const val NOT_SET = -1

        private const val KEY_THEME = "pref_theme"
    }
}
