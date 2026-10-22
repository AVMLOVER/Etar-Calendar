/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The web-URL normaliser, checked without a device.
 */
package com.android.calendar.composeui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WebLinkTest {

    @Test
    fun aBareHostGetsHttps() {
        assertEquals("https://example.com/x", normaliseWebUrl("example.com/x"))
        assertEquals("https://www.example.com", normaliseWebUrl("www.example.com"))
    }

    @Test
    fun anHttpOrHttpsUrlIsKept() {
        assertEquals("http://example.com/a?b=1", normaliseWebUrl("http://example.com/a?b=1"))
        assertEquals("HTTPS://example.com", normaliseWebUrl("HTTPS://example.com"))
    }

    @Test
    fun surroundingWhitespaceAndNewlinesAreTrimmed() {
        assertEquals("https://example.com", normaliseWebUrl("  https://example.com\n"))
        assertEquals("https://example.com", normaliseWebUrl("\n example.com \t"))
    }

    @Test
    fun aHostWithAPortIsNotAScheme() {
        assertEquals("https://example.com:8443/x", normaliseWebUrl("example.com:8443/x"))
    }

    @Test
    fun nonWebSchemesAreRefused() {
        assertNull(normaliseWebUrl("mailto:someone@example.com"))
        assertNull(normaliseWebUrl("tel:+123456"))
        assertNull(normaliseWebUrl("ftp://example.com/file"))
    }

    @Test
    fun anEmptyOrUnusableValueGivesNull() {
        assertNull(normaliseWebUrl(""))
        assertNull(normaliseWebUrl("   "))
        assertNull(normaliseWebUrl("https://"))
    }
}
