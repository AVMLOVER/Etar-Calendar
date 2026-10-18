/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * Draws the month widget's event bars.
 *
 * A colour handed to RemoteViews - setBackgroundColor - is painted as a plain rectangle: it ignores
 * the corners of a background drawable, and a RemoteViews cannot give a view a rounded shape. The
 * rounded ends therefore have to be part of an image, exactly as they are in the countdown widgets.
 */
package com.android.calendar.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import java.util.HashMap

object MonthWidgetBitmap {

    /** Cached per colour: a widget rebuilds every cell on every update, and this is pure drawing. */
    private val cache = HashMap<Int, Bitmap>()

    /**
     * A bar in [color]: three dp tall with rounded ends, stretched to the cell's width by the
     * ImageView's fitXY. The stretch distorts the ends by a pixel or two, which at this height is not
     * visible, and it saves measuring the cell from inside the provider.
     */
    fun bar(color: Int, density: Float): Bitmap {
        cache[color]?.let { if (!it.isRecycled) return it }

        val height = (3f * density).toInt().coerceAtLeast(2)
        val width = (40f * density).toInt().coerceAtLeast(height * 4)
        val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
        val radius = height / 2f
        canvas.drawRoundRect(
            RectF(0f, 0f, width.toFloat(), height.toFloat()),
            radius,
            radius,
            paint,
        )

        if (cache.size > 32) {
            cache.clear()
        }
        cache[color] = out
        return out
    }
}
