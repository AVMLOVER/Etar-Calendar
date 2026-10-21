/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * What a drawn screen paints its background with, and what to do when that comes out black.
 *
 * The theme's surface role is wallpaper-derived ("monet") on Android 12+ and the Material 3 baseline
 * otherwise. On the Black appearance the platform's neutral palette can resolve to pure black - the
 * Compose Material3 palette falls back to @android:color/black when the dynamic attributes are not
 * available - and then every drawn surface is #000: the grids, the app bar and the window all read as
 * one flat black rectangle. The Black theme is still meant to be the darkest appearance, but a screen
 * drawn entirely in black loses its grid lines, its hour bands and the step between the background
 * and the cards on it.
 *
 * So the surface role is sanitised: a surface that would read as black is replaced with the Material
 * 3 baseline surface for the appearance, blended with a little of the palette's primary so it keeps
 * the wallpaper's tint instead of turning into a neutral grey. Everything else - accents, text,
 * containers, outlines - stays as the palette supplied it.
 *
 * Pure functions because they are colour arithmetic, and because the "is it black" threshold and the
 * tint strength are worth pinning in a test rather than only seeing on a screenshot.
 */
package com.android.calendar.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance

/**
 * The Material 3 baseline dark surface (neutral tone 6): the very dark grey the grids paint when the
 * palette has no usable dark surface. Not #000 - the point is that it is a tonal step.
 */
val BASELINE_DARK_SURFACE = Color(0xFF141218)

/** The Material 3 baseline light surface (neutral tone 98). */
val BASELINE_LIGHT_SURFACE = Color(0xFFFFFBFE)

/**
 * Below this luminance a colour reads as black on a phone screen.
 *
 * The baseline dark surface has a luminance of about 0.006; a surface the palette means as "dark"
 * sits around 0.006-0.02, while #000 is 0. A threshold of 0.002 therefore separates black from every
 * tonal dark surface the palette produces, without treating an unusually dark palette as broken.
 */
private const val BLACK_SURFACE_LUMINANCE = 0.002f

/** How much of the palette's primary is blended into the fallback surface, per appearance. */
private const val DARK_TINT = 0.08f
private const val LIGHT_TINT = 0.04f

/** True when [surface] is the pure black that makes a drawn screen look flat. */
fun isBlackSurface(surface: Color): Boolean = surface.luminance() <= BLACK_SURFACE_LUMINANCE

/**
 * The surface role a drawn screen should paint.
 *
 * A surface the palette resolved to a tonal colour is kept exactly as it is. A black one is replaced
 * with the baseline surface for [dark], tinted with [accent] so it still belongs to the palette's
 * colour family rather than being a neutral grey.
 *
 * @param surface the surface role the theme resolved.
 * @param accent the palette's primary, used only as a tint.
 * @param dark true for the dark and black appearances.
 */
fun tonalSurface(surface: Color, accent: Color, dark: Boolean): Color {
    if (!isBlackSurface(surface)) {
        return surface
    }
    val baseline = if (dark) BASELINE_DARK_SURFACE else BASELINE_LIGHT_SURFACE
    return lerp(baseline, accent, if (dark) DARK_TINT else LIGHT_TINT)
}

/**
 * The container steps above [surface], for the cards and highlighted rows that sit on it.
 *
 * Same ordering as Material 3: lowest is the surface itself, each step is a little further towards
 * the content colour. Used only when the palette's own containers came out black; a palette that
 * supplied tonal containers keeps them.
 */
fun tonalContainers(surface: Color, content: Color): List<Color> = listOf(
    surface,
    lerp(surface, content, 0.04f),
    lerp(surface, content, 0.06f),
    lerp(surface, content, 0.09f),
    lerp(surface, content, 0.12f),
)
