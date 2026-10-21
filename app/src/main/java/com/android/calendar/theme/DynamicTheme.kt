package com.android.calendar.theme

import android.app.Activity
import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.util.TypedValue
import android.view.View
import android.view.ViewTreeObserver
import androidx.activity.enableEdgeToEdge
import androidx.annotation.AttrRes
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.ui.graphics.toArgb
import com.android.calendar.Utils
import com.android.calendar.theme.ThemeUtils.getSuffix
import com.android.calendar.theme.model.Theme
import com.google.android.material.color.MaterialColors
import ws.xsoh.etar.R
import com.google.android.material.R as MaterialR
import androidx.appcompat.R as AppCompatR

private const val TEAL: String = "teal"
private const val MONET: String = "monet"

/**
 * Tells the platform which night mode the app should be in.
 *
 * [applyTheme] calls this when an activity is created. The Material 3 settings screen calls it
 * again when the user picks a different mode: without that second call the activities already
 * living behind the settings screen keep their old colours until the app is restarted.
 */
fun applyApplicationNightMode(context: Context, themeMode: String) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
        uiModeManager.setApplicationNightMode(
            when (themeMode) {
                "light" -> UiModeManager.MODE_NIGHT_NO
                "dark", "black" -> UiModeManager.MODE_NIGHT_YES
                else -> UiModeManager.MODE_NIGHT_CUSTOM
            }
        )
    } else {
        AppCompatDelegate.setDefaultNightMode(
            when (themeMode) {
                "light" -> AppCompatDelegate.MODE_NIGHT_NO
                "dark", "black" -> AppCompatDelegate.MODE_NIGHT_YES
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
        )
    }
}

fun AppCompatActivity.applyTheme() {
    val selectedTheme = ThemeUtils.getTheme(this)

    val uiModeManager = getSystemService(Context.UI_MODE_SERVICE) as UiModeManager

    // Handle black theme/mode
    //
    // Pure black is the BLACK theme, and nothing else.
    //
    // There used to be a separate switch that also darkened DARK and a dark SYSTEM, which made those
    // indistinguishable from BLACK: choosing "Dark" gave black, and the ordinary dark palette could
    // not be reached at all. Two controls for one outcome also meant the two could disagree. The
    // theme choice now says it once: BLACK is pure black, DARK is the ordinary dark palette, and
    // SYSTEM follows the system.
    val wantPureBlack = selectedTheme == Theme.BLACK

    // The window's own colour, set outright rather than left to ?android:colorBackground.
    //
    // Relying on the attribute means the difference between Dark and Black rests on the theme
    // resolving two different values for it, and whether it does is not visible from here. Naming the
    // colour makes the choice unambiguous: Black is #000000 and Dark is the app's own dark surface,
    // rather than whatever a parent theme happens to supply for either.
    //
    // Only in a dark appearance, though - Light and a light System must keep the light background, so
    // this cannot be a plain else.
    val darkAppearance = when (selectedTheme) {
        Theme.LIGHT -> false
        Theme.DARK, Theme.BLACK -> true
        Theme.SYSTEM -> isSystemInDarkTheme(this)
    }
    if (darkAppearance) {
        theme.applyStyle(
            if (wantPureBlack) R.style.colorBackgroundBlack else R.style.colorBackgroundDark,
            true,
        )
    }

    // Set selected theme mode to the app. The stored value is the enum name lowercased, which is
    // what ThemeUtils.getTheme() parses back. This has to happen before the system bars are
    // configured: it is what decides whether the app is in its light or its dark appearance.
    applyApplicationNightMode(this, selectedTheme.name.lowercase())

    // Setup edge to edge
    // Edge to edge on every supported version, so the content runs behind the system bars. On
    // Android 11+ the bars themselves can be transparent; below that the platform draws an opaque
    // scrim over whatever the app puts behind them, which is the correct look for those versions.
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        setupEdgeToEdge(this)
    } else {
        enableEdgeToEdge()
    }

    setSystemBarConfiguration(light = !isSystemInDarkTheme(this))
    pinWindowBackground(dark = darkAppearance)
}

/**
 * Makes the window background the app's own tonal surface.
 *
 * With the content laid out edge to edge, the window background is what shows in the strips the
 * inset padding leaves free - the status bar strip at the top and the gesture strip at the bottom.
 * Leaving it to the theme means the strip is whatever `?android:attr/colorBackground` happens to
 * resolve to after the monet palette is applied, and the app bar and the system bars then disagree
 * with the rest of the screen. Pinning it to a single opaque colour keeps the top strip, the app
 * bar and the bottom strip on the same colour as the screens that paint their own background.
 *
 * The colour is the surface role, sanitised by [tonalSurface]: the Black theme's own background is
 * #000000, and on devices whose dynamic palette reports a black surface the role is black as well -
 * the whole window was then a flat black rectangle with no tonal step under the grid. A tonal
 * surface is painted instead; a palette that supplied one is used unchanged.
 *
 * It is re-applied once the first frame is about to be drawn: that is after
 * com.google.android.material.color.DynamicColors has installed the palette on this Activity's
 * theme, so the colour picked up here is the monet one rather than the static fallback.
 */
private fun AppCompatActivity.pinWindowBackground(dark: Boolean) {
    fun apply() {
        val surface = MaterialColors.getColor(this, MaterialR.attr.colorSurface, Color.TRANSPARENT)
        val accent = MaterialColors.getColor(this, AppCompatR.attr.colorPrimary, surface)
        // No black fallback: when the surface role cannot be resolved, the theme's own window
        // background is left in place rather than painted over in a colour no role names.
        if (surface != Color.TRANSPARENT) {
            val tonal = tonalSurface(
                androidx.compose.ui.graphics.Color(surface),
                androidx.compose.ui.graphics.Color(accent),
                dark,
            )
            window.setBackgroundDrawable(ColorDrawable(tonal.toArgb()))
        }
    }

    apply()

    // One re-application before the first frame. DynamicColors installs the monet palette from a
    // pre-draw callback of its own, so this is the first point at which the attribute resolves to
    // the wallpaper-derived colour.
    var reapplied = false
    window.decorView.viewTreeObserver.addOnPreDrawListener(
        object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                if (!reapplied) {
                    reapplied = true
                    apply()
                }
                return true
            }
        }
    )
}

private fun AppCompatActivity.setSystemBarConfiguration(light: Boolean) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
        // Below Android 11 the bars are still opaque bars the window paints. Give them the window
        // background so they match the app. From Android 11 on the bars are transparent and there is
        // nothing to configure: whatever the app draws behind them is what shows.
        val barColor =
            MaterialColors.getColor(this, android.R.attr.windowBackground, Color.BLACK)
        if (Build.VERSION.SDK_INT in Build.VERSION_CODES.O..Build.VERSION_CODES.Q) {
            window.navigationBarColor = barColor
        }
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        // The app paints its own background behind the system bars, so the platform must not
        // overpaint them with its own contrast colour.
        window.isStatusBarContrastEnforced = false
        window.isNavigationBarContrastEnforced = false
    }

    WindowInsetsControllerCompat(this.window, this.window.decorView.rootView).apply {
        isAppearanceLightStatusBars = light
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            isAppearanceLightNavigationBars = light
        }
    }
}

/**
 * Wraps the colour the current theme resolves [attr] to in a drawable, or null when the attribute
 * is not a plain colour (a bitmap or a material drawable, for instance).
 */
private fun Context.resolveThemeColorDrawable(@AttrRes attr: Int): ColorDrawable? {
    val value = TypedValue()
    if (!theme.resolveAttribute(attr, value, true)) {
        return null
    }
    return when {
        value.type >= TypedValue.TYPE_FIRST_COLOR_INT &&
            value.type <= TypedValue.TYPE_LAST_COLOR_INT -> ColorDrawable(value.data)

        value.resourceId != 0 -> runCatching { ColorDrawable(getColor(value.resourceId)) }.getOrNull()
        else -> null
    }
}

private fun Context.getStyledAttributeColor(id: Int): Int {
    val arr = obtainStyledAttributes(TypedValue().data, intArrayOf(id))
    val styledAttr = arr.getColor(0, Color.WHITE)
    arr.recycle()
    return styledAttr
}

fun getDialogStyle(context: Context): Int {
    val theme = ThemeUtils.getTheme(context)
    when (theme) {
        Theme.SYSTEM -> if (isSystemInDarkTheme(context)) {
            return android.R.style.Theme_DeviceDefault_Dialog
        } else {
            return android.R.style.Theme_DeviceDefault_Light_Dialog
        }

        Theme.LIGHT -> return android.R.style.Theme_DeviceDefault_Light_Dialog
        Theme.DARK, Theme.BLACK -> return android.R.style.Theme_DeviceDefault_Dialog
        else -> throw UnsupportedOperationException("Unknown theme: " + theme)
    }
}

fun getWidgetBackgroundStyle(context: Context): Int {
    // Pure black comes from the theme alone, the same way it does for the app's own background. The
    // switch this used to consult is gone, so SYSTEM no longer has to choose between two night
    // backgrounds - it takes the ordinary dark one, and BLACK is the only route to black.
    return when (ThemeUtils.getTheme(context)) {
        Theme.SYSTEM -> if (isSystemInDarkTheme(context)) {
            R.color.bg_dark
        } else {
            R.color.background_color
        }

        Theme.LIGHT -> R.color.background_color
        Theme.DARK -> R.color.bg_dark
        Theme.BLACK -> R.color.bg_black
    }
}

fun getPrimaryColor(context: Context?): String {
    if (Utils.isMonetAvailable(context)) {
        return MONET
    } else {
        return TEAL
    }
}

fun getColorId(name: String): Int {
    return when (name) {
        TEAL -> R.color.colorPrimary
        MONET -> android.R.color.system_accent1_500
        else -> throw java.lang.UnsupportedOperationException("Unknown color name : " + name)
    }
}

fun getColor(context: Context, id: String?): Int {
    val legacy = getLegacyColor(context, id)
    val role = if (id == null) 0 else m3ColorRoleFor(id)
    // Resolving the Material 3 role means the Canvas-drawn month grid, week/day grid, agenda,
    // mini month and calendar picker all take their colours from the wallpaper ("monet").
    // MaterialColors falls back to the legacy value when the attribute cannot be resolved.
    return if (role != 0) MaterialColors.getColor(context, role, legacy) else legacy
}

/**
 * Maps the legacy colour names used by the Canvas-drawn views onto Material 3 colour roles.
 *
 * Only surfaces, separators, day numbers, "today" accents and the primary event accent are
 * mapped: the colours that are painted *on top of* per-calendar event colours (for example
 * calendar_event_text_color) are deliberately left alone so that contrast is preserved.
 */
private fun m3ColorRoleFor(name: String): Int = when (name) {
    // Surfaces. The cells of the neighbouring months share the day cell's colour: the month grid is
    // one continuous surface, and tinting the leading and trailing cells made whole blocks of the
    // grid change colour as it scrolled. Those days are still told apart from the current month by
    // their day-number colour (month_day_number_other below).
    "month_bgcolor",
    "month_focus_month_bgcolor",
    "month_other_bgcolor" -> MaterialR.attr.colorSurface

    "calendar_hour_background",
    "calendar_future_bg_color" -> MaterialR.attr.colorSurfaceContainerLow

    "month_selected_week_bgcolor",
    "calendar_grid_area_selected",
    "day_clicked_background_color",
    "day_event_clicked_background_color" -> MaterialR.attr.colorSecondaryContainer

    "month_today_bgcolor" -> MaterialR.attr.colorPrimaryContainer
    "today_highlight_color" -> AppCompatR.attr.colorPrimary

    // Separators
    "month_grid_lines",
    "calendar_grid_line_inner_horizontal_color",
    "calendar_grid_line_inner_vertical_color" -> MaterialR.attr.colorOutlineVariant

    // Text
    "month_day_number",
    "month_mini_day_number",
    "agenda_item_standard_color",
    "calendar_date_banner_text_color",
    "calendar_hour_label" -> MaterialR.attr.colorOnSurface

    "month_day_number_other",
    "month_other_month_day_number",
    "month_day_names_color",
    "month_week_num_color",
    "agenda_item_declined_color",
    "agenda_item_where_text_color",
    "agenda_item_where_declined_text_color" -> MaterialR.attr.colorOnSurfaceVariant

    "month_today_number" -> MaterialR.attr.colorOnPrimaryContainer
    "week_today" -> AppCompatR.attr.colorPrimary

    // Weekend accents
    "month_saturday", "week_saturday",
    "month_sunday", "week_sunday" -> MaterialR.attr.colorTertiary

    // Events / calendars
    "month_event_color",
    "month_event_extra_color",
    "calendar_visible",
    "calendar_secondary_visible" -> AppCompatR.attr.colorPrimary

    "month_event_other_color",
    "month_event_extra_other_color",
    "calendar_hidden",
    "calendar_secondary_hidden" -> MaterialR.attr.colorOnSurfaceVariant

    "mini_month_today_outline_color" -> AppCompatR.attr.colorPrimary

    else -> 0
}

/** The original theme-suffix based resource lookup, kept as the fallback path. */
private fun getLegacyColor(context: Context, id: String?): Int {
    val suffix = getSuffix(context)
    val res = context.resources
    // When aapt is called with --rename-manifest-package, the package name is changed for the
    // application, but not for the resources. This is to find the package name of a known
    // resource to know what package to lookup the colors in.
    val packageName = res.getResourcePackageName(R.string.app_label)
    return res.getColor(res.getIdentifier(id + suffix, "color", packageName))
}

fun getDrawableId(context: Context, id: String?): Int {
    val suffix = getSuffix(context)
    val res = context.resources
    // When aapt is called with --rename-manifest-package, the package name is changed for the
    // application, but not for the resources. This is to find the package name of a known
    // resource to know what package to lookup the drawables in.
    val packageName = res.getResourcePackageName(R.string.app_label)
    return res.getIdentifier(id + suffix, "drawable", packageName)
}

fun isSystemInDarkTheme(context: Context): Boolean {
    return (context.resources
        .configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
}

/**
 * Lays the window out edge to edge and insets the content by the system bars.
 *
 * Android has enforced edge-to-edge for apps targeting API 35+ since Android 15: the window covers
 * the whole screen and the bars float on top of it. What the app still has to do is keep its own
 * content out from under the bars, and that is what the insets here are for.
 *
 * Top and sides become real padding, because the app bar has to start below the status bar. The
 * navigation bar inset is deliberately *not* turned into padding: padding there would lift the whole
 * screen by the height of the gesture bar and leave the strip under the gesture handle unpainted,
 * which is the "not immersive" look. Without that padding the app's own background - the month grid,
 * the event list, the year list - runs the full height and continues behind the gesture bar, and the
 * window background only ever shows through on the screens that leave it transparent.
 */
private fun setupEdgeToEdge(activity: Activity) {
    val window = activity.window

    WindowCompat.setDecorFitsSystemWindows(window, false)

    val content = window.findViewById<View>(android.R.id.content) ?: return
    ViewCompat.setOnApplyWindowInsetsListener(content) { v: View, windowInsets: WindowInsetsCompat ->
        val bars = windowInsets.getInsets(
            WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
        )
        v.setPadding(bars.left, bars.top, bars.right, 0)
        windowInsets
    }
    ViewCompat.requestApplyInsets(content)
}
