/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * Unit arithmetic for the countdown screen.
 *
 * These are the parts worth pinning down: which unit a remainder is shown in, when a countdown is
 * treated as passed, and how an annual countdown rolls over. They are pure functions, so they need no
 * device - which is the point, because the rest of this feature can only be checked by hand.
 */
package com.android.calendar.countdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.GregorianCalendar
import java.util.TimeZone

class CountdownStoreTest {

    private val zone: TimeZone = TimeZone.getTimeZone("Asia/Shanghai")

    /** A timestamp at the given wall-clock time in [zone]. Month is 1-based here, unlike Calendar. */
    private fun at(year: Int, month: Int, day: Int, hour: Int = 0, minute: Int = 0): Long =
        GregorianCalendar(zone).apply {
            clear()
            set(year, month - 1, day, hour, minute, 0)
        }.timeInMillis

    private fun countdown(
        name: String = "test",
        year: Int,
        month: Int,
        day: Int,
        hour: Int? = null,
        minute: Int = 0,
        repeat: Boolean = false,
    ) = Countdown(
        id = "id",
        name = name,
        color = 0xFF64B5F6.toInt(),
        dateIso = "%04d-%02d-%02d".format(year, month, day),
        timeIso = hour?.let { "%02d:%02d".format(it, minute) },
        repeatYearly = repeat,
    )

    private fun describe(
        countdown: Countdown,
        now: Long,
        unit: CountdownUnit = CountdownUnit.DAYS,
        decimals: Boolean = false,
        countToday: Boolean = false,
    ) = CountdownStore.describe(
        countdown = countdown,
        zone = zone.toZoneId(),
        now = now,
        unit = unit,
        allowDecimals = decimals,
        countToday = countToday,
    )

    // ------------------------------------------------------------------ whole units, the default

    @Test
    fun `days away are shown in days`() {
        val state = describe(countdown("trip", 2026, 10, 20), now = at(2026, 10, 10))
        assertEquals("10", state.value)
        assertEquals(CountdownUnit.DAYS, state.unit)
        assertFalse(state.passed)
    }

    @Test
    fun `fractional part is dropped without decimals`() {
        // 10 days and 12 hours away.
        val state = describe(countdown("trip", 2026, 10, 20), now = at(2026, 10, 10, hour = 12))
        assertEquals("9", state.value)
        assertEquals(CountdownUnit.DAYS, state.unit)
    }

    @Test
    fun `decimals are rounded to three places`() {
        // 9 days and 12 hours is 9.5 days.
        val state = describe(
            countdown("trip", 2026, 10, 20),
            now = at(2026, 10, 10, hour = 12),
            decimals = true,
        )
        assertEquals("9.5", state.value)
    }

    @Test
    fun `a whole value keeps no trailing zeros with decimals on`() {
        val state = describe(
            countdown("trip", 2026, 10, 20),
            now = at(2026, 10, 10),
            decimals = true,
        )
        assertEquals("10", state.value)
    }

    // --------------------------------------------------------------------------- stepping down

    @Test
    fun `days unit steps down to hours when less than a day remains`() {
        // Four hours before the target, still configured in days: "0 天" would be useless.
        val state = describe(countdown("trip", 2026, 10, 20), now = at(2026, 10, 19, hour = 20))
        assertEquals("4", state.value)
        assertEquals(CountdownUnit.HOURS, state.unit)
    }

    @Test
    fun `hours unit steps down to minutes when less than an hour remains`() {
        val state = describe(
            countdown("trip", 2026, 10, 20),
            now = at(2026, 10, 19, hour = 23, minute = 30),
            unit = CountdownUnit.HOURS,
        )
        assertEquals("30", state.value)
        assertEquals(CountdownUnit.MINUTES, state.unit)
    }

    @Test
    fun `minutes unit never steps down`() {
        val state = describe(
            countdown("trip", 2026, 10, 20),
            now = at(2026, 10, 19, hour = 23, minute = 59),
            unit = CountdownUnit.MINUTES,
        )
        assertEquals("1", state.value)
        assertEquals(CountdownUnit.MINUTES, state.unit)
    }

    // ------------------------------------------------------------------------------- passed

    @Test
    fun `a past date is reported as passed and counts up`() {
        val state = describe(countdown("gone", 2026, 10, 1), now = at(2026, 10, 11))
        assertTrue(state.passed)
        assertEquals("10", state.value)
        assertEquals(CountdownUnit.DAYS, state.unit)
    }

    @Test
    fun `the exact target moment counts as passed`() {
        // Reaching the target means it has arrived. Without this a countdown to today reads
        // "0 days left" for the whole of the day the user picked.
        val state = describe(countdown("now", 2026, 10, 10), now = at(2026, 10, 10))
        assertTrue(state.passed)
        assertEquals("0", state.value)
    }

    // ---------------------------------------------------------- the day a date-only countdown lands

    @Test
    fun `a date only countdown has arrived for the whole of its day`() {
        // The reported bug: on the day itself the card said "0 days left" whatever the time, because
        // a time-less countdown points at midnight and the days unit floored the elapsed time to 0.
        for (hour in listOf(0, 1, 9, 12, 23)) {
            val state = describe(
                countdown("today", 2026, 10, 13),
                now = at(2026, 10, 13, hour = hour, minute = 59),
            )
            assertTrue("at ${hour}h it should count as arrived", state.passed)
            assertEquals("at ${hour}h", "0", state.value)
            assertEquals("at ${hour}h", CountdownUnit.DAYS, state.unit)
        }
    }

    @Test
    fun `the day before a date only countdown still counts down`() {
        // 13:00 the day before: eleven hours remain, so the days unit steps down to hours.
        val state = describe(countdown("today", 2026, 10, 13), now = at(2026, 10, 12, hour = 13))
        assertFalse(state.passed)
        assertEquals("11", state.value)
        assertEquals(CountdownUnit.HOURS, state.unit)
    }

    @Test
    fun `more than a whole day away keeps the configured unit`() {
        // 10:00 two days before: more than 24 hours, so days still applies.
        val state = describe(countdown("later", 2026, 10, 14), now = at(2026, 10, 12, hour = 10))
        assertFalse(state.passed)
        assertEquals("1", state.value)
        assertEquals(CountdownUnit.DAYS, state.unit)
    }

    @Test
    fun `counting up stays in the configured unit while it shows a whole number`() {
        // One day and two hours past: "1 day ago", not "26 hours ago".
        val state = describe(countdown("gone", 2026, 10, 12), now = at(2026, 10, 13, hour = 2))
        assertTrue(state.passed)
        assertEquals("1", state.value)
        assertEquals(CountdownUnit.DAYS, state.unit)
    }

    @Test
    fun `counting up keeps the configured unit even when it reads zero`() {
        // 90 minutes past, days configured: "0 days ago". Stepping down here would show
        // "90 minutes ago" and then flip back to "1 day ago" later, which reads as a glitch.
        val state = describe(
            countdown("gone", 2026, 10, 13),
            now = at(2026, 10, 13, hour = 1, minute = 30),
        )
        assertTrue(state.passed)
        assertEquals("0", state.value)
        assertEquals(CountdownUnit.DAYS, state.unit)
    }

    @Test
    fun `a timed countdown arrives at its moment rather than at midnight`() {
        val timed = countdown("at nine", 2026, 10, 13, hour = 9)
        // 08:00 on the day: not yet.
        val before = describe(timed, now = at(2026, 10, 13, hour = 8))
        assertFalse(before.passed)
        assertEquals(CountdownUnit.HOURS, before.unit)
        // 09:00: arrived.
        val atMoment = describe(timed, now = at(2026, 10, 13, hour = 9))
        assertTrue(atMoment.passed)
    }

    // -------------------------------------------------------------------------- annual repeat

    @Test
    fun `a repeating countdown that has passed points at next year`() {
        val state = describe(
            countdown("birthday", 2020, 3, 1, repeat = true),
            now = at(2026, 10, 10),
        )
        assertFalse(state.passed)
        assertTrue(state.repeated)
        // 1 March 2027 is 142 days after 10 October 2026.
        assertEquals(CountdownUnit.DAYS, state.unit)
        val days = state.value.toInt()
        assertTrue("expected about 142 days, got $days", days in 141..143)
    }

    @Test
    fun `a repeating countdown still ahead this year does not roll over`() {
        val state = describe(
            countdown("birthday", 2026, 12, 25, repeat = true),
            now = at(2026, 10, 10),
        )
        assertFalse(state.passed)
        assertFalse(state.repeated)
        assertEquals(CountdownUnit.DAYS, state.unit)
    }

    @Test
    fun `a non repeating countdown stays passed`() {
        val state = describe(
            countdown("birthday", 2020, 3, 1, repeat = false),
            now = at(2026, 10, 10),
        )
        assertTrue(state.passed)
        assertFalse(state.repeated)
    }

    // --------------------------------------------------------------------------- count today

    @Test
    fun `count today adds the current day`() {
        val plain = describe(countdown("trip", 2026, 10, 20), now = at(2026, 10, 10))
        val inclusive = describe(
            countdown("trip", 2026, 10, 20),
            now = at(2026, 10, 10),
            countToday = true,
        )
        assertEquals("10", plain.value)
        assertEquals("11", inclusive.value)
    }

    @Test
    fun `count today does not apply to hours`() {
        val state = describe(
            countdown("trip", 2026, 10, 20),
            now = at(2026, 10, 19, hour = 20),
            countToday = true,
        )
        assertEquals("4", state.value)
        assertEquals(CountdownUnit.HOURS, state.unit)
    }

    @Test
    fun `count today does not apply once passed`() {
        val state = describe(
            countdown("gone", 2026, 10, 1),
            now = at(2026, 10, 11),
            countToday = true,
        )
        assertTrue(state.passed)
        assertEquals("10", state.value)
    }

    // ------------------------------------------------------------------------------ colour

    @Test
    fun `the palette cycles so consecutive countdowns differ`() {
        assertTrue("palette should hold more than one colour", CountdownStore.PALETTE.size > 1)
        // The colours are distinct, which is what makes "a different colour each time" work.
        assertEquals(
            CountdownStore.PALETTE.size,
            CountdownStore.PALETTE.toSet().size,
        )
    }
}
