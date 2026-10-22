/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The event block's three type sizes, checked without a device.
 *
 * The description used to be 9sp and was hard to read; the rule now is a readable description, a
 * title one clear step above it and a url no larger than the description. These cases keep the three
 * from drifting back together.
 */
package com.android.calendar.composeui

import org.junit.Assert.assertTrue
import org.junit.Test

class EventBlockTypeTest {

    /** The description is readable: 11 to 12sp, not the old 9sp. */
    @Test
    fun theDescriptionIsReadable() {
        val size = EventBlockType.descriptionSize.value
        assertTrue("description size $size", size >= 11f && size <= 12f)
    }

    /** The title is a clear step above the description. */
    @Test
    fun theTitleIsLargerThanTheDescription() {
        assertTrue(
            "title ${EventBlockType.titleSize.value} vs description " +
                EventBlockType.descriptionSize.value,
            EventBlockType.titleSize.value > EventBlockType.descriptionSize.value,
        )
    }

    /** The url is never larger than the description. */
    @Test
    fun theUrlIsNoLargerThanTheDescription() {
        assertTrue(
            "url ${EventBlockType.urlSize.value} vs description " +
                EventBlockType.descriptionSize.value,
            EventBlockType.urlSize.value <= EventBlockType.descriptionSize.value,
        )
    }

    /** Every line height leaves room for its glyphs. */
    @Test
    fun lineHeightsFitTheirSizes() {
        assertTrue(EventBlockType.titleLineHeight.value > EventBlockType.titleSize.value)
        assertTrue(EventBlockType.descriptionLineHeight.value > EventBlockType.descriptionSize.value)
        assertTrue(EventBlockType.urlLineHeight.value > EventBlockType.urlSize.value)
    }
}
