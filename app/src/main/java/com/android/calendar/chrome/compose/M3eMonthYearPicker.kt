/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * Month / year picker for the toolbar heading.
 *
 * This is the control the toolbar title opens. It shows a year as a row of month chips - the same
 * shape Google Calendar puts at the bottom of the month view - with the neighbouring years one step
 * away in each direction. Stepping from December into January therefore goes past the next year's
 * label, so the year change is visible instead of silently landing on the same numbered month.
 */
package com.android.calendar.chrome.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ws.xsoh.etar.R
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Years either side of the current one that the row can reach. */
private const val YEAR_SPAN = 5

private val MONTH_LABEL = DateTimeFormatter.ofPattern("M月", Locale.getDefault())

/**
 * @param year the year to centre the row on.
 * @param month the month to mark as selected, 1..12.
 * @param onPick called with the chosen year and month.
 */
@Composable
fun M3eMonthYearPicker(
    year: Int,
    month: Int,
    onPick: (year: Int, month: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val years = remember(year) { (year - YEAR_SPAN..year + YEAR_SPAN).toList() }
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = YEAR_SPAN.coerceAtLeast(0),
    )

    LazyRow(
        state = listState,
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items(years, key = { it }) { y ->
            Column {
                // The year is a heading above its months, so the row reads
                // "2026 / [1月][2月]…  2027 / [1月]…" and the year is never ambiguous.
                Text(
                    text = y.toString(),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (m in 1..12) {
                        val selected = y == year && m == month
                        Box(
                            modifier = Modifier
                                .height(40.dp)
                                .clip(RoundedCornerShape(percent = 50))
                                .background(
                                    if (selected) {
                                        MaterialTheme.colorScheme.secondaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.surfaceContainerHigh
                                    }
                                )
                                .clickable { onPick(y, m) }
                                .padding(horizontal = 16.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = MONTH_LABEL.format(java.time.YearMonth.of(y, m).atDay(1)),
                                style = MaterialTheme.typography.labelLarge,
                                color = if (selected) {
                                    MaterialTheme.colorScheme.onSecondaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    // When the picker is reopened, bring the year it was opened on back into view.
    LaunchedEffect(year, month) {
        listState.animateScrollToItem(YEAR_SPAN.coerceAtLeast(0))
    }
}

/**
 * The picker's heading.
 *
 * [day] is shown when a day is being picked as well, which is the case for the day, three day and
 * week views; the month view only cares about the month.
 */
@Composable
fun M3eMonthPickerHeader(day: LocalDate?, titleRes: Int? = null) {
    Column(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp)) {
        Text(
            text = androidx.compose.ui.res.stringResource(
                titleRes ?: if (day == null) R.string.m3e_pick_month else R.string.m3e_pick_day
            ),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (day != null) {
            Text(
                text = day.format(DateTimeFormatter.ofPattern("yyyy年M月d日", Locale.getDefault())),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Years the year picker can reach: the range the year view itself lays out. */
private const val PICKER_FIRST_YEAR = 1900
private const val PICKER_LAST_YEAR = 2100

/**
 * A row of years, for a heading that names a year rather than a month.
 *
 * The month picker's shape without the months: a year is a whole destination on its own, so the
 * row stops there instead of offering the twelve months inside it.
 *
 * @param year the year to mark as selected and bring into view when the picker opens.
 * @param onPick called with the chosen year.
 */
@Composable
fun M3eYearPicker(
    year: Int,
    onPick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selectedYear = year.coerceIn(PICKER_FIRST_YEAR, PICKER_LAST_YEAR)
    val years = remember { (PICKER_FIRST_YEAR..PICKER_LAST_YEAR).toList() }
    val selectedIndex = selectedYear - PICKER_FIRST_YEAR
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = (selectedIndex - 2).coerceAtLeast(0),
    )

    LazyRow(
        state = listState,
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items(years, key = { it }) { y ->
            val selected = y == selectedYear
            Box(
                modifier = Modifier
                    .height(40.dp)
                    .clip(RoundedCornerShape(percent = 50))
                    .background(
                        if (selected) {
                            MaterialTheme.colorScheme.secondaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        }
                    )
                    .clickable { onPick(y) }
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = y.toString(),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selected) {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
            }
        }
    }

    // When the picker is reopened, bring the year it was opened on back into view.
    LaunchedEffect(year) {
        listState.animateScrollToItem((selectedIndex - 2).coerceAtLeast(0))
    }
}

