/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The reminder behind a to-do's "remind me" switch.
 *
 * The reminder is this app's own alarm and this app's own notification, not a calendar alert on the
 * event: an alert is measured in minutes *before* its event, and an all-day event starts at midnight,
 * so a reminder at nine in the morning would have to be a negative offset - which CalendarProvider
 * rejects. The alarm is set for the moment the user picked instead.
 */
package com.android.calendar.todo

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.android.calendar.todo.compose.ComposeTodoActivity
import ws.xsoh.etar.R
import java.time.LocalDate
import java.time.ZoneId

object TodoReminder {

    private const val TAG = "TodoReminder"
    private const val CHANNEL_ID = "m3e_todo_reminders"

    /** The day the alarm is for, as an epoch day, which is also its request code. */
    const val EXTRA_EPOCH_DAY = "m3e_todo_epoch_day"

    /**
     * Sets, or clears, the alarm for one to-do.
     *
     * A reminder whose moment has already passed is not scheduled: the day it belongs to is over, and
     * firing it late would only be noise. Editing a to-do that is already past its moment therefore
     * leaves no alarm behind, which is what cancel() first takes care of.
     */
    fun schedule(context: Context, todo: Todo) {
        cancel(context, todo.date)
        if (!todo.remind) return
        val triggerAt = todo.remindAtMillis(ZoneId.systemDefault()) ?: return
        if (triggerAt <= System.currentTimeMillis()) return
        try {
            val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val pending = pendingIntent(context, todo.date, create = true) ?: return
            val canExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                alarm.canScheduleExactAlarms()
            } else {
                true
            }
            if (canExact) {
                alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
            } else {
                // Without the exact-alarm permission an exact alarm throws; an inexact one still
                // arrives, only not to the minute.
                alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
            }
        } catch (t: Throwable) {
            // A SecurityException here means the permission was refused; the reminder is then late
            // rather than lost, so it is not worth taking the screen down for.
            Log.w(TAG, "the reminder could not be scheduled exactly", t)
            try {
                val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                pendingIntent(context, todo.date, create = true)?.let {
                    alarm.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP, triggerAt, it,
                    )
                }
            } catch (t2: Throwable) {
                Log.w(TAG, "the reminder could not be scheduled", t2)
            }
        }
    }

    /** Drops the alarm for a day, if there was one. */
    fun cancel(context: Context, date: LocalDate) {
        try {
            val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            pendingIntent(context, date, create = false)?.let { alarm.cancel(it) }
        } catch (t: Throwable) {
            Log.w(TAG, "the reminder could not be cancelled", t)
        }
    }

    /**
     * Puts every reminder still to come back on the alarm clock.
     *
     * Alarms do not survive a restart, so this runs from the boot receiver and whenever the to-do
     * screen is opened - the two moments where a missing reminder would otherwise go unnoticed.
     */
    fun rescheduleAll(context: Context) {
        val from = LocalDate.now()
        for (date in TodoStore.datesWithTodo(context)) {
            if (date.isBefore(from)) continue
            val todo = TodoStore.forDate(context, date) ?: continue
            if (todo.remind) schedule(context, todo)
        }
    }

    private fun pendingIntent(context: Context, date: LocalDate, create: Boolean): PendingIntent? {
        val intent = Intent(context, TodoReminderReceiver::class.java).apply {
            putExtra(EXTRA_EPOCH_DAY, date.toEpochDay())
            // One alarm per day, told apart by the data uri rather than by extras, which are not part
            // of a PendingIntent's identity.
            data = android.net.Uri.parse("m3e-todo://reminder/" + date.toEpochDay())
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            (if (create) PendingIntent.FLAG_IMMUTABLE else 0)
        return if (create) {
            PendingIntent.getBroadcast(context, date.toEpochDay().toInt(), intent, flags)
        } else {
            PendingIntent.getBroadcast(
                context, date.toEpochDay().toInt(), intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
            )
        }
    }

    /** Creates the channel, which must exist before anything is posted to it. */
    fun channel(context: Context): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java)
            if (manager != null && manager.getNotificationChannel(CHANNEL_ID) == null) {
                manager.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ID,
                        context.getString(R.string.m3e_todo_reminder_channel),
                        NotificationManager.IMPORTANCE_HIGH,
                    ).apply {
                        description = context.getString(R.string.m3e_todo_reminder_channel_summary)
                    }
                )
            }
        }
        return CHANNEL_ID
    }

    /** Posts the reminder for one day. */
    fun notify(context: Context, date: LocalDate) {
        val todo = TodoStore.forDate(context, date) ?: return
        val open = todo.items.count { !it.done }
        val text = if (todo.items.isEmpty()) {
            context.getString(R.string.m3e_todo_reminder_text)
        } else {
            context.getString(R.string.m3e_todo_reminder_progress, todo.doneCount, todo.items.size)
        }
        val tap = PendingIntent.getActivity(
            context,
            date.toEpochDay().toInt(),
            ComposeTodoActivity.intent(context, date)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, channel(context))
            .setSmallIcon(R.drawable.stat_notify_calendar_events)
            .setContentTitle(todo.name.ifEmpty { context.getString(R.string.m3e_todo_title) })
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(tap)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            // Everything on the list being done is worth saying, but not worth a sound.
            .setSilent(open == 0 && todo.items.isNotEmpty())
            .build()
        try {
            NotificationManagerCompat.from(context)
                .notify(date.toEpochDay().toInt(), notification)
        } catch (t: Throwable) {
            Log.w(TAG, "the reminder could not be posted", t)
        }
    }

    /** The reminder text summaries, kept here so the wording sits with the code that uses it. */
    fun summary(context: Context, todo: Todo): String = if (todo.items.isEmpty()) {
        context.getString(R.string.m3e_todo_reminder_text)
    } else {
        context.getString(R.string.m3e_todo_reminder_progress, todo.doneCount, todo.items.size)
    }
}

/** The alarm's landing point: it posts the reminder. */
class TodoReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val epochDay = intent.getLongExtra(TodoReminder.EXTRA_EPOCH_DAY, Long.MIN_VALUE)
        if (epochDay == Long.MIN_VALUE) return
        TodoReminder.notify(context, LocalDate.ofEpochDay(epochDay))
    }
}
