package com.haodong.yimalaile.domain.notifications

import com.haodong.yimalaile.domain.menstrual.MenstrualService
import com.haodong.yimalaile.domain.menstrual.PredictedCycle
import com.haodong.yimalaile.domain.person.Person
import com.haodong.yimalaile.domain.person.PersonRepository
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.todayIn
import kotlin.time.Clock

/**
 * Computes upcoming reminders from cycle data + preferences and pushes them
 * to the platform scheduler. Call [reschedule] whenever:
 *   - the app starts
 *   - notification preferences change
 *   - menstrual records or predictions change
 *
 * Every reminder — including the daily report — is scheduled per person, so
 * each person gets their own stable alarm slot and person-named copy. The
 * daily report is only scheduled for the people still being tracked, so a
 * phone tracking several people gets a per-person daily nudge instead of one
 * global, meaningless report.
 */
class NotificationService(
    private val menstrualService: MenstrualService,
    private val personRepository: PersonRepository,
    private val scheduler: NotificationScheduler,
) {

    /**
     * Pre-resolved localized copy for every reminder. Body copy is a function
     * of the person's name — callers (AppViewModel) own the resources, and
     * [singlePerson] lets them fall back to a gender-neutral "Your" phrasing
     * when only one person is tracked.
     */
    data class Copy(
        val periodTitle: String,
        val periodBody: suspend (name: String, singlePerson: Boolean) -> String,
        val ovulationTitle: String,
        val ovulationBody: suspend (name: String, singlePerson: Boolean) -> String,
        val dailyTitle: String,
        val dailyBody: suspend (name: String, singlePerson: Boolean) -> String,
    )

    /** Recompute and apply the schedule for all enabled reminders. */
    suspend fun reschedule(
        prefs: NotificationPrefs,
        copy: Copy,
    ) {
        val people = personRepository.getPeople()
        val singlePerson = people.size == 1

        // If the whole feature is off, clear every slot and bail.
        if (!prefs.periodReminderEnabled &&
            !prefs.ovulationReminderEnabled &&
            !prefs.dailyReportEnabled
        ) {
            cancelPerPersonSlots()
            return
        }

        val tz = TimeZone.currentSystemDefault()
        val today = Clock.System.todayIn(tz)

        // ---------- Per-person reminders (cycle-dependent + daily report) ----------
        cancelPerPersonSlots()
        for (person in people) {
            val slot = slotFor(person)
            val predictions = menstrualService.predictNextCycles(
                personId = person.id,
                count = 6,
                cycleLength = person.cycleLength,
            )

            if (prefs.dailyReportEnabled) {
                val fireAt = nextDailyFireAt(today, prefs.dailyReportHour, prefs.dailyReportMinute)
                scheduler.schedule(
                    ScheduledReminder(
                        id = reminderId(ReminderKind.DAILY_REPORT, slot),
                        kind = ReminderKind.DAILY_REPORT,
                        title = copy.dailyTitle,
                        body = copy.dailyBody(person.name, singlePerson),
                        fireAt = fireAt,
                        repeatDaily = true,
                    )
                )
            } else {
                scheduler.cancel(ReminderKind.DAILY_REPORT, slot)
            }

            if (prefs.periodReminderEnabled) {
                val fire = nextReminderDate(
                    predictions = predictions,
                    daysBefore = prefs.periodReminderDaysBefore,
                    today = today,
                    anchor = ReminderAnchor.PERIOD_START,
                )
                if (fire != null) {
                    scheduler.schedule(
                        ScheduledReminder(
                            id = reminderId(ReminderKind.PERIOD, slot),
                            kind = ReminderKind.PERIOD,
                            title = copy.periodTitle,
                            body = copy.periodBody(person.name, singlePerson),
                            fireAt = fire.atTime(LocalTime(9, 0)),
                        )
                    )
                } else {
                    scheduler.cancel(ReminderKind.PERIOD, slot)
                }
            }

            if (prefs.ovulationReminderEnabled) {
                val fire = nextReminderDate(
                    predictions = predictions,
                    daysBefore = prefs.ovulationReminderDaysBefore,
                    today = today,
                    anchor = ReminderAnchor.OVULATION,
                )
                if (fire != null) {
                    scheduler.schedule(
                        ScheduledReminder(
                            id = reminderId(ReminderKind.OVULATION, slot),
                            kind = ReminderKind.OVULATION,
                            title = copy.ovulationTitle,
                            body = copy.ovulationBody(person.name, singlePerson),
                            fireAt = fire.atTime(LocalTime(9, 0)),
                        )
                    )
                } else {
                    scheduler.cancel(ReminderKind.OVULATION, slot)
                }
            }
        }

    }

    suspend fun hasPermission(): Boolean = scheduler.hasPermission()

    suspend fun requestPermission(): Boolean = scheduler.requestPermission()

    fun cancelAll() {
        scheduler.cancelAll()
    }

    // ---------- helpers ----------

    private enum class ReminderAnchor { PERIOD_START, OVULATION }

    private fun slotFor(person: Person): Int =
        (person.id.hashCode() and 0x7fffffff) % PERSON_SLOTS

    private fun reminderId(kind: ReminderKind, slot: Int): Int =
        kind.stableId * 1000 + slot

    private fun cancelPerPersonSlots() {
        for (slot in 0 until PERSON_SLOTS) {
            ReminderKind.entries.forEach { kind -> scheduler.cancel(kind, slot) }
        }
    }

    private fun nextDailyFireAt(today: LocalDate, hour: Int, minute: Int): LocalDateTime {
        val tz = TimeZone.currentSystemDefault()
        val now = Clock.System.now().toLocalDateTime(tz)
        val candidateToday = today.atTime(LocalTime(hour, minute))
        return if (candidateToday > now) candidateToday
        else today.plus(1, DateTimeUnit.DAY).atTime(LocalTime(hour, minute))
    }

    private fun nextReminderDate(
        predictions: List<PredictedCycle>,
        daysBefore: Int,
        today: LocalDate,
        anchor: ReminderAnchor,
    ): LocalDate? {
        for (p in predictions) {
            val anchorDate = when (anchor) {
                ReminderAnchor.PERIOD_START -> p.predictedStart
                // Ovulation ≈ 14 days before the next period start (luteal phase).
                ReminderAnchor.OVULATION -> p.predictedStart.plus(-14, DateTimeUnit.DAY)
            }
            val fire = anchorDate.plus(-daysBefore, DateTimeUnit.DAY)
            if (fire >= today) return fire
        }
        return null
    }
}