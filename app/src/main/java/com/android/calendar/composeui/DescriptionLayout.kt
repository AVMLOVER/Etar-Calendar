/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * How much event description a day-grid block can show under its title.
 *
 * The block's height is the only thing that decides this: an hour of the grid is a fixed height that
 * the pinch changes, so the same block shows more lines when the user zooms in and fewer when they
 * zoom out, without any per-block state. The title keeps its lines first, and the description gets
 * whatever whole lines are left; if not one line fits, the description is not drawn at all rather
 * than clipped to a bare ellipsis.
 *
 * The arithmetic is a pure function so the "do not waste space, do not clip mid-line" rule can be
 * checked without measuring text on a device.
 */
package com.android.calendar.composeui

import androidx.compose.ui.unit.Dp

/**
 * How many whole description lines fit in a block.
 *
 * @param blockHeight the block's laid-out height.
 * @param titleLines how many lines the title may take (its `maxLines`).
 * @param titleLineHeight one title line's height, including its line spacing.
 * @param descriptionLineHeight one description line's height.
 * @param verticalPadding the block's top and bottom padding together.
 * @param gap the space between the title and the description.
 * @return the number of lines the description may use; 0 when even one would not fit.
 */
fun descriptionLineBudget(
    blockHeight: Dp,
    titleLines: Int,
    titleLineHeight: Dp,
    descriptionLineHeight: Dp,
    verticalPadding: Dp,
    gap: Dp,
): Int {
    if (descriptionLineHeight.value <= 0f) {
        return 0
    }
    val used = (titleLineHeight * titleLines).value + verticalPadding.value + gap.value
    val available = blockHeight.value - used
    if (available < descriptionLineHeight.value) {
        return 0
    }
    return (available / descriptionLineHeight.value).toInt()
}

/** The lines a block gives its title and its description. */
data class BlockTextBudget(
    val titleLines: Int,
    val descriptionLines: Int,
)

/**
 * Splits a block's height between its title and its description.
 *
 * The title gets the number of lines it naturally needs ([naturalTitleLines], measured), except when
 * that second title line would leave no whole description line. Then the title is cut to one line and
 * the description gets the space - a title shown one line shorter is a better block than one with no
 * description at all, and it is what stops a zoomed-out block from showing every description as
 * empty. A description is still only shown if at least one whole line fits.
 */
fun textBudget(
    blockHeight: Dp,
    naturalTitleLines: Int,
    titleLineHeight: Dp,
    descriptionLineHeight: Dp,
    verticalPadding: Dp,
    gap: Dp,
): BlockTextBudget {
    val titleLines = naturalTitleLines.coerceIn(1, 2)
    val both = descriptionLineBudget(
        blockHeight, titleLines, titleLineHeight, descriptionLineHeight, verticalPadding, gap,
    )
    if (both > 0 || titleLines == 1) {
        return BlockTextBudget(titleLines, both)
    }
    val oneLineTitle = descriptionLineBudget(
        blockHeight, 1, titleLineHeight, descriptionLineHeight, verticalPadding, gap,
    )
    return if (oneLineTitle > 0) {
        BlockTextBudget(1, oneLineTitle)
    } else {
        BlockTextBudget(titleLines, 0)
    }
}
