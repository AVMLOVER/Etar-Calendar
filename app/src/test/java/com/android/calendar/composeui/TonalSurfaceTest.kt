/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The black-surface decision, checked without a device.
 *
 * The reported defect was a month grid painted #000 with no visible tonal step under the cards. The
 * decision is colour arithmetic: keep a tonal surface, replace a black one with the baseline tone
 * tinted by the palette, and keep the container steps ordered. These cases pin all three.
 *
 * In this package so the project's usual unit-test filter runs it; the code under test lives in
 * com.android.calendar.theme.
 */
package com.android.calendar.composeui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.android.calendar.theme.BASELINE_DARK_SURFACE
import com.android.calendar.theme.isBlackSurface
import com.android.calendar.theme.tonalContainers
import com.android.calendar.theme.tonalSurface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TonalSurfaceTest {

    private val teal = Color(0xFF006A60)

    /** Pure black and the near-black the palette can produce are both treated as black. */
    @Test
    fun blackAndNearBlackAreTreatedAsBlack() {
        assertTrue(isBlackSurface(Color.Black))
        assertTrue(isBlackSurface(Color(0xFF010101)))
        assertFalse(isBlackSurface(BASELINE_DARK_SURFACE))
        assertFalse(isBlackSurface(Color(0xFF141218)))
    }

    /** A healthy dark surface is returned unchanged - the sanitising must not tint every palette. */
    @Test
    fun aTonalSurfaceIsKeptAsItIs() {
        assertEquals(BASELINE_DARK_SURFACE, tonalSurface(BASELINE_DARK_SURFACE, teal, dark = true))
        val light = Color(0xFFFFFBFE)
        assertEquals(light, tonalSurface(light, teal, dark = false))
    }

    /** A black dark surface becomes a very dark grey, not the baseline and not black. */
    @Test
    fun aBlackDarkSurfaceBecomesTheTintedBaseline() {
        val surface = tonalSurface(Color.Black, teal, dark = true)
        assertFalse("still black", isBlackSurface(surface))
        assertTrue("not a dark surface", surface.luminance() < 0.1f)
        // Tinted: the fallback is not the plain baseline grey.
        assertTrue(surface != BASELINE_DARK_SURFACE)
    }

    /** A black light surface becomes the light baseline, not a dark one. */
    @Test
    fun aBlackLightSurfaceBecomesTheLightBaseline() {
        val surface = tonalSurface(Color.Black, teal, dark = false)
        assertFalse(isBlackSurface(surface))
        assertTrue("not a light surface", surface.luminance() > 0.8f)
    }

    /** A different accent gives a different tint - the colour belongs to the palette's family. */
    @Test
    fun theAccentTintsTheFallback() {
        val tealSurface = tonalSurface(Color.Black, teal, dark = true)
        val redSurface = tonalSurface(Color.Black, Color(0xFF8F1D1D), dark = true)
        assertTrue(tealSurface != redSurface)
    }

    /** The container steps start at the surface and each step moves towards the content colour. */
    @Test
    fun theContainerStepsRiseTowardsTheContent() {
        val surface = Color(0xFF141218)
        val content = Color(0xFFE6E1E5)
        val steps = tonalContainers(surface, content)
        assertEquals(5, steps.size)
        assertEquals(surface, steps.first())
        for (index in 1 until steps.size) {
            assertTrue(
                "step $index is not lighter than step ${index - 1}",
                steps[index].luminance() > steps[index - 1].luminance(),
            )
        }
    }
}
