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

/** The lines a block gives each of its four text kinds, in display order. */
data class BlockTextPlan(
    val titleLines: Int,
    val locationLines: Int,
    val descriptionLines: Int,
    val urlLines: Int,
)

/**
 * Splits a block's height between title, location, description and url, in that order.
 *
 * The order is the order they are drawn in and the order they get space in: the title first (two lines
 * at most, one when that buys a body line), then the location, then the description, then the url.
 * Each kind gets whole lines only, so nothing is clipped mid-line; a kind with no space left is simply
 * not drawn, so there are no blank lines. The url is one line, and only from space the description did
 * not need.
 */
fun blockTextPlan(
    blockHeight: Dp,
    naturalTitleLines: Int,
    titleLineHeight: Dp,
    bodyLineHeight: Dp,
    urlLineHeight: Dp,
    verticalPadding: Dp,
    gap: Dp,
    naturalLocationLines: Int,
    naturalDescriptionLines: Int,
    hasUrl: Boolean,
): BlockTextPlan {
    fun planFor(titleLines: Int): BlockTextPlan {
        val hasBody = naturalLocationLines > 0 || naturalDescriptionLines > 0 || hasUrl
        var available = blockHeight.value - verticalPadding.value -
            titleLineHeight.value * titleLines -
            if (hasBody) gap.value else 0f
        available = available.coerceAtLeast(0f)
        var location = 0
        var description = 0
        var url = 0
        if (bodyLineHeight.value > 0f) {
            location = minOf(
                naturalLocationLines,
                (available / bodyLineHeight.value).toInt(),
            ).coerceAtLeast(0)
            available -= location * bodyLineHeight.value
            description = minOf(
                naturalDescriptionLines,
                (available / bodyLineHeight.value).toInt(),
            ).coerceAtLeast(0)
            available -= description * bodyLineHeight.value
        }
        if (hasUrl && urlLineHeight.value > 0f && available >= urlLineHeight.value) {
            url = 1
        }
        return BlockTextPlan(titleLines, location, description, url)
    }

    val titleLines = naturalTitleLines.coerceIn(1, 2)
    val natural = planFor(titleLines)
    if (titleLines == 2 && natural.locationLines + natural.descriptionLines + natural.urlLines == 0) {
        val oneLineTitle = planFor(1)
        if (oneLineTitle.locationLines + oneLineTitle.descriptionLines + oneLineTitle.urlLines > 0) {
            return oneLineTitle
        }
    }
    return natural
}
