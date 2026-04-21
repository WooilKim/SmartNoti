package com.smartnoti.app.domain.usecase

import com.smartnoti.app.data.local.NotificationEntity

/**
 * Decides how SILENT-classified notifications get grouped in the system tray.
 *
 * The policy is intentionally entity-only so the same rule can be reused both in the listener
 * (tray replacement post) and on the Hidden screen (in-app grouping). See
 * `docs/plans/2026-04-21-silent-tray-sender-grouping.md` Task 1 for scope.
 *
 * Grouping contract:
 * - If [NotificationEntity.sender] is non-null and non-blank, group by that trimmed sender name.
 *   Case is preserved so unicode (Korean / umlauts / etc.) stays intact.
 * - Otherwise fall back to [NotificationEntity.packageName] so non-messaging surfaces (news,
 *   promos, system alerts) still cluster sensibly by app.
 *
 * Note: the "MessagingStyle hint" from the plan is encoded implicitly by the listener — it is the
 * listener's responsibility to leave `sender` null for non-messaging notifications. Task 3 of the
 * plan tightens that contract in the listener; this policy only reacts to what the entity carries.
 */
class SilentNotificationGroupingPolicy {

    fun groupKeyFor(entity: NotificationEntity): SilentGroupKey {
        val normalized = entity.sender?.trim().orEmpty()
        return if (normalized.isNotEmpty()) {
            SilentGroupKey.Sender(normalized)
        } else {
            SilentGroupKey.App(entity.packageName)
        }
    }
}

sealed class SilentGroupKey {
    data class Sender(val normalizedName: String) : SilentGroupKey()
    data class App(val packageName: String) : SilentGroupKey()
}
