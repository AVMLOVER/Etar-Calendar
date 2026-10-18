/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The month each month-widget instance is showing.
 *
 * Stored per widget rather than kept in memory: a provider is created fresh for every update and
 * every tap, so the month the arrows moved to has to outlive it. The widget id is what the launcher
 * hands back each time, which makes it the key.
 */
package com.android.calendar.widget

import android.content.Context
import java.time.YearMonth

object MonthWidgetState {

    private const val PREFS = "m3e_month_widget"
    private const val NO_MONTH = Int.MIN_VALUE

    /** The month the widget shows: the current one until its arrows have been used. */
    fun month(context: Context, widgetId: Int): YearMonth {
        val stored = prefs(context).getInt(key(widgetId), NO_MONTH)
        return if (stored == NO_MONTH) YearMonth.now() else fromIndex(stored)
    }

    fun shift(context: Context, widgetId: Int, months: Long) {
        save(context, widgetId, month(context, widgetId).plusMonths(months))
    }

    fun save(context: Context, widgetId: Int, month: YearMonth) {
        prefs(context).edit().putInt(key(widgetId), toIndex(month)).apply()
    }

    /** Drops the stored months of widgets that no longer exist. */
    fun clear(context: Context, widgetIds: IntArray) {
        val editor = prefs(context).edit()
        widgetIds.forEach { editor.remove(key(it)) }
        editor.apply()
    }

    private fun key(widgetId: Int) = "month_$widgetId"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // A month as one int - year * 12 + (month - 1) - so it sorts and stores without parsing a string.
    private fun toIndex(month: YearMonth): Int = month.year * 12 + (month.monthValue - 1)

    private fun fromIndex(index: Int): YearMonth = YearMonth.of(index / 12, index % 12 + 1)
}
