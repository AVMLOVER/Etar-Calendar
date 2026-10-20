/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * When the month pager has been dragged far enough to fetch the page it is heading for.
 *
 * The grid draws itself from calendar arithmetic, so a page is complete without its events; the only
 * question is how early to ask the provider so the markers are usually there by the time the page
 * becomes current. Settling alone is too late - the page is already on screen - and fetching on every
 * frame of a drag is the per-frame work that dropped frames before. The threshold is the middle:
 * once the drag is a quarter of the way towards the next page, that page is fetched.
 *
 * The decision is a pure function because it is only a page number and an offset, and because the
 * exact point it fires is worth pinning in a test rather than only feeling on a device.
 */
package com.android.calendar.composeui

/**
 * How far a drag has to go before the page it is heading for is fetched.
 *
 * A quarter of the way: early enough that the query usually returns before the page is current, and
 * late enough that a hint of a swipe does not start one.
 */
const val PREFETCH_THRESHOLD = 0.25f

/**
 * The page the pager is being dragged towards far enough to fetch, or null while the drag is still
 * mostly on the page it started from.
 *
 * @param currentPage the page the pager is on.
 * @param offsetFraction how far it has been dragged from that page, positive towards the next page
 *   and negative towards the previous one - `PagerState.currentPageOffsetFraction`.
 * @param threshold the distance, in pages, at which the drag counts as heading somewhere.
 */
fun pageToPrefetch(
    currentPage: Int,
    offsetFraction: Float,
    threshold: Float = PREFETCH_THRESHOLD,
): Int? = when {
    offsetFraction >= threshold -> currentPage + 1
    offsetFraction <= -threshold -> currentPage - 1
    else -> null
}
