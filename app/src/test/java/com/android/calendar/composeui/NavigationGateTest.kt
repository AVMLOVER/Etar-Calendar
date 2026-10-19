/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The gate a menu-driven navigation follows, checked without a device.
 *
 * There is almost nothing gated any more, and that is the point: a drawer choice snaps the drawer
 * shut without animating and runs on the next main-loop turn, so it waits for no drawer animation -
 * that wait was both the visible delay and a second leftward slide. Only the overflow popup, which
 * the platform dismisses after its callback returns, takes one posted retry.
 */
package com.android.calendar.composeui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationGateTest {

    /**
     * A drawer choice is not gated by any animation: the drawer is closed without animation before
     * the step is posted, so the only question left is whether a menu callback is still running.
     */
    @Test
    fun aDrawerChoiceIsNotGatedByTheDrawer() {
        assertTrue(isReadyToNavigate(menuDismissing = false))
    }

    /** The overflow popup is dismissed after its callback returns, so that callback gates one turn. */
    @Test
    fun aMenuChoiceWaitsForItsCallbackToReturn() {
        assertFalse(isReadyToNavigate(menuDismissing = true))
        assertTrue(isReadyToNavigate(menuDismissing = false))
    }

    /**
     * The step runs once, on the single turn it is posted for: a repeated turn cannot navigate
     * again, because taking it empties the slot.
     */
    @Test
    fun thePostedTurnRunsTheChoiceExactlyOnce() {
        val pending = PendingNavigation()
        var navigations = 0
        pending.request { navigations++ }
        assertTrue(pending.hasPending)

        pending.take()?.invoke()
        assertEquals(1, navigations)

        pending.take()?.invoke()
        assertEquals(1, navigations)
        assertNull(pending.take())
    }

    /** A second menu choice replaces the first rather than queueing two navigations. */
    @Test
    fun aSecondChoiceReplacesTheFirstInsteadOfQueueing() {
        val pending = PendingNavigation()
        val taken = mutableListOf<String>()
        pending.request { taken.add("first") }
        pending.request { taken.add("second") }
        pending.take()?.invoke()
        assertEquals(listOf("second"), taken)
        assertNull(pending.take())
    }

    /** Back while the choice is still waiting cancels it instead of letting it run later. */
    @Test
    fun aCancelledChoiceNeverRuns() {
        val pending = PendingNavigation()
        var navigations = 0
        pending.request { navigations++ }
        pending.cancel()
        assertNull(pending.take())
        assertEquals(0, navigations)
    }
}
