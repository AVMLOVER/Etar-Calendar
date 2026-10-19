/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * Host for the Material 3 Expressive "calendars to display" screen.
 */
package com.android.calendar.selectcalendars.compose

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.android.calendar.settings.compose.EtarM3ETheme
import com.android.calendar.settings.compose.SettingsRepository

/**
 * The Material 3 Expressive replacement for the calendars manager.
 *
 * The classic SelectVisibleCalendarsFragment only ever appeared in the tablet multi-pane layout,
 * so on phones this screen had no entry at all; it is now reachable from the overflow menu and
 * from the settings screen.
 */
class ComposeCalendarsActivity : ComponentActivity() {

    /**
     * Bumped on every resume, and read by the screen so it re-reads the calendars.
     *
     * This list now opens a screen of its own for a calendar, and that screen writes the very rows
     * this one draws: it can rename the calendar, recolour it, hide its events or delete it. Coming
     * back from it is a resume of this activity, and without this key the list was still the one it
     * had loaded before - the renamed calendar came back with its old name, and a deleted one was
     * still in the list until the screen was reopened.
     */
    private var reloadKey by mutableStateOf(0)

    /**
     * Re-reads the list on every resume.
     *
     * This list opens a screen of its own for a calendar, and that screen writes the very rows this
     * one draws: it can rename the calendar, recolour it, hide its events or delete it. Coming back
     * from it is a resume of this activity, and without this key the list was still the one it had
     * loaded before - the renamed calendar came back with its old name, and a deleted one was still
     * in the list until the screen was reopened. The animation itself is carried by the operations
     * that open and close screens now, so nothing is declared here.
     */
    override fun onResume() {
        super.onResume()
        reloadKey++
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val repo = SettingsRepository(this)

        setContent {
            val themeMode = remember { repo.string(KEY_THEME, "system") }
            val dark = when (themeMode) {
                "light" -> false
                "dark", "black" -> true
                else -> isSystemInDarkTheme()
            }
            

            EtarM3ETheme(darkTheme = dark) {
                M3eCalendarsScreen(
                    onBack = { finish() },
                    externalReloadKey = reloadKey,
                )
            }
        }
    }

    private companion object {
        const val KEY_THEME = "pref_theme"
    }
}
