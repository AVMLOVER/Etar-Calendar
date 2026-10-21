/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * Host for the Material 3 Expressive quick-response editor.
 */
package com.android.calendar.settings.compose

import android.os.Bundle
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.remember
import com.android.calendar.chrome.M3eAnimatedActivity

class ComposeQuickResponsesActivity : M3eAnimatedActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val repo = SettingsRepository(this)

        // Back closes this screen and nothing else.
        //
        // It used to call finishAffinity(), which empties the whole task: the settings screen it was
        // opened from went with it and the user landed on the launcher, with no way back to the
        // setting they had been editing.
        onBackPressedDispatcher.addCallback(this) { finish() }

        setContent {
            val themeMode = remember { repo.string(KEY_THEME, "system") }
            val dark = when (themeMode) {
                "light" -> false
                "dark", "black" -> true
                else -> isSystemInDarkTheme()
            }
            

            EtarM3ETheme(darkTheme = dark) {
                M3eQuickResponsesScreen()
            }
        }
    }

    private companion object {
        const val KEY_THEME = "pref_theme"
    }
}
