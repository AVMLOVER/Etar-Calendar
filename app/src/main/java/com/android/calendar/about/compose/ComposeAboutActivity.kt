/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The About screen, in the Material 3 Expressive chrome.
 *
 * One screen with a back arrow and a title, and nothing else in the bar: the logo, the version, the
 * authors and the licence, the three links, and the copyright at the foot. The drawer is not shown
 * here - this is a page you arrive at and leave, not a view to switch away from.
 */
package com.android.calendar.about.compose

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import com.android.calendar.chrome.M3eHostActivity
import com.android.calendar.chrome.compose.M3eEdgeToEdgeContent
import com.android.calendar.Utils
import ws.xsoh.etar.R
import java.util.Calendar

class ComposeAboutActivity : M3eHostActivity() {

    /** Not a drawer entry: this screen is reached from the menu, not switched to. */
    override val checkedMenuItemId: Int = 0
    override val currentScreenName: String = "ABOUT"

    /** Back returns to the calendar, as it does from the other drawer entries. */
    override val exitOnBack: Boolean = true

    /** The heading is the screen's name, so there is no period for a picker to open. */
    override val titleOpensPicker: Boolean = false

    /** No today, no jump-to-date, no search: this screen has no dates and nothing to look up. */
    override val showTodayAction: Boolean = false
    override val showGotoAction: Boolean = false
    override val showSearchAction: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setPickerTitle(getString(R.string.m3e_about_title))
        setM3eContent { AboutContent() }
    }
}

@Composable
private fun AboutContent() {
    val context = LocalContext.current
    val version = Utils.getVersionCode(context)
    val year = Calendar.getInstance().get(Calendar.YEAR).toString()

    fun open(urlRes: Int) {
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(context.getString(urlRes))))
        }
    }

    // Edge to edge: the insets are taken as padding here, so the content scrolls under the bars
    // rather than being boxed inside them.
    M3eEdgeToEdgeContent {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())

                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(24.dp))
            // The launcher icon, read from the package manager.
            //
            // painterResource cannot load it: from API 26 the icon is an adaptive-icon XML, and Compose
            // accepts only vectors and rasterised assets - which is what the crash on opening this
            // screen was. The drawable the package manager returns is already resolved for this device,
            // so it is drawn into a bitmap here.
            val appIcon = remember {
                runCatching {
                    val drawable = context.packageManager.getApplicationIcon(context.packageName)
                    val width = drawable.intrinsicWidth.takeIf { it > 0 } ?: 144
                    val height = drawable.intrinsicHeight.takeIf { it > 0 } ?: 144
                    val bitmap = android.graphics.Bitmap.createBitmap(
                        width, height, android.graphics.Bitmap.Config.ARGB_8888
                    )
                    drawable.setBounds(0, 0, width, height)
                    drawable.draw(android.graphics.Canvas(bitmap))
                    bitmap.asImageBitmap()
                }.getOrNull()
            }
            if (appIcon != null) {
                Image(
                    bitmap = appIcon,
                    contentDescription = null,
                    modifier = Modifier.size(120.dp),
                )
            }
            Spacer(Modifier.height(16.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ),
                shape = MaterialTheme.shapes.extraLarge,
            ) {
                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    InfoRow(
                        label = stringResource(R.string.preferences_build_version),
                        value = version,
                    )
                    InfoRow(
                        label = stringResource(R.string.authors),
                        value = stringResource(R.string.app_authors),
                        onClick = { open(R.string.app_authors_url) },
                    )
                    InfoRow(
                        label = stringResource(R.string.license),
                        value = stringResource(R.string.app_license),
                        onClick = { open(R.string.app_license_url) },
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // The three links, as Material 3 buttons.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                TextButton(onClick = { open(R.string.app_source_url) }) {
                    Text(stringResource(R.string.source_code))
                }
                TextButton(onClick = { open(R.string.app_changelog) }) {
                    Text(stringResource(R.string.changelog))
                }
                TextButton(onClick = { open(R.string.app_issues_url) }) {
                    Text(stringResource(R.string.preferences_issues))
                }
            }

            Spacer(Modifier.height(24.dp))

            Text(
                text = stringResource(R.string.app_copyright, year),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 24.dp),
            )
        }
    }
}

/** A labelled line in the information card, tappable when it leads somewhere. */
@Composable
private fun InfoRow(label: String, value: String, onClick: (() -> Unit)? = null) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
