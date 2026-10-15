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
 *     Chinese   10月13日-15日          10月31日-11月2日
 *     German    14-16 Okt.            31 Okt.-2 Nov.
 *     English   Oct 28-31             Oct 31-Nov 2
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
 */
internal fun rangeTitleOf(first: LocalDate): String {
    val locale = java.util.Locale.getDefault()
    val zone = ZoneId.systemDefault()

    fun formatted(date: LocalDate, skeleton: String): String {
        val pattern = android.text.format.DateFormat.getBestDateTimePattern(locale, skeleton)
        return java.text.SimpleDateFormat(pattern, locale)
            .format(java.util.Date(date.atStartOfDay(zone).toInstant().toEpochMilli()))
    }

    val monthPattern = formatted(first, "MMM").trim()
    val wholePattern = formatted(first, "MMMd").trim()

    /**
     * The month as this language abbreviates it in running text: "Okt." in German, "Oct." in English,
     * "10月" in Chinese.
     *
     * A function of the date, not a value worked out once. It was a value, and that is what made a
     * range across two months read "31 Okt-2 Okt": both sides were written with the first day's month,
     * so the second never changed. Read per date, each side names its own.
     *
     * The full stop is added here rather than expected from the pattern. Asking for "MMM" gives the
     * abbreviation without it - Android's formatter returns "Okt" and "Oct", and the stop that appears
     * in "14. Okt." belongs to the day, not to the month - so a title built from the piece alone came
     * out as "13-15 Okt". It is added only when the language actually writes one: whether it does is
     * read from the month name the platform gives, "Oktober" against "Okt", where the abbreviation
     * being a prefix of the full name means the language marks the shortening.
     */
    fun month(date: LocalDate): String {
        val abbreviated = formatted(date, "MMM").trim()
        if (abbreviated.endsWith('.')) {
            return abbreviated
        }
        val full = formatted(date, "MMMM").trim()
        // "Okt" is a prefix of "Oktober", so German marks the shortening with a stop. "May" is not a
        // prefix of "May" - the month has no longer form - and gets nothing.
        val shortens = abbreviated.isNotEmpty() && full.length > abbreviated.length &&
                full.startsWith(abbreviated)
        return if (shortens) "$abbreviated." else abbreviated
    }

    // Whether this language puts the day before the month, read from a date that has both.
    val monthFirst = wholePattern.indexOf(monthPattern) == 0

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
        return if (monthFirst) "${month(date)}$d" else "$d ${month(date)}"
    }

    val last = first.plusDays(2)
    return if (first.monthValue == last.monthValue && first.year == last.year) {
        // "10月13日-15日", "14-16 Okt.", "Oct. 28-31": the month once, the first day in full, the
        // second with only the part that does not repeat.
        val m = month(first)
        if (monthFirst) {
            "$m${dayAndSuffix(first)}-${dayAndSuffix(last)}"
        } else {
            "${dayAndSuffix(first)}-${dayAndSuffix(last)} $m"
        }
    } else {
        // "10月31日-11月2日" / "31 Okt.-2 Nov.": both sides named.
        side(first) + "-" + side(last)
    }
}
