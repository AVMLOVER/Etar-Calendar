/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The countdown ("倒数日") home screen widgets.
 *
 * Three sizes, each with its own provider so the launcher lists them separately:
 *
 *   2x2  one countdown, the number large
 *   4x2  a short list of up to three
 *   4x4  a longer list of up to eight
 *
 * Rows are built with RemoteViews.addView rather than by binding fixed ids in a layout. A
 * RemoteViews cannot address "the third row" of a layout: every view with the same id is updated
 * together, so a layout with eight rows sharing one set of ids would print the last countdown eight
 * times. Adding a freshly inflated row per entry gives each its own ids.
 */
package com.android.calendar.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.android.calendar.countdown.Countdown
import com.android.calendar.countdown.CountdownState
import com.android.calendar.countdown.CountdownStore
import com.android.calendar.countdown.CountdownUnit
import com.android.calendar.countdown.compose.ComposeCountdownActivity
import com.android.calendar.countdown.compose.KEY_COUNT_TODAY
import com.android.calendar.countdown.compose.KEY_DECIMALS
import com.android.calendar.countdown.compose.KEY_UNIT
import com.android.calendar.settings.compose.SettingsRepository
import ws.xsoh.etar.R
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Shared behaviour: read the chosen countdowns, build the rows, wire the taps. */
abstract class CountdownWidgetProvider : AppWidgetProvider() {

    /** How many rows this size can show - also read by the configuration screen. */
    abstract val slots: Int

    /** The widget's own layout. */
    protected abstract val layoutId: Int

    /** The row layout, inflated once per countdown. */
    protected abstract val rowLayoutId: Int

    /** True for the sizes that carry a header with an add button. */
    protected open val hasHeader: Boolean = false

    /** True for the one-up layout, which prints the number large. */
    protected open val oneUp: Boolean = false

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
        // Drop the stored selection for a widget that no longer exists.
        appWidgetIds.forEach { CountdownWidgetConfig.clear(context, it) }
    }

    fun build(context: Context, widgetId: Int): RemoteViews {
        val views = RemoteViews(context.packageName, layoutId)
        val repo = SettingsRepository(context)
        val unit = CountdownUnit.fromKey(repo.string(KEY_UNIT, CountdownUnit.DEFAULT.name))
        val decimals = repo.bool(KEY_DECIMALS, false)
        val countToday = repo.bool(KEY_COUNT_TODAY, false)

        val zone = ZoneId.systemDefault()
        val now = System.currentTimeMillis()

        val all = CountdownStore.all(context)
        val byId = all.associateBy { it.id }
        val chosen = CountdownWidgetConfig.chosenIds(context, widgetId)
            .mapNotNull { byId[it] }
            // Nothing configured yet, so a freshly dropped widget shows the first few rather than an
            // empty frame.
            .ifEmpty { all.take(slots) }
            .take(slots)

        val open = PendingIntent.getActivity(
            context,
            widgetId,
            Intent(context, ComposeCountdownActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        views.setOnClickPendingIntent(R.id.countdown_widget_root, open)
        if (hasHeader) {
            // The add button in the header opens the screen, which is where adding happens.
            views.setOnClickPendingIntent(R.id.countdown_widget_add, open)
        }

        // Rebuilt from scratch on every update, so a row that is no longer chosen disappears.
        views.removeAllViews(R.id.countdown_widget_list)

        if (chosen.isEmpty()) {
            views.setViewVisibility(R.id.countdown_widget_empty, View.VISIBLE)
        } else {
            views.setViewVisibility(R.id.countdown_widget_empty, View.GONE)
            chosen.forEach { countdown ->
                val state = CountdownStore.describe(
                    countdown = countdown,
                    zone = zone,
                    now = now,
                    unit = unit,
                    allowDecimals = decimals,
                    countToday = countToday,
                )
                views.addView(
                    R.id.countdown_widget_list,
                    buildRow(context, widgetId, countdown, state),
                )
            }
        }
        return views
    }

    private fun buildRow(
        context: Context,
        widgetId: Int,
        countdown: Countdown,
        state: CountdownState,
    ): RemoteViews {
        val row = RemoteViews(context.packageName, rowLayoutId)
        val (bmpWidth, bmpHeight) = bitmapSizeFor(context, widgetId)
        val radiusDp = if (oneUp) 20f else 14f
        val density = context.resources.displayMetrics.density

        // What sits behind the text.
        //
        // In the list sizes nothing does: those carry the app's own surface colour on the frame, and
        // the countdown's colour and picture are deliberately left out of them - a column of rows each
        // filled with a different colour, or a different photograph, was unreadable. The 2x2 is the
        // one that shows a countdown on its own colour.
        val picture: android.graphics.Bitmap? = if (oneUp) {
            loadBackgroundBitmap(context, countdown, bmpWidth, bmpHeight)
        } else {
            null
        }
        val onColor = if (picture != null && countdown.transparentBackground) {
            // Text over a photograph needs to be legible against anything, so it carries its own
            // shadow rather than a colour. White with a shadow reads on both a bright and a dark
            // picture.
            0xFFFFFFFF.toInt()
        } else if (picture != null) {
            // The colour is laid over the picture, so the same rule as the flat fill applies.
            if (isLight(countdown.color)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
        } else {
            if (isLight(countdown.color)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
        }

        // The list sizes leave the fill transparent so the frame's Material surface shows through; the
        // 2x2 fills with the countdown's colour unless it has a picture.
        val fill = picture ?: if (oneUp) {
            CountdownWidgetBitmap.roundedFill(
                color = countdown.color,
                widthPx = bmpWidth,
                heightPx = bmpHeight,
                radiusDp = radiusDp,
                density = density,
            )
        } else {
            CountdownWidgetBitmap.roundedFill(
                color = android.graphics.Color.TRANSPARENT,
                widthPx = bmpWidth,
                heightPx = bmpHeight,
                radiusDp = radiusDp,
                density = density,
            )
        }
        val layered = if (picture != null && !countdown.transparentBackground) {
            // Picture first, colour over it, corners rounded on the way out.
            CountdownWidgetBitmap.roundedTintOver(
                picture = picture,
                color = countdown.color,
                alpha = 0.72f,
                radiusDp = radiusDp,
                density = density,
            )
        } else {
            fill
        }
        row.setImageViewBitmap(R.id.countdown_row_fill, layered)

        val nameId = if (oneUp) R.id.countdown_row_name_heading else R.id.countdown_row_name
        row.setTextViewText(
            nameId,
            if (oneUp) {
                context.getString(
                    if (state.passed) R.string.m3e_countdown_small_ago
                    else R.string.m3e_countdown_small_left,
                    countdown.name,
                )
            } else {
                // "距 <name>", which is the wording the list uses.
                context.getString(R.string.m3e_countdown_widget_distance, countdown.name)
            },
        )
        row.setTextViewText(R.id.countdown_row_value, state.value)
        row.setTextViewText(R.id.countdown_row_unit, unitText(context, state.unit))
        row.setTextViewText(R.id.countdown_row_date, dateWithWeekday(context, countdown))

        if (!oneUp) {
            row.setTextViewText(
                R.id.countdown_row_heading,
                context.getString(
                    if (state.passed) R.string.m3e_countdown_passed
                    else R.string.m3e_countdown_remaining,
                ),
            )
            row.setTextColor(R.id.countdown_row_heading, onColor)
        }

        listOfNotNull(
            nameId,
            R.id.countdown_row_value,
            R.id.countdown_row_unit,
            R.id.countdown_row_date,
        ).forEach { row.setTextColor(it, onColor) }

        return row
    }

    /**
     * A pixel size for the fill bitmap, in the proportions of the row it will be shown in.
     *
     * The launcher knows the widget's real size and publishes it through the widget options, so the
     * bitmap is drawn to match rather than guessed at. The fallbacks are the sizes the widget was
     * declared with.
     */
    /**
     * The countdown's background picture, decoded and scaled towards the space it will be drawn in.
     *
     * Decoded here rather than handed over as a uri: RemoteViews cannot carry a persisted permission
     * grant to the launcher process, so the launcher has no access of its own. Null when there is no
     * picture, or when it has gone - a widget must not fail to draw over a missing file.
     */
    private fun loadBackgroundBitmap(
        context: Context,
        countdown: Countdown,
        widthPx: Int,
        heightPx: Int,
    ): android.graphics.Bitmap? {
        val uriString = countdown.backgroundImageUri ?: return null
        return try {
            val uri = android.net.Uri.parse(uriString)
            val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                android.graphics.BitmapFactory.decodeStream(stream, null, bounds)
            }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                return null
            }
            // Downsampled towards the widget's size: a camera image is tens of megapixels while this
            // is a few hundred pixels across, and the whole bitmap has to fit through the binder.
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= widthPx &&
                bounds.outHeight / (sample * 2) >= heightPx
            ) {
                sample *= 2
            }
            val options = android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                android.graphics.BitmapFactory.decodeStream(stream, null, options)
            }
        } catch (t: Throwable) {
            null
        }
    }
    private fun bitmapSizeFor(context: Context, widgetId: Int): Pair<Int, Int> {
        val options = AppWidgetManager.getInstance(context).getAppWidgetOptions(widgetId)
        val maxWidth = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 0) ?: 0
        val maxHeight = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0) ?: 0
        val density = context.resources.displayMetrics.density

        val widthDp = if (maxWidth > 0) maxWidth.toFloat() else if (oneUp) 110f else 250f
        // The 2x2 row fills the widget; a list row is one line of a column, so it is only as tall as
        // its own content.
        val heightDp = if (oneUp) {
            if (maxHeight > 0) maxHeight.toFloat() else 110f
        } else {
            64f
        }
        return Pair(
            (widthDp * density).toInt().coerceIn(1, 4096),
            (heightDp * density).toInt().coerceIn(1, 4096),
        )
    }

    private fun unitText(context: Context, unit: CountdownUnit): String = context.getString(        when (unit) {
            CountdownUnit.MINUTES -> R.string.m3e_countdown_unit_minutes
            CountdownUnit.HOURS -> R.string.m3e_countdown_unit_hours
            CountdownUnit.DAYS -> R.string.m3e_countdown_unit_days
            CountdownUnit.MONTHS -> R.string.m3e_countdown_unit_months
            CountdownUnit.YEARS -> R.string.m3e_countdown_unit_years
        }
    )

    /**
     * The date with its weekday, which is what the small print at the bottom shows.
     *
     * The pattern is built from resources so a translated build can phrase the date its own way;
     * the default is "9月30日 星期三".
     */
    private fun dateWithWeekday(context: Context, countdown: Countdown): String =
        countdown.date.format(
            DateTimeFormatter.ofPattern(
                context.getString(R.string.m3e_countdown_widget_date_pattern),
                Locale.getDefault(),
            )
        )

    private fun isLight(color: Int): Boolean {
        val r = (color shr 16) and 0xFF
        val g = (color shr 8) and 0xFF
        val b = color and 0xFF
        return (0.299 * r + 0.587 * g + 0.114 * b) > 160
    }
}

/** 2x2: one countdown. Name, the number large, then the date and weekday. */
class CountdownSmallWidgetProvider : CountdownWidgetProvider() {
    override val slots = 1
    override val layoutId = R.layout.countdown_widget_small
    override val rowLayoutId = R.layout.countdown_widget_row_small
    override val hasHeader = false
    override val oneUp = true
}

/** 4x2: a header with an add button, then up to three rows. */
class CountdownMediumWidgetProvider : CountdownWidgetProvider() {
    override val slots = 2
    override val layoutId = R.layout.countdown_widget_list
    override val rowLayoutId = R.layout.countdown_widget_row
    override val hasHeader = true
}

/** 4x4: the same table, with eight rows. */
class CountdownLargeWidgetProvider : CountdownWidgetProvider() {
    override val slots = 4
    override val layoutId = R.layout.countdown_widget_list
    override val rowLayoutId = R.layout.countdown_widget_row
    override val hasHeader = true
}

/** Which countdowns each widget instance was configured with. */
object CountdownWidgetConfig {

    private const val PREFS = "m3e_countdown_widgets"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun chosenIds(context: Context, widgetId: Int): List<String> =
        prefs(context).getString(key(widgetId), null)
            ?.split('\u0000')
            ?.filter { it.isNotEmpty() }
            ?: emptyList()

    fun setChosenIds(context: Context, widgetId: Int, ids: List<String>) {
        prefs(context).edit().putString(key(widgetId), ids.joinToString("\u0000")).apply()
    }

    fun clear(context: Context, widgetId: Int) {
        prefs(context).edit().remove(key(widgetId)).apply()
    }

    private fun key(widgetId: Int) = "widget_$widgetId"
}
