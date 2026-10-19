/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * When a menu choice may be turned into navigation, and the single slot that holds it until then.
 *
 * A drawer choice used to be acted on inside the drawer's own selection callback, in the same frame
 * the drawer began closing - and an overflow choice likewise ran while its popup was still being
 * dismissed. Asking the platform to start or finish an activity while another animation is running
 * is the same condition that loses a transition declaration elsewhere: the request is merged or
 * dropped, and the movement comes out stacked or facing the wrong way.
 *
 * The drawer is no longer an animation at all: a choice made from it snaps it shut without
 * animating and runs the step on the next main-loop turn, so its panel can neither delay the step
 * nor contribute a leftward slide of its own. The only thing left to wait for is the overflow popup,
 * which the platform dismisses after its callback returns. [isReadyToNavigate] is that rule and
 * [PendingNavigation] is the slot.
 *
 * The screen's own entry animation is deliberately not part of it either: a start must never wait
 * for it, and a finish carries its pair with the operation, so neither needs a gate.
 */
package com.android.calendar.composeui

/**
 * True when a menu-driven navigation step may be handed to the platform now.
 *
 * The drawer is deliberately absent: it is closed without animation before the step is posted, so
 * there is nothing about it left to wait for, and waiting for its close animation was both the
 * visible delay and a second leftward slide.
 *
 * @param menuDismissing true while the menu callback that produced the choice is still on the stack,
 *   so its popup has not been dismissed yet.
 */
fun isReadyToNavigate(menuDismissing: Boolean): Boolean = !menuDismissing

/**
 * The one navigation step waiting for the UI to be idle.
 *
 * One slot on purpose: a second menu choice replaces the first - the user changed their mind, and
 * two navigations from one gesture is always wrong - and a step is taken out of the slot before it
 * runs, so a callback that fires twice cannot navigate twice.
 */
class PendingNavigation {
    private var action: (() -> Unit)? = null

    /** True while a step is waiting. */
    val hasPending: Boolean
        get() = action != null

    /** Asks for [action] to run when the UI is idle, replacing any step already waiting. */
    fun request(action: () -> Unit) {
        this.action = action
    }

    /** Drops the waiting step without running it. */
    fun cancel() {
        action = null
    }

    /**
     * Removes and returns the waiting step, or null when there is none.
     *
     * The slot is empty by the time this returns, so the same step cannot be taken - and therefore
     * cannot run - twice.
     */
    fun take(): (() -> Unit)? {
        val pending = action
        action = null
        return pending
    }
}
