/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * What a countdown is drawn on: its colour, or a picture the user chose.
 */
package com.android.calendar.chrome.compose

import android.graphics.BitmapFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Draws [color] behind some content, or [backgroundImageUri] when there is one.
 *
 * A picture is never shown at full strength: the countdown's text sits on top of it, and the text is
 * white or black based on the colour alone. A scrim of the countdown's own colour is laid over the
 * image, which keeps the pairing readable whatever the picture happens to be - and ties the result
 * back to the colour the user chose.
 */
@Composable
fun CountdownBackdrop(
    color: Int,
    backgroundImageUri: String?,
    cornerRadius: Dp,
    modifier: Modifier = Modifier,
    /**
     * True to show the picture bare, with only the text drawn over it. False lays the countdown's
     * colour over the picture instead, which is what a busy photograph needs to stay readable.
     */
    transparent: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val image: ImageBitmap? = remember(backgroundImageUri) {
        backgroundImageUri?.let { loadBitmap(context, it) }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(Color(color)),
    ) {
        if (image != null) {
            androidx.compose.foundation.Image(
                bitmap = image,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                // The picture is decorative and its exact rendering does not matter, so the cheaper
                // filter is the right one.
                filterQuality = FilterQuality.Low,
                modifier = Modifier.fillMaxSize(),
            )
            if (!transparent) {
                // The colour laid over the picture. Heavy enough that white text stays legible on a
                // bright photograph, and it ties the result back to the colour that was chosen.
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(color).copy(alpha = 0.72f))
                )
            }
        }
        Box(modifier = Modifier.padding(0.dp)) { content() }
    }
}

/**
 * Decodes a picked image, downsampled.
 *
 * A modern camera image is tens of megapixels and this is a card a few hundred dp across, so the
 * bounds are read first and the decode is scaled to roughly the size it will be shown at.
 */
private fun loadBitmap(context: android.content.Context, uriString: String): ImageBitmap? = try {
    val uri = android.net.Uri.parse(uriString)
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, bounds)
    }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
        null
    } else {
        var sample = 1
        while (bounds.outWidth / sample > 1080 || bounds.outHeight / sample > 1080) {
            sample *= 2
        }
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, options)?.asImageBitmap()
        }
    }
} catch (t: Throwable) {
    // A picture that has gone missing - the user deleted it, or the grant was lost - must not take
    // the screen down. The countdown falls back to its colour.
    null
}
