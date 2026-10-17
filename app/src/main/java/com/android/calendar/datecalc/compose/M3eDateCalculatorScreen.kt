/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * Material 3 Expressive date calculator, in two parts.
 *
 * "Date interval" is how many days lie between two dates. "Date arithmetic" is the date a number of
 * days away from a given one, added or taken away. The two share one setting - whether the first day
 * counts - which sits under both of them, because it means the same thing to each: the day the count
 * starts from is part of the count.
 *
 * Both dates use the app's own calendar grid, which supports picking from the calendar as well as
 * typing the date, so year / month / day can be entered either way.
 */
package com.android.calendar.datecalc.compose

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.android.calendar.chrome.compose.M3eChromeScaffold
import com.android.calendar.chrome.compose.M3eDatePickerDialog
import com.android.calendar.chrome.compose.M3eScreen
import ws.xsoh.etar.R
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun M3eDateCalculatorScreen() {
    val today = remember { LocalDate.now() }

    // --- date interval ---------------------------------------------------------------------------
    var first by remember { mutableStateOf<LocalDate?>(today) }
    var second by remember { mutableStateOf<LocalDate?>(null) }
    var editingFirst by remember { mutableStateOf(false) }
    var editingSecond by remember { mutableStateOf(false) }

    // --- date arithmetic -------------------------------------------------------------------------
    var base by remember { mutableStateOf<LocalDate?>(today) }
    var add by remember { mutableStateOf(true) }
    var daysText by remember { mutableStateOf("") }
    var editingBase by remember { mutableStateOf(false) }
    var directionOpen by remember { mutableStateOf(false) }

    // --- both ------------------------------------------------------------------------------------
    var inclusive by remember { mutableStateOf(false) }

    M3eChromeScaffold(
        selected = M3eScreen.DATE_CALCULATOR,
        title = { Text(stringResource(R.string.m3e_date_calculator_title)) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { SectionTitle(stringResource(R.string.m3e_date_interval_title)) }

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
                    ResultLine(
                        label = stringResource(R.string.m3e_date_result),
                        value = intervalText(first, second, inclusive),
                    )
                }
            }

            item { SectionTitle(stringResource(R.string.m3e_date_offset_title)) }

            item {
                Card {
                    DateRow(
                        label = stringResource(R.string.m3e_date_offset_date),
                        value = base,
                        onClick = { editingBase = true },
                    )
                    HorizontalDivider()
                    // Add or subtract, the same dropdown the rest of the app uses for a value that
                    // has two answers.
                    Box {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { directionOpen = true }
                                .padding(vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource(R.string.m3e_date_offset_direction),
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = stringResource(
                                    if (add) R.string.m3e_date_offset_add
                                    else R.string.m3e_date_offset_subtract
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        DropdownMenu(
                            expanded = directionOpen,
                            onDismissRequest = { directionOpen = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.m3e_date_offset_add)) },
                                onClick = { add = true; directionOpen = false },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.m3e_date_offset_subtract)) },
                                onClick = { add = false; directionOpen = false },
                            )
                        }
                    }
                    HorizontalDivider()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.m3e_date_offset_days),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = daysText,
                            onValueChange = { typed ->
                                // Digits only, and never so long that the arithmetic could overflow.
                                daysText = typed.filter { it.isDigit() }.take(6)
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            placeholder = { Text("0") },
                            modifier = Modifier.width(140.dp),
                        )
                    }
                }
            }

            item {
                Card {
                    ResultLine(
                        label = stringResource(R.string.m3e_date_offset_result),
                        value = offsetText(base, parseDays(daysText), add, inclusive),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.m3e_date_offset_summary),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // One setting for both parts, which is why it sits below them rather than inside either.
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

    if (editingFirst || editingSecond || editingBase) {
        val initial = when {
            editingFirst -> first
            editingSecond -> second
            else -> base
        } ?: today
        // The app's own calendar grid rather than Material's DatePicker, which renders every column
        // of its weekday header as the first character of the name - seven "星" in Chinese.
        M3eDatePickerDialog(
            initial = initial,
            firstDayOfWeek = java.time.DayOfWeek.MONDAY,
            onDismiss = { editingFirst = false; editingSecond = false; editingBase = false },
            onPick = { picked ->
                if (editingFirst) first = picked
                else if (editingSecond) second = picked
                else base = picked
                editingFirst = false
                editingSecond = false
                editingBase = false
            },
        )
    }
}

/** A heading inside the page, not the toolbar's: the two parts of this screen are named this way. */
@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 2.dp),
    )
}

@Composable
private fun ResultLine(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/** The number typed into the days field, or null while it is empty. */
private fun parseDays(text: String): Int? = text.toIntOrNull()

@Composable
private fun intervalText(first: LocalDate?, second: LocalDate?, inclusive: Boolean): String {
    if (first == null || second == null) {
        return stringResource(R.string.m3e_date_need_both)
    }
    val days = ChronoUnit.DAYS.between(first, second)
    val magnitude = if (days < 0) -days else days
    val total = if (inclusive) magnitude + 1 else magnitude
    return stringResource(R.string.m3e_date_days, total)
}

/**
 * The date [days] away from [base].
 *
 * With "include the first day" ticked the day given counts as the first of them, so five days on
 * from the 1st is the 5th rather than the 6th; the same for days taken away.
 */
@Composable
private fun offsetText(
    base: LocalDate?,
    days: Int?,
    add: Boolean,
    inclusive: Boolean,
): String {
    if (base == null || days == null) {
        return stringResource(R.string.m3e_date_offset_need)
    }
    val counted = if (inclusive) (days - 1).coerceAtLeast(0) else days
    val result = if (add) base.plusDays(counted.toLong()) else base.minusDays(counted.toLong())
    return formatDate(result)
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
private fun formatDate(date: LocalDate): String = remember(date) {
    java.text.DateFormat.getDateInstance(java.text.DateFormat.LONG)
        .format(java.util.Date(date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()))
}

@Composable
private fun DateRow(label: String, value: LocalDate?, onClick: () -> Unit) {
    val context = LocalContext.current
    val text = value?.let { formatDate(it) } ?: context.getString(R.string.m3e_date_unset)

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
