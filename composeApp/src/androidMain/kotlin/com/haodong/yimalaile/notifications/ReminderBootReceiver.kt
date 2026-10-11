package com.haodong.yimalaile.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.haodong.yimalaile.di.AppComponent
import com.haodong.yimalaile.di.create
import com.haodong.yimalaile.domain.export.AndroidReportExportService
import com.haodong.yimalaile.domain.notifications.buildNotificationCopy
import com.haodong.yimalaile.infrastructure.persistence.appDataStore
import com.viktormykhailiv.kmp.health.HealthManagerFactory
import com.viktormykhailiv.kmp.health.HealthManagerFactoryOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * AlarmManager alarms do not survive a device reboot (unlike iOS, where
 * UNUserNotificationCenter keeps pending requests). This re-applies the stored
 * schedule on boot / package update so reminders keep firing after a restart.
 */
class ReminderBootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) return

        val appContext = context.applicationContext
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.Default + SupervisorJob()).launch {
            try {
                val scheduler = AndroidNotificationScheduler(appContext)
                val healthManager = HealthManagerFactory()
                    .createManager(options = HealthManagerFactoryOptions.default())
                val component = AppComponent.create(
                    appDataStore(appContext),
                    scheduler,
                    AndroidReportExportService(appContext),
                    healthManager,
                )
                val prefs = component.settingsRepository.getNotificationPrefs()
                if (!prefs.periodReminderEnabled &&
                    !prefs.ovulationReminderEnabled &&
                    !prefs.dailyReportEnabled
                ) {
                    return@launch
                }
                component.notificationService.reschedule(prefs = prefs, copy = buildNotificationCopy(prefs))
            } catch (_: Throwable) {
                // Never crash the boot flow; the schedule is re-applied on the
                // next app launch anyway.
            } finally {
                pendingResult.finish()
            }
        }
    }
}