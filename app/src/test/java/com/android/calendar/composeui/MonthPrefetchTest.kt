/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The early-load decision, checked without a device.
 *
 * The page being dragged towards is fetched once the drag passes a quarter of a page: earlier than
 * the settle, so the markers are usually there when the page becomes current, and not on every
 * frame, so a drag stays allocation-free. These cases pin the threshold and both directions.
 */
package com.android.calendar.composeui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MonthPrefetchTest {

    /** No drag yet: the page on screen is already the one loaded by the settle. */
    @Test
    fun aRestingPagerPrefetchesNothing() {
        assertNull(pageToPrefetch(currentPage = 10, offsetFraction = 0f))
    }

    /** A drag towards the next page passes the threshold: the next page is fetched. */
    @Test
    fun aForwardDragPrefetchesTheNextPage() {
        assertEquals(11, pageToPrefetch(currentPage = 10, offsetFraction = 0.3f))
    }

    /** A drag towards the previous page passes the threshold: the previous page is fetched. */
    @Test
    fun aBackwardDragPrefetchesThePreviousPage() {
        assertEquals(9, pageToPrefetch(currentPage = 10, offsetFraction = -0.3f))
    }

    /** Exactly a quarter of the way is far enough; a hair less is not. */
    @Test
    fun theThresholdIsInclusive() {
        assertEquals(11, pageToPrefetch(currentPage = 10, offsetFraction = PREFETCH_THRESHOLD))
        assertEquals(9, pageToPrefetch(currentPage = 10, offsetFraction = -PREFETCH_THRESHOLD))
        assertNull(pageToPrefetch(currentPage = 10, offsetFraction = 0.24f))
        assertNull(pageToPrefetch(currentPage = 10, offsetFraction = -0.24f))
    }

    /** The far end of the drag range still names the same page - one page ahead, not two. */
    @Test
    fun theFarEndOfADragStillNamesOneNeighbour() {
        assertEquals(11, pageToPrefetch(currentPage = 10, offsetFraction = 0.5f))
        assertEquals(9, pageToPrefetch(currentPage = 10, offsetFraction = -0.5f))
    }

    /** A landing page with nothing before it names page -1, which the caller bounds-checks. */
    @Test
    fun theEdgePagesAreReportedAsTheyAre() {
        assertEquals(0, pageToPrefetch(currentPage = 1, offsetFraction = -0.4f))
        assertEquals(-1, pageToPrefetch(currentPage = 0, offsetFraction = -0.4f))
    }

    /** The threshold is a parameter, so the caller can ask for a different point. */
    @Test
    fun aCustomThresholdIsHonoured() {
        assertNull(pageToPrefetch(currentPage = 10, offsetFraction = 0.3f, threshold = 0.4f))
        assertEquals(11, pageToPrefetch(currentPage = 10, offsetFraction = 0.4f, threshold = 0.4f))
    }
}
