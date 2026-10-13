/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * Material 3 Expressive date calculator: how many days lie between two dates.
 *
 * Both dates use the Material 3 DatePicker dialog, which supports picking from the calendar as
 * well as typing the date, so year / month / day can be entered either way. The first date
 * defaults to today and the second one starts out unset.
 */
package com.android.calendar.datecalc.compose

import com.android.calendar.chrome.compose.M3eDatePickerDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.android.calendar.chrome.compose.M3eChromeScaffold
import com.android.calendar.chrome.compose.M3eScreen
import ws.xsoh.etar.R
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun M3eDateCalculatorScreen() {
    val zone = remember { ZoneId.systemDefault() }
    val today = remember { LocalDate.now() }

    var first by remember { mutableStateOf<LocalDate?>(today) }
    var second by remember { mutableStateOf<LocalDate?>(null) }
    var inclusive by remember { mutableStateOf(false) }
    var editingFirst by remember { mutableStateOf(false) }
    var editingSecond by remember { mutableStateOf(false) }

    M3eChromeScaffold(
        selected = M3eScreen.DATE_CALCULATOR,
        title = { Text(stringResource(R.string.m3e_date_calculator_title)) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Card {
                    DateRow(
                        label = stringResource(R.string.m3e_date_first),
                        value = first,
                        onClick = { editingFirst = true },
                    )
                    HorizontalDivider()
                    DateRow(
                        label = stringResource(R.string.m3e_date_second),
                        value = second,
                        onClick = { editingSecond = true },
                    )
                }
            }

            item {
                Card {
                    Text(
                        text = stringResource(R.string.m3e_date_result),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = resultText(first, second, inclusive),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            item {
                Card {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { inclusive = !inclusive }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = inclusive, onCheckedChange = { inclusive = it })
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(
                                text = stringResource(R.string.m3e_date_inclusive),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Text(
                                text = stringResource(R.string.m3e_date_inclusive_summary),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }

    if (editingFirst || editingSecond) {
        val initial = (if (editingFirst) first else second) ?: today
        // The app's own calendar grid rather than Material's DatePicker, which renders every column
        // of its weekday header as the first character of the name - seven "星" in Chinese.
        M3eDatePickerDialog(
            initial = initial,
            firstDayOfWeek = java.time.DayOfWeek.MONDAY,
            onDismiss = { editingFirst = false; editingSecond = false },
            onPick = { picked ->
                if (editingFirst) first = picked else second = picked
                editingFirst = false
                editingSecond = false
            },
        )
    }
}

@Composable
private fun resultText(first: LocalDate?, second: LocalDate?, inclusive: Boolean): String {
    if (first == null || second == null) {
        return stringResource(R.string.m3e_date_need_both)
    }
    val days = ChronoUnit.DAYS.between(first, second)
    val magnitude = if (days < 0) -days else days
    val total = if (inclusive) magnitude + 1 else magnitude
    return stringResource(R.string.m3e_date_days, total)
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(modifier = Modifier.padding(16.dp)) { content() }
    }
}

@Composable
private fun DateRow(label: String, value: LocalDate?, onClick: () -> Unit) {
    val context = LocalContext.current
    val text = remember(value) {
        value?.let {
            java.text.DateFormat.getDateInstance(java.text.DateFormat.LONG)
                .format(java.util.Date(it.atStartOfDay(ZoneId.systemDefault())
                    .toInstant().toEpochMilli()))
        } ?: context.getString(R.string.m3e_date_unset)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = if (value == null) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.primary
            },
        )
    }
}
