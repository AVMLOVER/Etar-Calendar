/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The window treatment every Compose screen uses: edge-to-edge, transparent bars, tonal backdrop.
 *
 * M3eHostActivity got this through applyTheme(): the app's night mode was applied, the content was
 * laid out edge to edge, the system bar icons were given the appearance the app's theme needs, and
 * the window background was pinned to the tonal surface. The screens that are plain
 * ComponentActivities (the settings screen, the event editor and the details screen, the calendars
 * manager, quick responses, the countdown widget config, the dialog activities) only called
 * enableEdgeToEdge(), which:
 *   - reads the *system* night mode for its bar-icon appearance, not the app's stored theme, and
 *   - leaves the window background as the theme's android:colorBackground.
 * On MIUI/HyperOS that combination made the status bar paint solid black on exactly those screens:
 * the bar is transparent in the window flags, but the window behind it is black in the Black theme,
 * so the OEM's own bar layer has black to show. The host screens were unaffected because their
 * window background is the tonal surface.
 *
 * applyM3eWindow() applies the same four pieces to any ComponentActivity. The transparent dialog
 * screens call it with paintWindowBackground = false: their window must stay transparent, or the
 * dialog would sit on a full-screen surface.
 */
package com.android.calendar.theme

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.graphics.toArgb
import androidx.core.view.WindowInsetsControllerCompat
import com.android.calendar.theme.model.Theme
import com.google.android.material.color.MaterialColors
import com.google.android.material.R as MaterialR
import androidx.appcompat.R as AppCompatR
import androidx.compose.ui.graphics.Color as ComposeColor

/**
 * True when the app's stored theme choice draws a dark appearance.
 *
 * The stored choice, not the system's night mode: the app can be in its Dark or Black appearance
 * while the system is light, and the status bar icons have to follow the drawn screen.
 */
fun isDarkAppearance(context: android.content.Context): Boolean = when (ThemeUtils.getTheme(context)) {
    Theme.LIGHT -> false
    Theme.DARK, Theme.BLACK -> true
    Theme.SYSTEM -> isSystemInDarkTheme(context)
}

/**
 * The tonal surface behind the system bars, as ARGB, or null when the palette has no surface role.
 *
 * Scrimmed against pure black by [tonalSurface], so the Black theme's #000000 window cannot be what
 * an OEM's status bar layer shows through.
 */
fun tonalSurfaceArgb(context: android.content.Context, dark: Boolean): Int? {
    val surface = MaterialColors.getColor(context, MaterialR.attr.colorSurface, Color.TRANSPARENT)
    if (surface == Color.TRANSPARENT) {
        return null
    }
    val accent = MaterialColors.getColor(context, AppCompatR.attr.colorPrimary, surface)
    return tonalSurface(ComposeColor(surface), ComposeColor(accent), dark).toArgb()
}

/**
 * Applies the app's window treatment to this screen.
 *
 * @param paintWindowBackground true for full-screen screens, which paint the tonal surface behind
 *   their content. The transparent dialog screens pass false so their window stays see-through.
 */
fun ComponentActivity.applyM3eWindow(paintWindowBackground: Boolean = true) {
    val dark = isDarkAppearance(this)

    // The bars' icon appearance and the window background depend on the app's night mode, so it is
    // applied before the window is configured - the same order applyTheme() uses on the host.
    applyApplicationNightMode(this, ThemeUtils.getTheme(this).name.lowercase())

    val clear = Color.TRANSPARENT
    val barStyle = if (dark) {
        SystemBarStyle.dark(clear)
    } else {
        SystemBarStyle.light(clear, clear)
    }
    enableEdgeToEdge(statusBarStyle = barStyle, navigationBarStyle = barStyle)

    // The colours are set outright as well as through the style, and the contrast scrims are
    // switched off: MIUI/HyperOS paints its own bar colour when it is not told the bars are
    // transparent, which is what produced the black status bar on these screens.
    @Suppress("DEPRECATION")
    run {
        window.statusBarColor = clear
        window.navigationBarColor = clear
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        window.isStatusBarContrastEnforced = false
        window.isNavigationBarContrastEnforced = false
    }

    // The icon appearance follows the app's theme, not the system's night mode.
    WindowInsetsControllerCompat(window, window.decorView).apply {
        isAppearanceLightStatusBars = !dark
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            isAppearanceLightNavigationBars = !dark
        }
    }

    if (paintWindowBackground) {
        tonalSurfaceArgb(this, dark)?.let { window.setBackgroundDrawable(ColorDrawable(it)) }
    }
}
