/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The remembered hour spacing of the day, three day and week grids.
 *
 * The pinch zoom is a per-session adjustment the user expects to survive leaving the screen and, in
 * practice, the app - Google Calendar keeps it the same way. Stored as dp per hour and clamped against
 * the viewport when it is used, so a value stored on one window size still makes sense on another.
 */
package com.android.calendar.composeui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

object HourZoomPref {

    private const val FILE = "m3e_hour_zoom"
    private const val KEY = "hour_height_dp"

    /**
     * Bumped by [reset].
     *
     * The grids remember the height they loaded when they opened, so writing the default to the
     * preference alone would not change a grid that is already alive underneath the settings screen.
     * Reading this value in a grid's composition makes it re-read the preference when it changes.
     */
    private var generation by mutableIntStateOf(0)

    /** The current reset generation; read it in composition to observe [reset]. */
    val resetGeneration: Int
        get() = generation

    /** The stored hour height, or today's default when the user has never pinched. */
    fun load(context: Context): Float =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .getFloat(KEY, DEFAULT_HOUR_HEIGHT.value)

    /** Remembers [hourHeight] for the next time a grid opens. */
    fun save(context: Context, hourHeight: Float) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit()
            .putFloat(KEY, hourHeight)
            .apply()
    }

    /** Puts the spacing back to the default for every grid, including ones already composed. */
    fun reset(context: Context) {
        save(context, DEFAULT_HOUR_HEIGHT.value)
        generation++
    }
}
