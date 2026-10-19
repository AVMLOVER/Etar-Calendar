/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The .ics import dialog, as a screen of its own.
 *
 * The settings screen shows this dialog from Compose directly. The overflow menu cannot: it lives in
 * the host activity's Java menu handling, and calling a composable from there means passing the
 * synthetic composer parameter, which is brittle. A transparent activity hosting the same dialog
 * gives the menu a way in without a second implementation of it.
 */
package com.android.calendar.chrome.compose

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.android.calendar.settings.compose.EtarM3ETheme
import com.android.calendar.settings.compose.SettingsRepository

class ComposeImportActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

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
                // Transparent, so only the dialog is visible over the screen that launched this.
                Surface(modifier = Modifier.fillMaxSize(), color = Color.Transparent) {
                    IcsImportDialog(onDismiss = { finish() })
                }
            }
        }
    }
}
