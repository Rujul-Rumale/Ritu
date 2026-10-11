package com.haodong.yimalaile.domain.menstrual

import kotlinx.datetime.LocalDate

interface DailyNoteRepository {
    suspend fun getNote(personId: String, date: LocalDate): DailyNote?
    suspend fun getAllNotes(personId: String): List<DailyNote>
    suspend fun saveNote(note: DailyNote)
    suspend fun deleteNote(personId: String, date: LocalDate)
    suspend fun deleteAllNotesForPerson(personId: String)
}