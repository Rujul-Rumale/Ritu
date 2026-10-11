package com.haodong.yimalaile.domain.menstrual

import kotlinx.datetime.*
import me.tatarka.inject.annotations.Inject
import kotlin.time.Clock

/** A meaningful threshold for "we have enough cycles to predict without padding". */
const val ENOUGH_RECORDS_FOR_PREDICTION = 3

data class MenstrualCycle(
    val record: MenstrualRecord,
    val cycleLength: Int?,
    val cycleEndDate: LocalDate,
    val ovulationDates: Set<LocalDate>,
    val ovulationPeakDate: LocalDate,
    val isAnomaly: Boolean = false // 是否为异常短周期（< 14天）
)

/**
 * Person-scoped menstrual cycle service.
 *
 * Every operation is scoped to a [personId]; records from other people are
 * never loaded, so predictions, statistics and auto-confirmation are fully
 * isolated per person.
 *
 * Authority hierarchy for a "hot" date range:
 *   manual / health-import  >  auto-confirmed  >  prediction
 *
 * When a manual record is entered near an assumed (AUTO_CONFIRMED) period,
 * the assumption is reconciled away so the calendar never shows two competing
 * periods and future predictions re-anchor to the real observation.
 */
@Inject
class MenstrualService(private val repository: RecordsRepository) {

    // ---------- Record period arrival / departure ----------

    /**
     * User confirms "姨妈来了" — period has arrived on [startDate].
     * If [endDate] is provided (user selected it), use it directly.
     * Otherwise, creates a record with an estimated end date based on average period length.
     */
    suspend fun recordPeriodStart(
        personId: String,
        startDate: LocalDate,
        endDate: LocalDate? = null,
    ): AddRecordResult {
        val all = repository.getAllRecords(personId)

        val avgPeriod = averagePeriodLength(all) ?: 5
        val estimatedEnd = endDate ?: startDate.plus(avgPeriod - 1, DateTimeUnit.DAY)

        // A real observation invalidates any assumed period in this range.
        reconcileAutoConfirmed(personId)

        val remaining = repository.getAllRecords(personId)
        val overlaps = remaining.any { record ->
            val rEnd = record.endDate ?: startDate // treat legacy null-endDate as today
            record.startDate <= estimatedEnd && startDate <= rEnd
        }
        if (overlaps) return AddRecordResult.OverlappingPeriod

        val now = Clock.System.now().toEpochMilliseconds()
        val result = repository.insertRecord(
            MenstrualRecord(
                id = newId(), personId = personId, startDate = startDate, endDate = estimatedEnd,
                endConfirmed = endDate != null,
                createdAtEpochMillis = now, updatedAtEpochMillis = now,
                source = RecordSource.MANUAL,
            )
        )
        return result
    }

    /**
     * User confirms "姨妈走了" — period ended on [endDate].
     * Finds the most recent record covering today (or the latest record) and sets its endDate.
     */
    suspend fun recordPeriodEnd(personId: String, endDate: LocalDate): Boolean {
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
        val all = repository.getAllRecords(personId)

        // Find the record that covers today, or the most recent record
        val record = all.find { r ->
            val rEnd = r.endDate ?: today
            today in r.startDate..rEnd
        } ?: all.maxByOrNull { it.startDate } ?: return false

        if (endDate < record.startDate) return false
        val trimmedDailyRecords = record.dailyRecords.filter { it.date <= endDate }
        return repository.updateRecord(
            record.copy(
                endDate = endDate,
                endConfirmed = true,
                dailyRecords = trimmedDailyRecords,
                updatedAtEpochMillis = Clock.System.now().toEpochMilliseconds(),
            )
        )
    }

    // ---------- Daily logging ----------

    suspend fun logDay(personId: String, recordId: String, day: DailyRecord): Boolean {
        val record = repository.getAllRecords(personId).find { it.id == recordId } ?: return false
        val updatedDaily = record.dailyRecords.filter { it.date != day.date } + day
        return repository.updateRecord(
            record.copy(dailyRecords = updatedDaily,
                updatedAtEpochMillis = Clock.System.now().toEpochMilliseconds())
        )
    }

    // ---------- Backfill ----------

    suspend fun backfillPeriod(personId: String, startDate: LocalDate, endDate: LocalDate): AddRecordResult {
        if (endDate < startDate) return AddRecordResult.InvalidDateRange

        // A real observation invalidates any assumed period in this range.
        reconcileAutoConfirmed(personId)

        val remaining = repository.getAllRecords(personId)
        val overlaps = remaining.any { record ->
            val rEnd = record.endDate ?: endDate
            record.startDate <= endDate && startDate <= rEnd
        }
        if (overlaps) return AddRecordResult.OverlappingPeriod

        val now = Clock.System.now().toEpochMilliseconds()
        return repository.insertRecord(
            MenstrualRecord(id = newId(), personId = personId, startDate = startDate, endDate = endDate,
                endConfirmed = true, createdAtEpochMillis = now, updatedAtEpochMillis = now)
        )
    }

    // ---------- State ----------

    /**
     * Get the current cycle state for one person.
     * [cycleLength] is the person's configured cycle length (default 28).
     */
    suspend fun getCycleState(personId: String, cycleLength: Int = 28): CycleState {
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
        val all = repository.getAllRecords(personId)
        val predictions = predictNextCycles(all, cycleLength = cycleLength)

        // Auto-confirm predictions that are 3+ days past their end
        autoConfirmPastPredictions(personId, all, predictions, today)

        // Re-read after potential auto-confirmation inserts
        val records = repository.getAllRecords(personId)
            .sortedByDescending { it.startDate }
            .take(10)

        val updatedPredictions = predictNextCycles(repository.getAllRecords(personId), cycleLength = cycleLength)

        // Current period: a record covering today whose end hasn't been confirmed yet
        val currentPeriod = records.find { r ->
            !r.endConfirmed && r.endDate != null &&
            today in r.startDate..r.endDate
        }

        // In predicted period: today falls within a prediction (and no real record covers it)
        val inPredictedPeriod = currentPeriod == null && updatedPredictions.any { pred ->
            val avgPeriod = averagePeriodLength(records) ?: 5
            val pEnd = pred.predictedEnd ?: pred.predictedStart.plus(avgPeriod - 1, DateTimeUnit.DAY)
            today in pred.predictedStart..pEnd
        }

        return CycleState(
            records = records,
            predictions = updatedPredictions,
            currentPeriod = currentPeriod,
            inPredictedPeriod = inPredictedPeriod,
        )
    }

    private suspend fun autoConfirmPastPredictions(
        personId: String,
        existingRecords: List<MenstrualRecord>,
        predictions: List<PredictedCycle>,
        today: LocalDate,
    ) {
        val avgPeriod = averagePeriodLength(existingRecords) ?: 5

        for (pred in predictions) {
            val pEnd = pred.predictedEnd ?: pred.predictedStart.plus(avgPeriod - 1, DateTimeUnit.DAY)
            val confirmDate = pEnd.plus(3, DateTimeUnit.DAY)

            if (today < confirmDate) continue

            // Check no real record already covers this range
            val alreadyCovered = existingRecords.any { r ->
                val rEnd = r.endDate ?: today
                r.startDate <= pEnd && pred.predictedStart <= rEnd
            }
            if (alreadyCovered) continue

            val now = Clock.System.now().toEpochMilliseconds()
            repository.insertRecord(
                MenstrualRecord(
                    id = newId(),
                    personId = personId,
                    startDate = pred.predictedStart,
                    endDate = pEnd,
                    endConfirmed = true,
                    createdAtEpochMillis = now,
                    updatedAtEpochMillis = now,
                    source = RecordSource.AUTO_CONFIRMED,
                )
            )
        }
    }

    /**
     * New real data overrides any and all auto-confirmed assumptions for the
     * person. An auto-confirmed record is derived state (a guess inferred from
     * older real observations); the moment a fresh real period, edit or backfill
     * lands, every guess is dropped and getCycleState() reconstitutes the chain
     * anchored on the real records. This keeps the assumption chain from ever
     * masquerading as observed history.
     */
    private suspend fun reconcileAutoConfirmed(personId: String) {
        val toRemove = repository.getAllRecords(personId)
            .filter { it.source == RecordSource.AUTO_CONFIRMED }
        toRemove.forEach { repository.deleteRecord(it.id, personId) }
    }

    // ---------- Edit / Delete ----------

    suspend fun editRecordDates(
        personId: String,
        recordId: String,
        newStart: LocalDate?,
        newEnd: LocalDate?,
    ): Boolean {
        val record = repository.getAllRecords(personId).find { it.id == recordId } ?: return false
        val start = newStart ?: record.startDate
        val end = newEnd ?: record.endDate
        if (end != null && end < start) return false

        // Editing a real record to overlap an assumption resolves the assumption.
        if (record.source == RecordSource.MANUAL || record.source == RecordSource.HEALTH_IMPORT) {
            reconcileAutoConfirmed(personId)
        }

        val trimmedDaily = if (end != null) record.dailyRecords.filter { it.date in start..end } else record.dailyRecords
        return repository.updateRecord(
            record.copy(
                startDate = start,
                endDate = end,
                dailyRecords = trimmedDaily,
                updatedAtEpochMillis = Clock.System.now().toEpochMilliseconds(),
            )
        )
    }

    suspend fun deleteRecord(personId: String, recordId: String): Boolean =
        repository.deleteRecord(recordId, personId)

    // ---------- Data management ----------

    suspend fun clearAllData() {
        repository.clearAll()
    }

    /**
     * Seed a synthetic past history so predictions work immediately, using the
     * same rules as first-run onboarding:
     *  - if the person has no records at all, anchor the series one cycle ago,
     *  - then pad further into the past until we reach [ENOUGH_RECORDS_FOR_PREDICTION].
     *
     * Returns the total record count after seeding. Idempotent: when the person
     * already has enough records, this does nothing.
     */
    suspend fun seedInitialHistory(personId: String, cycleLength: Int, periodDuration: Int): Int {
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
        if (repository.getAllRecords(personId).isEmpty()) {
            val anchorStart = today.minus(cycleLength, DateTimeUnit.DAY)
            val anchorEnd = anchorStart.plus(periodDuration - 1, DateTimeUnit.DAY)
            backfillPeriod(personId, anchorStart, anchorEnd)
        }

        var state = getCycleState(personId, cycleLength)
        var recordsCount = state.records.size
        if (recordsCount >= ENOUGH_RECORDS_FOR_PREDICTION) return recordsCount

        val anchor = state.records.minByOrNull { it.startDate }?.startDate ?: return recordsCount
        val needed = ENOUGH_RECORDS_FOR_PREDICTION + 2 - recordsCount
        for (i in 1..needed) {
            val pastStart = anchor.minus(cycleLength * i, DateTimeUnit.DAY)
            val pastEnd = pastStart.plus(periodDuration - 1, DateTimeUnit.DAY)
            backfillPeriod(personId, pastStart, pastEnd)
        }

        state = getCycleState(personId, cycleLength)
        val finalCount = state.records.size
        return finalCount
    }

    /**
     * Every non-deleted record for one person, sorted by start date ascending.
     * Used by the PDF export flow.
     */
    suspend fun getAllRecords(personId: String): List<MenstrualRecord> =
        repository.getAllRecords(personId)
            .filter { !it.isDeleted }
            .sortedBy { it.startDate }

    // ---------- Prediction ----------

    suspend fun predictNextCycles(personId: String, count: Int = 3, cycleLength: Int = 28): List<PredictedCycle> =
        predictNextCycles(repository.getAllRecords(personId), count, cycleLength)

    // ---------- Cycle Analysis ----------

    /**
     * Get detailed cycle information for a specific record.
     * Logic:
     * 1. Cycle Length: If next record exists, use (next.start - this.start). Otherwise use [defaultCycleLength].
     * 2. Cycle End: The day before next record starts.
     * 3. Ovulation: Scientific calculation based on the *actual or estimated cycle length*.
     *    Standard fertile window is usually in the middle of the cycle.
     *    According to user request, we use the "count-back method" (CycleLength - 14).
     *    Luteal phase is relatively constant (around 14 days).
     */
    fun getMenstrualCycle(
        record: MenstrualRecord,
        allRecords: List<MenstrualRecord>,
        predictions: List<PredictedCycle> = emptyList(),
        defaultCycleLength: Int = 28
    ): MenstrualCycle {
        val sorted = allRecords.filter { !it.isDeleted }.sortedBy { it.startDate }
        val index = sorted.indexOfFirst { it.id == record.id }

        val cycleLength = if (index != -1 && index < sorted.size - 1) {
            sorted[index].startDate.until(sorted[index + 1].startDate, DateTimeUnit.DAY).toInt()
        } else null

        // 标记是否为异常短周期
        val isAnomaly = cycleLength != null && cycleLength < 14

        val effectiveLength = cycleLength ?: defaultCycleLength
        val cycleEndDate = if (index != -1 && index < sorted.size - 1) {
            sorted[index + 1].startDate.plus(-1, DateTimeUnit.DAY)
        } else {
            val nextStart = predictions.firstOrNull()?.predictedStart
                ?: record.startDate.plus(effectiveLength, DateTimeUnit.DAY)
            nextStart.plus(-1, DateTimeUnit.DAY)
        }

        // Standard medical calculation: ovulation usually occurs ~14 days
        // before the start of the next period. The luteal phase (ovulation to
        // next period) is fairly fixed at ~14 days, while the follicular phase
        // (period to ovulation) varies more; cycle length depends mainly on it.
        val peakDayOffset = (effectiveLength - 14).coerceAtLeast(0)
        val peakDate = record.startDate.plus(peakDayOffset, DateTimeUnit.DAY)

        val ovulationDates = buildSet {
            // 如果是异常短周期，排卵计算可能不适用，但这里仍按公式给出一个参考，UI层会提示异常
            // 排卵期窗口通常为6天（高峰日前4天到后1天）
            for (offset in -4..1) {
                add(peakDate.plus(offset, DateTimeUnit.DAY))
            }
        }

        return MenstrualCycle(
            record = record,
            cycleLength = cycleLength ?: defaultCycleLength,
            cycleEndDate = cycleEndDate,
            ovulationDates = ovulationDates,
            ovulationPeakDate = peakDate,
            isAnomaly = isAnomaly
        )
    }


    // ---------- Private ----------

    /**
     * Predict next cycles using the configured [cycleLength] as fallback.
     * Anomalously short cycles (< 14 days) are excluded from the average.
     */
    private fun predictNextCycles(records: List<MenstrualRecord>, count: Int = 3, cycleLength: Int = 28): List<PredictedCycle> {
        // 计算历史平均周期长度，排除掉周期小于 14 天的记录
        val sorted = records.filter { !it.isDeleted }.sortedBy { it.startDate }
        val cycleLengths = mutableListOf<Int>()
        for (i in 0 until sorted.size - 1) {
            val length = sorted[i].startDate.until(sorted[i + 1].startDate, DateTimeUnit.DAY).toInt()
            if (length >= 14) {
                cycleLengths.add(length)
            }
        }

        val effectiveCycleLength = if (cycleLengths.isNotEmpty()) {
            cycleLengths.sum() / cycleLengths.size
        } else {
            cycleLength
        }

        val avgPeriodLength = averagePeriodLength(records)
        val lastStart = records.maxByOrNull { it.startDate }?.startDate ?: return emptyList()
        return (1..count).map { i ->
            val start = lastStart.plus(effectiveCycleLength * i, DateTimeUnit.DAY)
            PredictedCycle(start, avgPeriodLength?.let { start.plus(it - 1, DateTimeUnit.DAY) })
        }
    }


    private fun newId() = "record_${Clock.System.now().toEpochMilliseconds()}_${(0..9999).random()}"
}