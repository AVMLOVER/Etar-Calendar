/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The pinch zoom limits, checked without a device.
 *
 * The limits are stated as visible hours - four at the closest zoom, nineteen at the widest - so they
 * hold on any screen. These cases pin the clamp, the mapping both ways, and the direction of a pinch.
 */
package com.android.calendar.composeui

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HourZoomTest {

    private val viewport = 600.dp

    /** At the closest zoom four hours fill the viewport; at the widest, nineteen. */
    @Test
    fun theLimitsAreFourAndNineteenVisibleHours() {
        val range = hourHeightRange(viewport)
        assertEquals(600f / 19f, range.start, 0.001f)
        assertEquals(600f / 4f, range.endInclusive, 0.001f)
        assertEquals(4f, visibleHoursFor(range.endInclusive, viewport), 0.001f)
        assertEquals(19f, visibleHoursFor(range.start, viewport), 0.001f)
    }

    /** A stored height outside the range is clamped into it. */
    @Test
    fun theHourHeightIsClampedToTheRange() {
        assertEquals(600f / 4f, clampHourHeight(1000f, viewport), 0.001f)
        assertEquals(600f / 19f, clampHourHeight(1f, viewport), 0.001f)
        assertEquals(60f, clampHourHeight(60f, viewport), 0.001f)
    }

    /** A pinch out makes the hour taller but never past the widest zoom; a pinch in the reverse. */
    @Test
    fun aPinchIsAppliedAndClamped() {
        assertEquals(120f, zoomedHourHeight(60f, 2f, viewport), 0.001f)
        assertEquals(60f, zoomedHourHeight(120f, 0.5f, viewport), 0.001f)
        // Half of 60 would show twenty hours, one more than the widest zoom allows, so it clamps.
        assertEquals(600f / 19f, zoomedHourHeight(60f, 0.5f, viewport), 0.001f)
        assertEquals(600f / 4f, zoomedHourHeight(140f, 3f, viewport), 0.001f)
        assertEquals(600f / 19f, zoomedHourHeight(40f, 0.1f, viewport), 0.001f)
    }

    /** The default spacing shows the expected number of hours on a phone-sized viewport. */
    @Test
    fun theDefaultSpacingMapsBackToItsVisibleHours() {
        assertEquals(10f, visibleHoursFor(DEFAULT_HOUR_HEIGHT.value, viewport), 0.001f)
    }

    /** A viewport that has not been measured yet falls back to the default spacing, unclamped. */
    @Test
    fun anUnmeasuredViewportFallsBackToTheDefault() {
        val range = hourHeightRange(0.dp)
        assertEquals(DEFAULT_HOUR_HEIGHT.value, range.start, 0.001f)
        assertEquals(DEFAULT_HOUR_HEIGHT.value, range.endInclusive, 0.001f)
    }

    /** Every clamp keeps the visible hours inside the two limits. */
    @Test
    fun theClampedHeightAlwaysShowsBetweenFourAndNineteenHours() {
        for (height in listOf(1f, 20f, 60f, 150f, 500f)) {
            val clamped = clampHourHeight(height, viewport)
            val visible = visibleHoursFor(clamped, viewport)
            assertTrue("$visible visible hours for $height", visible in 4f..19f)
        }
    }
}
