/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The rows of the to-do widget.
 *
 * A RemoteViewsService rather than rows added by the provider: the widget shows the day's checklist,
 * whose length the widget cannot know, and a ListView is what makes it scroll. The factory reads the
 * store every time it is asked to refresh, so a tick on the widget and a tick on the screen produce
 * the same list.
 *
 * The colours come from the widget's own colour resources, not from the app's theme: a widget is
 * inflated by the launcher, which knows nothing of that theme - a `?attr` role resolved to nothing
 * there, and the text ended up black on a black card.
 */
package com.android.calendar.widget

import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.android.calendar.todo.TodoItem
import com.android.calendar.todo.TodoStore
import ws.xsoh.etar.R
import java.time.LocalDate

class TodoWidgetService : RemoteViewsService() {

    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory =
        TodoRowFactory(applicationContext)
}

private class TodoRowFactory(private val context: Context) :
    RemoteViewsService.RemoteViewsFactory {

    private var items: List<TodoItem> = emptyList()

    override fun onCreate() = Unit

    /**
     * Re-read on every refresh, rather than once when the service is created: the widget always
     * shows today, and the list changes whenever a line is ticked here or on the screen.
     */
    override fun onDataSetChanged() {
        items = TodoStore.forDate(context, LocalDate.now())?.items.orEmpty()
    }

    override fun onDestroy() {
        items = emptyList()
    }

    override fun getCount(): Int = items.size

    override fun getViewAt(position: Int): RemoteViews {
        val row = RemoteViews(context.packageName, R.layout.todo_widget_row)
        val item = items.getOrNull(position) ?: return row

        row.setTextViewText(R.id.todo_widget_row_text, item.text)
        // Done lines are dimmed rather than struck through: a RemoteViews can set the paint flags,
        // but only by replacing all of them, which changes how the text is rasterised.
        row.setTextColor(
            R.id.todo_widget_row_text,
            context.getColor(
                if (item.done) R.color.widget_on_surface_variant else R.color.widget_on_surface
            ),
        )
        // The box is a vector of the widget's own colours: an `?attr` tint would resolve to nothing
        // in the launcher and leave it in a fixed palette of its own.
        row.setImageViewResource(
            R.id.todo_widget_row_check,
            if (item.done) R.drawable.ic_widget_check_done else R.drawable.ic_widget_check_open,
        )
        row.setContentDescription(
            R.id.todo_widget_row_check,
            context.getString(
                if (item.done) {
                    R.string.m3e_todo_widget_mark_not_done
                } else {
                    R.string.m3e_todo_widget_mark_done
                }
            ),
        )
        // The item's id goes with the tap. It rides on the row's fill-in intent, which is merged into
        // the collection's pending intent template on its way to the provider.
        row.setOnClickFillInIntent(
            R.id.todo_widget_row_root,
            Intent().putExtra(TodoWidgetProvider.EXTRA_ITEM_ID, item.id),
        )
        return row
    }

    override fun getLoadingView(): RemoteViews? = null

    override fun getViewTypeCount(): Int = 1

    override fun getItemId(position: Int): Long =
        items.getOrNull(position)?.id?.hashCode()?.toLong() ?: 0L

    override fun hasStableIds(): Boolean = true
}
