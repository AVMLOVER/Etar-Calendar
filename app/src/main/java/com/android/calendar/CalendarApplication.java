/*
 * Copyright (C) 2007 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.calendar;

import android.app.Application;
import android.content.SharedPreferences;

import com.android.calendar.settings.GeneralPreferences;
import com.android.calendar.settings.ViewDetailsPreferences;
import com.google.android.material.color.DynamicColors;

public class CalendarApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();

        /*
         * Material 3 "monet": re-map every Material 3 colour role from the user's wallpaper.
         *
         * hasDynamicColor() is false below Android 12, where this is a no-op and the app falls
         * back to the Material 3 palette declared in res/values/themes.xml. Applying it here -
         * once, and before any Activity is created - is what makes the dynamic palette reach the
         * Activities, the widgets, the dialogs and the Canvas-drawn month/week/day grids alike.
         */
        if (DynamicColors.isDynamicColorAvailable()) {
            DynamicColors.applyToActivitiesIfAvailable(this);
        }

        /*
         * Ensure the default values are set for any receiver, activity,
         * service, etc. of Calendar
         * please increment SHARED_PREFS_VERSION each time the new default value appears
         * in a layout xml file in order to make sure it will be initialized
         */
        final int SHARED_PREFS_VERSION = 2;
        final String VERSION_KEY = "spv";
        SharedPreferences preferences = GeneralPreferences.Companion.getSharedPreferences(this);
        if (preferences.getInt(VERSION_KEY, 0) != SHARED_PREFS_VERSION) {
            GeneralPreferences.Companion.setDefaultValues(this);
            ViewDetailsPreferences.Companion.setDefaultValues(this);

            /*
             * The lunar date line is only useful in Chinese-script locales. Everywhere else it
             * crowds the day header and the year view's month-start underlines mean nothing, so
             * both start switched off. Written here rather than left to a code default so the
             * switches in the settings screens show the same state the app actually uses.
             */
            if (!preferences.contains(LunarUtils.PREF_SHOW_LUNAR)) {
                preferences.edit()
                        .putBoolean(LunarUtils.PREF_SHOW_LUNAR, LunarUtils.defaultShowLunar())
                        .apply();
            }

            preferences.edit().putInt(VERSION_KEY, SHARED_PREFS_VERSION).apply();
        }

        // Save the version number, for upcoming 'What's new' screen.  This will be later be
        // moved to that implementation.
        Utils.setSharedPreference(this, GeneralPreferences.KEY_VERSION,
                Utils.getVersionCode(this));

        // Initialize the registry mapping some custom behavior.
        ExtensionsFactory.init(getAssets());

        installCrashLogger();

        // Repairs all-day events written before the countdown feature stored them the way
        // CalendarProvider requires.
        //
        // On a background thread: it reads the countdown list and queries the provider, neither of
        // which belongs on the path to the first frame. It is guarded by a stored flag and does
        // nothing once it has run.
        new Thread(() -> {
            try {
                com.android.calendar.countdown.CountdownAllDayMigration.INSTANCE.run(this);
            } catch (Throwable ignored) {
                // A failed repair must not affect the app's start.
            }
        }, "countdown-allday-migration").start();
    }

    /**
     * Writes uncaught exceptions to a file under the app's external files directory.
     *
     * A crash with no logcat session attached is otherwise impossible to diagnose from a bug
     * report; the file can simply be sent on. Nothing is uploaded anywhere.
     */
    private void installCrashLogger() {
        final Thread.UncaughtExceptionHandler previous =
                Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            try {
                java.io.File dir = getExternalFilesDir(null);
                if (dir != null) {
                    java.io.File out = new java.io.File(
                            dir, "crash-" + System.currentTimeMillis() + ".txt");
                    try (java.io.PrintWriter writer = new java.io.PrintWriter(out)) {
                        writer.println("thread: " + thread.getName());
                        writer.println("version: " + Utils.getVersionCode(this));
                        throwable.printStackTrace(writer);
                    }
                }
            } catch (Throwable ignored) {
                // Never let the logger itself take the process down differently.
            }
            if (previous != null) {
                previous.uncaughtException(thread, throwable);
            }
        });
    }
}
