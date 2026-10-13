/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The countdown detail sheet, the delete dialog, and the card image export.
 */
package com.android.calendar.countdown.compose

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.ImageBitmap
import com.android.calendar.chrome.compose.CountdownBackdrop
import android.graphics.Bitmap
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.android.calendar.chrome.compose.onColorFor
import com.android.calendar.countdown.Countdown
import com.android.calendar.countdown.CountdownState
import com.android.calendar.countdown.CountdownStore
import com.android.calendar.countdown.CountdownUnit
import com.android.calendar.countdown.PngExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ws.xsoh.etar.R

/**
 * The half-height sheet a card opens.
 *
 * It repeats what the card says at a readable size and offers the two things that need more room
 * than a card has: saving the card as an image, and deleting.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CountdownDetailSheet(
    countdown: Countdown,
    state: CountdownState,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // The card is captured through a graphics layer rather than redrawn onto a Bitmap: the sheet is
    // already showing exactly what should be saved, so there is one definition of how it looks.
    val graphicsLayer = rememberGraphicsLayer()
    // A fixed shape for the exported card: 320 by 420 is a portrait card that reads well shared.
    // 3:4, which is a portrait card that reads well shared. The exported PNG is this ratio too.
    val exportWidth = 300.dp
    val exportHeight = 400.dp
    var saving by remember { mutableStateOf(false) }

    val onColor = onColorFor(countdown.color)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // The card, drawn once, and recorded as it is drawn.
            //
            // It used to be drawn twice - a recording box that was itself visible, plus a second copy
            // showing the capture - which put the picture on screen twice and pushed the buttons off
            // the bottom. Recording and drawing are the same pass here, so there is one card and what
            // gets saved is literally what is on screen.
            //
            // The size is fixed at a 3:4 portrait card. That is what stops a background picture's
            // proportions from stretching the sheet, and the export follows the recorded pixels rather
            // than guessing at them.
            Box(
                modifier = Modifier
                    .size(exportWidth, exportHeight)
                    .drawWithContent {
                        graphicsLayer.record { this@drawWithContent.drawContent() }
                        drawLayer(graphicsLayer)
                    },
                contentAlignment = Alignment.Center,
            ) {
                CountdownBackdrop(
                    color = countdown.color,
                    backgroundImageUri = countdown.backgroundImageUri,
                    cornerRadius = 24.dp,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    CardFace(countdown = countdown, state = state, onColor = onColor)
                }
            }

            Spacer(Modifier.height(20.dp))

            // ------------------------------------------------------------------------- the actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TextButton(
                    onClick = {
                        if (saving) return@TextButton
                        saving = true
                        scope.launch {
                            val message = exportCard(context, graphicsLayer, countdown.name)
                            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                            saving = false
                        }
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = stringResource(
                            if (saving) R.string.m3e_countdown_saving
                            else R.string.m3e_countdown_save_image
                        )
                    )
                }
                TextButton(onClick = onEdit, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.m3e_countdown_edit))
                }
                TextButton(onClick = onDelete, modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.m3e_event_delete),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/**
 * The card's contents.
 *
 * Used twice: inside the fixed-size box that gets recorded for export, and in the smaller copy the
 * sheet shows. One definition means the two cannot drift apart.
 */
@Composable
private fun CardFace(countdown: Countdown, state: CountdownState, onColor: Color) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.m3e_countdown_detail_distance, countdown.name),
            style = MaterialTheme.typography.labelLarge,
            color = onColor.copy(alpha = 0.85f),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(
                if (state.passed) R.string.m3e_countdown_passed
                else R.string.m3e_countdown_remaining
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = onColor.copy(alpha = 0.85f),
        )
        Text(
            text = state.value,
            style = MaterialTheme.typography.displayMedium,
            fontWeight = FontWeight.Bold,
            color = onColor,
            textAlign = TextAlign.Center,
        )
        Text(
            text = unitLabel(state.unit),
            style = MaterialTheme.typography.bodyMedium,
            color = onColor.copy(alpha = 0.85f),
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = countdown.date.format(CountdownStore.DATE_FORMAT) +
                (countdown.time?.let { " " + it.toString() } ?: ""),
            style = MaterialTheme.typography.labelMedium,
            color = onColor.copy(alpha = 0.75f),
        )
    }
}
/** Captures the recorded layer and writes it to Downloads. Returns the message to show. */
private suspend fun exportCard(
    context: android.content.Context,
    graphicsLayer: androidx.compose.ui.graphics.layer.GraphicsLayer,
    name: String,
): String {
    val bitmap: Bitmap = try {
        // toImageBitmap has to run on the main thread: it reads the layer's GPU-backed contents.
        val image = graphicsLayer.toImageBitmap()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            image.asAndroidBitmap()
        } else {
            // asAndroidBitmap only exists from API 26. Below that the card cannot be exported, which
            // is a limitation of the platform rather than of this screen.
            return context.getString(R.string.m3e_countdown_save_failed)
        }
    } catch (t: Throwable) {
        return context.getString(R.string.m3e_countdown_save_failed)
    }

    return withContext(Dispatchers.IO) {
        try {
            PngExporter.saveToDownloads(context, bitmap, name)
            context.getString(R.string.m3e_countdown_saved, name)
        } catch (t: Throwable) {
            context.getString(R.string.m3e_countdown_save_failed)
        }
    }
}

/**
 * The one delete confirmation, used by the card, by a left swipe, and by the sheet.
 *
 * The "also delete the calendar event" box appears only when there is an event to delete, which is
 * what keeps an ordinary countdown from being asked about something that does not exist.
 */
@Composable
fun CountdownDeleteDialog(
    countdown: Countdown,
    onDismiss: () -> Unit,
    onConfirm: (alsoDeleteEvent: Boolean) -> Unit,
) {
    // Defaults to deleting the pair: they were created together, and leaving the event behind is the
    // surprising outcome.
    var alsoDeleteEvent by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.extraLarge,
        title = { Text(stringResource(R.string.m3e_countdown_delete_title)) },
        text = {
            Column {
                Text(countdown.name, style = MaterialTheme.typography.bodyLarge)
                if (countdown.eventId != null) {
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = alsoDeleteEvent,
                            onCheckedChange = { alsoDeleteEvent = it },
                        )
                        Text(
                            text = stringResource(R.string.m3e_countdown_also_delete_event),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(alsoDeleteEvent && countdown.eventId != null) }) {
                Text(
                    text = stringResource(R.string.m3e_event_delete),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.m3e_cancel)) }
        },
    )
}

/**
 * The agenda's confirmation, shown only for an event that has a countdown.
 *
 * An event without one is deleted exactly as before, with no dialog: the swipe is the confirmation,
 * and asking on every row would be noise.
 */
@Composable
fun EventDeleteWithCountdownDialog(
    eventTitle: String,
    /** The paired countdown's name, or null when the event has none. */
    countdownName: String?,
    onDismiss: () -> Unit,
    onConfirm: (alsoDeleteCountdown: Boolean) -> Unit,
) {
    var alsoDeleteCountdown by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.extraLarge,
        title = { Text(stringResource(R.string.m3e_event_delete_title)) },
        text = {
            Column {
                Text(eventTitle.ifBlank { stringResource(R.string.no_title_label) })
                // The box is only useful when there is a countdown to remove; without one the dialog
                // is a plain confirmation, which is what every swipe now gets.
                if (countdownName != null) {
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = alsoDeleteCountdown,
                            onCheckedChange = { alsoDeleteCountdown = it },
                        )
                        Text(
                            text = stringResource(
                                R.string.m3e_event_also_delete_countdown,
                                countdownName,
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(alsoDeleteCountdown) }) {
                Text(
                    text = stringResource(R.string.m3e_event_delete),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.m3e_cancel)) }
        },
    )
}
