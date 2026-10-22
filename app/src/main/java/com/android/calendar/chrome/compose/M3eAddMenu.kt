/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The floating add menu: the three add actions as pills above the add button.
 *
 * This replaces the bottom sheet. A sheet dimmed the calendar and slid over it, while the add button
 * is a corner control: the Google pattern is a small floating menu at that corner, with the button
 * itself turning into the close control. Nothing else on screen moves, the calendar stays visible.
 *
 * The open menu is a full-window Dialog: its window is above the activity, so one scrim can cover the
 * whole window - the native app bar included - with the pills and the cross button drawn above it.
 * Back is handled by the dialog's own dismissal, so no activity is started and no screen navigates.
 *
 * The pills are laid out above the button in a bottom-end aligned column, so they grow upward from
 * the button and remain on screen in landscape. When the menu is closed nothing is left behind and the
 * screen is fully usable again.
 */
package com.android.calendar.chrome.compose

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.scaleOut
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.android.calendar.composeui.AddFabSpec
import ws.xsoh.etar.R

/** Longest exit: the slowest pill's 180ms plus its 80ms stagger. */
private const val MENU_EXIT_MILLIS = 260

/** One action in the floating add menu. */
class M3eAddMenuEntry(
    val painter: Painter,
    val label: String,
    val onClick: () -> Unit,
)

/**
 * The three add actions, with the icons and labels the chooser always had.
 *
 * The to-do icon is the drawer's today icon - a layer list, so rememberDrawablePainter loads it and
 * stamps today's day number in, because painterResource cannot load a layer list.
 */
@Composable
fun rememberAddMenuEntries(
    onAddEvent: () -> Unit,
    onAddTodo: () -> Unit,
    onAddCountdown: () -> Unit,
): List<M3eAddMenuEntry> = listOf(
    M3eAddMenuEntry(
        painter = painterResource(R.drawable.outline_calendar_view_month),
        label = stringResource(R.string.m3e_add_event),
        onClick = onAddEvent,
    ),
    M3eAddMenuEntry(
        painter = rememberDrawablePainter(R.drawable.today_icon_24, todayIcon = true),
        label = stringResource(R.string.m3e_add_todo),
        onClick = onAddTodo,
    ),
    M3eAddMenuEntry(
        painter = painterResource(R.drawable.outline_countdown),
        label = stringResource(R.string.m3e_add_countdown),
        onClick = onAddCountdown,
    ),
)

/**
 * The add button and, while [expanded], its three actions floating above it, over a modal scrim.
 *
 * The menu is a full-window Dialog rather than a layer inside the screen's own Box: the app bar is a
 * native sibling of the Compose content, so a scrim drawn inside that content can only ever cover the
 * grid, never the bar. The Dialog window is above the whole activity, which is what lets one scrim
 * cover the entire window - bar included - with the pills and the cross button painted above it.
 *
 * @param expanded whether the menu is open; the screen owns the flag so back and the button agree.
 * @param onToggle opens the menu, or closes it from the button's cross state.
 */
@Composable
fun M3eAddFabMenu(
    expanded: Boolean,
    onToggle: () -> Unit,
    entries: List<M3eAddMenuEntry>,
    modifier: Modifier = Modifier,
) {
    // Back while the menu is open closes the menu and nothing else: it is part of this screen, so
    // the screen must not navigate or finish for it. The handler only exists while it is open.
    BackHandler(enabled = expanded, onBack = onToggle)

    // The dialog lives until the exit has played out. `if (expanded) Dialog` disposed it the moment
    // dismissal began, so nothing could animate out; now it stays mounted while open and for the
    // exit's duration after closing. A re-open during that window cancels the pending unmount and
    // reverses the running animations from wherever they are.
    var dialogMounted by remember { mutableStateOf(expanded) }
    LaunchedEffect(expanded) {
        if (expanded) {
            dialogMounted = true
        } else if (dialogMounted) {
            kotlinx.coroutines.delay(MENU_EXIT_MILLIS.toLong())
            dialogMounted = false
        }
    }

    /** Dismiss once: a tap that arrives while the exit is already running is ignored. */
    fun requestDismiss() {
        if (expanded) {
            onToggle()
        }
    }

    // One rotation, shared by the button in the screen and the copy in the dialog: the two are the
    // same control seen through two windows, so both have to show the same angle at every instant.
    // Keeping it here also means a fast open/close cannot leave the glyph at a stale angle, because
    // the animation is never re-created when the dialog comes and goes.
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 45f else 0f,
        animationSpec = tween(durationMillis = 200),
        label = "add-button-rotation",
    )

    Box(modifier = modifier.fillMaxSize()) {
        // The button is always here, even while the dialog is open. The dialog's copy sits exactly on
        // top of it at the same coordinates, so the container never disappears or fades: it is one
        // continuous element, and only its glyph turns.
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            horizontalAlignment = Alignment.End,
        ) {
            M3eAddFab(
                onClick = onToggle,
                contentDescription = stringResource(
                    if (expanded) R.string.m3e_add_menu_close else R.string.m3e_add_chooser_title,
                ),
                iconRotation = rotation,
            )
        }
    }

    if (dialogMounted) {
        Dialog(
            onDismissRequest = ::requestDismiss,
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnClickOutside = false,
                decorFitsSystemWindows = false,
            ),
        ) {
            // The dialog window's own dim and its window animation are both switched off. The scrim
            // below is ours, at the Material modal alpha; a platform dim would darken the screen
            // twice, and the theme's window animation is what made the button appear to fade and
            // scale in as a second copy of itself. All motion is the composition's own, so opening
            // and closing are the same animation run in opposite directions.
            val dialogWindow =
                (androidx.compose.ui.platform.LocalView.current.parent as?
                    androidx.compose.ui.window.DialogWindowProvider)?.window
            androidx.compose.runtime.SideEffect {
                dialogWindow?.setDimAmount(0f)
                dialogWindow?.setWindowAnimations(0)
            }

            // The scrim fades with the menu. It is its own Animatable because the dialog mounts when
            // the menu is already open: an animateFloatAsState would start at its target and skip the
            // fade-in. Both directions use the exit's 260ms so the two finish together.
            val scrimAlpha = remember { androidx.compose.animation.core.Animatable(0f) }
            LaunchedEffect(expanded) {
                scrimAlpha.animateTo(
                    targetValue = if (expanded) AddFabSpec.SCRIM_ALPHA else 0f,
                    animationSpec = tween(durationMillis = MENU_EXIT_MILLIS),
                )
            }

            Box(modifier = Modifier.fillMaxSize()) {
                // The scrim is the first child, so everything the menu draws after it is painted on
                // top. It takes the taps itself: a tap that dismisses the menu can never reach a day
                // cell, an event, the pager or the button underneath, and it covers the app bar too
                // because this window is above the activity. Material's scrim role at the modal alpha.
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            MaterialTheme.colorScheme.scrim.copy(alpha = scrimAlpha.value),
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = ::requestDismiss,
                        ),
                )

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    entries.forEachIndexed { index, entry ->
                        // Each pill has its own transition state so the three can stagger independently
                        // while the dialog lifetime is governed by the single timer above. A pill is
                        // composed while it is visible or animating in either direction, so its exit
                        // really runs; the exit reverses the stagger and uses the entrance durations.
                        val pillState = remember { MutableTransitionState(false) }
                        LaunchedEffect(expanded) { pillState.targetState = expanded }
                        if (pillState.currentState || pillState.targetState) {
                            AnimatedVisibility(
                                visibleState = pillState,
                                enter = fadeIn(
                                    tween(durationMillis = 140, delayMillis = index * 40),
                                ) +
                                    scaleIn(
                                        animationSpec = tween(
                                            durationMillis = 180,
                                            delayMillis = index * 40,
                                        ),
                                        initialScale = 0.6f,
                                        transformOrigin = TransformOrigin(1f, 1f),
                                    ) +
                                    slideInVertically(
                                        animationSpec = tween(
                                            durationMillis = 180,
                                            delayMillis = index * 40,
                                        ),
                                        initialOffsetY = { height -> height / 2 },
                                    ),
                                exit = fadeOut(
                                    tween(
                                        durationMillis = 140,
                                        delayMillis = (entries.lastIndex - index) * 40,
                                    ),
                                ) +
                                    scaleOut(
                                        animationSpec = tween(
                                            durationMillis = 180,
                                            delayMillis = (entries.lastIndex - index) * 40,
                                        ),
                                        targetScale = 0.6f,
                                        transformOrigin = TransformOrigin(1f, 1f),
                                    ) +
                                    slideOutVertically(
                                        animationSpec = tween(
                                            durationMillis = 180,
                                            delayMillis = (entries.lastIndex - index) * 40,
                                        ),
                                        targetOffsetY = { height -> height / 2 },
                                    ),
                            ) {
                                AddPill(entry)
                            }
                        }
                    }

                    // The same button, at the same bottom-end position and inset as the one in the
                    // screen, sharing its rotation. The dialog's copy is what is above the scrim and
                    // therefore tappable; because it is identical and coincident, the hand-over is
                    // invisible.
                    M3eAddFab(
                        onClick = ::requestDismiss,
                        contentDescription = stringResource(R.string.m3e_add_menu_close),
                        iconRotation = rotation,
                    )
                }
            }
        }
    }
}

/** One action as a tonal pill: icon and label on a `secondaryContainer` capsule. */
@Composable
private fun AddPill(entry: M3eAddMenuEntry) {
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .clickable(onClick = entry.onClick)
            .padding(start = 16.dp, end = 20.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = entry.painter,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = entry.label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}
