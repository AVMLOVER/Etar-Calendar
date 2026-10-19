/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The month widget, 4x4: a month grid on the home screen.
 *
 * The header names the month and year, pages to the month before and after it, and starts an event
 * from its plus button. Under that the seven weekday names and six weeks of day cells, each day
 * carrying its number and a bar per event in that event's calendar colour - today's number circled,
 * the days of the neighbouring months dimmed, as the month view in the app draws them.
 *
 * The grid is written out in the layout (42 cells, each with its own ids) and filled here, rather
 * than assembled from RemoteViews added to a container or fed by an adapter. A widget's launcher-side
 * inflation refuses RemoteViews nested inside RemoteViews - which is what produced its "could not
 * load the widget" line - and a fixed grid needs no nesting at all. A month spans four to six weeks,
 * so the sixth row is hidden in the months that do not use it.
 */
package com.android.calendar.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.CalendarContract
import android.text.format.DateFormat
import android.view.View
import android.widget.RemoteViews
import com.android.calendar.CalendarController
import com.android.calendar.Utils
import com.android.calendar.composeui.ComposeDayActivity
import com.android.calendar.composeui.DaysRepository
import com.android.calendar.composeui.groupMarkersByDay
import ws.xsoh.etar.R
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Date
import java.util.Locale

class MonthWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        manager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        appWidgetIds.forEach { id ->
            manager.updateAppWidget(id, build(context, id))
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        MonthWidgetState.clear(context, appWidgetIds)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_PREV, ACTION_NEXT -> {
                val widgetId = intent.getIntExtra(
                    AppWidgetManager.EXTRA_APPWIDGET_ID,
                    AppWidgetManager.INVALID_APPWIDGET_ID,
                )
                if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
                    return
                }
                MonthWidgetState.shift(
                    context,
                    widgetId,
                    if (intent.action == ACTION_PREV) -1L else 1L,
                )
                AppWidgetManager.getInstance(context)
                    ?.updateAppWidget(widgetId, build(context, widgetId))
            }

            // The grid always has to agree with the date and the locale it is drawn for, so the day
            // rolling over (or the language changing) rebuilds it. AppWidgetProvider itself only
            // acts on its own update action.
            Intent.ACTION_DATE_CHANGED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_LOCALE_CHANGED,
            // An event was added, moved or deleted in the calendar: the bars are out of date.
            Intent.ACTION_PROVIDER_CHANGED ->
                updateAll(context)
        }
    }

    /** Rebuilds every month widget, which is what a date, locale or provider change needs. */
    fun updateAll(context: Context) {
        val manager = AppWidgetManager.getInstance(context) ?: return
        manager.getAppWidgetIds(ComponentName(context, MonthWidgetProvider::class.java))
            .forEach { id -> manager.updateAppWidget(id, build(context, id)) }
    }

    /** Builds one month widget: the header, the weekday names and the six weeks of cells. */
    fun build(context: Context, widgetId: Int): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.month_widget)
        val locale = Locale.getDefault()
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val month = MonthWidgetState.month(context, widgetId)

        views.setTextViewText(R.id.month_widget_title, monthTitle(month, locale))

        views.setOnClickPendingIntent(
            R.id.month_widget_prev,
            monthTap(context, widgetId, ACTION_PREV, requestCode(widgetId, REQUEST_PREV)),
        )
        views.setOnClickPendingIntent(
            R.id.month_widget_next,
            monthTap(context, widgetId, ACTION_NEXT, requestCode(widgetId, REQUEST_NEXT)),
        )
        views.setOnClickPendingIntent(
            R.id.month_widget_add,
            createEventTap(context, requestCode(widgetId, REQUEST_ADD)),
        )

        val firstDayOfWeek = WeekStart.dayOfWeek(context)
        WEEKDAY_IDS.forEachIndexed { index, id ->
            views.setTextViewText(
                id,
                firstDayOfWeek.plus(index.toLong()).getDisplayName(TextStyle.NARROW, locale),
            )
        }

        // The grid starts on the week's first day, so every row below the header is a whole week -
        // the same alignment the month view in the app uses.
        val firstOfMonth = month.atDay(1)
        val leadingDays = ((firstOfMonth.dayOfWeek.value - firstDayOfWeek.value) + 7) % 7
        val gridStart = firstOfMonth.minusDays(leadingDays.toLong())
        val weeks = (leadingDays + month.lengthOfMonth() + 6) / 7

        // The sixth row is hidden in months that only span five weeks, so the grid does not end with
        // a row of the following month's days hanging under it.
        for (row in 0 until WEEKS) {
            views.setViewVisibility(
                MonthWidgetIds.rows[row],
                if (row < weeks) View.VISIBLE else View.GONE,
            )
        }

        val events = try {
            DaysRepository(context.applicationContext).load(
                gridStart.atStartOfDay(zone).toInstant().toEpochMilli(),
                gridStart.plusDays((WEEKS * 7).toLong()).atStartOfDay(zone)
                    .toInstant().toEpochMilli(),
                // A month of a busy, shared calendar holds more than the default window allows, and
                // the bars that fell outside it would simply be missing.
                limit = 2000,
            )
        } catch (t: Throwable) {
            emptyList()
        }
        val byDay = groupMarkersByDay(events, gridStart, WEEKS * 7, zone)
        val density = context.resources.displayMetrics.density

        for (index in 0 until WEEKS * 7) {
            val day = gridStart.plusDays(index.toLong())
            val inMonth = YearMonth.from(day) == month
            val isToday = day == today

            views.setTextViewText(MonthWidgetIds.days[index], day.dayOfMonth.toString())
            views.setTextColor(
                MonthWidgetIds.days[index],
                context.getColor(
                    when {
                        // On today's filled circle the number has to read against the primary.
                        isToday -> R.color.widget_on_primary
                        inMonth -> R.color.widget_on_surface
                        // The grid's tails: the days of the months on either side are there to keep
                        // the rows full, and reading them at full strength made it look as though
                        // the month had more days than it does.
                        else -> R.color.widget_on_surface_dim
                    }
                ),
            )
            views.setViewVisibility(
                MonthWidgetIds.circles[index],
                if (isToday) View.VISIBLE else View.GONE,
            )

            val colors = byDay[day].orEmpty().take(MAX_BARS).map { it.calendarColor }
            BARS.forEachIndexed { slot, ids ->
                val color = colors.getOrNull(slot)
                if (color == null) {
                    views.setViewVisibility(ids[index], View.GONE)
                } else {
                    views.setViewVisibility(ids[index], View.VISIBLE)
                    views.setImageViewBitmap(ids[index], MonthWidgetBitmap.bar(color, density))
                }
            }

            views.setOnClickPendingIntent(
                MonthWidgetIds.cells[index],
                dayTap(context, widgetId, day, index, zone),
            )
        }
        return views
    }

    /** A day cell's tap: opens the day view on that day. */
    private fun dayTap(
        context: Context,
        widgetId: Int,
        day: LocalDate,
        index: Int,
        zone: ZoneId,
    ): PendingIntent = PendingIntent.getActivity(
        context,
        requestCode(widgetId, index),
        Intent(context, ComposeDayActivity::class.java)
            .putExtra(
                CalendarContract.EXTRA_EVENT_BEGIN_TIME,
                day.atStartOfDay(zone).toInstant().toEpochMilli(),
            )
            // The tap names the view it wants, so it is opened even when the "Default view" setting
            // names another one: the day view a landing screen hands over to that setting, and
            // without this a cell tapped while the default was, say, the month view would open the
            // month instead of the day it was tapped from.
            .putExtra(Utils.INTENT_KEY_VIEW_TYPE, Utils.INTENT_VALUE_VIEW_TYPE_DAY),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /** The arrow above the grid: moves this widget's month by one and rebuilds it. */
    private fun monthTap(
        context: Context,
        widgetId: Int,
        action: String,
        requestCode: Int,
    ): PendingIntent = PendingIntent.getBroadcast(
        context,
        requestCode,
        Intent(context, MonthWidgetProvider::class.java)
            .setAction(action)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /**
     * The plus button: a new event today, at the same "next slot" time the app's own add button
     * uses - the next half hour, at least thirty and at most sixty minutes away.
     */
    private fun createEventTap(context: Context, requestCode: Int): PendingIntent {
        val start = Utils.getNextCreateEventStart(System.currentTimeMillis())
        val intent = CalendarController.getInstance(context)
            .generateCreateEventIntent(start, start + EVENT_LENGTH_MILLIS, false, null, -1L)
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /** The month and year, written the way the language writes them: "October 2026", "2026年10月". */
    private fun monthTitle(month: YearMonth, locale: Locale): String {
        val millis = month.atDay(1)
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
        val pattern = DateFormat.getBestDateTimePattern(locale, "yMMMM")
        return SimpleDateFormat(pattern, locale).format(Date(millis))
    }

    private fun requestCode(widgetId: Int, offset: Int): Int = widgetId * REQUEST_STRIDE + offset

    companion object {
        /** The arrow taps, delivered back to this provider. */
        const val ACTION_PREV = "com.android.calendar.widget.MONTH_PREV"
        const val ACTION_NEXT = "com.android.calendar.widget.MONTH_NEXT"

        /** Request codes are spaced so a widget's arrows, add button and day cells cannot collide. */
        private const val REQUEST_STRIDE = 64

        /** Offsets past the forty two day cells within a widget's block of request codes. */
        private const val REQUEST_PREV = 42
        private const val REQUEST_NEXT = 43
        private const val REQUEST_ADD = 44

        /** Six weeks, the most a month can span; the grid in the layout has exactly that many. */
        private const val WEEKS = 6

        /** How many bars a day cell shows: the height of a cell is the number and two thin lines. */
        private const val MAX_BARS = 2

        private const val EVENT_LENGTH_MILLIS = 60L * 60L * 1000L

        /** The bar ImageViews, in slot order. */
        private val BARS = arrayOf(MonthWidgetIds.barFirst, MonthWidgetIds.barSecond)

        private val WEEKDAY_IDS = intArrayOf(
            R.id.month_widget_w0,
            R.id.month_widget_w1,
            R.id.month_widget_w2,
            R.id.month_widget_w3,
            R.id.month_widget_w4,
            R.id.month_widget_w5,
            R.id.month_widget_w6,
        )
    }
}
