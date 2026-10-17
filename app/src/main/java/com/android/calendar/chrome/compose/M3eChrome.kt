/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * Content slot for the standalone Material 3 screens.
 *
 * The drawer and top bar used to be drawn here in Compose. They now come from M3eHostActivity,
 * which uses the same DrawerLayout / NavigationView / Toolbar the calendar itself uses, so all
 * this does is render the screen's content in the host's content slot.
 */
package com.android.calendar.chrome.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Which calendar screen a host activity represents; kept for call sites that name one. */
enum class M3eScreen {
    DAY,
    WEEK,
    MONTH,
    YEAR,
    AGENDA,
    DATE_CALCULATOR,
    CALENDARS,
    SETTINGS,
    ABOUT,
}

/**
 * Renders [content] inside the host's content slot.
 *
 * [selected] and [title] are retained so each screen keeps declaring its identity and heading, but
 * the hosting activity is what actually displays them.
 *
 * The system bar insets are deliberately NOT applied here. Every screen hosted by M3eHostActivity
 * already sits under a view hierarchy that carries them, so padding here was a second copy: on the
 * screens whose first element is a TopAppBar the extra strip was invisible behind the bar, but on
 * the ones that start straight into content - the three day grid, the date calculator - it showed as
 * a gap between the toolbar and the first row. Screens hosted by a plain ComponentActivity apply
 * [m3eSystemBarPadding] themselves.
 */
@Composable
@Suppress("UNUSED_PARAMETER")
fun M3eChromeScaffold(
    selected: M3eScreen?,
    title: @Composable () -> Unit,
    content: @Composable (PaddingValues) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        content(PaddingValues(0.dp))
    }
}

/**
 * Keeps content clear of the status bar and the display cutout, and only those.
 *
 * For screens whose host is a plain ComponentActivity and which therefore have no view hierarchy
 * insetting them. The navigation bar is deliberately left out: the app is edge to edge, so content
 * is meant to run behind the gesture bar rather than stop short of it. Padding the bottom here is
 * what made the settings screen - and only that screen, because it is the one whose first element is
 * a TopAppBar - look like the bar was not immersive. Scrolling content that wants its last row above
 * the gesture bar adds that inset itself.
 */
@Composable
fun Modifier.m3eSystemBarPadding(): Modifier =
    this.windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Top))
        .windowInsetsPadding(WindowInsets.displayCutout)

/**
 * Stops a Scaffold from turning the navigation bar inset into content padding.
 *
 * A Scaffold reads the window insets and hands them to its content as PaddingValues, bottom
 * included. On a screen that is meant to be edge to edge that is wrong on two counts: the content is
 * pushed clear of the gesture bar, which is what made settings and the event editor look like the
 * bar was not immersive, and the scroll container's own contentPadding is then added on top of it.
 *
 * Consuming the bottom inset inside this box means the top inset still reaches the Scaffold - the app
 * bar must stay below the status bar - while the bottom one is already accounted for.
 */
@Composable
fun M3eEdgeToEdgeContent(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .consumeWindowInsets(WindowInsets.navigationBars)
    ) {
        content()
    }
}
