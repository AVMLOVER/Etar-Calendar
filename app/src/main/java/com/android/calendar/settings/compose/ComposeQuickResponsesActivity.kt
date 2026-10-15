/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * Host for the Material 3 Expressive quick-response editor.
 */
package com.android.calendar.settings.compose

import com.android.calendar.chrome.declareReturnSlide
import com.android.calendar.chrome.declareForwardSlide
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.remember

class ComposeQuickResponsesActivity : ComponentActivity() {

    /**
     * Declares the slide this screen plays when it is covered, and the reverse when it is revealed.
     *
     * Like the settings screen it is opened from, this is a plain ComponentActivity and so does not
     * inherit the declarations M3eHostActivity makes.
     */
    override fun onResume() {
        super.onResume()
        declareForwardSlide()
        declareReturnSlide()
    }

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
