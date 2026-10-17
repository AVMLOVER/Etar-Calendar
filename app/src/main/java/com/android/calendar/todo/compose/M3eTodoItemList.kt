/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The checklist itself, shared by the to-do screen and its editor.
 *
 * The two differ in what a row is - a line of text with a tick box, or a field being written - but
 * not in how the rows behave: both are reordered by dragging the handle at the end of the row, and
 * both are deleted by swiping the row to the left, which asks before it removes anything. Keeping
 * one implementation is what keeps the two from drifting apart.
 */
package com.android.calendar.todo.compose

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.android.calendar.todo.TodoItem
import ws.xsoh.etar.R

/**
 * The rows of a checklist.
 *
 * [onMove] is handed the index a row is at and the index it should take, and is expected to hand
 * back a reordered list; the rows are reordered as the drag crosses the middle of its neighbour, so
 * the list follows the finger rather than jumping when it is let go.
 */
@Composable
fun TodoItemColumn(
    items: List<TodoItem>,
    modifier: Modifier = Modifier,
    editable: Boolean = false,
    showCheckboxes: Boolean = false,
    onToggle: (TodoItem) -> Unit = {},
    onTextChange: (TodoItem, String) -> Unit = { _, _ -> },
    onMove: (Int, Int) -> Unit = { _, _ -> },
    onDeleteRequest: (TodoItem) -> Unit = {},
) {
    // Read through a state holder so a drag that is already under way sees the reordered list: the
    // pointer input is keyed on the item, and re-keying it on every move would cancel the gesture.
    val currentItems by rememberUpdatedState(items)
    val heights = remember { mutableStateMapOf<String, Int>() }
    var draggingId by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableStateOf(0f) }
    val context = LocalContext.current
    val haptics = remember(context) { TodoHaptics(context) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items.forEachIndexed { index, item ->
            key(item.id) {
                val dragging = draggingId == item.id
                val dismissState = rememberSwipeToDismissBoxState(
                    confirmValueChange = { value ->
                        if (value == SwipeToDismissBoxValue.EndToStart) {
                            haptics.tick()
                            onDeleteRequest(item)
                        }
                        // Never dismissed by the gesture itself: the confirmation dialog decides.
                        false
                    },
                )
                SwipeToDismissBox(
                    state = dismissState,
                    enableDismissFromStartToEnd = false,
                    enableDismissFromEndToStart = true,
                    backgroundContent = {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    color = MaterialTheme.colorScheme.errorContainer,
                                    shape = MaterialTheme.shapes.large,
                                )
                                .padding(horizontal = 20.dp),
                            contentAlignment = Alignment.CenterEnd,
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_delete),
                                contentDescription = stringResource(R.string.m3e_event_delete),
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                            )
                        }
                    },
                    modifier = Modifier
                        .zIndex(if (dragging) 1f else 0f)
                        .graphicsLayer { translationY = if (dragging) dragOffset else 0f }
                        .onSizeChanged { heights[item.id] = it.height },
                ) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (showCheckboxes) {
                                Checkbox(
                                    checked = item.done,
                                    onCheckedChange = { onToggle(item) },
                                )
                            }
                            if (editable) {
                                OutlinedTextField(
                                    value = item.text,
                                    onValueChange = { typed -> onTextChange(item, typed) },
                                    modifier = Modifier.weight(1f).padding(vertical = 2.dp),
                                    placeholder = { Text(item.text.ifEmpty { "" }) },
                                    // Wrapping is the point of a checklist line, so the field grows
                                    // with what is written into it rather than scrolling sideways.
                                    singleLine = false,
                                    maxLines = 6,
                                )
                            } else {
                                Text(
                                    text = item.text,
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(horizontal = 4.dp)
                                        .alpha(if (item.done) 0.55f else 1f),
                                    style = MaterialTheme.typography.bodyLarge,
                                    textDecoration = if (item.done) {
                                        TextDecoration.LineThrough
                                    } else {
                                        TextDecoration.None
                                    },
                                )
                            }
                            TodoDragHandle(
                                modifier = Modifier.pointerInput(item.id) {
                                    detectDragGestures(
                                        onDragStart = {
                                            draggingId = item.id
                                            dragOffset = 0f
                                            haptics.tick()
                                        },
                                        onDragEnd = {
                                            draggingId = null
                                            dragOffset = 0f
                                        },
                                        onDragCancel = {
                                            draggingId = null
                                            dragOffset = 0f
                                        },
                                        onDrag = { change, amount ->
                                            change.consume()
                                            dragOffset += amount.y
                                            val list = currentItems
                                            val at = list.indexOfFirst { it.id == item.id }
                                            if (at < 0) return@detectDragGestures
                                            if (dragOffset > 0 && at < list.size - 1) {
                                                val next = heights[list[at + 1].id] ?: 0
                                                if (next > 0 && dragOffset > next / 2f) {
                                                    onMove(at, at + 1)
                                                    dragOffset -= next
                                                }
                                            } else if (dragOffset < 0 && at > 0) {
                                                val previous = heights[list[at - 1].id] ?: 0
                                                if (previous > 0 && -dragOffset > previous / 2f) {
                                                    onMove(at, at - 1)
                                                    dragOffset += previous
                                                }
                                            }
                                        },
                                    )
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** The two columns of dots a drag handle is drawn from: no icon font is a dependency here. */
@Composable
private fun TodoDragHandle(modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(
        modifier = modifier
            .size(36.dp)
            .padding(8.dp)
    ) {
        val radius = size.minDimension / 8f
        val columns = listOf(0.3f, 0.7f)
        val rows = listOf(0.22f, 0.5f, 0.78f)
        for (x in columns) {
            for (y in rows) {
                drawCircle(
                    color = color,
                    radius = radius,
                    center = Offset(size.width * x, size.height * y),
                )
            }
        }
    }
}

/** Adds a row, under the ones already there. */
@Composable
fun TodoAddItemRow(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.primary,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = text, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

/** The short tick the project's other swipe gestures play. */
class TodoHaptics(private val context: android.content.Context) {
    fun tick() {
        try {
            val vibrator = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                (context.getSystemService(android.content.Context.VIBRATOR_MANAGER_SERVICE)
                        as? android.os.VibratorManager)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(android.content.Context.VIBRATOR_SERVICE)
                        as? android.os.Vibrator
            }
            vibrator?.let {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    it.vibrate(
                        android.os.VibrationEffect.createPredefined(
                            android.os.VibrationEffect.EFFECT_TICK
                        )
                    )
                } else {
                    @Suppress("DEPRECATION")
                    it.vibrate(20)
                }
            }
        } catch (t: Throwable) {
            // A device without a vibrator is not a reason to fail a swipe.
        }
    }
}
