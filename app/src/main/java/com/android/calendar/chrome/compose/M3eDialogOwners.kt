/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * Makes a Dialog's view tree usable by Compose.
 *
 * A dialog is its own window and carries none of the ViewTree owners Compose looks for, so a
 * ComposeView placed inside one fails with "ViewTreeLifecycleOwner not found". Installing the host's
 * owners onto the dialog's whole tree fixes that, and has to happen both before and after show():
 * the decor view's children are not all present until the window is laid out.
 */
package com.android.calendar.chrome.compose

import android.app.Dialog
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

object M3eDialogOwners {

    /**
     * Copies the host's owners onto [dialog]'s view tree.
     *
     * Safe to call more than once, and worth calling again after show() - see the note above.
     */
    @JvmStatic
    fun install(host: android.content.Context, dialog: Dialog) {
        val lifecycleOwner = host as? LifecycleOwner ?: return
        val savedState = host as? SavedStateRegistryOwner ?: return
        val viewModelStore = host as? ViewModelStoreOwner ?: return
        val decor = dialog.window?.decorView ?: return
        walk(decor, lifecycleOwner, savedState, viewModelStore)
    }

    private fun walk(
        view: android.view.View,
        owner: LifecycleOwner,
        savedState: SavedStateRegistryOwner,
        viewModelStore: ViewModelStoreOwner,
    ) {
        view.setViewTreeLifecycleOwner(owner)
        view.setViewTreeSavedStateRegistryOwner(savedState)
        view.setViewTreeViewModelStoreOwner(viewModelStore)
        if (view is android.view.ViewGroup) {
            for (i in 0 until view.childCount) {
                walk(view.getChildAt(i), owner, savedState, viewModelStore)
            }
        }
    }
}
