/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The event delete confirmation, as a screen of its own.
 *
 * The agenda and the countdown list show this dialog from Compose directly. The day view cannot: it
 * is a View, and calling a composable from Java means passing the synthetic composer parameter, which
 * is brittle. A transparent activity that hosts the same dialog gives the day view a way in without a
 * second implementation of the question.
 */
package com.android.calendar.event.compose

import com.android.calendar.chrome.declareReturnSlide
import com.android.calendar.chrome.declareForwardSlide
import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.android.calendar.countdown.CountdownStore
import com.android.calendar.countdown.compose.EventDeleteWithCountdownDialog
import com.android.calendar.settings.compose.EtarM3ETheme
import com.android.calendar.settings.compose.SettingsRepository
import ws.xsoh.etar.R
import java.time.ZoneId

class ComposeEventDeleteActivity : ComponentActivity() {

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

        val eventId = intent.getLongExtra(EXTRA_EVENT_ID, -1L)
        val title = intent.getStringExtra(EXTRA_EVENT_TITLE)
        if (eventId <= 0L) {
            finish()
            return
        }

        val repo = SettingsRepository(this)
        val themeMode = repo.string("pref_theme", "system")
        val dark = when (themeMode) {
            "light" -> false
            "dark", "black" -> true
            else -> (resources.configuration.uiMode and
                    android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                    android.content.res.Configuration.UI_MODE_NIGHT_YES
        }
        

        setContent {
            EtarM3ETheme(darkTheme = dark) {
                DeleteConfirmation(eventId = eventId, title = title.orEmpty())
            }
        }
    }

    /**
     * Shows the shared dialog and does the deletion.
     *
     * The countdown is looked up here rather than passed in, so the answer is the same one the agenda
     * would give - including the name-and-day fallback for a countdown that has no recorded id.
     */
    @Composable
    private fun DeleteConfirmation(eventId: Long, title: String) {
        val activity = this
        val countdown = remember(eventId) {
            CountdownStore.forEventOrSimilar(
                context = activity,
                eventId = eventId,
                eventTitle = title,
                eventStartMillis = System.currentTimeMillis(),
                zone = ZoneId.systemDefault(),
            )
        }

        // Transparent, so only the dialog is visible over whatever launched this.
        Surface(modifier = Modifier.fillMaxSize(), color = Color.Transparent) {
            EventDeleteWithCountdownDialog(
                eventTitle = title,
                countdownName = countdown?.name,
                onDismiss = { finish() },
                onConfirm = { alsoDeleteCountdown ->
                    if (countdown != null && alsoDeleteCountdown) {
                        CountdownStore.remove(activity, countdown.id)
                        com.android.calendar.widget.CountdownWidgetUpdater.updateAll(activity)
                    } else if (countdown != null) {
                        CountdownStore.unlinkEvent(activity, eventId)
                    }
                    EventEditorRepository(activity).deleteEvent(eventId)
                    setResult(Activity.RESULT_OK)
                    finish()
                },
            )
        }
    }

    companion object {
        const val EXTRA_EVENT_ID = "m3eDeleteEventId"
        const val EXTRA_EVENT_TITLE = "m3eDeleteEventTitle"
    }
}
