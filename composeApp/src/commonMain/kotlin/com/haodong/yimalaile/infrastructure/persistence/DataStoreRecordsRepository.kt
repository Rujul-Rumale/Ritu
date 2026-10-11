package com.haodong.yimalaile.infrastructure.persistence

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.haodong.yimalaile.domain.menstrual.*
import kotlinx.coroutines.flow.first
import me.tatarka.inject.annotations.Inject
import kotlin.time.Clock

internal val RECORDS_KEY = stringPreferencesKey("menstrual_records")

@Inject
class DataStoreRecordsRepository(
    private val dataStore: DataStore<Preferences>
) : RecordsRepository {

    override suspend fun insertRecord(record: MenstrualRecord): AddRecordResult {
        val existing = loadRecords()
        saveRecords(existing + record)
        return AddRecordResult.Success(record)
    }

    override suspend fun getAllRecords(personId: String): List<MenstrualRecord> =
        loadRecords().filter { it.personId == personId && !it.isDeleted }

    override suspend fun updateRecord(record: MenstrualRecord): Boolean {
        val existing = loadRecords()
        val index = existing.indexOfFirst { it.id == record.id && it.personId == record.personId }
        if (index == -1) return false
        val updated = existing.toMutableList()
        updated[index] = record
        saveRecords(updated)
        return true
    }

    override suspend fun deleteRecord(recordId: String, personId: String): Boolean {
        val existing = loadRecords()
        val index = existing.indexOfFirst { it.id == recordId && it.personId == personId }
        if (index == -1) return false
        val updated = existing.toMutableList()
        updated[index] = updated[index].copy(
            isDeleted = true,
            updatedAtEpochMillis = Clock.System.now().toEpochMilliseconds()
        )
        saveRecords(updated)
        return true
    }

    override suspend fun deleteAllRecordsForPerson(personId: String) {
        val existing = loadRecords()
        val remaining = existing.filterNot { it.personId == personId }
        if (remaining.size != existing.size) saveRecords(remaining)
    }

    override suspend fun clearAll() {
        dataStore.edit { prefs -> prefs.remove(RECORDS_KEY) }
    }

    /** Every stored record including soft-deleted ones — lifecycle, migration, export internals. */
    suspend fun loadAllRaw(): List<MenstrualRecord> = loadRecords()

    suspend fun saveRecordsRaw(records: List<MenstrualRecord>) = saveRecords(records)

    private suspend fun loadRecords(): List<MenstrualRecord> {
        val json = dataStore.data.first()[RECORDS_KEY] ?: return emptyList()
        return json.toRecordList()
    }

    private suspend fun saveRecords(records: List<MenstrualRecord>) {
        dataStore.edit { prefs ->
            prefs[RECORDS_KEY] = records.toRecordJson()
        }
    }
}