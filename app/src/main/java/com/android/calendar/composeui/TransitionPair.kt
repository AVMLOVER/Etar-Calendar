/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The two slide pairs, and which navigation operation plays which.
 *
 * Direction travels with the operation now: a start carries its animation in ActivityOptions, and a
 * finish passes its pair to overridePendingTransition right after finish(). Nothing is stored on the
 * activity to be read later, which is what the per-activity override did - and what was silently
 * dropped whenever the platform was not in the state that accepted it.
 *
 * One table decides the pairs, so a future change cannot swap the halves of one operation without
 * failing the test that pins them.
 */
package com.android.calendar.composeui

import ws.xsoh.etar.R

/** An enter/exit animation pair, in the order the platform takes them. */
data class SlideAnimationPair(val enterAnim: Int, val exitAnim: Int)

/** The navigation operation an animation is chosen for. */
enum class SlideOperation {
    /** A new page opens: it arrives from the right while the page it covers leaves to the left. */
    START,

    /** A page finishes and the page underneath returns from the left while it leaves right. */
    FINISH_RETURN,

    /** A page finishes because the page it opens takes its place: both keep moving left. */
    FINISH_FORWARD,
}

/**
 * The operation a finish represents: only a hand-over keeps moving forward.
 *
 * A finish the user asked for - back, or the menu entry that reveals the page underneath - is a
 * return. A finish that happens because this screen opened its replacement is a hand-over, and the
 * pair must keep moving the way the new page is moving.
 *
 * @param handsOver true when the screen is being replaced by the screen it is opening.
 */
fun finishOperation(handsOver: Boolean): SlideOperation =
    if (handsOver) SlideOperation.FINISH_FORWARD else SlideOperation.FINISH_RETURN

/**
 * The pair [operation] must play.
 *
 * A start and a hand-over move forward; a return moves back. That is the whole rule, and it holds
 * for the drawer, the overflow menu and the back key because all three end in one of these three
 * operations.
 */
fun animationPairFor(operation: SlideOperation): SlideAnimationPair = when (operation) {
    SlideOperation.START,
    SlideOperation.FINISH_FORWARD,
    -> SlideAnimationPair(R.anim.slide_left_in, R.anim.slide_left_out)

    SlideOperation.FINISH_RETURN ->
        SlideAnimationPair(R.anim.slide_right_in, R.anim.slide_right_out)
}
