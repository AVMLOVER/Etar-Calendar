/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The hour spacing of the day, three day and week grids, and the pinch limits.
 *
 * The grids are laid out from one number: the height of an hour. Pinching changes that number within
 * two fixed limits - four hours visible at the closest zoom, nineteen at the widest - which is the
 * same range Google Calendar offers. Expressing the limit as "visible hours" rather than as an
 * absolute dp height keeps it independent of the screen: the same limit works on a phone and on a
 * tablet, because the hour height is derived from the viewport each time.
 *
 * Pure functions because this is arithmetic the gesture and the layout both depend on, and because
 * the clamp is worth checking without a device.
 */
package com.android.calendar.composeui

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** The fewest hours that can be on screen at once: the closest zoom. */
const val MIN_VISIBLE_HOURS = 4f

/** The most hours that can be on screen at once: the widest zoom. */
const val MAX_VISIBLE_HOURS = 19f

/** The spacing the grids have always used, and the one a first run starts from. */
val DEFAULT_HOUR_HEIGHT = 60.dp

/** The hour heights allowed for a viewport: the visible-hours limits expressed in dp per hour. */
fun hourHeightRange(viewportHeight: Dp): ClosedFloatingPointRange<Float> {
    if (viewportHeight.value <= 0f) {
        return DEFAULT_HOUR_HEIGHT.value..DEFAULT_HOUR_HEIGHT.value
    }
    return (viewportHeight.value / MAX_VISIBLE_HOURS)..(viewportHeight.value / MIN_VISIBLE_HOURS)
}

/**
 * The hour height to lay the grid out with, clamped to the visible-hours limits.
 *
 * The stored value can be anything - it survives across rotations and window sizes - so it is
 * clamped again against the viewport it is about to be drawn in.
 */
fun clampHourHeight(hourHeight: Float, viewportHeight: Dp): Float =
    hourHeight.coerceIn(hourHeightRange(viewportHeight))

/** How many hours [hourHeight] shows in [viewportHeight]. */
fun visibleHoursFor(hourHeight: Float, viewportHeight: Dp): Float =
    if (hourHeight <= 0f) MAX_VISIBLE_HOURS else viewportHeight.value / hourHeight

/**
 * The hour height after a pinch of [zoom] around [hourHeight], clamped.
 *
 * A pinch out (zoom > 1) makes an hour taller, which shows fewer hours; a pinch in does the reverse.
 */
fun zoomedHourHeight(hourHeight: Float, zoom: Float, viewportHeight: Dp): Float =
    clampHourHeight(hourHeight * zoom, viewportHeight)
