/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The sideways slide between the calendar and the screens that open over it.
 *
 * Where this has to be declared took a few attempts to get right, and the reasons are worth keeping:
 *
 * `Theme.Etar` also carries a windowAnimationStyle, but on Android 14 and up the platform no longer
 * uses it for a transition between two activities in the same task - the new screen is simply laid
 * over the old one, which is the "it just appears" this was meant to fix.
 *
 * `overrideActivityTransition` is the replacement, but it is only accepted while the activity is
 * resumed or finishing. Called from onCreate, or from onPause while being covered, it is discarded
 * without complaint, and the platform default plays instead. So it is called from onResume, which is
 * a state where it always applies, and it is re-applied on every resume because the transition for
 * the next launch is chosen from whatever the current activity last set.
 *
 * The animations are the same pair the day, week and agenda fragments use, so going into a view and
 * coming back out looks the same whichever kind of screen it turns out to be: entering moves left,
 * leaving moves right.
 */
package com.android.calendar.chrome

import android.app.Activity
import android.os.Build
import ws.xsoh.etar.R

/**
 * Declares the slide this activity will play when it is next covered.
 *
 * Call from onResume. A screen opened from here arrives from the right while this one leaves to the
 * left, so the transition reads as moving leftwards into the new screen.
 */
fun Activity.declareForwardSlide() {
    overrideTransition(
        Activity.OVERRIDE_TRANSITION_OPEN,
        R.anim.slide_left_in,
        R.anim.slide_left_out,
    )
}

/**
 * A screen on its way back to the calendar, still travelling the same way.
 *
 * For the day / three day / week views, which are read as one after another: a step between them
 * keeps moving left instead of reversing halfway through the sequence. Declared on the screen that
 * is finishing, because that is the one whose transition decides how the pair moves - a finishing
 * screen that declares nothing gets the reverse of the animation it was entered with, which is what
 * made every step out of the three day view go back to the right.
 */
fun Activity.leaveForward() {
    overrideTransition(
        Activity.OVERRIDE_TRANSITION_CLOSE,
        R.anim.slide_left_in,
        R.anim.slide_left_out,
    )
}

/**
 * Declares the slide this activity will play when it is next revealed.
 *
 * Call from onResume. The screen it is being revealed from goes back off to the right and this one
 * returns from the left, which is the reverse of [declareForwardSlide] - so a screen hands the
 * calendar back the way it came.
 */
fun Activity.declareReturnSlide() {
    overrideTransition(
        Activity.OVERRIDE_TRANSITION_CLOSE,
        R.anim.slide_right_in,
        R.anim.slide_right_out,
    )
}

/**
 * The override that is currently in force, per activity and per state.
 *
 * Calling `overrideActivityTransition` while a window animation is running makes the platform wait
 * for that animation, and this was being called on every resume - including the resume that happens
 * in the middle of the animation coming back from another screen. Re-applying the same value does
 * nothing, so it is not applied again.
 *
 * A WeakHashMap so a finished activity is not held on to; the values are the animation pairs last
 * set, which is all that is needed to tell whether a call would be a no-op.
 */
private val appliedTransitions = java.util.WeakHashMap<Activity, MutableMap<Int, Long>>()

/**
 * Applies one half of the slide, ignoring a platform that will not take it, and skipping the call
 * when the same pair is already in force for that state.
 *
 * Below Android 14 the older call covers both halves and is accepted at any point. A transition that
 * does not get set is a cosmetic loss, and never worth taking the screen down for.
 */
private fun Activity.overrideTransition(state: Int, enterAnim: Int, exitAnim: Int) {
    val key = (enterAnim.toLong() shl 32) or (exitAnim.toLong() and 0xFFFFFFFFL)
    val forActivity = appliedTransitions.getOrPut(this) { HashMap() }
    if (forActivity[state] == key) {
        return
    }
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(state, enterAnim, exitAnim)
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(enterAnim, exitAnim)
        }
        forActivity[state] = key
    } catch (t: Throwable) {
        // Something already changed; leave whatever was set before in place.
        forActivity.remove(state)
    }
}
