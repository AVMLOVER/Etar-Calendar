/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The type sizes inside a grid event block.
 *
 * The description used to be 9sp, one step below the 11sp title, and was hard to read in a block on a
 * coloured background. It is now 11sp - the same size the title used to be - with the title one clear
 * step above it and the url one below, so a block reads title, description, url in that order of
 * weight. The fitting budget measures whatever sizes these are, so raising them changes the number of
 * lines a block shows, never whether the text fits.
 */
package com.android.calendar.composeui

import androidx.compose.ui.unit.sp

object EventBlockType {

    /** The title: one clear step above the description. */
    val titleSize = 13.sp
    val titleLineHeight = 16.sp

    /** The description: readable on a coloured block, one step below the title. */
    val descriptionSize = 11.sp
    val descriptionLineHeight = 14.sp

    /** The url: one step below the description. */
    val urlSize = 10.sp
    val urlLineHeight = 13.sp
}
