/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * Which confirmation a swipe on a to-do line asks for.
 *
 * Swiping one line of a list asks "remove this item". Swiping the list's last line leaves nothing to
 * remove it from, so the honest question is the whole-to-do one - the same dialog the title bar and the
 * editor use - and its answer removes the day's to-do, which is what the screen then shows as its empty
 * state. Pure so the boundary is checked without a device.
 */
package com.android.calendar.composeui

/** The confirmation a swipe should open. */
enum class TodoDeleteConfirmation {
    /** One line remains: the question is about the whole to-do. */
    WHOLE_TODO,

    /** More than one line remains: the question is about the swiped item. */
    ITEM,
}

/** The confirmation for a list of [itemCount] lines, where one of them is being swiped away. */
fun todoDeleteConfirmationFor(itemCount: Int): TodoDeleteConfirmation =
    if (itemCount <= 1) TodoDeleteConfirmation.WHOLE_TODO else TodoDeleteConfirmation.ITEM
