/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The battery-optimisation warning, as a Material 3 Expressive screen of its own.
 *
 * The calendar's reminders, widgets and background work all stop when the system is allowed to
 * optimise the app's battery use, so the warning has to be seen once. It used to be a small
 * exclamation mark in the title bar, which was easy to miss and easy to dismiss by accident; it is now
 * a dialog that says what is at stake before the app is used.
 *
 * Shown once per install until the user answers. Ignoring the optimisation is the answer that makes
 * the app work, but it is the user's decision and the dialog says so.
 */
package com.android.calendar.chrome.compose

import com.android.calendar.chrome.declareReturnSlide
import com.android.calendar.chrome.declareForwardSlide
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.android.calendar.settings.compose.EtarM3ETheme
import com.android.calendar.settings.compose.SettingsRepository
import ws.xsoh.etar.R

class ComposeBatteryDialogActivity : ComponentActivity() {

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
                Surface(modifier = Modifier.fillMaxSize(), color = Color.Transparent) {
                    Warning(
                        onDismiss = { finish() },
                        onOpenSettings = {
                            // The system's own request, which is what actually turns the
                            // optimisation off. The app cannot do it for the user.
                            runCatching {
                                startActivity(
                                    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                                        .setData(
                                            android.net.Uri.parse(
                                                "package:" + packageName
                                            )
                                        )
                                )
                            }
                            setResult(Activity.RESULT_OK)
                            finish()
                        },
                        onIgnore = {
                            // "Ignore" means the user has decided: record it so the warning does not
                            // come back, which is what the setting in the app does too.
                            repo.setBool(
                                SETTING_KEY,
                                true,
                            )
                            setResult(Activity.RESULT_OK)
                            finish()
                        },
                    )
                }
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun Warning(
        onDismiss: () -> Unit,
        onOpenSettings: () -> Unit,
        onIgnore: () -> Unit,
    ) {
        AlertDialog(
            // Not dismissable by tapping outside: the point is that this is read once. Back still
            // closes it, and the warning returns the next time the app starts.
            onDismissRequest = onDismiss,
            shape = MaterialTheme.shapes.extraLarge,
            title = {
                Text(
                    text = stringResource(R.string.m3e_battery_title),
                    color = MaterialTheme.colorScheme.error,
                )
            },
            text = {
                Column {
                    Text(text = stringResource(R.string.m3e_battery_message))
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.m3e_battery_risk),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = onIgnore) {
                    Text(
                        text = stringResource(R.string.m3e_battery_ignore),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = onOpenSettings) {
                    Text(stringResource(R.string.m3e_battery_do_not_ignore))
                }
            },
        )
    }

    companion object {
        /**
         * The same preference the settings screen's switch writes.
         *
         * Shared so the dialog and the switch cannot disagree about whether the warning is settled.
         */
        const val SETTING_KEY = "pref_do_not_check_battery_optimization"
    }
}
