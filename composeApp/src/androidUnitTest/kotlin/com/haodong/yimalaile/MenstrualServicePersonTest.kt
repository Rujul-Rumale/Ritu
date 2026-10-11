package com.haodong.yimalaile

import com.haodong.yimalaile.domain.menstrual.AddRecordResult
import com.haodong.yimalaile.domain.menstrual.ENOUGH_RECORDS_FOR_PREDICTION
import com.haodong.yimalaile.domain.menstrual.MenstrualService
import com.haodong.yimalaile.domain.menstrual.RecordSource
import com.haodong.yimalaile.fakes.FakeRecordsRepository
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Person-scoped behaviour of [MenstrualService]: every operation touches only
 * its own person, auto-confirmed predictions keep continuity, and manual real
 * observations supersede guessed periods without ever producing overlaps.
 */
class MenstrualServicePersonTest {

    private fun overlaps(a: LocalDate, aEnd: LocalDate, b: LocalDate, bEnd: LocalDate): Boolean =
        a <= bEnd && b <= aEnd

    // ═══════════════════════════════════════════════════
    // Person isolation
    // ═══════════════════════════════════════════════════

    @Test
    fun recordsAreIsolatedPerPerson() = runBlocking {
        val service = MenstrualService(FakeRecordsRepository())
        service.backfillPeriod("a", LocalDate(2026, 1, 1), LocalDate(2026, 1, 5))
        service.backfillPeriod("b", LocalDate(2026, 1, 10), LocalDate(2026, 1, 14))

        val allA = service.getAllRecords("a")
        val allB = service.getAllRecords("b")
        assertEquals(1, allA.size)
        assertEquals(1, allB.size)
        assertTrue(allA.all { it.personId == "a" })
        assertTrue(allB.all { it.personId == "b" })
    }

    @Test
    fun deletingARecordOnlyAffectsItsPerson() = runBlocking {
        val service = MenstrualService(FakeRecordsRepository())
        service.backfillPeriod("a", LocalDate(2026, 1, 1), LocalDate(2026, 1, 5))
        service.backfillPeriod("a", LocalDate(2026, 2, 1), LocalDate(2026, 2, 5))
        service.backfillPeriod("b", LocalDate(2026, 1, 10), LocalDate(2026, 1, 14))

        val victim = service.getAllRecords("a").first { it.startDate == LocalDate(2026, 1, 1) }
        assertTrue(service.deleteRecord("a", victim.id))

        assertEquals(1, service.getAllRecords("a").size)
        assertEquals(1, service.getAllRecords("b").size)
    }

    @Test
    fun advanceOnePersonsDataDoesNotImpactAnother() = runBlocking {
        val repo = FakeRecordsRepository()
        val service = MenstrualService(repo)
        service.backfillPeriod("a", LocalDate(2026, 1, 1), LocalDate(2026, 1, 5))
        service.backfillPeriod("b", LocalDate(2026, 1, 10), LocalDate(2026, 1, 14))

        // Person a gains an auto-confirmed chain + more records; b must stay put.
        service.getCycleState("a", 28)
        assertTrue(service.getAllRecords("a").size > 1)
        assertEquals(1, service.getAllRecords("b").size)
        assertEquals(listOf("b"), service.getAllRecords("b").map { it.personId })
    }

    // ═══════════════════════════════════════════════════
    // Initial history seeding + predictions
    // ═══════════════════════════════════════════════════

    @Test
    fun seedInitialHistoryCreatesEnoughNonOverlappingRecordsAndIsIdempotent() = runBlocking {
        val service = MenstrualService(FakeRecordsRepository())
        val count1 = service.seedInitialHistory("a", 28, 5)
        assertTrue(count1 >= ENOUGH_RECORDS_FOR_PREDICTION)

        val records = service.getAllRecords("a")
        assertTrue(records.size >= ENOUGH_RECORDS_FOR_PREDICTION)
        assertTrue(records.all { it.personId == "a" })
        records.zipWithNext().forEach { (prev, next) ->
            assertTrue("records must not overlap", next.startDate > prev.endDate!!)
        }

        // Idempotent: a second call must not add anything.
        val count2 = service.seedInitialHistory("a", 28, 5)
        assertEquals(count1, count2)
        assertTrue(service.predictNextCycles("a").isNotEmpty())
    }

    // ═══════════════════════════════════════════════════
    // Auto-confirm continuity + manual override
    // ═══════════════════════════════════════════════════

    @Test
    fun getCycleStateAutoConfirmsPredictedPeriodsInThePast() = runBlocking {
        val service = MenstrualService(FakeRecordsRepository())
        service.backfillPeriod("a", LocalDate(2026, 3, 1), LocalDate(2026, 3, 5))

        val state = service.getCycleState("a", 28)
        val confirmed = state.records.filter { it.source == RecordSource.AUTO_CONFIRMED }
        assertTrue("expected auto-confirmed predictions, got none", confirmed.isNotEmpty())
    }

    @Test
    fun manualRecordSupersedesAdjacentAutoConfirmedPeriod() = runBlocking {
        val service = MenstrualService(FakeRecordsRepository())
        service.backfillPeriod("a", LocalDate(2026, 1, 1), LocalDate(2026, 1, 5))
        service.getCycleState("a", 28) // auto-confirms predictions at ~Jan29, Feb26, Mar25
        assertTrue(service.getAllRecords("a").any { it.source == RecordSource.AUTO_CONFIRMED })

        val result = service.recordPeriodStart("a", LocalDate(2026, 1, 28))
        val success = result as? AddRecordResult.Success
            ?: throw AssertionError("expected Success, got $result")

        val after = service.getAllRecords("a")
        assertTrue(after.any { it.id == success.record.id })
        assertFalse(
            "a real and assumed period must never overlap",
            after.any { r ->
                r.id != success.record.id &&
                    overlaps(r.startDate, r.endDate ?: r.startDate, success.record.startDate, success.record.endDate!!)
            }
        )
    }

    @Test
    fun manualRecordSupersedesAutoConfirmedChainAfterPreviousAnchor() = runBlocking {
        val service = MenstrualService(FakeRecordsRepository())
        service.backfillPeriod("a", LocalDate(2026, 1, 1), LocalDate(2026, 1, 5))
        service.getCycleState("a", 28)
        assertTrue(service.getAllRecords("a").any { it.source == RecordSource.AUTO_CONFIRMED })

        // The user records a real period in March; all guessed periods in
        // between (Jan 29 … Mar) were projections and must be dropped.
        val result = service.recordPeriodStart("a", LocalDate(2026, 3, 1))
        val success = result as? AddRecordResult.Success
            ?: throw AssertionError("expected Success, got $result")

        val after = service.getAllRecords("a")
        assertEquals(2, after.size)
        assertFalse(after.any { it.source == RecordSource.AUTO_CONFIRMED })
        assertTrue(after.any { it.startDate == LocalDate(2026, 1, 1) })
        assertTrue(after.any { it.startDate == LocalDate(2026, 3, 1) })
    }

    @Test
    fun trueOverlapWithManualRecordIsRejected() = runBlocking {
        val service = MenstrualService(FakeRecordsRepository())
        service.backfillPeriod("a", LocalDate(2026, 1, 1), LocalDate(2026, 1, 5))

        val result = service.recordPeriodStart("a", LocalDate(2026, 1, 3))
        assertTrue(result is AddRecordResult.OverlappingPeriod)
        // Nothing must have been inserted or deleted.
        assertEquals(1, service.getAllRecords("a").size)
    }

    @Test
    fun backfillOverlapIsRejectedPerPerson() = runBlocking {
        val service = MenstrualService(FakeRecordsRepository())
        service.backfillPeriod("a", LocalDate(2026, 1, 1), LocalDate(2026, 1, 5))
        service.backfillPeriod("b", LocalDate(2026, 1, 10), LocalDate(2026, 1, 14))

        // Same dates for "a" overlap → rejected; "b" never sees the collision.
        assertTrue(service.backfillPeriod("a", LocalDate(2026, 1, 1), LocalDate(2026, 1, 5)) is AddRecordResult.OverlappingPeriod)
        assertEquals(1, service.getAllRecords("a").size)
    }

    @Test
    fun editingOnePersonsRecordBoundsOnlyTouchesThatPerson() = runBlocking {
        val service = MenstrualService(FakeRecordsRepository())
        service.backfillPeriod("a", LocalDate(2026, 1, 1), LocalDate(2026, 1, 5))
        service.backfillPeriod("b", LocalDate(2026, 1, 10), LocalDate(2026, 1, 14))

        val aRecord = service.getAllRecords("a").first()
        assertTrue(service.editRecordDates("a", aRecord.id, newStart = null, newEnd = LocalDate(2026, 1, 7)))

        assertEquals(LocalDate(2026, 1, 7), service.getAllRecords("a").single().endDate)
        assertEquals(LocalDate(2026, 1, 14), service.getAllRecords("b").single().endDate)
    }
}