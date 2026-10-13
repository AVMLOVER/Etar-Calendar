/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * Host for the Material 3 Expressive settings screen.
 */
package com.android.calendar.settings.compose

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.android.calendar.theme.applyApplicationNightMode

/**
 * The Material 3 Compose settings UI. This is the only settings screen: the classic
 * PreferenceFragmentCompat screens have been removed.
 */
class ComposeSettingsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val repo = SettingsRepository(this)

        setContent {
            // Theme mode is hoisted here so that changing it re-themes the screen immediately.
            var themeMode by remember { mutableStateOf(repo.string(KEY_THEME, "system")) }

            val systemDark = isSystemInDarkTheme()
            val dark = when (themeMode) {
                "light" -> false
                "dark", "black" -> true
                else -> systemDark
            }
            

            EtarM3ETheme(darkTheme = dark) {
                M3eSettingsScreen(
                    repo = repo,
                    darkTheme = dark,
                    onThemeModeChanged = { mode ->
                        themeMode = mode
                        // Re-theme the rest of the app straight away. The activities sitting
                        // behind this screen would otherwise keep their old colours until the
                        // app is restarted.
                        applyApplicationNightMode(this, mode)
                    },
                    onBack = { finish() },
                    onOpenCalendars = {
                        startActivity(
                            Intent(
                                this,
                                com.android.calendar.selectcalendars.compose
                                    .ComposeCalendarsActivity::class.java
                            )
                        )
                    },
                )
            }
        }
    }

    companion object {
        const val KEY_THEME = "pref_theme"

        /** Whether the given theme mode should render in night mode right now. */
        fun isDarkMode(configuration: Configuration, themeMode: String): Boolean = when (themeMode) {
            "light" -> false
            "dark", "black" -> true
            else -> (configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                    Configuration.UI_MODE_NIGHT_YES
        }
    }
}
