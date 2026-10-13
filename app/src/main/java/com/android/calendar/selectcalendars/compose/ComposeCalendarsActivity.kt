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
import androidx.compose.runtime.remember
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
                M3eCalendarsScreen(onBack = { finish() })
            }
        }
    }

    private companion object {
        const val KEY_THEME = "pref_theme"
    }
}
