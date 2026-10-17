/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The two lines a day screen's heading is made of, in the day view's own wording.
 *
 * The day view draws the date on the first line and - on the second - whether the day is yesterday,
 * today or tomorrow, the weekday, and the lunar date when that setting is on. A screen that shows a
 * single day should read the same way, so the same two lines are built here rather than invented a
 * second time at the call site.
 */
package com.android.calendar.chrome

import android.content.Context
import com.android.calendar.LunarUtils
import ws.xsoh.etar.R
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import java.util.Locale

object DayHeading {

    /** The date, abbreviated the way the day view's heading abbreviates it: 2026年10月17日, 17. Okt. 2026. */
    fun title(context: Context, date: LocalDate): String {
        val millis = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        // "yMMMd" puts the year where the language puts it, and keeps the month abbreviated: the full
        // name leaves no room on a row shared with the action icons.
        val pattern = android.text.format.DateFormat.getBestDateTimePattern(
            Locale.getDefault(), "yMMMd",
        )
        return SimpleDateFormat(pattern, Locale.getDefault()).format(Date(millis))
    }

    /**
     * Yesterday / today / tomorrow (when the day is one of them), the weekday, then the lunar date
     * when the setting is on - the second line of the day view's heading.
     */
    fun subtitle(context: Context, date: LocalDate): String {
        val zone = ZoneId.systemDefault()
        val millis = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val weekdayPattern = android.text.format.DateFormat.getBestDateTimePattern(
            Locale.getDefault(), "EEE",
        )
        val weekday = SimpleDateFormat(weekdayPattern, Locale.getDefault()).format(Date(millis))
        val today = LocalDate.now(zone)
        var text = when (date) {
            today -> context.getString(R.string.agenda_today, weekday)
            today.minusDays(1) -> context.getString(R.string.agenda_yesterday, weekday)
            today.plusDays(1) -> context.getString(R.string.agenda_tomorrow, weekday)
            else -> weekday
        }
        if (LunarUtils.showLunar(context)) {
            // The day view names the lunar month rather than only the day, and this heading is a day.
            val lunar = LunarUtils.get(
                context, date.year, date.monthValue - 1, date.dayOfMonth,
                LunarUtils.FORMAT_LUNAR_MONTH_DAY or LunarUtils.FORMAT_ONE_FESTIVAL,
                false, null,
            )
            if (!lunar.isNullOrEmpty()) {
                text = text + "，" + lunar
            }
        }
        return text
    }
}
