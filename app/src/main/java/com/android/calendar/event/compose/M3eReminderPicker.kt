/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * Shared reminder picker for the event editor and the event details screen.
 *
 * One row showing what is set; tapping it opens a checkbox menu where several reminders can be
 * ticked at once. The menu stays open while options are ticked, so a set of reminders is chosen in
 * one visit instead of one row plus an "add" button at a time. Both screens use this exact
 * composable, so the reminders UI is the same in the editor and in the viewer.
 */
package com.android.calendar.event.compose

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ws.xsoh.etar.R

/**
 * Lead times offered in the checkbox menu.
 *
 * The superset of what the editor and the details screen each used to offer, matching the classic
 * editor's array (R.array.reminder_minutes_values) so every reminder the classic editor could
 * create remains editable here.
 */
val M3E_REMINDER_VALUES = listOf(0, 5, 10, 15, 30, 60, 120, 240, 480, 720, 1440, 2880, 10080)

/** "At time of event", "10 minutes before" … for one lead time. */
@Composable
fun m3eReminderLabel(minutes: Int): String = when {
    minutes <= 0 -> stringResource(R.string.m3e_reminder_at_time)
    minutes % (24 * 60) == 0 -> stringResource(R.string.m3e_reminder_days, minutes / (24 * 60))
    minutes % 60 == 0 -> stringResource(R.string.m3e_reminder_hours, minutes / 60)
    else -> stringResource(R.string.m3e_reminder_minutes, minutes)
}

/** "提前10分钟、日程开始时" (CJK glue) / "10 minutes before, at time of event". */
@Composable
fun m3eReminderSummary(reminders: List<Int>): String {
    if (reminders.isEmpty()) {
        return stringResource(R.string.m3e_no_reminders)
    }
    val labels = ArrayList<String>(reminders.size)
    reminders.sortedDescending().forEach { labels.add(m3eReminderLabel(it)) }
    val language = java.util.Locale.getDefault().language
    val separator = if (language == "zh" || language == "ja" || language == "ko") "、" else ", "
    return labels.joinToString(separator)
}

/**
 * The checkbox menu itself, shared by the editor, the details screen and the agenda rows.
 *
 * Custom checkbox rows rather than DropdownMenuItem: the menu must stay open while several options
 * are ticked in one visit, and each change is applied the moment it is made - the details screen
 * saves it straight away, the editor carries it into the save, the agenda writes it and re-reads.
 * The menu closes on a tap outside or on back, which is the platform's own dismissal.
 */
@Composable
fun M3eReminderCheckboxMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    reminders: List<Int>,
    onRemindersChanged: (List<Int>) -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
    ) {
        // The options are laid out in a column of their own with a gap between them, and that
        // column is capped and scrollable.
        //
        // Flat rows with no space read as one block of text, and with thirteen options the menu is
        // taller than the space under the toolbar on a phone: without the cap and the scroll the
        // last few lead times - including "at time of event", the one at the top of the list - could
        // not be reached at all.
        Column(
            modifier = Modifier
                .heightIn(max = 380.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            M3E_REMINDER_VALUES.forEach { option ->
                val checked = option in reminders
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.large)
                        .clickable {
                            onRemindersChanged(
                                if (checked) reminders - option else reminders + option,
                            )
                        }
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = checked,
                        onCheckedChange = null,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = m3eReminderLabel(option),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        }
    }
}

/**
 * The reminders row shared by the editor and the details screen.
 *
 * @param reminders the currently set lead times, in minutes before the start.
 * @param onRemindersChanged called with the new set each time an option is ticked or unticked.
 * @param label the row's own label, "Notifications"/"提醒" - the two call sites name the same
 *              thing, and passing it keeps the composable free of the card structure around it.
 */
@Composable
fun M3eReminderMenuRow(
    reminders: List<Int>,
    onRemindersChanged: (List<Int>) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { menuOpen = true }
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            // No weight on the value: the label takes the free space and this sits against the end
            // of the row, which is how the time zone row below it reads. Given a weight of its own
            // it started halfway across and looked left-aligned under a right-aligned neighbour.
            Text(
                text = m3eReminderSummary(reminders),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .widthIn(max = 200.dp)
                    .padding(start = 8.dp),
            )
        }

        M3eReminderCheckboxMenu(
            expanded = menuOpen,
            onDismissRequest = { menuOpen = false },
            reminders = reminders,
            onRemindersChanged = onRemindersChanged,
        )
    }
}
