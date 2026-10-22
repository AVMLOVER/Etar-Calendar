/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * Compose painters for drawables painterResource cannot load.
 *
 * painterResource only accepts vector and raster resources. A <layer-list> - which is what the
 * drawer's today icon is, because its day number is a second layer - throws
 * "Only VectorDrawables and rasterized asset types are supported" at runtime. The chooser showed
 * exactly that crash. This helper loads any drawable through AppCompatResources, optionally stamps
 * today's day number into it the way the drawer and the toolbar do, and rasterises the result into a
 * BitmapPainter once per icon.
 *
 * The rasterisation is once, in a remember keyed on the resource: the icon is static for the life of
 * the composition, so there is no work on later frames.
 */
package com.android.calendar.chrome.compose

import android.graphics.drawable.LayerDrawable
import androidx.annotation.DrawableRes
import androidx.appcompat.content.res.AppCompatResources
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.core.graphics.drawable.toBitmap
import com.android.calendar.Utils
import kotlin.math.roundToInt

/**
 * Loads [resId] into a Compose painter, including layer-list drawables.
 *
 * @param todayIcon when true and the drawable is a layer list, today's day number is drawn into its
 *   day layer with [Utils.setTodayIcon] - the same call the drawer and the toolbar make, so the icon
 *   in the sheet shows the date the drawer's does.
 */
@Composable
fun rememberDrawablePainter(
    @DrawableRes resId: Int,
    todayIcon: Boolean = false,
): Painter {
    val context = LocalContext.current
    val density = LocalDensity.current
    return remember(resId, todayIcon) {
        val drawable = checkNotNull(AppCompatResources.getDrawable(context, resId)) {
            "Missing drawable 0x${Integer.toHexString(resId)}"
        }
        // A drawable from resources shares its constant state; mutating the layers in place would
        // stamp this screen's day number onto every other use of the same icon.
        val mutated = drawable.mutate()
        if (todayIcon) {
            (mutated as? LayerDrawable)?.let {
                Utils.setTodayIcon(it, context, Utils.getTimeZone(context, null))
            }
        }
        val fallback = (24 * density.density).roundToInt()
        val width = mutated.intrinsicWidth.takeIf { it > 0 } ?: fallback
        val height = mutated.intrinsicHeight.takeIf { it > 0 } ?: fallback
        BitmapPainter(mutated.toBitmap(width, height).asImageBitmap())
    }
}
