/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * Month picker hosted in a dialog under the toolbar.
 *
 * Two things had to be got right for this to work at all:
 *
 * 1. It is a Dialog, not a PopupWindow. A PopupWindow's decor view is built by the window manager
 *    and carries no ViewTreeLifecycleOwner.
 * 2. The activity's lifecycle owners are installed on every view of the dialog's own window before
 *    the ComposeView attaches. ComponentActivity installs the owners on *its* decor view only; a
 *    dialog window it does not build is a separate tree, so Compose's lookup up from the ComposeView
 *    reaches the dialog's root without finding one and throws
 *    "ViewTreeLifecycleOwner not found from ComposeView".
 */
package com.android.calendar.chrome.compose

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.android.calendar.settings.compose.EtarM3ETheme
import com.android.calendar.settings.compose.SettingsRepository
import java.time.LocalDate

object M3eMonthPickerDialog {

    private const val KEY_THEME = "pref_theme"

    /**
     * Shows the picker just under [anchor], spanning the screen width.
     *
     * @param onPick called with the chosen year and 1-based month; the dialog closes itself first.
     * @return the dialog, so the caller can dismiss it if it needs to.
     */
    fun show(
        context: Context,
        anchor: View,
        onPick: java.util.function.BiConsumer<Int, Int>,
    ): Dialog {
        val repo = SettingsRepository(context)
        val themeMode = repo.string(KEY_THEME, "system")
        val dark = when (themeMode) {
            "light" -> false
            "dark", "black" -> true
            else -> (context.resources.configuration.uiMode and
                    android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                    android.content.res.Configuration.UI_MODE_NIGHT_YES
        }
        

        val today = LocalDate.now()

        val dialog = Dialog(context)
        // No title bar; the Compose header names what the picker does.
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)

        // The reference has to exist before the content can close it; a one element holder keeps
        // that legal without a nullable cast.
        val holder = arrayOfNulls<Dialog>(1)

        val content = ComposeView(context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                EtarM3ETheme(darkTheme = dark) {
                    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
                        Column {
                            M3eMonthPickerHeader(day = null)
                            M3eMonthYearPicker(
                                year = today.year,
                                month = today.monthValue,
                                onPick = { year, month ->
                                    holder[0]?.dismiss()
                                    onPick.accept(year, month)
                                },
                            )
                        }
                    }
                }
            }
        }

        dialog.setContentView(content)
        holder[0] = dialog

        // Install the owners on the dialog's whole view tree before it is shown, and therefore before
        // the ComposeView is attached to a window.
        val owner = context as? LifecycleOwner
        val savedState = context as? SavedStateRegistryOwner
        val viewModelStore = context as? ViewModelStoreOwner
        dialog.window?.decorView?.let { decor ->
            installOwners(decor, owner, savedState, viewModelStore)
        }

        dialog.show()

        dialog.window?.let { window ->
            window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            // Positioned after show(), because the window does not exist until then.
            window.setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
            window.setGravity(Gravity.TOP)
            val location = IntArray(2)
            anchor.getLocationOnScreen(location)
            window.attributes = window.attributes.apply {
                y = location[1] + anchor.height
            }
            // setContentView may have built part of the tree after the decor was walked, so make sure
            // the content view itself carries them too.
            installOwners(window.decorView, owner, savedState, viewModelStore)
        }
        return dialog
    }

    /**
     * Publishes the host's lifecycle owners onto [view] and everything below it.
     *
     * Compose looks these up by walking up from the ComposeView, so having them on every node means
     * the walk succeeds whatever the shape of the window's tree.
     */
    private fun installOwners(
        view: View,
        owner: LifecycleOwner?,
        savedState: SavedStateRegistryOwner?,
        viewModelStore: ViewModelStoreOwner?,
    ) {
        if (owner != null) {
            view.setViewTreeLifecycleOwner(owner)
        }
        if (savedState != null) {
            view.setViewTreeSavedStateRegistryOwner(savedState)
        }
        if (viewModelStore != null) {
            view.setViewTreeViewModelStoreOwner(viewModelStore)
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                installOwners(view.getChildAt(i), owner, savedState, viewModelStore)
            }
        }
    }
}
