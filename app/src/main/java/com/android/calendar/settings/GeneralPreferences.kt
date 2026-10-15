/*
 * Copyright (C) 2020 Dominik Schürmann <dominik@schuermann.eu>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.android.calendar.settings

import android.annotation.TargetApi
import android.app.backup.BackupManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Vibrator
import android.provider.CalendarContract
import android.provider.CalendarContract.CalendarCache
import android.provider.SearchRecentSuggestions
import android.provider.Settings
import android.text.TextUtils
import java.text.DateFormatSymbols
import java.util.Calendar
import java.util.Locale
import android.util.SparseIntArray
import android.widget.Toast
import androidx.preference.CheckBoxPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import androidx.preference.SwitchPreference
import com.android.calendar.CalendarController
import com.android.calendar.CalendarRecentSuggestionsProvider
import com.android.calendar.CalendarUtils
import com.android.calendar.Utils
import com.android.calendar.alerts.AlertReceiver
import com.android.calendar.event.EventViewUtils
import com.android.calendar.theme.isSystemInDarkTheme
import com.android.calendar.timezonepicker.TimeZoneInfo
import com.android.calendar.timezonepicker.TimeZonePickerDialog
import com.android.calendar.timezonepicker.TimeZonePickerUtils
import ws.xsoh.etar.R
import java.util.TimeZone

/**
 * Preference storage for the settings the calendar reads at runtime.
 *
 * This used to be a PreferenceFragmentCompat screen as well; the Material 3 settings screen
 * replaced that UI, so only the keys, the defaults and the SharedPreferences accessor remain -
 * they are referenced from all over the app (Utils alone uses them in dozens of places).
 *
 * setDefaultValues still reads res/xml/general_preferences.xml, which is kept purely as the
 * declaration of the default values.
 */
class GeneralPreferences private constructor() {
    companion object {
        // Preference keys
        const val KEY_THEME_PREF = "pref_theme"
        const val KEY_REAL_EVENT_COLORS = "pref_real_event_colors"
        /** Show buttons to switch between the day, week and month views in the toolbar */
        const val KEY_VIEW_SWITCH_BUTTONS = "pref_view_switch_buttons"
        const val KEY_DO_NOT_CHECK_BATTERY_OPTIMIZATION = "pref_do_not_check_battery_optimization"
        const val KEY_DEFAULT_START = "preferences_default_start"
        const val KEY_HIDE_DECLINED = "preferences_hide_declined"
        const val KEY_STAGGERED_DISPLAY = "preferences_staggered_display"
        const val KEY_WEEK_START_DAY = "preferences_week_start_day"
        const val KEY_SHOW_WEEK_NUM = "preferences_show_week_num"
        const val KEY_DAYS_PER_WEEK = "preferences_days_per_week"
        const val KEY_MDAYS_PER_WEEK = "preferences_mdays_per_week"
        const val KEY_CLEAR_SEARCH_HISTORY = "preferences_clear_search_history"
        const val KEY_ALERTS_CATEGORY = "preferences_alerts_category"
        const val KEY_ALERTS = "preferences_alerts"
        const val KEY_NOTIFICATION = "preferences_notification"
        const val KEY_ALERTS_VIBRATE = "preferences_alerts_vibrate"
        const val KEY_ALERTS_RINGTONE = "preferences_alerts_ringtone"
        const val KEY_ALERTS_POPUP = "preferences_alerts_popup"
        const val KEY_SHOW_CONTROLS = "preferences_show_controls"
        const val KEY_DEFAULT_REMINDER = "preferences_default_reminder"
        const val NO_REMINDER = -2147483648
        const val NO_REMINDER_STRING = "-2147483648"
        const val REMINDER_DEFAULT_TIME = 10 // in minutes
        const val KEY_USE_CUSTOM_SNOOZE_DELAY = "preferences_custom_snooze_delay"
        const val KEY_DEFAULT_SNOOZE_DELAY = "preferences_default_snooze_delay"
        const val SNOOZE_DELAY_DEFAULT_TIME = 5 // in minutes
        const val KEY_DEFAULT_CELL_HEIGHT = "preferences_default_cell_height"
        const val KEY_VERSION = "preferences_version"
        /** Key to SharePreference for default view (CalendarController.ViewType)  */
        const val KEY_START_VIEW = "preferred_startView"
        /**
         * Key to SharePreference for default detail view (CalendarController.ViewType)
         * Typically used by widget
         */
        const val KEY_DETAILED_VIEW = "preferred_detailedView"
        const val KEY_DEFAULT_CALENDAR = "preference_defaultCalendar"

        /** Key to preference for default new event duration (if provider doesn't indicate one)  */
        const val KEY_DEFAULT_EVENT_DURATION = "preferences_default_event_duration"
        const val EVENT_DURATION_DEFAULT = "60"

        // These must be in sync with the array preferences_week_start_day_values
        const val WEEK_START_DEFAULT = "-1"
        const val WEEK_START_SUNDAY = "1"
        const val WEEK_START_MONDAY = "2"
        const val WEEK_START_TUESDAY = "3"
        const val WEEK_START_WEDNESDAY = "4"
        const val WEEK_START_THURSDAY = "5"
        const val WEEK_START_FRIDAY = "6"
        const val WEEK_START_SATURDAY = "7"
        // Default preference values
        const val DEFAULT_DEFAULT_START = "-2"

        /**
         * The view the app opens on, before the user has chosen one.
         *
         * The month, which is the view everything else is reached from - and the one the back
         * gesture treats as home, so a fresh install steps out of the app from where it started
         * rather than from the day grid.
         */
        const val DEFAULT_START_VIEW = CalendarController.ViewType.MONTH

        /**
         * Set once the user has chosen a start view in the settings.
         *
         * Until then the app must not write one: it used to store whatever view was on screen every
         * time it was paused, so "the default" was really "the last view looked at" - which is how a
         * fresh install ended up opening on the day view. With this flag the stored value only
         * changes when the user asks for it to.
         */
        const val KEY_START_VIEW_CHOSEN = "preferred_startView_chosen"

        const val DEFAULT_DETAILED_VIEW = CalendarController.ViewType.DAY
        const val DEFAULT_SHOW_WEEK_NUM = false
        // This should match the XML file.
        const val DEFAULT_RINGTONE = "content://settings/system/notification_sound"
        // The name of the shared preferences file. This name must be maintained for historical
        // reasons, as it's what PreferenceManager assigned the first time the file was created.
        const val SHARED_PREFS_NAME = "com.android.calendar_preferences"
        const val SHARED_PREFS_NAME_NO_BACKUP = "com.android.calendar_preferences_no_backup"
        private const val KEY_HOME_TZ_ENABLED = "preferences_home_tz_enabled"
        private const val KEY_HOME_TZ = "preferences_home_tz"
        private const val FRAG_TAG_TIME_ZONE_PICKER = "TimeZonePicker"

        // experimental
        const val KEY_OTHER_COPY_DB = "preferences_copy_db"
        const val KEY_OTHER_REMINDERS_RESPONDED = "preferences_reminders_responded"

        internal const val REQUEST_CODE_ALERT_RINGTONE = 42

        /** Return a properly configured SharedPreferences instance  */
        fun getSharedPreferences(context: Context): SharedPreferences {
            return context.getSharedPreferences(SHARED_PREFS_NAME, Context.MODE_PRIVATE)
        }

        /** Set the default shared preferences in the proper context */
        fun setDefaultValues(context: Context) {
            PreferenceManager.setDefaultValues(context, SHARED_PREFS_NAME, Context.MODE_PRIVATE,
                    R.xml.general_preferences, true)
        }
    }
}
