/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The size, shape and scrim of the add controls.
 *
 * Every screen's bottom-right add button has to look like the same control, so the numbers live in one
 * place: a rounded square rather than the default circle, one step larger than the 56dp FAB (64dp with
 * a 28dp icon), and a menu scrim that is Material's own modal scrim - the scrim role at the standard
 * 32% - rather than a colour of our own.
 */
package com.android.calendar.composeui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

object AddFabSpec {

    /** The container size; one step above the standard 56dp FAB. */
    val size = 64.dp

    /** The icon size, scaled with the container from the standard 24dp. */
    val iconSize = 28.dp

    /** The corner radius: a rounded square, not a circle. */
    val corner = 20.dp

    /** The shape every add button uses. */
    val shape = RoundedCornerShape(corner)

    /** Material's modal scrim alpha. */
    const val SCRIM_ALPHA = 0.32f
}
