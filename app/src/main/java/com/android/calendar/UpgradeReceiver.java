/*
 * Copyright (C) 2013 The Android Open Source Project
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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class UpgradeReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(final Context context, final Intent intent) {
        Utils.trySyncAndDisableUpgradeReceiver(context);
        // Alarms do not survive a restart, so the to-do reminders that are still to come are put
        // back on the clock here as well as when the to-do screen is opened.
        try {
            com.android.calendar.todo.TodoReminder.INSTANCE.rescheduleAll(context);
        } catch (Throwable t) {
            // A missing reminder is not worth taking the boot receiver down for.
            Log.w("UpgradeReceiver", "to-do reminders could not be rescheduled", t);
        }
    }

}
