/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The calendar colour palette, swatch and picker.
 *
 * Shared by the calendars list and the screen that edits one calendar: both change the same column
 * of the same table, both have to offer the same choices, and both write through
 * CalendarsRepository.setColor. A second copy would drift - a colour offered on one screen would
 * stop being offered on the other - so the palette and the dialog live here and each screen only
 * decides when to open them.
 */
package com.android.calendar.selectcalendars.compose

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.android.calendar.chrome.compose.HsvColorPicker
import com.google.android.material.color.MaterialColors
import ws.xsoh.etar.R

/**
 * A palette that reads well in both light and dark themes. Content is free to pick any colour;
 * these are just convenient starting points.
 */
internal val CALENDAR_COLOR_PALETTE = listOf(
    0xFFD32F2F.toInt(), 0xFFE64A19.toInt(), 0xFFF57C00.toInt(), 0xFFFBC02D.toInt(),
    0xFF689F38.toInt(), 0xFF388E3C.toInt(), 0xFF00796B.toInt(), 0xFF0097A7.toInt(),
    0xFF1976D2.toInt(), 0xFF303F9F.toInt(), 0xFF7B1FA2.toInt(), 0xFFC2185B.toInt(),
    0xFF5D4037.toInt(), 0xFF455A64.toInt(),
)

/**
 * Harmonises a calendar colour towards the wallpaper-derived primary so the swatches match the
 * event colours shown in the month / week / day / agenda views.
 */
@Composable
internal fun calendarSwatchColor(calendarColor: Int): Color {
    val primary = MaterialTheme.colorScheme.primary.toArgb()
    return remember(calendarColor, primary) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Color(MaterialColors.harmonize(calendarColor, primary))
        } else {
            Color(calendarColor)
        }
    }
}

/** The dot that stands for a calendar's colour, on a list row and on the edit screen's colour row. */
@Composable
internal fun CalendarColorSwatch(
    color: Int,
    modifier: Modifier = Modifier,
    size: Dp = 20.dp,
) {
    Box(
        modifier = modifier
            .size(size)
            .background(color = calendarSwatchColor(color), shape = CircleShape)
            .border(
                width = 1.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = CircleShape,
            ),
    )
}

/**
 * The colour picker both screens open for a calendar.
 *
 * The palette is a set of starting points; anything the user actually wants is available through the
 * picker behind the switch, which covers the whole range rather than fourteen colours. The dialog is
 * Material 3 - the extra-large corner token and the Compose AlertDialog - so it matches the rest of
 * the app rather than the platform's Material 2 dialog.
 */
@Composable
internal fun CalendarColorDialog(
    initial: Int,
    onDismiss: () -> Unit,
    onPick: (Int) -> Unit,
) {
    var custom by remember { mutableStateOf(false) }
    var customColor by remember { mutableStateOf(initial) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.extraLarge,
        title = {
            Text(
                stringResource(
                    if (custom) R.string.m3e_event_color_custom
                    else R.string.m3e_calendars_color_title
                )
            )
        },
        text = {
            if (custom) {
                HsvColorPicker(
                    initial = initial,
                    onColor = { customColor = it },
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Rows of six swatches.
                    CALENDAR_COLOR_PALETTE.chunked(6).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            row.forEach { color ->
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .background(color = Color(color), shape = CircleShape)
                                        .border(
                                            width = if (color == initial) 2.5.dp else 0.dp,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            shape = CircleShape,
                                        )
                                        .clickable { onPick(color) },
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (custom) {
                TextButton(onClick = { onPick(customColor) }) {
                    Text(stringResource(R.string.create_event_dialog_save))
                }
            } else {
                TextButton(onClick = { custom = true }) {
                    Text(stringResource(R.string.m3e_event_color_custom))
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = { if (custom) custom = false else onDismiss() }
            ) {
                Text(stringResource(R.string.m3e_back))
            }
        },
    )
}
