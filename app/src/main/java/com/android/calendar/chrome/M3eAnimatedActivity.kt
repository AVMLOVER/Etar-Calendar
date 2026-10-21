/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The slide transitions for the full-screen activities that are not M3eHostActivity.
 *
 * The host passes its animation with each start and its return pair with each finish, so every
 * screen it opens slides in from the right and slides back to the right when it closes. The screens
 * that are plain ComponentActivities - event details, the editor, the settings screen, the calendars
 * manager and the quick-response editor - were outside that family: the host's ActivityOptions still
 * brought them in with a slide, but their own finish carried nothing, so the platform played its
 * default cross-fade on the way back while every host screen slid.
 *
 * The two overrides here are the same two mechanisms the host uses, and for the same reason:
 * a start takes its animation with the operation (ActivityOptions), and a finish takes its pair
 * with the operation (overridePendingTransition called immediately after finish()).
 *
 * The transparent dialog activities (the .ics import, the event delete confirmation, the battery
 * warning) deliberately do not extend this. Their windows are Theme.Etar.Transparent with
 * windowAnimationStyle @null: they are dialogs, and a sideways slide would break that look.
 */
package com.android.calendar.chrome

import android.app.ActivityOptions
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import com.android.calendar.composeui.SlideOperation
import com.android.calendar.composeui.animationPairFor
import com.android.calendar.theme.applyM3eWindow

abstract class M3eAnimatedActivity : ComponentActivity() {

    /**
     * The same window treatment the host gets from applyTheme().
     *
     * Edge to edge with transparent bars, the icon appearance the app's theme needs, and the tonal
     * surface behind the bars. Without it these screens inherited only enableEdgeToEdge()'s system
     * night-mode decision and the theme's window background, which is black in the Black theme -
     * what MIUI/HyperOS painted the status bar with.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyM3eWindow()
    }

    /**
     * Starts [intent] with the forward pair passed explicitly with the operation.
     *
     * The one-argument [startActivity] delegates to this overload, so no start from this screen
     * can miss the pair - the same guarantee [M3eHostActivity] gives its own screens.
     */
    @Suppress("DEPRECATION")
    override fun startActivity(intent: Intent, options: Bundle?) {
        val pair = animationPairFor(SlideOperation.START)
        super.startActivity(
            intent,
            ActivityOptions.makeCustomAnimation(this, pair.enterAnim, pair.exitAnim).toBundle(),
        )
    }

    /**
     * Finishes this screen with the return pair passed immediately after the finish.
     *
     * The pair is computed before the call so nothing runs between `finish()` and
     * `overridePendingTransition`, which is the ordering the platform reads.
     */
    @Suppress("DEPRECATION")
    override fun finish() {
        val pair = animationPairFor(SlideOperation.FINISH_RETURN)
        super.finish()
        overridePendingTransition(pair.enterAnim, pair.exitAnim)
    }
}
