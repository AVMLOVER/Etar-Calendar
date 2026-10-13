/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The countdown screen's date picker.
 *
 * Material 3's DatePicker was rendering every column of its weekday header as the first character of
 * the name - seven "星" in Chinese - so the header is built here from the locale's own abbreviated
 * names. The grid underneath comes from CountdownCalendar, which is also where the week-start choice
 * lives.
 */
package com.android.calendar.chrome.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.android.calendar.countdown.CountdownCalendar
import ws.xsoh.etar.R
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * A month grid.
 *
 * @param initial the day to open on
 * @param firstDayOfWeek which column the week starts in
 * @param onPick called with the chosen day
 */
@Composable
fun M3eDatePickerDialog(
    initial: LocalDate,
    firstDayOfWeek: DayOfWeek,
    onDismiss: () -> Unit,
    onPick: (LocalDate) -> Unit,
) {
    // The month on screen, kept on the 1st so month lengths cannot shift it.
    var month by remember { mutableStateOf(initial.withDayOfMonth(1)) }
    // Defaults to the day being edited; cleared once the user starts browsing, so a day in another
    // month is not shown as selected.
    var selected by remember { mutableStateOf(initial) }
    val today = remember { LocalDate.now() }
    val locale = Locale.getDefault()

    val monthLabel = remember(month, locale) {
        // The TextStyle overload picks the right shape per locale, so an English build reads
        // "October 2026" instead of exposing a literal 年.
        month.format(DateTimeFormatter.ofPattern("yyyy MMMM", locale))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.extraLarge,
        title = { Text(monthLabel) },
        text = {
            Column {
                // Month navigation.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    TextButton(onClick = { month = CountdownCalendar.shiftMonth(month, -1) }) {
                        Text("‹")
                    }
                    Text(
                        text = monthLabel,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    TextButton(onClick = { month = CountdownCalendar.shiftMonth(month, 1) }) {
                        Text("›")
                    }
                }

                Spacer(Modifier.height(4.dp))
                WeekdayHeader(firstDayOfWeek, locale)
                Spacer(Modifier.height(4.dp))

                // Six rows of seven, laid out by hand: a LazyVerticalGrid inside a dialog has to be
                // given a height, and the grid is a fixed 42 cells anyway.
                val grid = remember(month, firstDayOfWeek) {
                    CountdownCalendar.grid(month, firstDayOfWeek)
                }
                grid.chunked(7).forEach { week ->
                    Row(modifier = Modifier.fillMaxWidth()) {
                        week.forEach { day ->
                            DayCell(
                                day = day,
                                inMonth = CountdownCalendar.isInMonth(day, month),
                                isSelected = day == selected,
                                isToday = day == today,
                                onClick = {
                                    selected = day
                                    onPick(day)
                                },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                // Today is one tap away regardless of which month is being browsed.
                TextButton(
                    onClick = { onPick(today) },
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Text(stringResource(R.string.m3e_date_picker_today))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.m3e_cancel)) }
        },
    )
}

@Composable
private fun WeekdayHeader(firstDayOfWeek: DayOfWeek, locale: Locale) {
    Row(modifier = Modifier.fillMaxWidth()) {
        CountdownCalendar.weekDays(firstDayOfWeek).forEach { day ->
            // One character per column: 日 一 二 三 四 五 六 in Chinese, S M T W T F S in English.
            //
            // Derived from the locale's own short name rather than a hard-coded table, so a build in
            // another language still gets its own letters. Chinese short names are 周一 .. 周日, and
            // dropping the shared 周 leaves the single character a calendar header conventionally
            // shows - which is what this screen wants, not the full three character form and not the
            // locale's narrow form (which is the same character but reached in a way that produced the
            // raw 星 this replaces).
            val label = remember(day, locale) {
                val short = day.getDisplayName(TextStyle.SHORT, locale)
                if (locale.language == "zh") short.removePrefix("周").take(1) else short
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Visible,
                softWrap = false,
                modifier = Modifier.weight(1f).padding(vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun DayCell(    day: LocalDate,
    inMonth: Boolean,
    isSelected: Boolean,
    isToday: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(
                    // Unselected days take the dialog's own surface colour rather than a colour of
                    // their own: naming one here turned every cell into a block, black under the
                    // pure-black theme, instead of leaving the grid on the dialog's background.
                    when {
                        isSelected -> MaterialTheme.colorScheme.primary
                        isToday -> MaterialTheme.colorScheme.primaryContainer
                        else -> MaterialTheme.colorScheme.surfaceContainerHigh
                    }
                )
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = day.dayOfMonth.toString(),
                style = MaterialTheme.typography.bodyMedium,
                color = when {
                    isSelected -> MaterialTheme.colorScheme.onPrimary
                    // Days from the neighbouring months are shown so the weeks line up, but dimmed:
                    // they belong to a month the user is not looking at.
                    !inMonth -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    isToday -> MaterialTheme.colorScheme.onPrimaryContainer
                    else -> LocalContentColor.current
                },
            )
        }
    }
}
