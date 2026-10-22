/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The add controls' shared numbers and the swipe-delete boundary, checked without a device.
 */
package com.android.calendar.composeui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AddFabSpecTest {

    /** The add button is a 64dp rounded square, not a 56dp circle. */
    @Test
    fun theAddButtonIsALargeRoundedSquare() {
        assertEquals(64f, AddFabSpec.size.value, 0.001f)
        assertEquals(20f, AddFabSpec.corner.value, 0.001f)
        assertTrue(AddFabSpec.size.value > 56f)
    }

    /** The icon grows with the container but stays inside it. */
    @Test
    fun theIconIsProportionalAndInsideTheContainer() {
        assertEquals(28f, AddFabSpec.iconSize.value, 0.001f)
        assertTrue(AddFabSpec.iconSize.value > 24f)
        assertTrue(AddFabSpec.iconSize.value < AddFabSpec.size.value)
    }

    /** The scrim is Material's modal 32%. */
    @Test
    fun theScrimUsesTheModalAlpha() {
        assertEquals(0.32f, AddFabSpec.SCRIM_ALPHA, 0.001f)
    }
}
