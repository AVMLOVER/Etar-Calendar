/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * Host for the Material 3 Expressive event details screen.
 */
package com.android.calendar.event.compose

import com.android.calendar.chrome.declareReturnSlide
import com.android.calendar.chrome.declareForwardSlide
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.remember
import com.android.calendar.agenda.compose.AgendaRepository
import com.android.calendar.settings.compose.EtarM3ETheme
import com.android.calendar.settings.compose.SettingsRepository
import ws.xsoh.etar.R

/**
 * Read-only Material 3 Expressive view of a single event.
 *
 * It deliberately does not write anything: the editing flow still goes through the classic
 * editor, which is opened from the "Edit" action. The loading path used here is also the
 * prerequisite for a future Compose editor, so it is validated in a read-only setting first.
 */
class ComposeEventDetailsActivity : ComponentActivity() {

    /** Kept as a field because the reminder and delete writes need it after onCreate returns. */
    private var eventId: Long = -1L

    /**
     * Declares the slide this screen plays when it is covered, and the reverse when it is
     * revealed.
     *
     * Declared on every screen that opens over the calendar, dialogs included, so that the
     * movement is the same everywhere. It is a plain ComponentActivity, so it does not inherit
     * the declarations M3eHostActivity makes.
     */
    override fun onResume() {
        super.onResume()
        declareForwardSlide()
        declareReturnSlide()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        eventId = intent.getLongExtra(EXTRA_EVENT_ID, -1L)
        val repo = SettingsRepository(this)

        setContent {
            val themeMode = remember { repo.string(KEY_THEME, "system") }
            val dark = when (themeMode) {
                "light" -> false
                "dark", "black" -> true
                else -> isSystemInDarkTheme()
            }
            

            EtarM3ETheme(darkTheme = dark) {
                M3eEventDetailsScreen(
                    eventId = eventId,
                    onBack = { finish() },
                    onEdit = { id -> openEditor(id) },
                    onDelete = { deleteEvent(eventId) },
                    onRemindersChanged = { minutes -> setReminders(minutes) },
                    onTimezoneChanged = { zone -> setTimezone(zone) },
                )
            }
        }
    }

    /**
     * Deletes the event and leaves the screen.
     *
     * The classic EventInfoFragment offers deletion, so this screen has to as well now that tapping
     * an event in any calendar view lands here.
     */
    private fun deleteEvent(eventId: Long) {
        val deleted = EventDetailsRepository(this).delete(this, eventId)
        if (deleted) {
            setResult(RESULT_OK)
            finish()
        } else {
            android.widget.Toast.makeText(
                this,
                R.string.m3e_event_delete_failed,
                android.widget.Toast.LENGTH_SHORT,
            ).show()
        }
    }

    /**
     * Writes the event's notification lead times.
     *
     * Delegated to the agenda repository, which owns the reminder write and keeps the event's
     * HAS_ALARM flag in step. Going through it means the list and this screen write reminders the
     * same way.
     */
    private fun setReminders(minutes: List<Int>) {
        val saved = AgendaRepository(applicationContext).setReminderMinutes(eventId, minutes)
        if (!saved) {
            android.widget.Toast.makeText(
                this,
                R.string.m3e_reminder_save_failed,
                android.widget.Toast.LENGTH_SHORT,
            ).show()
        }
    }

    /**
     * Writes the event's time zone.
     *
     * One column, written directly rather than through the editor's whole save path: the value has no
     * bearing on anything else about the event, and a change made here is meant to apply at once.
     */
    private fun setTimezone(zone: String) {
        val values = android.content.ContentValues().apply {
            put(android.provider.CalendarContract.Events.EVENT_TIMEZONE, zone)
        }
        val ok = try {
            contentResolver.update(
                android.content.ContentUris.withAppendedId(
                    android.provider.CalendarContract.Events.CONTENT_URI,
                    eventId,
                ),
                values,
                null,
                null,
            ) > 0
        } catch (t: Throwable) {
            false
        }
        android.widget.Toast.makeText(
            this,
            if (ok) R.string.m3e_event_saved else R.string.m3e_event_save_failed,
            android.widget.Toast.LENGTH_SHORT,
        ).show()
        if (ok) {
            // The screen re-reads rather than keeping a local copy, so what it shows is what is
            // stored.
            recreate()
        }
    }

    /** Opens the Material 3 Expressive editor for this event. */
    private fun openEditor(eventId: Long) {
        startActivity(
            Intent(this, ComposeEventEditorActivity::class.java)
                .putExtra(ComposeEventEditorActivity.EXTRA_EVENT_ID, eventId)
        )
    }

    companion object {
        const val EXTRA_EVENT_ID = "m3eEventId"
        private const val KEY_THEME = "pref_theme"
    }
}
