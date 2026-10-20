/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The seven weekday labels a grid header shows, in the locale's own narrow or short form.
 *
 * The month widget needs single-character weekday names - a 4x4 widget gives each of the seven
 * columns about a tenth of its width - and the month screen's header needs the abbreviated ones.
 * Both read them from here, so the two cannot disagree about the order or the source.
 *
 * Why not java.time's TextStyle.NARROW: this app enables core library desugaring, and the backport's
 * DateTimeTextProvider builds a "narrow" weekday as the first code point of the full name. In Chinese
 * that turns 星期一 into 星, and the widget showed 星 星 星 星 星 星 星. The platform's own date
 * formatting is backed by ICU and has the real narrow forms for the EEEEE field, so the labels are
 * built through android.text.format.DateFormat + java.text.SimpleDateFormat instead.
 */
package com.android.calendar.composeui

import java.text.SimpleDateFormat
import java.time.DayOfWeek
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** The field width that asks the locale for its narrow weekday names: 一, M, S. */
const val NARROW_WEEKDAY_SKELETON = "EEEEE"

/** The field width that asks the locale for its abbreviated weekday names: 周一, Mo, Sun. */
const val SHORT_WEEKDAY_SKELETON = "EEE"

/** The name of each weekday, so the order can be checked without the platform's locale data. */
fun interface WeekdayNameSource {
    fun nameOf(day: DayOfWeek): String
}

/**
 * The seven labels for the week that starts on [firstDayOfWeek], in display order.
 *
 * Ordering is the only thing this decides, which is why it takes the names instead of formatting
 * them: the app's "first day of the week" setting decides where the week starts, and a grid that
 * ordered them by the locale's default would disagree with the days underneath it.
 */
fun weekdayLabels(firstDayOfWeek: DayOfWeek, names: WeekdayNameSource): List<String> =
    (0 until 7).map { offset -> names.nameOf(firstDayOfWeek.plus(offset.toLong())) }

/** The locale's narrow weekday names for [firstDayOfWeek], in display order. */
fun narrowWeekdayLabels(firstDayOfWeek: DayOfWeek, locale: Locale): List<String> =
    weekdayLabels(firstDayOfWeek, platformWeekdayNames(NARROW_WEEKDAY_SKELETON, locale))

/** The locale's abbreviated weekday names for [firstDayOfWeek], in display order. */
fun shortWeekdayLabels(firstDayOfWeek: DayOfWeek, locale: Locale): List<String> =
    weekdayLabels(firstDayOfWeek, platformWeekdayNames(SHORT_WEEKDAY_SKELETON, locale))

/**
 * Weekday names from the platform's locale data.
 *
 * The names are formatted from one fixed week rather than from [DayOfWeek.getDisplayName], because
 * of the desugaring fallback described at the top of this file. A fixed Sunday at midday is the
 * reference: midday keeps a DST shift from moving the day, and the names do not depend on the date
 * they are printed for.
 */
private fun platformWeekdayNames(skeleton: String, locale: Locale): WeekdayNameSource {
    val pattern = android.text.format.DateFormat.getBestDateTimePattern(locale, skeleton)
    val format = SimpleDateFormat(pattern, locale)
    val calendar = Calendar.getInstance().apply {
        clear()
        set(2024, Calendar.JANUARY, 7, 12, 0, 0)
    }
    val names = HashMap<DayOfWeek, String>(7)
    for (offset in 0 until 7) {
        val day = DayOfWeek.SUNDAY.plus(offset.toLong())
        names[day] = format.format(Date(calendar.timeInMillis))
        calendar.add(Calendar.DAY_OF_MONTH, 1)
    }
    return WeekdayNameSource { day -> names.getValue(day) }
}
