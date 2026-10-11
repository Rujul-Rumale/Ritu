package com.haodong.yimalaile.infrastructure.persistence

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.haodong.yimalaile.domain.person.DEFAULT_PERSON_NAME
import com.haodong.yimalaile.domain.person.Person
import com.haodong.yimalaile.domain.person.PersonRepository
import kotlinx.coroutines.flow.first
import me.tatarka.inject.annotations.Inject
import kotlin.time.Clock

private val PEOPLE_KEY = stringPreferencesKey("people")

// Legacy global settings keys (same literal names SettingsRepository uses).
private val LEGACY_CYCLE_LENGTH_KEY = stringPreferencesKey("cycle_length")
private val LEGACY_PERIOD_DURATION_KEY = stringPreferencesKey("period_duration")

@Inject
class DataStorePersonRepository(
    private val dataStore: DataStore<Preferences>,
    private val recordsRepository: DataStoreRecordsRepository,
    private val dailyNoteRepository: DataStoreDailyNoteRepository,
) : PersonRepository {

    // Per-process guard so migration runs at most once.
    private var migrationChecked = false

    override suspend fun getPeople(): List<Person> {
        ensureMigrated()
        return loadPeople()
    }

    override suspend fun getPerson(id: String): Person? {
        ensureMigrated()
        return loadPeople().find { it.id == id }
    }

    override suspend fun insertPerson(person: Person): Boolean {
        val existing = loadPeople()
        if (existing.any { it.id == person.id }) return false
        savePeople(existing + person)
        return true
    }

    override suspend fun updatePerson(person: Person): Boolean {
        val existing = loadPeople()
        val index = existing.indexOfFirst { it.id == person.id }
        if (index == -1) return false
        val updated = existing.toMutableList()
        updated[index] = person
        savePeople(updated)
        return true
    }

    override suspend fun deletePerson(id: String) {
        // Destructive and deliberate: remove the person AND all of their data.
        recordsRepository.deleteAllRecordsForPerson(id)
        dailyNoteRepository.deleteAllNotesForPerson(id)
        savePeople(loadPeople().filterNot { it.id == id })
    }

    // ---------- Migration ----------

    /**
     * One-time migration of legacy single-person installations.
     *
     * If no people exist but menstrual records do (the old global store),
     * create a default "Me" person and assign every existing record to them,
     * preserving dates, sources and soft-delete flags. Prediction state is
     * derived, so nothing extra needs to be carried over.
     */
    private suspend fun ensureMigrated() {
        if (migrationChecked) return
        migrationChecked = true

        val prefs = dataStore.data.first()
        if (prefs[PEOPLE_KEY] != null) return

        val recordsJson = prefs[RECORDS_KEY]
        val hasLegacyRecords = recordsJson != null && recordsJson.trim().run {
            isNotBlank() && length > 2 && this != "[]" && this != ""
        }
        if (!hasLegacyRecords) return

        val now = Clock.System.now().toEpochMilliseconds()
        val cycleLength = prefs[LEGACY_CYCLE_LENGTH_KEY]?.toIntOrNull() ?: 28
        val periodLength = prefs[LEGACY_PERIOD_DURATION_KEY]?.toIntOrNull() ?: 5
        val me = Person(
            id = "person_default",
            name = DEFAULT_PERSON_NAME,
            cycleLength = cycleLength,
            periodLength = periodLength,
            createdAtEpochMillis = now,
            updatedAtEpochMillis = now,
            isForMe = true,
        )

        val records = recordsJson.toRecordList()
            .map { it.copy(personId = me.id) }

        dataStore.edit { p ->
            p[PEOPLE_KEY] = listOf(me).toPersonJson()
            p[RECORDS_KEY] = records.toRecordJson()
        }
    }

    // ---------- Serialization ----------

    private suspend fun loadPeople(): List<Person> {
        val json = dataStore.data.first()[PEOPLE_KEY] ?: return emptyList()
        return json.toPersonList()
    }

    private suspend fun savePeople(people: List<Person>) {
        dataStore.edit { prefs -> prefs[PEOPLE_KEY] = people.toPersonJson() }
    }
}

private fun List<Person>.toPersonJson(): String {
    return "[" + joinToString(",") { p ->
        val name = p.name.escapeJson()
        """{"id":"${p.id}","name":"$name","cycleLength":${p.cycleLength},"periodLength":${p.periodLength},"createdAt":${p.createdAtEpochMillis},"updatedAt":${p.updatedAtEpochMillis},"colorIndex":${p.colorIndex},"isForMe":${p.isForMe}}"""
    } + "]"
}

private fun String.toPersonList(): List<Person> {
    if (this == "[]" || isBlank()) return emptyList()
    return try {
        removeSurrounding("[", "]")
            .split("},{")
            .map { it.removePrefix("{").removeSuffix("}") }
            .mapNotNull { entry ->
                val map = entry.parseJsonObject()
                val id = map["id"] ?: return@mapNotNull null
                Person(
                    id = id,
                    name = map["name"] ?: "",
                    cycleLength = map["cycleLength"]?.toIntOrNull() ?: 28,
                    periodLength = map["periodLength"]?.toIntOrNull() ?: 5,
                    createdAtEpochMillis = map["createdAt"]?.toLongOrNull() ?: 0L,
                    updatedAtEpochMillis = map["updatedAt"]?.toLongOrNull() ?: 0L,
                    colorIndex = map["colorIndex"]?.toIntOrNull() ?: 0,
                    isForMe = map["isForMe"]?.toBooleanStrictOrNull() ?: false,
                )
            }
    } catch (_: Exception) {
        emptyList()
    }
}

/**
 * Tolerant key/value scanner for a single flat JSON object. Handles both quoted
 * string values (unescaping them) and unquoted numeric/boolean literals, so it
 * survives every shape [toPersonJson] has produced since the person store was
 * introduced.
 */
private fun String.parseJsonObject(): Map<String, String> {
    val map = mutableMapOf<String, String>()
    val len = length
    var i = 0
    while (i < len) {
        val keyOpen = indexOf('"', i)
        if (keyOpen < 0) break
        val keyClose = indexOf('"', keyOpen + 1)
        if (keyClose < 0) break
        val key = substring(keyOpen + 1, keyClose)
        val colon = indexOf(':', keyClose)
        if (colon < 0) break
        i = colon + 1
        while (i < len && (this[i] == ' ' || this[i] == '\t')) i++
        if (i >= len) break

        if (this[i] == '"') {
            val valueStart = i + 1
            var valueEnd = valueStart
            while (valueEnd < len) {
                if (this[valueEnd] == '"' && this[valueEnd - 1] != '\\') break
                valueEnd++
            }
            map[key] = substring(valueStart, valueEnd.coerceAtMost(len)).unescapeJson()
            i = if (valueEnd < len) valueEnd + 1 else len
        } else {
            var valueEnd = i
            while (valueEnd < len && this[valueEnd] != ',' && this[valueEnd] != '}') valueEnd++
            map[key] = substring(i, valueEnd)
            i = if (valueEnd < len) valueEnd + 1 else len
        }
    }
    return map
}

internal fun String.escapeJson(): String =
    replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")

internal fun String.unescapeJson(): String =
    replace("\\n", "\n").replace("\\\"", "\"").replace("\\\\", "\\")