/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The URL property picker, checked without a device.
 */
package com.android.calendar.composeui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EventUrlPropertyTest {

    private val privateName = "private:URL"
    private val normalName = "URL"

    @Test
    fun thePlainPropertyIsRead() {
        assertEquals(
            "https://example.com/event",
            pickEventUrl(
                listOf(normalName to " https://example.com/event "),
                privateName,
                normalName,
            ),
        )
    }

    @Test
    fun thePrivatePropertyWins() {
        assertEquals(
            "https://private.example.com",
            pickEventUrl(
                listOf(normalName to "https://public.example.com", privateName to "https://private.example.com"),
                privateName,
                normalName,
            ),
        )
    }

    @Test
    fun noUrlRowGivesNull() {
        assertNull(pickEventUrl(emptyList(), privateName, normalName))
    }

    @Test
    fun aBlankValueDoesNotCountAsAUrl() {
        assertNull(pickEventUrl(listOf(normalName to "   "), privateName, normalName))
        assertEquals(
            "https://fallback.example.com",
            pickEventUrl(
                listOf(privateName to "  ", normalName to "https://fallback.example.com"),
                privateName,
                normalName,
            ),
        )
    }
}
