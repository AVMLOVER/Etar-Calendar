/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * Configuration for the countdown widgets: which countdowns each instance shows.
 *
 * One activity serves all three sizes. The 2x2 shows a single countdown so it takes a single choice;
 * the two list sizes take up to their number of rows. Leaving a list size short is allowed - the widget
 * simply shows fewer rows.
 */
package com.android.calendar.countdown.compose

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.android.calendar.chrome.compose.ColorSwatch
import com.android.calendar.chrome.compose.M3eEdgeToEdgeContent
import com.android.calendar.chrome.compose.m3eSystemBarPadding
import com.android.calendar.countdown.Countdown
import com.android.calendar.countdown.CountdownStore
import com.android.calendar.settings.compose.EtarM3ETheme
import com.android.calendar.settings.compose.SettingsRepository
import com.android.calendar.widget.CountdownWidgetConfig
import com.android.calendar.widget.CountdownWidgetUpdater
import ws.xsoh.etar.R

class CountdownWidgetConfigActivity : ComponentActivity() {

    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Until the user confirms, a cancellation must leave no widget behind.
        setResult(Activity.RESULT_CANCELED)

        widgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        val repo = SettingsRepository(this)
        // Same theme resolution the other Compose hosts use.
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
                ConfigScreen(
                    widgetId = widgetId,
                    onConfirm = { ids -> confirm(ids) },
                    onCancel = { finish() },
                )
            }
        }
    }

    private fun confirm(ids: List<String>) {
        CountdownWidgetConfig.setChosenIds(this, widgetId, ids)
        // Refresh the instance now, so the launcher shows the choice without waiting for the next
        // scheduled update.
        val manager = AppWidgetManager.getInstance(this)
        CountdownWidgetUpdater.update(this, manager, widgetId)
        setResult(
            Activity.RESULT_OK,
            Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId),
        )
        finish()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConfigScreen(
    widgetId: Int,
    onConfirm: (List<String>) -> Unit,
    onCancel: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val all = remember { CountdownStore.all(context) }

    // The limit matches the widget's row count: one for the 2x2, three or eight for the lists.
    val limit = remember(widgetId) { CountdownWidgetUpdater.slotsFor(context, widgetId) }
    val single = limit == 1

    var selected by remember {
        mutableStateOf(CountdownWidgetConfig.chosenIds(context, widgetId).toSet())
    }

    fun toggle(id: String) {
        selected = when {
            single -> if (selected.contains(id)) emptySet() else setOf(id)
            selected.contains(id) -> selected - id
            selected.size < limit -> selected + id
            else -> selected
        }
    }

    M3eEdgeToEdgeContent {
        Scaffold(
            modifier = Modifier.m3eSystemBarPadding(),
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.m3e_countdown_widget_choose)) },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent
                    ),
                )
            },
        ) { padding ->
            Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                if (all.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.m3e_countdown_empty),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    Text(
                        text = stringResource(R.string.m3e_countdown_widget_choose_hint, limit),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        items(all, key = { it.id }) { countdown ->
                            ChoiceRow(
                                countdown = countdown,
                                selected = selected.contains(countdown.id),
                                single = single,
                                onClick = { toggle(countdown.id) },
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onCancel) {
                        Text(stringResource(R.string.m3e_cancel))
                    }
                    TextButton(
                        onClick = { onConfirm(all.map { it.id }.filter { selected.contains(it) }) },
                        enabled = selected.isNotEmpty(),
                    ) { Text(stringResource(R.string.m3e_ok)) }
                }
            }
        }
    }
}

@Composable
private fun ChoiceRow(
    countdown: Countdown,
    selected: Boolean,
    single: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (single) {
            RadioButton(selected = selected, onClick = onClick)
        } else {
            // onCheckedChange rather than onClick: the row already handles clicks, and a click
            // listener here would fire the toggle a second time for the same touch.
            Checkbox(checked = selected, onCheckedChange = { onClick() })
        }
        // The countdown's colour, so the choice on screen matches what the widget will show.
        ColorSwatch(color = countdown.color, size = 20.dp)
        Text(
            text = countdown.name,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = 12.dp).weight(1f),
            maxLines = 1,
        )
        Text(
            text = countdown.date.format(CountdownStore.DATE_FORMAT),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
