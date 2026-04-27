package com.smartnoti.app.notification

import com.smartnoti.app.domain.model.NotificationDecision
import com.smartnoti.app.domain.model.SilentMode

internal object SourceNotificationRoutingPolicy {
    fun route(
        decision: NotificationDecision,
        hidePersistentSourceNotification: Boolean,
        suppressSourceNotification: Boolean,
        @Suppress("UNUSED_PARAMETER") silentMode: SilentMode? = null,
    ): SourceNotificationRouting {
        return when (decision) {
            NotificationDecision.PRIORITY -> SourceNotificationRouting(
                cancelSourceNotification = false,
                notifyReplacementNotification = false,
            )
            // Plan `docs/plans/2026-04-28-fix-issue-511-cancel-source-on-replacement.md`
            // Task 3 (Option C invariant): every SILENT classification owned by
            // SmartNoti posts a replacement (the silent_group tray entry +
            // the user's Hidden inbox row are SmartNoti's user-visible surface
            // for SILENT) AND cancels the source. The previous SilentMode-
            // gated `keepSourceInTray` branch was the root cause of issue
            // #511 — Quiet Hours fired SILENT for Gmail, classifier returned
            // ARCHIVED, source stayed in tray alongside the silent_group
            // child for a 4–6-entry duplicate Railway pile. SilentMode is
            // still honoured downstream for inbox-tab routing (the persisted
            // row still lands in 보관 중 vs 처리됨), but it no longer gates
            // the source-tray cancel.
            //
            // The `suppressSourceNotification` parameter — sourced from
            // `NotificationSuppressionPolicy.shouldSuppressSourceNotification`
            // gated by the user's `suppressedSourceApps` list — is now
            // bypassed for SILENT. The list is preserved as "next-time"
            // membership state (auto-expansion still records it for
            // diagnostics), but it no longer prevents the cancel.
            NotificationDecision.SILENT -> SourceNotificationRouting(
                cancelSourceNotification = true,
                notifyReplacementNotification = true,
            )
            // Plan `docs/plans/2026-04-28-fix-issue-511-cancel-source-on-replacement.md`
            // Task 3: DIGEST already cancelled when the user opted into
            // `suppressDigestAndSilent` AND the package was on the suppress
            // list. The Option C invariant lifts both gates — every DIGEST
            // SmartNoti classifies posts a replacement and cancels the
            // source. The `hidePersistentSourceNotification` /
            // `suppressSourceNotification` parameters are kept in the
            // signature so callers do not break, but no longer participate
            // in the routing decision for DIGEST (auto-expansion still
            // records list membership for the next notification).
            NotificationDecision.DIGEST -> SourceNotificationRouting(
                cancelSourceNotification = true,
                notifyReplacementNotification = true,
            )
            // IGNORE — plan `2026-04-21-ignore-tier-fourth-decision` Task 4
            // formalized the behaviour: cancel the source tray entry
            // unconditionally (user asked to delete), never post a
            // replacement alert.
            NotificationDecision.IGNORE -> SourceNotificationRouting(
                cancelSourceNotification = true,
                notifyReplacementNotification = false,
            )
        }
    }
}

internal data class SourceNotificationRouting(
    val cancelSourceNotification: Boolean,
    val notifyReplacementNotification: Boolean,
)
