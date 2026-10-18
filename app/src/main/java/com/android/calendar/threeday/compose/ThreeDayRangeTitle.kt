/*
 * Copyright (C) 2010 The Android Open Source Project
 * Copyright (C) 2022 The Calyx Institute
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.calendar.threeday.compose

import java.time.LocalDate
import java.time.ZoneId

/**
 * The three day range, written the way each language writes dates.
 *
 *     Chinese   2026年10月13日-15日      2026年10月31日-11月2日
 *     German    13-15 Okt. 2026         31 Okt.-2 Nov. 2026
 *     English   Oct 13-15, 2026         Oct 31-Nov 2, 2026
 *
 * The same builder writes the week's heading, which is the same kind of range with seven days in
 * it, so [last] is a parameter rather than the first day plus two. Everything below works from the
 * two ends of the range and does not care how many days they hold.
 *
 * Built from the platform's own patterns rather than from the month number and the characters 月 and
 * 日, which is what used to be here and which showed a Chinese range in every language.
 *
 * Each piece is asked for on its own rather than cut out of a whole date:
 *
 *  - "MMM" gives the month as the language abbreviates it, and its full stop is dropped - German
 *    writes "Okt." standing alone but "Okt" once a range follows it.
 *  - "d" or "d日" gives the day, with whatever the language puts after the number. That is "13日" in
 *    Chinese and a bare "13" in German and English, because the two write their dates differently:
 *    German writes a lone day as "14." and that full stop is an ordinal, which is right in "14.
 *    Oktober" and wrong in a range - the range asked for is "14-16 Okt." - so a day with a full stop
 *    and no 日 keeps only its digits.
 *  - The order comes from the language by asking for a date that has both and seeing which piece comes
 *    first. It cannot be read off the two pieces themselves, and it is not always day-then-month.
 *
 * With the pieces known, both ranges are written the same way: the month named once when the three
 * days stay inside it, and on both sides when they do not. The earlier code had a branch per case,
 * which is why one language came out as "Nov. 3-5" inside a month and "31. Okt.-2. Nov." across two,
 * and why a space appeared in one of them and not the other.
 *
 * Top level rather than a member of the three day activity, because the screen shows the same range
 * as its tab title and could not reach a member. There used to be a second builder over there,
 * written as "M月d日" for every language; this is now the only one.
 *
 * @param first the first day of the range.
 * @param last the last day of the range, inclusive. Defaults to the three day view's own range.
 */
internal fun rangeTitleOf(first: LocalDate, last: LocalDate = first.plusDays(2)): String {
    val locale = java.util.Locale.getDefault()
    val zone = ZoneId.systemDefault()

    fun formatted(date: LocalDate, skeleton: String): String {
        val pattern = android.text.format.DateFormat.getBestDateTimePattern(locale, skeleton)
        return java.text.SimpleDateFormat(pattern, locale)
            .format(java.util.Date(date.atStartOfDay(zone).toInstant().toEpochMilli()))
    }

    val monthPattern = formatted(first, "MMM").trim()
    val wholePattern = formatted(first, "MMMd").trim()

    // Whether this language puts the month before the day, read from a date that has both.
    val monthFirst = wholePattern.indexOf(monthPattern) == 0

    // What the language writes between the month and the day when the month comes first: a space in
    // English ("Sep 27"), nothing in Chinese ("9月27日").
    //
    // Read out of the whole date rather than assumed. It used to be omitted, which is what made the
    // English heading read "Sep.27-Oct.3": the month was concatenated straight onto the number,
    // where English puts a space and Chinese does not.
    val monthDayGlue = if (monthFirst) {
        val dayDigits = formatted(first, "d").filter { it.isDigit() }
        val monthStart = wholePattern.indexOf(monthPattern)
        val monthEnd = if (monthStart < 0) 0 else monthStart + monthPattern.length
        // Searched from the end of the month, not from the start of the date: a month that carries a
        // number - 10月, 1月 - contains the day's digits when the day is a single digit, and the
        // match found there would leave the glue as the month's own text.
        val dayStart = if (dayDigits.isEmpty()) monthEnd else wholePattern.indexOf(dayDigits, monthEnd)
        if (dayStart >= monthEnd) wholePattern.substring(monthEnd, dayStart) else " "
    } else {
        ""
    }

    /**
     * The month as this language writes it inside a running date: "Sept." in German, "Oct." in
     * English, "10月" in Chinese.
     *
     * Read out of the full date rather than rebuilt from the "MMM" piece. The piece alone is wrong
     * in German for September: the platform returns "Sep", and appending the shortening stop gave
     * "Sep." where German actually writes "Sept." - the extra t is part of the abbreviation and the
     * piece does not carry it. The full date ("16. Sept.") does, so the month is cut out of that
     * around the day number instead.
     *
     * Day-first languages keep the punctuation the full date carries. Month-first languages write
     * no stop in the date itself ("Oct 13"), so the stop is added back when the piece is a
     * shortening of the full name - which is what the English heading used to show.
     *
     * A function of the date, not a value worked out once, because a range across two months names
     * each side's own month.
     */
    fun month(date: LocalDate): String {
        val whole = formatted(date, "MMMd").trim()
        val dayDigits = formatted(date, "d").filter { it.isDigit() }
        // The day's LAST occurrence: a single-digit day in a month like 1月 would otherwise match
        // the month's own number first.
        val dayIndex = if (monthFirst) whole.lastIndexOf(dayDigits) else whole.indexOf(dayDigits)
        val extracted = if (dayIndex >= 0 && dayDigits.isNotEmpty()) {
            if (monthFirst) {
                whole.substring(0, dayIndex).trimEnd(' ', '.')
            } else {
                whole.substring(dayIndex + dayDigits.length).trim().trimStart(' ', '.')
            }
        } else {
            formatted(date, "MMM").trim()
        }
        if (monthFirst && extracted.isNotEmpty() && !extracted.endsWith('.')) {
            val full = formatted(date, "MMMM").trim()
            if (full.length > extracted.length && full.startsWith(extracted)) {
                return "$extracted."
            }
        }
        return extracted
    }

    // What the language writes after the day number, if anything.
    //
    // Read from the whole date rather than assumed: the character between the digits and the month is
    // whatever that language puts there - 日 in Chinese, a full stop in German, a space in English.
    val rawDay = formatted(first, "d")
    val dayTail = rawDay.dropWhile { !it.isDigit() }.dropWhile { it.isDigit() }

    // A tail that is a full stop is an ordinal, which belongs to a date written on its own and not to
    // a range: German writes "14. Oktober" but the range asked for is "14-16 Okt.". Any other tail is
    // part of the day as the language writes it - 日 - and is kept. Trailing spaces are dropped first,
    // so German's ". " is recognised as the full stop it is.
    val dayTailTrimmed = dayTail.trim()
    val daySuffix = if (dayTailTrimmed == "." || dayTailTrimmed.isEmpty()) "" else dayTailTrimmed

    fun number(date: LocalDate): String =
        formatted(date, "d").filter { it.isDigit() }

    fun dayAndSuffix(date: LocalDate): String = number(date) + daySuffix

    // The day with the month beside it, for a range that crosses from one month into the next:
    // "10月31日", "31 Okt.", "Oct. 31".
    fun side(date: LocalDate): String {
        val d = dayAndSuffix(date)
        return if (monthFirst) "${month(date)}$monthDayGlue$d" else "$d ${month(date)}"
    }

    val range = if (first.monthValue == last.monthValue && first.year == last.year) {
        // "10月13日-15日", "14-16 Okt.", "Oct. 28-31": the month once, the first day in full, the
        // second with only the part that does not repeat.
        val m = month(first)
        if (monthFirst) {
            "$m$monthDayGlue${dayAndSuffix(first)}-${dayAndSuffix(last)}"
        } else {
            "${dayAndSuffix(first)}-${dayAndSuffix(last)} $m"
        }
    } else {
        // "10月31日-11月2日" / "31 Okt.-2 Nov.": both sides named.
        side(first) + "-" + side(last)
    }

    // The year the range falls in, placed where the language puts it in a full date: Chinese
    // writes it first - "2026年10月13日-15日" - while German and English write it last -
    // "13-15 Okt. 2026", "Oct 13-15, 2026". The glue around the year (年, " ", ", ") is read from
    // the language's own full-date pattern rather than assumed, the same way the day suffix is.
    val fullYearDate = formatted(first, "yMMMd").trim()
    // Digits only: some locales write the bare year with its suffix (2026年), which the glue
    // extraction below would then pick up twice.
    val yearDigits = formatted(first, "y").filter { it.isDigit() }
    val yearStart = fullYearDate.indexOf(yearDigits)
    val monthIndex = fullYearDate.indexOf(monthPattern)

    // Where the language puts the year, read from the positions of the pieces in a full date.
    //
    // It used to be decided by "does the month come before the day", which is a different question:
    // English writes the month first *and* the year last, so the Chinese branch was taken and the
    // heading read "2026年Sep.27-Oct.3" in English. Chinese and Japanese put the year before the
    // month; German and English put it after the whole date.
    val yearFirst = yearStart >= 0 && monthIndex >= 0 && yearStart < monthIndex

    return if (yearFirst) {
        // Year first, with the language's own suffix after it: "2026年" + "10月13日-15日".
        fullYearDate.substring(0, monthIndex) + range
    } else if (yearStart >= 0) {
        // Year last, with the language's own glue before it: "13-15 Okt. 2026", "Oct 13-15, 2026".
        // The glue is the run of punctuation and spaces directly before the year in the full date.
        //
        // It is read from the text, not measured from the month pattern: the "MMM" piece ("Sep")
        // can be shorter than the month inside the date ("Sept."), and measuring from it left the
        // month's own tail - "t. " - behind as though it were glue, which is how the title came out
        // as "Sep.t. 2026". Walking backwards from the year stops exactly where the month ends.
        val beforeYear = fullYearDate.substring(0, yearStart)
        var glue = ""
        var k = beforeYear.length
        while (k > 0) {
            val ch = beforeYear[k - 1]
            if (ch.isLetterOrDigit()) {
                break
            }
            glue = ch + glue
            k--
        }
        // A full stop picked up there is the month's own abbreviation stop - German writes
        // "Okt." and the range's month already carries it; keeping it doubled the dot.
        if (glue.startsWith(".") && month(first).endsWith(".")) {
            glue = glue.substring(1)
        }
        range + glue + yearDigits
    } else {
        "$range $yearDigits"
    }
}
