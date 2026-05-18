package com.haodong.yimalaile.ui.pages.onboarding

import com.haodong.yimalaile.domain.menstrual.MenstrualService
import com.haodong.yimalaile.domain.settings.SettingsRepository
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
import kotlin.time.Clock

/** A meaningful threshold for "we have enough cycles to predict without padding". */
internal const val ENOUGH_RECORDS_FOR_PREDICTION = 3

/** What the user told us during the period-status step. */
internal sealed interface PeriodEntry {
    /** No explicit entry — either skipped or status step never shown. */
    data object None : PeriodEntry

    /** User is currently on their period, started on [start]. */
    data class Active(val start: LocalDate) : PeriodEntry

    /** User's last period was a complete [start]..[end] range. */
    data class Ended(val start: LocalDate, val end: LocalDate) : PeriodEntry
}

/**
 * Persist the onboarding result.
 *
 * - Saves cycle settings.
 * - Writes the manually-entered period (active or ended), if any.
 * - Pads with synthetic past cycles when we don't already have enough data
 *   for predictions to feel useful. If the user gave us nothing at all,
 *   we anchor the synthetic series one cycle in the past so predictions
 *   still have something to project forward from.
 */
internal suspend fun commitOnboarding(
    service: MenstrualService,
    settings: SettingsRepository,
    cycleLength: Int,
    periodDuration: Int,
    entry: PeriodEntry,
) {
    settings.setCycleLength(cycleLength)
    settings.setPeriodDuration(periodDuration)

    when (entry) {
        is PeriodEntry.Active -> service.recordPeriodStart(entry.start)
        is PeriodEntry.Ended -> service.backfillPeriod(entry.start, entry.end)
        PeriodEntry.None -> Unit
    }

    val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
    var state = service.getCycleState(cycleLength)

    // If no records at all (user picked "unsure"), seed a synthetic anchor
    // ~one cycle ago so predictions have a starting point. Past records still
    // get added by the loop below.
    if (state.records.isEmpty()) {
        val anchorStart = today.minus(cycleLength, DateTimeUnit.DAY)
        val anchorEnd = anchorStart.plus(periodDuration - 1, DateTimeUnit.DAY)
        service.backfillPeriod(anchorStart, anchorEnd)
        state = service.getCycleState(cycleLength)
    }

    val recordsCount = state.records.size
    if (recordsCount >= ENOUGH_RECORDS_FOR_PREDICTION) return

    val anchor = state.records.minByOrNull { it.startDate }?.startDate ?: return
    val needed = ENOUGH_RECORDS_FOR_PREDICTION + 2 - recordsCount
    for (i in 1..needed) {
        val pastStart = anchor.minus(cycleLength * i, DateTimeUnit.DAY)
        val pastEnd = pastStart.plus(periodDuration - 1, DateTimeUnit.DAY)
        service.backfillPeriod(pastStart, pastEnd)
    }
}
