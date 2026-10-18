/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * Refreshing the to-do widget.
 *
 * Called from the store rather than from each screen: every way a to-do can change - the editor, a
 * tick on the screen, a tick on the widget itself, a delete - ends in TodoStore.save or
 * TodoStore.delete, and that is the one place the home screen has to be told about.
 */
package com.android.calendar.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import ws.xsoh.etar.R

object TodoWidgetUpdater {

    /**
     * Rebuilds every to-do widget.
     *
     * Both halves are refreshed: the frame (the date in the header, and whether the day has anything
     * at all) through updateAppWidget, and the rows through notifyAppWidgetViewDataChanged, because
     * the rows come from the service and are not part of the RemoteViews that was just built.
     */
    fun updateAll(context: Context) {
        val manager = AppWidgetManager.getInstance(context) ?: return
        val ids = manager.getAppWidgetIds(ComponentName(context, TodoWidgetProvider::class.java))
        if (ids.isEmpty()) {
            return
        }
        ids.forEach { id ->
            manager.updateAppWidget(id, TodoWidgetProvider().build(context, id))
        }
        manager.notifyAppWidgetViewDataChanged(ids, R.id.todo_widget_list)
    }
}
