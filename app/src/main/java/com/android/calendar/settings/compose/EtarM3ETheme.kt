/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * Material 3 Expressive theme wrapper for the Compose-based settings screen.
 */
package com.android.calendar.settings.compose

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.android.calendar.theme.tonalContainers
import com.android.calendar.theme.tonalSurface

/**
 * Material 3 Expressive shapes. Material 3 Expressive leans on visibly larger corner radii than
 * the original Material 3 spec, which is what gives cards, dialogs and buttons their softer look.
 */
val EtarExpressiveShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp),
)

/**
 * The application theme for Compose surfaces.
 *
 * Colour comes from the wallpaper ("monet") through [dynamicLightColorScheme] /
 * [dynamicDarkColorScheme] on Android 12+, and from the Material 3 baseline palette otherwise.
 * Motion comes from [MotionScheme.expressive], which is the Material 3 Expressive motion model
 * (spatial springs for size/position changes, effects springs for colour/alpha).
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun EtarM3ETheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current

    val base: ColorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)

        darkTheme -> darkColorScheme()
        else -> lightColorScheme()
    }

    // The caller passes the palette it wants: DynamicColors already installs pure black for the
    // BLACK theme, so there is nothing to darken here. The pureBlack parameter this used to take was
    // a second way of saying the same thing, and the two could disagree.
    //
    // That is exactly why the surface is checked rather than trusted: on the Black appearance the
    // palette can resolve the neutral roles to #000, and then every drawn screen - the grids, the
    // cards, the app bar - is one flat black rectangle with no tonal step between them. A black
    // surface is replaced with the Material 3 baseline surface for this appearance, tinted with the
    // palette's primary, so the darkest theme still draws in tonal steps. A palette that supplied a
    // real surface is left untouched. See TonalSurface.kt.
    val surface = tonalSurface(base.surface, base.primary, darkTheme)
    val colorScheme = if (surface == base.surface) {
        base
    } else {
        val steps = tonalContainers(surface, base.onSurface)
        base.copy(
            surface = surface,
            background = surface,
            surfaceContainerLowest = steps[0],
            surfaceContainerLow = steps[1],
            surfaceContainer = steps[2],
            surfaceContainerHigh = steps[3],
            surfaceContainerHighest = steps[4],
        )
    }

    MaterialTheme(
        colorScheme = colorScheme,
        motionScheme = MotionScheme.expressive(),
        shapes = EtarExpressiveShapes,
        typography = Typography(),
        content = content,
    )
}

/** True when the platform can supply a wallpaper-derived palette (Android 12+). */
val isMonetSupported: Boolean
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
