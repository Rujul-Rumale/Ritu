package com.haodong.yimalaile.notifications

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.haodong.yimalaile.MainActivity
import com.haodong.yimalaile.R
import com.haodong.yimalaile.domain.notifications.ReminderKind
import com.haodong.yimalaile.domain.notifications.ScheduledReminder
import kotlin.time.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn

/**
 * Delivered when an [android.app.AlarmManager] reminder fires. Builds a simple
 * notification that taps through to [MainActivity]. Daily repeating reminders
 * re-arm themselves for the next day here, since the scheduler uses one-shot
 * allow-while-idle alarms.
 */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != AndroidNotificationScheduler.ACTION_REMINDER) return

        val id = intent.getIntExtra(AndroidNotificationScheduler.EXTRA_ID, 0)
        val title = intent.getStringExtra(AndroidNotificationScheduler.EXTRA_TITLE).orEmpty()
        val body = intent.getStringExtra(AndroidNotificationScheduler.EXTRA_BODY).orEmpty()
        val kindName = intent.getStringExtra(AndroidNotificationScheduler.EXTRA_KIND).orEmpty()
        val repeatDaily = intent.getBooleanExtra(AndroidNotificationScheduler.EXTRA_REPEAT_DAILY, false)
        val hour = intent.getIntExtra(AndroidNotificationScheduler.EXTRA_HOUR, 9)
        val minute = intent.getIntExtra(AndroidNotificationScheduler.EXTRA_MINUTE, 0)

        val contentIntent = PendingIntent.getActivity(
            context,
            id,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, AndroidNotificationScheduler.CHANNEL_ID)
            .setSmallIcon(R.drawable.ritu_monochrome)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()

        val manager = NotificationManagerCompat.from(context)
        if (manager.areNotificationsEnabled()) {
            try {
                manager.notify(id, notification)
            } catch (_: SecurityException) {
                // POST_NOTIFICATIONS not granted — silently drop.
            }
        }

        if (repeatDaily) {
            val kind = ReminderKind.entries.firstOrNull { it.name == kindName } ?: ReminderKind.DAILY_REPORT
            val tz = TimeZone.currentSystemDefault()
            val next = Clock.System.todayIn(tz)
                .plus(1, DateTimeUnit.DAY)
                .atTime(LocalTime(hour, minute))
            AndroidNotificationScheduler(context.applicationContext).schedule(
                ScheduledReminder(
                    id = id,
                    kind = kind,
                    title = title,
                    body = body,
                    fireAt = next,
                    repeatDaily = true,
                )
            )
        }
    }
}
