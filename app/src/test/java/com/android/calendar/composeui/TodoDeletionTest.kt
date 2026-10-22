/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The swipe-delete boundary, checked without a device.
 *
 * One line left means the whole to-do is the question; two or more means the item is. These cases pin
 * the boundary so the last-line swipe cannot fall back to the item dialog.
 */
package com.android.calendar.composeui

import org.junit.Assert.assertEquals
import org.junit.Test

class TodoDeletionTest {

    @Test
    fun theLastLineAsksAboutTheWholeTodo() {
        assertEquals(TodoDeleteConfirmation.WHOLE_TODO, todoDeleteConfirmationFor(1))
    }

    @Test
    fun anEmptyListStillAsksAboutTheWholeTodo() {
        assertEquals(TodoDeleteConfirmation.WHOLE_TODO, todoDeleteConfirmationFor(0))
    }

    @Test
    fun twoOrMoreLinesAskAboutTheItem() {
        assertEquals(TodoDeleteConfirmation.ITEM, todoDeleteConfirmationFor(2))
        assertEquals(TodoDeleteConfirmation.ITEM, todoDeleteConfirmationFor(5))
    }
}
