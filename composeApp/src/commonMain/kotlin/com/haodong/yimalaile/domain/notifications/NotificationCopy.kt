package com.haodong.yimalaile.domain.notifications

import org.jetbrains.compose.resources.getString
import yimalaile.composeapp.generated.resources.Res
import yimalaile.composeapp.generated.resources.notif_daily_body
import yimalaile.composeapp.generated.resources.notif_daily_body_person
import yimalaile.composeapp.generated.resources.notif_daily_title
import yimalaile.composeapp.generated.resources.notif_ovulation_body
import yimalaile.composeapp.generated.resources.notif_ovulation_body_person
import yimalaile.composeapp.generated.resources.notif_ovulation_title
import yimalaile.composeapp.generated.resources.notif_period_body
import yimalaile.composeapp.generated.resources.notif_period_body_person
import yimalaile.composeapp.generated.resources.notif_period_title

/**
 * Pre-resolved localized [NotificationService.Copy] used by every caller that
 * re-applies the schedule (app start, onboarding, settings, boot receiver).
 * This keeps the localized copy in one place instead of duplicating it per
 * platform entry point.
 */
suspend fun buildNotificationCopy(prefs: NotificationPrefs): NotificationService.Copy =
    NotificationService.Copy(
        periodTitle = getString(Res.string.notif_period_title),
        periodBody = { name, single ->
            if (single) getString(Res.string.notif_period_body, prefs.periodReminderDaysBefore)
            else getString(Res.string.notif_period_body_person, name, prefs.periodReminderDaysBefore)
        },
        ovulationTitle = getString(Res.string.notif_ovulation_title),
        ovulationBody = { name, single ->
            if (single) getString(Res.string.notif_ovulation_body, prefs.ovulationReminderDaysBefore)
            else getString(Res.string.notif_ovulation_body_person, name, prefs.ovulationReminderDaysBefore)
        },
        dailyTitle = getString(Res.string.notif_daily_title),
        dailyBody = { name, single ->
            if (single) getString(Res.string.notif_daily_body)
            else getString(Res.string.notif_daily_body_person, name)
        },
    )