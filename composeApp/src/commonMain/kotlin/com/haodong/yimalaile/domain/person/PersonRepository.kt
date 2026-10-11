package com.haodong.yimalaile.domain.person

interface PersonRepository {

    /**
     * All people, ordered by creation time. Triggers a one-time migration of
     * legacy single-person data (existing records are assigned to a default
     * "Me" person) if it has not happened yet.
     */
    suspend fun getPeople(): List<Person>

    suspend fun getPerson(id: String): Person?

    suspend fun insertPerson(person: Person): Boolean

    suspend fun updatePerson(person: Person): Boolean

    /**
     * Permanently remove the person together with every menstrual record and
     * daily note that belongs to them. Irreversible by design.
     */
    suspend fun deletePerson(id: String)
}