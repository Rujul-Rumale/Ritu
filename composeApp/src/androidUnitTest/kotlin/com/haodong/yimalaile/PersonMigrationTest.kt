package com.haodong.yimalaile

import android.app.Application
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import com.haodong.yimalaile.domain.menstrual.DailyRecord
import kotlinx.coroutines.flow.first
import com.haodong.yimalaile.domain.menstrual.MenstrualRecord
import com.haodong.yimalaile.domain.menstrual.RecordSource
import com.haodong.yimalaile.domain.person.DEFAULT_PERSON_NAME
import com.haodong.yimalaile.domain.settings.SettingsRepository
import com.haodong.yimalaile.infrastructure.persistence.DataStoreDailyNoteRepository
import com.haodong.yimalaile.infrastructure.persistence.DataStorePersonRepository
import com.haodong.yimalaile.infrastructure.persistence.DataStoreRecordsRepository
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * One-time migration of legacy single-person installations: a default "Me"
 * person is created and every existing global record is assigned to them,
 * preserving dates, sources and deletion flags.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PersonMigrationTest {

    private fun legacyRecord(
        id: String,
        startDate: LocalDate,
        endDate: LocalDate,
    ) = MenstrualRecord(
        id = id,
        personId = "", // legacy store had no person concept
        startDate = startDate,
        endDate = endDate,
        endConfirmed = true,
        dailyRecords = listOf(
            DailyRecord(date = startDate)
        ),
        createdAtEpochMillis = 1700000000000L,
        updatedAtEpochMillis = 1700000000000L,
        source = RecordSource.MANUAL,
    )

    @Test
    fun insertAndReadPersonRoundTrip() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val dataStore = PreferenceDataStoreFactory.create {
            File(context.filesDir, "roundtrip_${System.nanoTime()}.preferences_pb")
        }
        runBlocking {
            val recordsRepository = DataStoreRecordsRepository(dataStore)
            val personRepository = DataStorePersonRepository(
                dataStore,
                recordsRepository,
                DataStoreDailyNoteRepository(dataStore)
            )
            val nameWithEscapes = "娜娜\"quote\\back"
            assertTrue(
                personRepository.insertPerson(
                    com.haodong.yimalaile.domain.person.Person(
                        id = "custom",
                        name = nameWithEscapes,
                        cycleLength = 31,
                        periodLength = 6,
                        createdAtEpochMillis = 12345L,
                        updatedAtEpochMillis = 67890L,
                        colorIndex = 2,
                    )
                )
            )

            val people = personRepository.getPeople()
            assertEquals(1, people.size)
            val read = people.single()
            assertEquals("custom", read.id)
            assertEquals(nameWithEscapes, read.name)
            assertEquals(31, read.cycleLength)
            assertEquals(6, read.periodLength)
            assertEquals(12345L, read.createdAtEpochMillis)
            assertEquals(67890L, read.updatedAtEpochMillis)
            assertEquals(2, read.colorIndex)
        }
    }

    @Test
    fun legacyRecordsMigrateToDefaultMePerson() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val dataStore = PreferenceDataStoreFactory.create {
            File(context.filesDir, "migration_${System.nanoTime()}.preferences_pb")
        }

        runBlocking {
            val recordsRepository = DataStoreRecordsRepository(dataStore)
            val settings = SettingsRepository(dataStore)

            // A pre-existing installation with data and custom lengths.
            settings.setCycleLength(31)
            settings.setPeriodDuration(6)
            recordsRepository.saveRecordsRaw(
                listOf(
                    legacyRecord("r1", LocalDate(2026, 1, 1), LocalDate(2026, 1, 5)),
                    legacyRecord("r2", LocalDate(2026, 2, 1), LocalDate(2026, 2, 7)),
                )
            )

            val personRepository = DataStorePersonRepository(
                dataStore,
                recordsRepository,
                DataStoreDailyNoteRepository(dataStore)
            )

            val people = personRepository.getPeople()
            assertEquals(1, people.size)
            val me = people.single()
            assertEquals("person_default", me.id)
            assertEquals(DEFAULT_PERSON_NAME, me.name)
            assertEquals(31, me.cycleLength)
            assertEquals(6, me.periodLength)

            val migrated = recordsRepository.getAllRecords("person_default")
            assertEquals(2, migrated.size)
            assertTrue(migrated.all { it.personId == "person_default" })
            assertEquals(LocalDate(2026, 1, 1), migrated.first { it.id == "r1" }.startDate)
            assertEquals(RecordSource.MANUAL, migrated.first { it.id == "r1" }.source)

            // Properties keep correct even though inherited from a different
            // record stream — sanity check that deletion flags survived.
            assertFalse(migrated.any { it.isDeleted })

            // Idempotent: running again must not create a second person.
            assertEquals(1, personRepository.getPeople().size)
        }
    }

    @Test
    fun noMigrationWhenPeopleAlreadyExist() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val dataStore = PreferenceDataStoreFactory.create {
            File(context.filesDir, "migration_${System.nanoTime()}.preferences_pb")
        }

        runBlocking {
            val recordsRepository = DataStoreRecordsRepository(dataStore)
            val personRepository = DataStorePersonRepository(
                dataStore,
                recordsRepository,
                DataStoreDailyNoteRepository(dataStore)
            )

            personRepository.insertPerson(
                com.haodong.yimalaile.domain.person.Person(
                    id = "custom",
                    name = "Anna",
                    cycleLength = 29,
                    periodLength = 5,
                    createdAtEpochMillis = 1700000000000L,
                    updatedAtEpochMillis = 1700000000000L,
                )
            )
            recordsRepository.saveRecordsRaw(listOf(legacyRecord("r9", LocalDate(2026, 3, 1), LocalDate(2026, 3, 4))))

            val people = personRepository.getPeople()
            assertEquals(1, people.size)
            assertEquals("custom", people.single().id)
            assertEquals(0, recordsRepository.getAllRecords("person_default").size)
        }
    }

    @Test
    fun deletionCascadesToRecordsAndNotes() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val dataStore = PreferenceDataStoreFactory.create {
            File(context.filesDir, "migration_${System.nanoTime()}.preferences_pb")
        }

        runBlocking {
            val recordsRepository = DataStoreRecordsRepository(dataStore)
            val notesRepository = DataStoreDailyNoteRepository(dataStore)
            val personRepository = DataStorePersonRepository(dataStore, recordsRepository, notesRepository)

            personRepository.insertPerson(
                com.haodong.yimalaile.domain.person.Person(
                    id = "victim",
                    name = "Victim",
                    cycleLength = 28,
                    periodLength = 5,
                    createdAtEpochMillis = 1L,
                    updatedAtEpochMillis = 1L,
                )
            )
            personRepository.insertPerson(
                com.haodong.yimalaile.domain.person.Person(
                    id = "bak",
                    name = "Bak",
                    cycleLength = 28,
                    periodLength = 5,
                    createdAtEpochMillis = 2L,
                    updatedAtEpochMillis = 2L,
                )
            )
            recordsRepository.insertRecord(legacyRecord("v1", LocalDate(2026, 1, 1), LocalDate(2026, 1, 5)).copy(personId = "victim"))
            recordsRepository.insertRecord(legacyRecord("b1", LocalDate(2026, 1, 1), LocalDate(2026, 1, 5)).copy(personId = "bak"))
            notesRepository.saveNote(
                com.haodong.yimalaile.domain.menstrual.DailyNote(
                    personId = "victim",
                    date = LocalDate(2026, 3, 1),
                    mood = com.haodong.yimalaile.domain.menstrual.Mood.SAD,
                    notes = "should vanish",
                )
            )

            personRepository.deletePerson("victim")

            assertEquals(0, recordsRepository.getAllRecords("victim").size)
            assertEquals(1, recordsRepository.getAllRecords("bak").size)
            assertEquals(null, notesRepository.getNote("victim", LocalDate(2026, 3, 1)))
            assertEquals(1, personRepository.getPeople().size)
            assertEquals("bak", personRepository.getPeople().single().id)
        }
    }
}