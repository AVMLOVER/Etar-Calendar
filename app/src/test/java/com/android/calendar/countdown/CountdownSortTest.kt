/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The countdown list's ordering.
 */
package com.android.calendar.countdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.util.GregorianCalendar
import java.util.TimeZone

class CountdownSortTest {

    private val tz: TimeZone = TimeZone.getTimeZone("Asia/Shanghai")
    private val zone: ZoneId = tz.toZoneId()

    /** Noon, so a date-only entry is unambiguously later or earlier than "now" on that day. */
    private fun at(year: Int, month: Int, day: Int, hour: Int = 12): Long =
        GregorianCalendar(tz).apply {
            clear()
            set(year, month - 1, day, hour, 0, 0)
        }.timeInMillis

    private fun countdown(
        id: String,
        name: String,
        year: Int,
        month: Int,
        day: Int,
        repeat: Boolean = false,
    ) = Countdown(
        id = id,
        name = name,
        color = 0,
        dateIso = "%04d-%02d-%02d".format(year, month, day),
        timeIso = null,
        repeatYearly = repeat,
    )

    // Added order: a, b, c, d with d well past, b soonest, c furthest.
    private val past = countdown("a", "Zebra", 2026, 1, 1)
    private val soon = countdown("b", "apple", 2026, 10, 15)
    private val far = countdown("c", "Mango", 2027, 6, 1)
    private val recentPast = countdown("d", "banana", 2026, 10, 11)
    private val list = listOf(past, soon, far, recentPast)

    private val now = at(2026, 10, 13)

    private fun sorted(order: CountdownStore.SortOrder) =
        CountdownStore.sorted(list, order, zone, now).map { it.id }

    @Test
    fun `added order is left alone`() {
        assertEquals(listOf("a", "b", "c", "d"), sorted(CountdownStore.SortOrder.ADDED))
    }

    @Test
    fun `added reversed puts the newest first`() {
        assertEquals(listOf("d", "c", "b", "a"), sorted(CountdownStore.SortOrder.ADDED_REVERSED))
    }

    @Test
    fun `name order is case insensitive`() {
        // apple, banana, Mango, Zebra
        assertEquals(listOf("b", "d", "c", "a"), sorted(CountdownStore.SortOrder.NAME))
    }

    @Test
    fun `time ascending runs from the earliest past to the furthest future`() {
        // Zebra Jan 2026, banana Oct 11, apple Oct 15, Mango Jun 2027
        assertEquals(listOf("a", "d", "b", "c"), sorted(CountdownStore.SortOrder.TIME_ASC))
    }

    @Test
    fun `time descending runs from the furthest future to the earliest past`() {
        assertEquals(listOf("c", "b", "d", "a"), sorted(CountdownStore.SortOrder.TIME_DESC))
    }

    @Test
    fun `time mixed is ordered by distance, ignoring direction`() {
        // banana is two days past and apple two days ahead, so the two are equidistant and either may
        // come first - what matters is that direction is ignored and both beat the distant entries.
        val order = sorted(CountdownStore.SortOrder.TIME_MIXED)
        assertEquals(listOf("c", "a"), order.takeLast(2))
        assertEquals(setOf("b", "d"), order.take(2).toSet())
    }

    @Test
    fun `time mixed puts a nearer entry ahead of a further one on its own side`() {
        // banana (2 days past) must beat Zebra (much further past), and apple (2 days ahead) must
        // beat Mango. Distance, not direction, is what orders them.
        val order = sorted(CountdownStore.SortOrder.TIME_MIXED)
        assertTrue(order.indexOf("d") < order.indexOf("a"))
        assertTrue(order.indexOf("b") < order.indexOf("c"))
    }

    @Test
    fun `a repeating countdown sorts by its next occurrence, not the stored year`() {
        // A birthday first entered in 1990 and already past this year: it is next year's, so it must
        // sort ahead of anything beyond that, not behind everything.
        val birthday = countdown("e", "birthday", 1990, 3, 1, repeat = true)
        val withBirthday = list + birthday
        val order = CountdownStore
            .sorted(withBirthday, CountdownStore.SortOrder.TIME_ASC, zone, now)
            .map { it.id }
        // 1 March 2027 falls between Mango (June 2027) and the rest.
        assertEquals(listOf("a", "d", "b", "e", "c"), order)
    }

    @Test
    fun `the default order is time ascending`() {
        assertEquals(CountdownStore.SortOrder.TIME_ASC, CountdownStore.SortOrder.DEFAULT)
    }

    @Test
    fun `an unrecognised stored value falls back to the default`() {
        assertEquals(
            CountdownStore.SortOrder.DEFAULT,
            CountdownStore.SortOrder.fromKey("nonsense"),
        )
        assertEquals(
            CountdownStore.SortOrder.NAME,
            CountdownStore.SortOrder.fromKey("NAME"),
        )
    }
}
