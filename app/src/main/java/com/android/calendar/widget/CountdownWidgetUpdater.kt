/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * Refreshing countdown widgets, and finding out how many rows a given instance can hold.
 */
package com.android.calendar.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context

object CountdownWidgetUpdater {

    /**
     * How many countdowns the widget owning [widgetId] can show.
     *
     * Read back from the provider the instance belongs to, rather than passed around, because the
     * configuration activity is started by the launcher and only receives the widget id.
     */
    fun slotsFor(context: Context, widgetId: Int): Int {
        val manager = AppWidgetManager.getInstance(context)
        return when (manager.getAppWidgetInfo(widgetId)?.provider?.className) {
            CountdownSmallWidgetProvider::class.java.name -> 1
            // Two for the 4x2, four for the 4x4: the numbers the sizes are named for, kept in step
            // with the slots each provider declares.
            CountdownMediumWidgetProvider::class.java.name -> 2
            CountdownLargeWidgetProvider::class.java.name -> 4
            // An unknown provider is treated as the smallest, which is the safe direction: it will
            // accept a single choice rather than silently dropping some of several.
            else -> 1
        }
    }

    /** Rebuild one instance. */
    fun update(context: Context, manager: AppWidgetManager, widgetId: Int) {
        val info = manager.getAppWidgetInfo(widgetId) ?: return
        val views = when (info.provider.className) {
            CountdownSmallWidgetProvider::class.java.name ->
                CountdownSmallWidgetProvider().build(context, widgetId)
            CountdownMediumWidgetProvider::class.java.name ->
                CountdownMediumWidgetProvider().build(context, widgetId)
            CountdownLargeWidgetProvider::class.java.name ->
                CountdownLargeWidgetProvider().build(context, widgetId)
            else -> null
        } ?: return
        manager.updateAppWidget(widgetId, views)
    }

    /**
     * Rebuild every countdown widget.
     *
     * Called after the countdown list changes, so a widget does not keep showing a countdown that was
     * deleted or renamed until its next scheduled update.
     */
    fun updateAll(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        listOf(
            CountdownSmallWidgetProvider::class.java,
            CountdownMediumWidgetProvider::class.java,
            CountdownLargeWidgetProvider::class.java,
        ).forEach { provider ->
            manager.getAppWidgetIds(ComponentName(context, provider)).forEach { id ->
                update(context, manager, id)
            }
        }
    }
}
