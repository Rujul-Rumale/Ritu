package com.haodong.yimalaile.domain.person

/** Fallback display name for a person who was never given one (incl. the legacy migration). */
const val DEFAULT_PERSON_NAME = "Me"

/**
 * A single tracked person. Every menstrual record, prediction and
 * person-specific setting belongs to exactly one [Person].
 *
 * The [id] is stable and unique; the [name] may change freely and is
 * never used as the persistence key.
 */
data class Person(
    val id: String,
    val name: String,
    val cycleLength: Int,
    val periodLength: Int,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    /** Palette index used for dashboards / avatars. Purely cosmetic. */
    val colorIndex: Int = 0,
    /**
     * True when this person is the app owner themselves ("Me"), rather than
     * someone they track. At most one person can be marked this way; it drives
     * first-person copy and the single-person home routing.
     */
    val isForMe: Boolean = false,
)