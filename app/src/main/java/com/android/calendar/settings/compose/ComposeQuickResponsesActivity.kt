/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * Host for the Material 3 Expressive quick-response editor.
 */
package com.android.calendar.settings.compose

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.remember

class ComposeQuickResponsesActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val repo = SettingsRepository(this)

        onBackPressedDispatcher.addCallback(this) { finishAffinity() }

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
