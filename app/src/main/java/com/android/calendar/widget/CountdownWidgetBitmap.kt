/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * Draws the countdown widgets' rounded colour fills.
 *
 * A colour handed to RemoteViews - setBackgroundColor - is painted as a plain rectangle: it ignores
 * the corners of a background drawable, and RemoteViews cannot give it a rounded shape. The corners
 * therefore have to be part of an image, which is what these produce.
 */
package com.android.calendar.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF

object CountdownWidgetBitmap {

    /** Cached per colour and size: a widget redraws on every update, and this is pure arithmetic. */
    private val cache = HashMap<String, Bitmap>()

    /**
     * A rounded copy of [picture] with [color] laid over it at [alpha].
     *
     * The countdown's colour over its own picture, which is the non-transparent presentation. The
     * corners are cut here rather than by the ImageView, because RemoteViews has no clipping.
     */
    fun roundedTintOver(
        picture: Bitmap,
        color: Int,
        alpha: Float,
        radiusDp: Float,
        density: Float,
    ): Bitmap {
        val w = picture.width.coerceAtLeast(1)
        val h = picture.height.coerceAtLeast(1)
        val key = "tint:$color:$alpha:$w:$h:$radiusDp:${picture.hashCode()}"
        cache[key]?.let { if (!it.isRecycled) return it }

        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val radius = (radiusDp * density).coerceAtMost(minOf(w, h) / 2f)
        val rect = RectF(0f, 0f, w.toFloat(), h.toFloat())
        val path = android.graphics.Path().apply {
            addRoundRect(rect, radius, radius, android.graphics.Path.Direction.CW)
        }
        canvas.clipPath(path)
        // Centre-crop, so a picture of any proportions fills the card without being squashed.
        val scale = maxOf(w.toFloat() / picture.width, h.toFloat() / picture.height)
        val drawW = picture.width * scale
        val drawH = picture.height * scale
        canvas.drawBitmap(
            picture,
            null,
            RectF((w - drawW) / 2f, (h - drawH) / 2f, (w + drawW) / 2f, (h + drawH) / 2f),
            Paint(Paint.FILTER_BITMAP_FLAG),
        )
        canvas.drawColor(
            android.graphics.Color.argb(
                (alpha.coerceIn(0f, 1f) * 255).toInt(),
                android.graphics.Color.red(color),
                android.graphics.Color.green(color),
                android.graphics.Color.blue(color),
            ),
            android.graphics.PorterDuff.Mode.SRC_OVER,
        )

        if (cache.size > 32) {
            cache.clear()
        }
        cache[key] = out
        return out
    }

    /**
     * A rounded rectangle of [color], [widthPx] by [heightPx] pixels, with [radiusDp] corners.
     *
     * Drawn with anti-aliasing, which is why it cannot simply be a shape drawable: the corners have
     * to look round at whatever size the launcher hands the widget.
     *
     * The caller must pass a width and height in the same proportion as the view, because the
     * ImageView scales the bitmap to fit and a mismatched aspect ratio would squash the corners into
     * ellipses.
     */
    fun roundedFill(
        color: Int,
        widthPx: Int,
        heightPx: Int,
        radiusDp: Float,
        density: Float,
    ): Bitmap {
        val w = widthPx.coerceAtLeast(1)
        val h = heightPx.coerceAtLeast(1)
        val key = "$color:$w:$h:$radiusDp"
        cache[key]?.let { if (!it.isRecycled) return it }

        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
        // Inset by half a pixel so the anti-aliased edge is not clipped by the bitmap bounds.
        val inset = 0.5f
        val rect = RectF(inset, inset, w - inset, h - inset)
        // Never more than half the shorter side, which would otherwise make the two radii overlap and
        // the shape degenerate into a lozenge.
        val radius = (radiusDp * density).coerceAtMost(minOf(w, h) / 2f)
        canvas.drawRoundRect(rect, radius, radius, paint)

        // The cache is bounded by hand: colours are few and sizes change only on resize.
        if (cache.size > 32) {
            cache.clear()
        }
        cache[key] = bitmap
        return bitmap
    }
}
