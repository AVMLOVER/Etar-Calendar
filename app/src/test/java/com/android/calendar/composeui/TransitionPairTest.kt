/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The animation pairs, checked without a device.
 *
 * Direction is now passed with the operation, so these ids are the whole contract: a start and a
 * hand-over move left, a return moves right. Swapping one pair, or giving a finish the forward pair,
 * would be invisible until someone watched the animation - these assertions catch it.
 */
package com.android.calendar.composeui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import ws.xsoh.etar.R

class TransitionPairTest {

    /** A new page opens from the right: the page it covers leaves to the left. */
    @Test
    fun aStartMovesLeft() {
        val pair = animationPairFor(SlideOperation.START)
        assertEquals(R.anim.slide_left_in, pair.enterAnim)
        assertEquals(R.anim.slide_left_out, pair.exitAnim)
    }

    /** A return: the leaving page goes right and the revealed one comes back from the left. */
    @Test
    fun aReturningFinishMovesRight() {
        val pair = animationPairFor(SlideOperation.FINISH_RETURN)
        assertEquals(R.anim.slide_right_in, pair.enterAnim)
        assertEquals(R.anim.slide_right_out, pair.exitAnim)
    }

    /** A finish that hands over to the page it opened keeps moving the way that page is moving. */
    @Test
    fun aHandOverFinishKeepsMovingLeft() {
        val pair = animationPairFor(SlideOperation.FINISH_FORWARD)
        assertEquals(R.anim.slide_left_in, pair.enterAnim)
        assertEquals(R.anim.slide_left_out, pair.exitAnim)
    }

    /** The forward pair is one pair, shared by the start and the hand-over finish. */
    @Test
    fun theForwardOperationsShareTheSamePair() {
        assertEquals(
            animationPairFor(SlideOperation.START),
            animationPairFor(SlideOperation.FINISH_FORWARD),
        )
    }

    /** The return pair is the reverse of the forward one, and not accidentally the same. */
    @Test
    fun theReturnPairIsTheReverseOfTheForwardOne() {
        val forward = animationPairFor(SlideOperation.START)
        val back = animationPairFor(SlideOperation.FINISH_RETURN)
        assertNotEquals(forward, back)
        assertEquals(R.anim.slide_right_in, back.enterAnim)
        assertEquals(R.anim.slide_right_out, back.exitAnim)
    }

    /** Only a hand-over finish moves forward; every other finish is a return. */
    @Test
    fun onlyAHandOverFinishMovesForward() {
        assertEquals(SlideOperation.FINISH_FORWARD, finishOperation(handsOver = true))
        assertEquals(SlideOperation.FINISH_RETURN, finishOperation(handsOver = false))
    }
}
