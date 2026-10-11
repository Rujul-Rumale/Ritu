package com.haodong.yimalaile.fakes

import com.haodong.yimalaile.domain.person.Person
import com.haodong.yimalaile.domain.person.PersonRepository

/** In-memory [PersonRepository] for tests / previews / screenshot data. */
class FakePersonRepository(
    private val people: MutableList<Person> = mutableListOf(),
) : PersonRepository {

    override suspend fun getPeople(): List<Person> =
        people.sortedBy { it.createdAtEpochMillis }

    override suspend fun getPerson(id: String): Person? =
        people.firstOrNull { it.id == id }

    override suspend fun insertPerson(person: Person): Boolean {
        if (people.any { it.id == person.id }) return false
        people.add(person)
        return true
    }

    override suspend fun updatePerson(person: Person): Boolean {
        val idx = people.indexOfFirst { it.id == person.id }
        if (idx == -1) return false
        people[idx] = person
        return true
    }

    override suspend fun deletePerson(id: String) {
        people.removeAll { it.id == id }
    }
}