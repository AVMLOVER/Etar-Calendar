/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The to-do widget: today's checklist on the home screen.
 *
 * The header names today and is the way into the screen - the editor for today's to-do when there
 * is one, and the editor for a new one when there is not. The rows are the checklist itself: one tap
 * marks a line done, the gesture the screen has, and the change goes through the same store, so the
 * widget and the screen cannot disagree about what is done.
 *
 * The rows live in a RemoteViewsService behind a ListView because the list is unbounded. A
 * RemoteViews cannot add an arbitrary number of rows to a layout, and a to-do list that silently
 * stopped after the third line would be worse than one that scrolls.
 */
package com.android.calendar.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Build
import android.text.format.DateFormat
import android.view.View
import android.widget.RemoteViews
import com.android.calendar.todo.TodoStore
import com.android.calendar.todo.compose.ComposeTodoActivity
import ws.xsoh.etar.R
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import java.util.Locale

class TodoWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        manager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        appWidgetIds.forEach { id ->
            manager.updateAppWidget(id, build(context, id))
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_TOGGLE -> {
                val itemId = intent.getStringExtra(EXTRA_ITEM_ID) ?: return
                // The store is a provider query followed by preferences writes, none of which belongs
                // on the thread a broadcast is delivered on. goAsync is what keeps the process alive
                // until the tap has been written back - which is also what makes the widget show the
                // tick it was given.
                val pending = goAsync()
                Thread {
                    try {
                        toggle(context, itemId)
                    } finally {
                        pending.finish()
                    }
                }.start()
            }

            // The widget always shows today, so the day rolling over has to rebuild it even though
            // nothing about the to-do itself changed. Super handles APPWIDGET_UPDATE; these are the
            // broadcasts the manifest filter asks for on top of it, and AppWidgetProvider does not
            // act on them by itself.
            Intent.ACTION_DATE_CHANGED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_LOCALE_CHANGED ->
                TodoWidgetUpdater.updateAll(context)
        }
    }

    /** Marks one line done, or not done, and writes it back the way the screen does. */
    private fun toggle(context: Context, itemId: String) {
        val todo = TodoStore.forDate(context, LocalDate.now()) ?: return
        if (todo.items.none { it.id == itemId }) {
            return
        }
        val items = todo.items.map { item ->
            if (item.id == itemId) item.copy(done = !item.done) else item
        }
        // Done lines sink to the bottom here too, which is what the screen does when a box is ticked.
        // Saving is also what updates the widget: TodoStore.save calls the updater.
        TodoStore.save(context, todo.copy(items = items.sortedBy { it.done }))
    }

    /** Builds the widget: the header, and the frame the day's checklist is shown in. */
    fun build(context: Context, widgetId: Int): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.todo_widget)
        val locale = Locale.getDefault()
        val today = LocalDate.now()
        val millis = today.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

        views.setTextViewText(R.id.todo_widget_date, format(millis, "MMMd", locale))
        views.setTextViewText(R.id.todo_widget_weekday, format(millis, "EEE", locale))
        views.setContentDescription(
            R.id.todo_widget_header,
            context.getString(R.string.m3e_todo_widget_header),
        )

        // One tap on the header opens today's to-do in the editor. Whether that means adding or
        // editing is decided by the screen, from whether the day already has a to-do - so the widget
        // does not have to keep a stale copy of that answer.
        views.setOnClickPendingIntent(
            R.id.todo_widget_header,
            PendingIntent.getActivity(
                context,
                0,
                ComposeTodoActivity.intent(context, today)
                    .putExtra(ComposeTodoActivity.EXTRA_OPEN_EDITOR, true),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            ),
        )

        views.setRemoteAdapter(
            R.id.todo_widget_list,
            // The id travels with the service intent, which is how a collection widget tells its
            // rows which instance they are being built for.
            Intent(context, TodoWidgetService::class.java)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId),
        )

        // The list and the empty line are both always in the layout; which one shows is decided by
        // the day's own to-do, so a widget whose last line was just ticked away does not keep an
        // empty list on screen until something else refreshes it.
        val hasItems = TodoStore.forDate(context, today)?.items?.isNotEmpty() == true
        views.setViewVisibility(R.id.todo_widget_list, if (hasItems) View.VISIBLE else View.GONE)
        views.setViewVisibility(R.id.todo_widget_empty, if (hasItems) View.GONE else View.VISIBLE)

        views.setPendingIntentTemplate(R.id.todo_widget_list, toggleTemplate(context))
        return views
    }

    /**
     * The template every row's tap runs through: the row adds the id of the line it shows, and the
     * template carries it back here.
     */
    private fun toggleTemplate(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            1,
            Intent(context, TodoWidgetProvider::class.java).setAction(ACTION_TOGGLE),
            // The row's id arrives in a fill-in intent, and a fill-in is only merged into a mutable
            // PendingIntent - an immutable one ignores it, which is a tap that does nothing at all.
            PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    PendingIntent.FLAG_MUTABLE
                } else {
                    0
                },
        )

    /** The date as this language writes it: "18 Oct", "10月18日". */
    private fun format(millis: Long, skeleton: String, locale: Locale): String {
        val pattern = DateFormat.getBestDateTimePattern(locale, skeleton)
        return SimpleDateFormat(pattern, locale).format(Date(millis))
    }

    companion object {
        /** What a row's tap carries back to [TodoWidgetProvider]. */
        const val ACTION_TOGGLE = "com.android.calendar.todo.widget.TOGGLE"

        /** Which line was tapped, put on the intent by the row's fill-in. */
        const val EXTRA_ITEM_ID = "com.android.calendar.todo.widget.ITEM_ID"
    }
}
