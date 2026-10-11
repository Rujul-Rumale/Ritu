package com.haodong.yimalaile.domain.menstrual

interface RecordsRepository {

    /**
     * Insert a record. The record must carry a valid [MenstrualRecord.personId].
     */
    suspend fun insertRecord(record: MenstrualRecord): AddRecordResult

    /**
     * Every non-deleted record belonging to [personId], unsorted.
     * Records from other people are never returned.
     */
    suspend fun getAllRecords(personId: String): List<MenstrualRecord>

    suspend fun updateRecord(record: MenstrualRecord): Boolean

    /** Soft-delete [recordId] if it belongs to [personId]. */
    suspend fun deleteRecord(recordId: String, personId: String): Boolean

    /**
     * Hard-delete every record belonging to [personId]. Used when a person is
     * removed; deliberately not scoped to a single record.
     */
    suspend fun deleteAllRecordsForPerson(personId: String)

    /**
     * Wipe the whole records store. Only used by the global "clear all data"
     * reset, never by normal person/record operations.
     */
    suspend fun clearAll()
}