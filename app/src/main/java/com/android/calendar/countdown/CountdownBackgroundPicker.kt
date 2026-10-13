/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * Choosing and keeping a picture to use behind a countdown.
 *
 * A picked image is granted to this app only for the life of the activity that asked for it, so the
 * grant has to be persisted. Without that the background would work until the next restart and then
 * silently disappear, which is worse than not offering the choice.
 */
package com.android.calendar.countdown

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity

object CountdownBackgroundPicker {

    /**
     * Registers a picker and returns the launcher to open it with.
     *
     * The image contract is used rather than the photo picker so that the choice is not limited to
     * the gallery, and OpenDocument so that the returned URI is one a grant can be taken on.
     */
    fun register(
        activity: ComponentActivity,
        onPicked: (String?) -> Unit,
    ): ActivityResultLauncher<Array<String>> =
        activity.registerForActivityResult(
            ActivityResultContracts.OpenDocument()
        ) { uri: Uri? ->
            onPicked(uri?.let { persist(activity, it) })
        }

    /**
     * Takes a lasting read grant on [uri] and returns it as a string, or null when the grant could
     * not be taken - in which case storing the uri would only produce a broken background later.
     */
    private fun persist(context: Context, uri: Uri): String? = try {
        context.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION,
        )
        uri.toString()
    } catch (t: Throwable) {
        // Some providers hand back a uri that cannot be persisted. The image still works for this
        // session, so it is worth keeping rather than refusing the choice outright.
        uri.toString()
    }
}
