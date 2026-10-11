package com.haodong.yimalaile.ui.pages.onboarding

import com.haodong.yimalaile.domain.menstrual.MenstrualService
import com.haodong.yimalaile.domain.person.DEFAULT_PERSON_NAME
import com.haodong.yimalaile.domain.person.Person
import com.haodong.yimalaile.domain.person.PersonRepository
import kotlinx.datetime.LocalDate
import kotlin.time.Clock

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
 * Persist the onboarding result as a new person.
 *
 * - Creates the [Person] and stores the per-person cycle settings.
 * - Writes the manually-entered period (active or ended), if any.
 * - Pads with synthetic past cycles when we don't already have enough data
 *   for predictions to feel useful. If the user gave us nothing at all,
 *   the synthetic series is anchored one cycle in the past so predictions
 *   still have something to project forward from.
 */
internal suspend fun commitOnboarding(
    service: MenstrualService,
    personRepository: PersonRepository,
    personName: String,
    isForMe: Boolean,
    cycleLength: Int,
    periodDuration: Int,
    entry: PeriodEntry,
): Person {
    val now = Clock.System.now().toEpochMilliseconds()
    val personId = "person_${now}"
    val person = Person(
        id = personId,
        name = if (isForMe) DEFAULT_PERSON_NAME else personName.trim().ifEmpty { DEFAULT_PERSON_NAME },
        cycleLength = cycleLength,
        periodLength = periodDuration,
        createdAtEpochMillis = now,
        updatedAtEpochMillis = now,
        isForMe = isForMe,
    )
    personRepository.insertPerson(person)

    when (entry) {
        is PeriodEntry.Active -> service.recordPeriodStart(personId, entry.start)
        is PeriodEntry.Ended -> service.backfillPeriod(personId, entry.start, entry.end)
        PeriodEntry.None -> Unit
    }

    service.seedInitialHistory(personId, cycleLength, periodDuration)
    return person
}