/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * Picks an event's URL out of its extended-property rows.
 *
 * The provider has no url column: the URL lives in ExtendedProperties under the ical4android name, and
 * a synced calendar may store the private-prefixed form instead. Reading it is one decision - private
 * wins, blank values do not count - so it is a pure function, checked without a device.
 */
package com.android.calendar.composeui

/**
 * @param rows the (NAME, VALUE) pairs read for one event.
 * @param privateName the private-prefixed URL property name.
 * @param normalName the plain URL property name.
 * @return the URL, trimmed, or null when no row carries a non-blank value.
 */
fun pickEventUrl(
    rows: List<Pair<String, String>>,
    privateName: String,
    normalName: String,
): String? {
    // Blank rows are dropped first: a blank private row must not shadow a real plain one.
    val nonBlank = rows.mapNotNull { (name, value) ->
        val trimmed = value.trim()
        if (trimmed.isEmpty()) null else name to trimmed
    }
    val fromPrivate = nonBlank.firstOrNull { it.first == privateName }?.second
    val fromNormal = nonBlank.firstOrNull { it.first == normalName }?.second
    return fromPrivate ?: fromNormal
}
