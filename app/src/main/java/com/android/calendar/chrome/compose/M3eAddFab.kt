/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The add button every screen uses: a 64dp rounded square, one step above the standard FAB.
 *
 * It is a plain Surface with a click modifier rather than a FloatingActionButton: the container has to
 * be a rounded square of an exact size and the icon has to be able to rotate (the add menu's cross),
 * and Material's FAB sizes do not offer that combination. The size, shape and icon size come from
 * AddFabSpec so every screen and the menu's own button cannot drift apart.
 */
package com.android.calendar.chrome.compose

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.android.calendar.composeui.AddFabSpec
import ws.xsoh.etar.R

/**
 * The add button.
 *
 * @param iconRotation rotates the glyph, which is how the menu turns the plus into a cross without
 *   replacing the button or moving it.
 */
@Composable
fun M3eAddFab(
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    iconRotation: Float = 0f,
    painter: Painter = painterResource(R.drawable.ic_add),
) {
    Surface(
        modifier = modifier
            .size(AddFabSpec.size)
            .clip(AddFabSpec.shape)
            .clickable(onClick = onClick),
        shape = AddFabSpec.shape,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shadowElevation = 6.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                painter = painter,
                contentDescription = contentDescription,
                modifier = Modifier
                    .size(AddFabSpec.iconSize)
                    .graphicsLayer { rotationZ = iconRotation },
            )
        }
    }
}
