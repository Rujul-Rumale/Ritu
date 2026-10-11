package com.haodong.yimalaile.domain.notifications

/**
 * Number of per-person reminder slots. Person-specific reminders get a
 * deterministic slot derived from their id, so each person has a stable,
 * reschedulable alarm identity.
 */
const val PERSON_SLOTS = 256

/**
 * Platform abstraction for delivering scheduled local notifications.
 *
 * Implementations:
 *  - Android: AlarmManager + BroadcastReceiver + NotificationManagerCompat
 *  - iOS:     UNUserNotificationCenter with UNCalendarNotificationTrigger
 *
 * Scheduling is idempotent per [ReminderKind] and per-person [slot] — calling
 * [schedule] for the same combination replaces the previous reminder.
 */
interface NotificationScheduler {

    /** Whether the user has granted notification permission at the OS level. */
    suspend fun hasPermission(): Boolean

    /**
     * Ask the OS for notification permission. On Android 13+ this shows the
     * system dialog; on iOS it shows the UNUserNotificationCenter prompt.
     * Returns true if permission is granted after the call.
     */
    suspend fun requestPermission(): Boolean

    /** Schedule (or replace) a reminder. Safe to call even if permission is denied — no-op in that case. */
    fun schedule(reminder: ScheduledReminder)

    /** Cancel any pending reminder for the given kind and person slot (defaults to slot 0). */
    fun cancel(kind: ReminderKind, slot: Int = 0)

    /** Cancel every reminder this app has scheduled. */
    fun cancelAll()
}