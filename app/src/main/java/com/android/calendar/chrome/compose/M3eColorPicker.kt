/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * Colour picking, shared by the event editor and the countdown screen.
 *
 * Both need the same two things - a palette and continuous adjustment - and having one copy means a
 * colour chosen in one place behaves the same as in the other.
 */
package com.android.calendar.chrome.compose

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

/**
 * Continuous colour adjustment.
 *
 * Three sliders rather than a wheel: hue picks the angle, then saturation and lightness refine it,
 * with a preview showing the result. Each slider's track is drawn as the gradient it controls, so
 * what moving it will do is visible before touching it.
 *
 * The state lives here and is emitted on every change, so the caller's row follows the sliders as
 * they move.
 */
@Composable
fun HsvColorPicker(initial: Int, onColor: (Int) -> Unit) {
    val start = remember(initial) {
        FloatArray(3).also { android.graphics.Color.colorToHSV(initial, it) }
    }
    var hue by remember(initial) { mutableFloatStateOf(start[0]) }
    var saturation by remember(initial) { mutableFloatStateOf(start[1]) }
    var lightness by remember(initial) { mutableFloatStateOf(start[2]) }

    fun emit() {
        onColor(android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, lightness)))
    }

    Column {
        // Hue: the full spectrum.
        GradientSlider(
            value = hue,
            range = 0f..360f,
            stops = (0..6).map {
                Color(android.graphics.Color.HSVToColor(floatArrayOf(it * 60f, 1f, 1f)))
            },
            onChange = { hue = it; emit() },
        )
        Spacer(Modifier.height(6.dp))
        // Saturation: grey through to the chosen hue at full strength.
        GradientSlider(
            value = saturation,
            range = 0f..1f,
            stops = listOf(
                Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, 0f, lightness))),
                Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, 1f, lightness))),
            ),
            onChange = { saturation = it; emit() },
        )
        Spacer(Modifier.height(6.dp))
        // Lightness: black through the hue to white.
        GradientSlider(
            value = lightness,
            range = 0f..1f,
            stops = listOf(
                Color.Black,
                Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, 0.5f))),
                Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, 1f))),
            ),
            onChange = { lightness = it; emit() },
        )
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            ColorSwatch(
                color = android.graphics.Color.HSVToColor(
                    floatArrayOf(hue, saturation, lightness)
                ),
                size = 32.dp,
            )
        }
    }
}

/**
 * A slider whose track is the gradient it controls.
 *
 * Drawn by hand: Material's Slider takes a single colour, and a colour picker whose sliders are one
 * flat colour does not say what they do. Dragging and tapping both move it.
 */
@Composable
fun GradientSlider(
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    stops: List<Color>,
    onChange: (Float) -> Unit,
) {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(32.dp)
            .pointerInput(range) {
                detectHorizontalDragGestures { change, _ ->
                    val fraction = (change.position.x / size.width).coerceIn(0f, 1f)
                    onChange(range.start + fraction * (range.endInclusive - range.start))
                }
            }
            .pointerInput(range) {
                detectTapGestures { offset ->
                    val fraction = (offset.x / size.width).coerceIn(0f, 1f)
                    onChange(range.start + fraction * (range.endInclusive - range.start))
                }
            },
    ) {
        val barHeight = 12.dp.toPx()
        val top = (size.height - barHeight) / 2f
        drawRoundRect(
            brush = Brush.horizontalGradient(stops),
            topLeft = Offset(0f, top),
            size = Size(size.width, barHeight),
            cornerRadius = CornerRadius(barHeight / 2f),
        )
        val fraction = ((value - range.start) / (range.endInclusive - range.start)).coerceIn(0f, 1f)
        val centre = Offset(fraction * size.width, size.height / 2f)
        drawCircle(color = Color.White, radius = barHeight * 0.75f, center = centre)
        drawCircle(
            color = Color.Black,
            radius = barHeight * 0.75f,
            center = centre,
            style = Stroke(width = 2f),
        )
    }
}

/** A colour chip. */
@Composable
fun ColorSwatch(color: Int, size: androidx.compose.ui.unit.Dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(Color(color))
    )
}

/** A row of colour chips, wrapped at [perRow]. The selected one is ticked. */
@Composable
fun ColorPaletteGrid(
    colors: List<Int>,
    selected: Int,
    onSelect: (Int) -> Unit,
    perRow: Int = 5,
) {
    Column {
        colors.chunked(perRow).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                row.forEach { color ->
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color(color))
                            .clickable { onSelect(color) },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (color == selected) {
                            Text("✓", color = Color.White)
                        }
                    }
                }
            }
        }
    }
}

/** True when a colour is light enough that black text reads better on it than white. */
fun isLightColor(color: Int): Boolean {
    val r = (color shr 16) and 0xFF
    val g = (color shr 8) and 0xFF
    val b = color and 0xFF
    // Rec. 601 luma, which is close enough for choosing a foreground.
    return (0.299 * r + 0.587 * g + 0.114 * b) > 160
}

/** The colour a Material surface would use for text on [color]. */
@Composable
fun onColorFor(color: Int): Color =
    if (isLightColor(color)) Color.Black else Color.White
