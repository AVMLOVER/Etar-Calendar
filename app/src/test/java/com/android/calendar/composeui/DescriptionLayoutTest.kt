/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The description's line budget, checked without a device.
 *
 * The rule is that the block's height decides how many lines fit, after the title's own lines: never
 * a fixed one or two, never a clipped line without room for the ellipsis, and no description at all
 * in a block too short for one line. These cases pin that arithmetic.
 */
package com.android.calendar.composeui

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class DescriptionLayoutTest {

    private fun budget(
        blockHeight: Float,
        titleLines: Int = 2,
        titleLineHeight: Float = 14f,
        descriptionLineHeight: Float = 11f,
    ) = descriptionLineBudget(
        blockHeight = blockHeight.dp,
        titleLines = titleLines,
        titleLineHeight = titleLineHeight.dp,
        descriptionLineHeight = descriptionLineHeight.dp,
        verticalPadding = 4.dp,
        gap = 2.dp,
    )

    private fun textBudgetAt(
        blockHeight: Float,
        naturalTitleLines: Int,
        titleLineHeight: Float = 16f,
        descriptionLineHeight: Float = 11f,
    ) = textBudget(
        blockHeight = blockHeight.dp,
        naturalTitleLines = naturalTitleLines,
        titleLineHeight = titleLineHeight.dp,
        descriptionLineHeight = descriptionLineHeight.dp,
        verticalPadding = 4.dp,
        gap = 2.dp,
    )

    /** A tall block uses every whole line that is left, not a fixed one or two. */
    @Test
    fun aTallBlockGetsEveryLineThatFits() {
        // 2 x 14 title + 4 padding + 2 gap = 34; 90 - 34 = 56; 56 / 11 = 5 lines.
        assertEquals(5, budget(blockHeight = 90f))
        // 2 x 14 + 4 + 2 = 34; 200 - 34 = 166; 166 / 11 = 15 lines.
        assertEquals(15, budget(blockHeight = 200f))
    }

    /** More height never gives fewer lines. */
    @Test
    fun moreHeightNeverGivesFewerLines() {
        var previous = 0
        var height = 20f
        while (height <= 200f) {
            val lines = budget(height)
            assertTrueAtLeast(previous, lines, height)
            previous = lines
            height += 5f
        }
    }

    /** A block too short for one description line shows none: no bare ellipsis line. */
    @Test
    fun aShortBlockShowsNoDescription() {
        assertEquals(0, budget(blockHeight = 20f))
        assertEquals(0, budget(blockHeight = 34f)) // exactly the title's space
        assertEquals(0, budget(blockHeight = 44f)) // one line short of 45
    }

    /** Exactly one description line's space gives exactly one line. */
    @Test
    fun exactlyOneLineGivesOneLine() {
        assertEquals(1, budget(blockHeight = 45f))
    }

    /** A one-line title leaves more room for the description than a two-line title. */
    @Test
    fun aShorterTitleLeavesMoreLines() {
        assertEquals(6, budget(blockHeight = 90f, titleLines = 1))
        assertEquals(5, budget(blockHeight = 90f, titleLines = 2))
    }

    /** A zero or negative line height cannot fit anything; a broken metric must not divide by zero. */
    @Test
    fun aBrokenLineHeightFitsNothing() {
        assertEquals(
            0,
            descriptionLineBudget(
                blockHeight = 100.dp,
                titleLines = 1,
                titleLineHeight = 14.dp,
                descriptionLineHeight = 0.dp,
                verticalPadding = 4.dp,
                gap = 2.dp,
            ),
        )
    }

    /** A two-line title is cut to one when its second line would leave no whole description line. */
    @Test
    fun theTitleDropsToOneLineToMakeRoomForADescription() {
        // 40 - 4 - 2 = 34; two title lines (32) leave 2dp, one title line (16) leaves 18dp - one line.
        val dropped = textBudgetAt(blockHeight = 40f, naturalTitleLines = 2)
        assertEquals(1, dropped.titleLines)
        assertEquals(1, dropped.descriptionLines)
    }

    /** A one-line title keeps its line and the rest of the block goes to the description. */
    @Test
    fun aOneLineTitleKeepsItsLineAndUsesTheRest() {
        // 60 - 4 - 16 - 2 = 38; 38 / 11 = 3 lines.
        val budget = textBudgetAt(blockHeight = 60f, naturalTitleLines = 1)
        assertEquals(1, budget.titleLines)
        assertEquals(3, budget.descriptionLines)
    }

    /** A block too short for either keeps the title's own lines and no description. */
    @Test
    fun aBlockTooShortForAnythingKeepsTheTitleAlone() {
        val budget = textBudgetAt(blockHeight = 20f, naturalTitleLines = 2)
        assertEquals(2, budget.titleLines)
        assertEquals(0, budget.descriptionLines)
    }

    /**
     * The same block at the two zoom limits.
     *
     * The hour height is the only difference: at the widest zoom an hour is 600/19dp, at the closest
     * 600/4dp. A one-hour block shows the title only at the widest and several description lines at
     * the closest, which is the behaviour the pinch is meant to produce.
     */
    @Test
    fun theZoomLimitsChangeHowMuchDescriptionFits() {
        val viewport = 600f
        val widest = textBudgetAt(blockHeight = viewport / 19f, naturalTitleLines = 2)
        assertEquals(0, widest.descriptionLines)

        val closest = textBudgetAt(blockHeight = viewport / 4f, naturalTitleLines = 2)
        // 150 - 4 - 32 - 2 = 112; 112 / 11 = 10 lines.
        assertEquals(10, closest.descriptionLines)
    }

    private fun assertTrueAtLeast(previous: Int, lines: Int, height: Float) {
        if (lines < previous) {
            throw AssertionError("height $height gave $lines lines, fewer than the $previous before it")
        }
    }
}
