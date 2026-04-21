package com.smartnoti.app.domain.usecase

import com.smartnoti.app.data.local.NotificationEntity
import com.smartnoti.app.domain.model.NotificationUiModel

/**
 * Decides how SILENT-classified notifications get grouped in the system tray.
 *
 * The policy is intentionally shape-agnostic — it accepts the raw Room entity and the
 * domain `NotificationUiModel` through the same contract so the listener (UI models) and
 * storage callers (entities) can share one source of truth. See
 * `docs/plans/2026-04-21-silent-tray-sender-grouping.md` Task 1 for scope.
 *
 * Grouping contract:
 * - If `sender` is non-null and non-blank, group by that trimmed sender name.
 *   Case is preserved so unicode (Korean / umlauts / etc.) stays intact.
 * - Otherwise fall back to `packageName` so non-messaging surfaces (news, promos, system
 *   alerts) still cluster sensibly by app.
 *
 * Note: the "MessagingStyle hint" from the plan is encoded implicitly by the listener — it is
 * the listener's responsibility to leave `sender` null for non-messaging notifications. Task 3
 * of the plan tightens that contract in the listener; this policy only reacts to what the row
 * carries.
 */
class SilentNotificationGroupingPolicy {

    fun groupKeyFor(entity: NotificationEntity): SilentGroupKey =
        keyFor(sender = entity.sender, packageName = entity.packageName)

    fun groupKeyFor(notification: NotificationUiModel): SilentGroupKey =
        keyFor(sender = notification.sender, packageName = notification.packageName)

    private fun keyFor(sender: String?, packageName: String): SilentGroupKey {
        val normalized = sender?.trim().orEmpty()
        return if (normalized.isNotEmpty()) {
            SilentGroupKey.Sender(normalized)
        } else {
            SilentGroupKey.App(packageName)
        }
    }
}

sealed class SilentGroupKey {
    data class Sender(val normalizedName: String) : SilentGroupKey()
    data class App(val packageName: String) : SilentGroupKey()
}
