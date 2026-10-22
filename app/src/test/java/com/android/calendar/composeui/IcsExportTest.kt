/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The iCalendar export writer, checked without a device.
 *
 * The cases pin the two date forms (all-day VALUE=DATE from the UTC-midnight convention, timed in
 * UTC), the escaping a reader depends on, the 75-octet folding, and the empty calendar.
 */
package com.android.calendar.composeui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets

class IcsExportTest {

    private fun event(
        id: Long = 1,
        title: String = "Team sync",
        description: String? = null,
        location: String? = null,
        url: String? = null,
        start: Long = 1_800_000_000_000,
        end: Long = 1_800_003_600_000,
        allDay: Boolean = false,
        rrule: String? = null,
        duration: String? = null,
    ) = IcsEvent(id, title, description, location, url, start, end, allDay, rrule, duration)

    @Test
    fun aTimedEventIsWrittenInUtc() {
        val ics = IcsExport.build(listOf(event()), nowMillis = 0)
        assertTrue(ics.contains("BEGIN:VCALENDAR"))
        assertTrue(ics.contains("VERSION:2.0"))
        assertTrue(ics.contains("CALSCALE:GREGORIAN"))
        assertTrue(ics.contains("PRODID:${IcsExport.PRODID}"))
        assertTrue(ics.contains("DTSTAMP:19700101T000000Z"))
        assertTrue(ics.contains("DTSTART:20270115T080000Z"))
        assertTrue(ics.contains("DTEND:20270115T090000Z"))
        assertFalse(ics.contains("VALUE=DATE"))
    }

    @Test
    fun anAllDayEventUsesTheDateFormAndItsUtcDay() {
        // All-day events are stored at UTC midnight: this is the 15th in UTC.
        val start = 1_800_000_000_000L
        val day = start / 86_400_000L * 86_400_000L
        val ics = IcsExport.build(listOf(event(start = day, end = day + 86_400_000L, allDay = true)))
        assertTrue(ics.contains("DTSTART;VALUE=DATE:20270115"))
        assertTrue(ics.contains("DTEND;VALUE=DATE:20270116"))
        assertFalse(ics.contains("DTSTART:2027"))
    }

    @Test
    fun textFieldsAreEscaped() {
        val ics = IcsExport.build(
            listOf(
                event(
                    title = "Lunch, with; the team",
                    description = "Line one\nLine two \\ end",
                    location = "Cafe, Main St; 2F",
                    url = "https://example.com/a,b;c",
                )
            ),
        )
        assertTrue(ics.contains("SUMMARY:Lunch\\, with\\; the team"))
        assertTrue(ics.contains("DESCRIPTION:Line one\\nLine two \\\\ end"))
        assertTrue(ics.contains("LOCATION:Cafe\\, Main St\\; 2F"))
        assertTrue(ics.contains("URL:https://example.com/a\\,b\\;c"))
    }

    @Test
    fun rruleAndDurationAreIncluded() {
        val ics = IcsExport.build(
            listOf(event(rrule = "FREQ=WEEKLY;BYDAY=MO", duration = "PT1H")),
        )
        assertTrue(ics.contains("RRULE:FREQ=WEEKLY;BYDAY=MO"))
        assertTrue(ics.contains("DURATION:PT1H"))
    }

    @Test
    fun everyLineIsFoldedToSeventyFiveOctets() {
        val long = "word ".repeat(60)
        val ics = IcsExport.build(listOf(event(title = long)))
        val lines = ics.split("\r\n").filter { it.isNotEmpty() }
        for (line in lines) {
            assertTrue(
                "line of ${IcsExport.octetLength(line)} octets: $line",
                IcsExport.octetLength(line) <= 75,
            )
        }
        assertTrue("continuation lines start with a space", lines.any { it.startsWith(" ") })
    }

    @Test
    fun foldingNeverSplitsAUtf8Sequence() {
        val text = "日期".repeat(40)
        val folded = IcsExport.fold("SUMMARY:$text")
        for (line in folded.split("\r\n").filter { it.isNotEmpty() }) {
            assertTrue(IcsExport.octetLength(line) <= 75)
            // Decoding the line must not contain the replacement character a split sequence produces.
            assertFalse(line.contains('\uFFFD'))
            line.toByteArray(StandardCharsets.UTF_8)
        }
        assertEquals(text, folded.replace("\r\n", "").replace(" ", "").removePrefix("SUMMARY:"))
    }

    @Test
    fun anEmptyCalendarIsStillAValidCalendar() {
        val ics = IcsExport.build(emptyList())
        assertTrue(ics.contains("BEGIN:VCALENDAR"))
        assertTrue(ics.contains("END:VCALENDAR"))
        assertFalse(ics.contains("BEGIN:VEVENT"))
    }
}
