package com.haodong.yimalaile.domain.health

import com.haodong.yimalaile.domain.person.Person

/**
 * Single rule for all platform health sync (Health Connect / HealthKit via
 * HealthKMP, and any future Google Health cloud API):
 *
 * platform stores are single-user, so only the owner's own profile
 * ([Person.isForMe]) may sync. Every other person stays local-only.
 */
fun isHealthSyncAllowed(person: Person?): Boolean = person?.isForMe == true

/**
 * Outcome of a gated sync attempt. Checked in this order:
 * gate (isForMe) -> user toggle -> platform availability.
 */
sealed interface HealthSyncResult {
    /** Person is not the owner profile — sync is disabled by policy. */
    data object DisabledForOtherPerson : HealthSyncResult

    /** Owner profile, but the user left the health toggle off. */
    data object DisabledByUser : HealthSyncResult

    /** Allowed but the platform SDK / permission is not available yet. */
    data class Unavailable(val reason: String) : HealthSyncResult

    /** Allowed and completed. Counts are best-effort for UI feedback. */
    data class Success(val imported: Int, val exported: Int) : HealthSyncResult
}
