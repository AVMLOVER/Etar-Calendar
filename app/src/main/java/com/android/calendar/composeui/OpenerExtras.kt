/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * Marks an intent as opening a screen over the activity it was started from.
 *
 * The host screens read two extras: EXTRA_RETURN_TO_OPENER says back should reveal the screen
 * underneath rather than leave the app, and EXTRA_OPENER_CLASS names that screen so a menu entry for
 * it is a step back instead of a fresh push. Without them a screen is "effectively landing" and back
 * calls finishAffinity(), which takes the whole task to the launcher - what the chooser's to-do and
 * countdown launches did.
 *
 * The activity is taken as a context rather than read as `javaClass` inside an apply block: there the
 * implicit receiver is the Intent, so a bare `javaClass` named android.content.Intent, the bug that
 * once made every host screen record the wrong opener.
 */
package com.android.calendar.composeui

import android.app.Activity
import android.content.Context
import android.content.Intent

/** Marks [this] as opening over the activity [context] belongs to. */
fun Intent.openedOver(context: Context): Intent {
    val activity = context as? Activity ?: return this
    return putExtra(ComposeCalendarViewActivity.EXTRA_RETURN_TO_OPENER, true)
        .putExtra(ComposeCalendarViewActivity.EXTRA_OPENER_CLASS, activity.javaClass.name)
}
