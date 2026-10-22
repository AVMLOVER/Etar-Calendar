/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * A minimal, valid iCalendar writer for exporting a whole calendar.
 *
 * The app's com.android.calendar.icalendar classes are built around reading a file and sharing one
 * event: VEvent only accepts unary properties without parameters, so it cannot express the
 * VALUE=DATE form an all-day event needs. Rather than change the importer's model, this writes the
 * subset of RFC 5545 a calendar export needs, with escaping and line folding done properly. It is a
 * pure function so the output can be checked without a device.
 */
package com.android.calendar.composeui

import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** One stored event row to write as a VEVENT. */
data class IcsEvent(
    val id: Long,
    val title: String?,
    val description: String?,
    val location: String?,
    val url: String?,
    val startMillis: Long,
    val endMillis: Long,
    val allDay: Boolean,
    val rrule: String?,
    val duration: String?,
)

object IcsExport {

    const val PRODID = "-//Etar//Etar Calendar//EN"

    private val UTC_DATE_TIME: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC)
    private val UTC_DATE: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC)

    /**
     * The whole calendar as an iCalendar string.
     *
     * All-day events are written as VALUE=DATE from their UTC date, the convention the app stores
     * them in (midnight UTC, exclusive end); timed events are written in UTC, which every reader
     * understands and which avoids a VTIMEZONE block.
     */
    fun build(events: List<IcsEvent>, nowMillis: Long = System.currentTimeMillis()): String {
        val stamp = UTC_DATE_TIME.format(Instant.ofEpochMilli(nowMillis))
        val out = StringBuilder()
        out.appendLine("BEGIN:VCALENDAR")
        out.appendLine("PRODID:$PRODID")
        out.appendLine("VERSION:2.0")
        out.appendLine("CALSCALE:GREGORIAN")
        for (event in events) {
            out.appendLine("BEGIN:VEVENT")
            out.appendLine("UID:${event.id}-${event.startMillis}@ws.xsoh.etar")
            out.appendLine("DTSTAMP:$stamp")
            if (event.allDay) {
                out.appendLine("DTSTART;VALUE=DATE:${UTC_DATE.format(Instant.ofEpochMilli(event.startMillis))}")
                if (event.endMillis > event.startMillis) {
                    out.appendLine("DTEND;VALUE=DATE:${UTC_DATE.format(Instant.ofEpochMilli(event.endMillis))}")
                }
            } else {
                out.appendLine("DTSTART:${UTC_DATE_TIME.format(Instant.ofEpochMilli(event.startMillis))}")
                if (event.endMillis > event.startMillis) {
                    out.appendLine("DTEND:${UTC_DATE_TIME.format(Instant.ofEpochMilli(event.endMillis))}")
                }
            }
            event.title?.takeIf { it.isNotEmpty() }?.let {
                out.appendLine("SUMMARY:${escapeText(it)}")
            }
            event.description?.takeIf { it.isNotEmpty() }?.let {
                out.appendLine("DESCRIPTION:${escapeText(it)}")
            }
            event.location?.takeIf { it.isNotEmpty() }?.let {
                out.appendLine("LOCATION:${escapeText(it)}")
            }
            event.url?.takeIf { it.isNotEmpty() }?.let {
                out.appendLine("URL:${escapeText(it)}")
            }
            event.rrule?.takeIf { it.isNotEmpty() }?.let { out.appendLine("RRULE:$it") }
            event.duration?.takeIf { it.isNotEmpty() }?.let { out.appendLine("DURATION:$it") }
            out.appendLine("END:VEVENT")
        }
        out.appendLine("END:VCALENDAR")
        return fold(out.toString())
    }

    /** Escapes the characters RFC 5545 reserves in a text value. */
    fun escapeText(value: String): String {
        val out = StringBuilder(value.length)
        for (ch in value) {
            when (ch) {
                '\\' -> out.append("\\\\")
                ';' -> out.append("\\;")
                ',' -> out.append("\\,")
                '\n' -> out.append("\\n")
                '\r' -> Unit
                else -> out.append(ch)
            }
        }
        return out.toString()
    }

    /**
     * Folds every content line at 75 octets.
     *
     * The limit is octets, not characters, and a UTF-8 sequence must not be split: the split points
     * are chosen on code point boundaries. Continuation lines start with one space, which is counted
     * in their 75.
     */
    fun fold(text: String): String {
        val out = StringBuilder()
        for (line in text.split("\r\n", "\n")) {
            if (line.isEmpty()) {
                continue
            }
            var current = line
            var first = true
            while (true) {
                val limit = if (first) 75 else 74
                val cut = cutAtOctets(current, limit)
                out.append(if (first) "" else " ").append(current.substring(0, cut)).append("\r\n")
                if (cut >= current.length) {
                    break
                }
                current = current.substring(cut)
                first = false
            }
        }
        return out.toString()
    }

    /** The longest prefix of [text] whose UTF-8 form fits in [maxOctets], ending on a code point. */
    private fun cutAtOctets(text: String, maxOctets: Int): Int {
        var octets = 0
        var index = 0
        while (index < text.length) {
            val codePoint = text.codePointAt(index)
            val width = utf8Length(codePoint)
            if (octets + width > maxOctets) {
                break
            }
            octets += width
            index += Character.charCount(codePoint)
        }
        return index
    }

    private fun utf8Length(codePoint: Int): Int = when {
        codePoint <= 0x7F -> 1
        codePoint <= 0x7FF -> 2
        codePoint <= 0xFFFF -> 3
        else -> 4
    }

    /** The octet length of a line, for the folding tests. */
    fun octetLength(line: String): Int = line.toByteArray(StandardCharsets.UTF_8).size
}
