/*
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.calendar.settings.compose

import android.content.Context
import com.android.calendar.settings.GeneralPreferences

/**
 * Thin Compose-friendly facade over the app's SharedPreferences.
 *
 * It uses [GeneralPreferences.getSharedPreferences], i.e. exactly the same store (and the same
 * keys) as the classic PreferenceFragmentCompat screens, so settings written by either UI are
 * immediately visible to the other.
 */
class SettingsRepository(context: Context) {

    private val prefs = GeneralPreferences.getSharedPreferences(context)

    fun bool(key: String, defaultValue: Boolean): Boolean = prefs.getBoolean(key, defaultValue)

    fun string(key: String, defaultValue: String): String =
        prefs.getString(key, defaultValue) ?: defaultValue

    fun setBool(key: String, value: Boolean) {
        prefs.edit().putBoolean(key, value).apply()
    }

    fun setString(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }
}
