package com.haodong.yimalaile.domain.health

import com.haodong.yimalaile.domain.menstrual.RecordSource
import com.haodong.yimalaile.domain.menstrual.RecordsRepository
import com.haodong.yimalaile.domain.person.Person
import com.haodong.yimalaile.domain.settings.SettingsRepository
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import me.tatarka.inject.annotations.Inject
import kotlin.time.Instant

/**
 * Person-scoped platform health sync (Health Connect / HealthKit via HealthKMP).
 *
 * Policy: platform health stores are single-user with no per-person partition,
 * so only the owner's own profile ([Person.isForMe]) may sync. Any other
 * person gets [HealthSyncResult.DisabledForOtherPerson] and never reaches the
 * platform SDK — their history stays local-only.
 *
 * Imported records are tagged [RecordSource.HEALTH_IMPORT] and overlapping
 * ranges are skipped, so a re-sync never duplicates or clobbers manual data.
 * Export only sends manual records with a confirmed end date.
 */
@Inject
class HealthSyncManager(
    private val healthService: HealthService,
    private val recordsRepository: RecordsRepository,
    private val settingsRepository: SettingsRepository,
) {

    suspend fun getAuthStatus(): HealthAuthStatus = healthService.getAuthStatus()

    suspend fun requestAuthorization(): Boolean = healthService.requestAuthorization()

    suspend fun sync(person: Person): HealthSyncResult {
        if (!isHealthSyncAllowed(person)) return HealthSyncResult.DisabledForOtherPerson
        if (!settingsRepository.isHealthSyncEnabled()) return HealthSyncResult.DisabledByUser
        if (healthService.getAuthStatus() != HealthAuthStatus.AUTHORIZED) {
            return HealthSyncResult.Unavailable("Health access not authorized")
        }

        val tz = TimeZone.currentSystemDefault()
        val now = kotlin.time.Clock.System.now()
        val today = now.toLocalDateTime(tz).date

        val lastSyncMillis = settingsRepository.getHealthLastSync()
        val startDate = if (lastSyncMillis > 0L) {
            Instant.fromEpochMilliseconds(lastSyncMillis).toLocalDateTime(tz).date
        } else {
            today.minus(1, DateTimeUnit.YEAR)
        }

        val imported = importFromHealth(person.id, startDate, today)
        val exported = exportToHealth(person.id)

        settingsRepository.setHealthLastSync(now.toEpochMilliseconds())
        return HealthSyncResult.Success(imported = imported, exported = exported)
    }

    suspend fun exportNow(person: Person): HealthSyncResult {
        if (!isHealthSyncAllowed(person)) return HealthSyncResult.DisabledForOtherPerson
        if (!settingsRepository.isHealthSyncEnabled()) return HealthSyncResult.DisabledByUser
        if (healthService.getAuthStatus() != HealthAuthStatus.AUTHORIZED) {
            return HealthSyncResult.Unavailable("Health access not authorized")
        }
        val exported = exportToHealth(person.id)
        return HealthSyncResult.Success(imported = 0, exported = exported)
    }

    /** Defensive helper for call sites holding a [Person]. */
    suspend fun isSyncEnabledFor(person: Person): Boolean =
        isHealthSyncAllowed(person) && settingsRepository.isHealthSyncEnabled()

    private suspend fun importFromHealth(
        personId: String,
        startDate: kotlinx.datetime.LocalDate,
        endDate: kotlinx.datetime.LocalDate,
    ): Int {
        val healthRecords = healthService.readPeriods(personId, startDate, endDate)
        if (healthRecords.isEmpty()) return 0

        val existingRecords = recordsRepository.getAllRecords(personId)
        var importedCount = 0

        for (record in healthRecords) {
            val overlaps = existingRecords.any { existing ->
                val existingEnd = existing.endDate ?: existing.startDate
                val recordEnd = record.endDate ?: record.startDate
                existing.startDate <= recordEnd && record.startDate <= existingEnd
            }
            if (!overlaps) {
                recordsRepository.insertRecord(record)
                importedCount++
            }
        }
        return importedCount
    }

    private suspend fun exportToHealth(personId: String): Int {
        val records = recordsRepository.getAllRecords(personId)
            .filter { it.source != RecordSource.HEALTH_IMPORT && it.endDate != null }
        if (records.isEmpty()) return 0
        healthService.writePeriods(records)
        return records.size
    }
}
