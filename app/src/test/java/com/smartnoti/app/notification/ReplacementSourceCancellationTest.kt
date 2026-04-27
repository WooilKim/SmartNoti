package com.smartnoti.app.notification

import com.smartnoti.app.data.settings.SmartNotiSettings
import com.smartnoti.app.domain.model.AlertLevel
import com.smartnoti.app.domain.model.LockScreenVisibilityMode
import com.smartnoti.app.domain.model.NotificationStatusUi
import com.smartnoti.app.domain.model.NotificationUiModel
import com.smartnoti.app.domain.model.SourceNotificationSuppressionState
import com.smartnoti.app.domain.model.VibrationMode
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Failing tests for plan
 * `docs/plans/2026-04-28-fix-issue-511-cancel-source-on-replacement.md`
 * Task 1 (Issue #511, P0 release-blocker) — pin the new invariant that
 * **whenever SmartNoti posts a replacement, it ALSO cancels the source tray
 * entry in the same transaction**, regardless of whether the source app is
 * on the user's `suppressedSourceApps` list.
 *
 * Wrapper-confirmed via Railway emails on R3CY2058DLJ: SmartNoti posts the
 * silent-group replacement (Quiet Hours fired → SILENT classification) but
 * does NOT cancel the source because today the cancel gate is
 * `NotificationSuppressionPolicy.shouldSuppressSourceNotification(...)`,
 * which short-circuits when `packageName ∉ suppressedSourceApps` AND
 * `sourceSuppressionState = NOT_CONFIGURED`. Result: 4–6 duplicate entries
 * (Gmail original × N + group summary + SmartNoti silent_group × M) in the
 * tray simultaneously.
 *
 * Option C (per plan Architecture) flips the contract: replacement-builder
 * sites unconditionally cancel the source after `notify(replacement)`
 * succeeds. The `suppressSourceForDigestAndSilent` flag and
 * `suppressedSourceApps` list become persistence/UX state for "next time"
 * only; they no longer gate the cancel.
 *
 * RED expectations against current `main`:
 *  - `silent_quiet_hours_railway_fixture_with_empty_suppress_list_cancels_source`
 *    will fail because SILENT under empty `suppressedSourceApps` currently
 *    routes to `cancelSource = false` AND `notifyReplacement = false` — no
 *    replacement is posted, no cancel happens, source stays in tray. The
 *    Task 3 fix (SmartNoti owns the replacement post for SILENT-on-Quiet-
 *    Hours and cancels source) makes both calls happen.
 *  - `digest_with_replacement_cancels_source` already passes the cancel half
 *    via `NotificationSuppressionPolicy` when the package is on the suppress
 *    list, but this test pins the broader invariant — no list dependency.
 *  - `silent_with_replacement_persistent_bypass_cancels_source` exercises the
 *    SILENT-with-replacement path that exists today through persistent-source
 *    hiding bypass. RED today because SILENT routing returns
 *    `notifyReplacement = false` regardless of the persistent bypass branch.
 *  - `priority_pass_through_does_not_cancel_or_replace` is the regression
 *    guard for Task 4 — must stay GREEN through Task 3 fix.
 *
 * Tests reuse the [RecordingSourceTrayActions] fake from
 * [NotificationDecisionPipelineCharacterizationTest] so the assertions hit
 * the production [NotificationDecisionPipeline] surface end-to-end. No new
 * fake interface is introduced here; the plan's `SourceCancellationService`
 * naming is internal to Task 3 implementation. From Task 1's perspective the
 * existing `SourceTrayActions.cancelSource` IS the cancellation service
 * port, and the contract being pinned is "every postReplacement call is
 * paired with a cancelSource call for the same sourceEntryKey".
 */
class ReplacementSourceCancellationTest {

    @Test
    fun silent_quiet_hours_railway_fixture_with_empty_suppress_list_cancels_source() = runTest {
        // Issue #511 fixture: Quiet Hours is active, classifier returns SILENT,
        // user has NOT opted into per-app suppression (empty
        // `suppressedSourceApps`, `suppressSourceForDigestAndSilent = false`).
        // sourceSuppressionState is NOT_CONFIGURED on input — exactly the
        // Railway DB row shape observed in the wild.
        val actions = RecordingSourceTrayActions()
        val pipeline = NotificationDecisionPipeline(actions)

        val baseNotification = railwayNotification(
            id = "RailwayBuildFailed",
            status = NotificationStatusUi.SILENT,
            sourceSuppressionState = SourceNotificationSuppressionState.NOT_CONFIGURED,
        )
        val sourceKey = "0|com.google.android.gm|7|RailwayBuildFailed|10001"

        pipeline.dispatch(
            NotificationDecisionPipeline.DispatchInput(
                baseNotification = baseNotification,
                sourceEntryKey = sourceKey,
                packageName = GMAIL_PACKAGE,
                appName = "Gmail",
                postedAtMillis = 1_700_000_000_000L,
                contentSignature = "sig-railway-build",
                settings = settings(
                    suppressSourceForDigestAndSilent = false,
                    suppressedSourceApps = emptySet(),
                ),
                isPersistent = false,
                shouldBypassPersistentHiding = false,
                isProtectedSourceNotification = false,
            ),
        )

        // Option C invariant: whenever a replacement WOULD reach the user
        // (and SILENT under Quiet Hours does — the silent-group tray surface
        // owned by SilentHiddenSummaryNotifier is the user-visible
        // replacement), the source MUST be cancelled by the same routing
        // decision. Today the pipeline neither posts a pipeline-level
        // replacement nor cancels — both halves of the invariant are RED.
        assertEquals(
            "SILENT under Quiet Hours must cancel the source tray entry exactly once",
            listOf(sourceKey),
            actions.cancelledKeys,
        )
        // The replacement-post path may still be owned by the silent-group
        // observer rather than the pipeline itself; the Task 3 fix decides
        // which surface owns it. Either way, the row MUST record that a
        // replacement is in flight so the migration runner (Task 5) can
        // recover ad-hoc trapped duplicates.
        val saved = actions.savedNotifications.single()
        assertTrue(
            "SILENT-with-replacement row must record replacementNotificationIssued = true " +
                "so MigrateOrphanedSourceCancellationRunner can find trapped duplicates",
            saved.notification.replacementNotificationIssued,
        )
    }

    @Test
    fun digest_with_replacement_cancels_source_regardless_of_suppress_list() = runTest {
        // DIGEST today already cancels the source because the user opted
        // into `suppressSourceForDigestAndSilent`. This test pins the
        // BROADER invariant: even if the user has NOT opted in (empty
        // suppress list, flag false), DIGEST must still cancel the source
        // when SmartNoti posts a replacement. The plan promises a single
        // cancel-after-post path that no longer depends on the user's
        // suppress list.
        val actions = RecordingSourceTrayActions()
        val pipeline = NotificationDecisionPipeline(actions)

        val baseNotification = railwayNotification(
            id = "RailwayDeployCrashed",
            status = NotificationStatusUi.DIGEST,
            sourceSuppressionState = SourceNotificationSuppressionState.NOT_CONFIGURED,
        )
        val sourceKey = "0|com.google.android.gm|8|RailwayDeployCrashed|10002"

        pipeline.dispatch(
            NotificationDecisionPipeline.DispatchInput(
                baseNotification = baseNotification,
                sourceEntryKey = sourceKey,
                packageName = GMAIL_PACKAGE,
                appName = "Gmail",
                postedAtMillis = 1_700_000_000_001L,
                contentSignature = "sig-railway-deploy",
                settings = settings(
                    suppressSourceForDigestAndSilent = false,
                    suppressedSourceApps = emptySet(),
                    hidePersistentSourceNotifications = false,
                ),
                isPersistent = false,
                shouldBypassPersistentHiding = false,
                isProtectedSourceNotification = false,
            ),
        )

        // Today (RED): DIGEST + empty suppress list + flag off → routing
        // returns cancel=false, replacement=false. Task 3 must flip both
        // halves so the invariant holds across all replacement-builder
        // sites.
        assertEquals(
            "DIGEST must cancel the source whenever a replacement is posted, " +
                "independent of suppress-list membership",
            listOf(sourceKey),
            actions.cancelledKeys,
        )
        assertEquals(
            "DIGEST must post the replacement so the user still sees the digest",
            1,
            actions.replacementCalls,
        )
        val saved = actions.savedNotifications.single()
        assertTrue(saved.notification.replacementNotificationIssued)
    }

    @Test
    fun silent_with_replacement_persistent_bypass_cancels_source() = runTest {
        // Coverage for the SILENT-with-replacement code path that already
        // exists today via persistent-source hiding bypass: the listener
        // forwards `shouldBypassPersistentHiding = true` and the routing
        // policy degrades to legacy `silentMode = ARCHIVED` with a
        // replacement post. The cancel-after-post invariant still applies —
        // SmartNoti owning the replacement means the source MUST go.
        val actions = RecordingSourceTrayActions()
        val pipeline = NotificationDecisionPipeline(actions)

        val baseNotification = railwayNotification(
            id = "RailwaySilentReplacement",
            status = NotificationStatusUi.SILENT,
            sourceSuppressionState = SourceNotificationSuppressionState.NOT_CONFIGURED,
        )
        val sourceKey = "0|com.google.android.gm|9|RailwaySilentReplacement|10003"

        pipeline.dispatch(
            NotificationDecisionPipeline.DispatchInput(
                baseNotification = baseNotification,
                sourceEntryKey = sourceKey,
                packageName = GMAIL_PACKAGE,
                appName = "Gmail",
                postedAtMillis = 1_700_000_000_002L,
                contentSignature = "sig-railway-silent",
                // Settings shape that triggers SILENT-with-replacement today:
                // suppress flag ON + package ON the suppress list. Even with
                // those user opt-ins, the current routing policy returns
                // notifyReplacement=false for SILENT (only DIGEST gets
                // replacement). The plan unifies SILENT into the same
                // post-then-cancel invariant.
                settings = settings(
                    suppressSourceForDigestAndSilent = true,
                    suppressedSourceApps = setOf(GMAIL_PACKAGE),
                ),
                isPersistent = false,
                shouldBypassPersistentHiding = false,
                isProtectedSourceNotification = false,
            ),
        )

        assertEquals(
            "SILENT-with-replacement must cancel the source",
            listOf(sourceKey),
            actions.cancelledKeys,
        )
        assertEquals(
            "SILENT must post the replacement so the user still sees the silent-group entry",
            1,
            actions.replacementCalls,
        )
        val saved = actions.savedNotifications.single()
        assertTrue(saved.notification.replacementNotificationIssued)
    }

    @Test
    fun priority_pass_through_does_not_cancel_or_replace() = runTest {
        // Regression guard for Task 4: PRIORITY routing leaves the source
        // alone (no cancel) AND posts no replacement. The Task 3 fix MUST
        // NOT affect this branch — PRIORITY pass-through is the user's
        // explicit "let it through unmodified" contract. This test stays
        // GREEN today and MUST stay GREEN after the cancel-after-post fix.
        val actions = RecordingSourceTrayActions()
        val pipeline = NotificationDecisionPipeline(actions)

        val baseNotification = railwayNotification(
            id = "RailwayCriticalAlert",
            status = NotificationStatusUi.PRIORITY,
            sourceSuppressionState = SourceNotificationSuppressionState.NOT_CONFIGURED,
        )
        val sourceKey = "0|com.google.android.gm|10|RailwayCriticalAlert|10004"

        pipeline.dispatch(
            NotificationDecisionPipeline.DispatchInput(
                baseNotification = baseNotification,
                sourceEntryKey = sourceKey,
                packageName = GMAIL_PACKAGE,
                appName = "Gmail",
                postedAtMillis = 1_700_000_000_003L,
                contentSignature = "sig-railway-priority",
                // Even with the most aggressive suppress-flag-and-list
                // settings, PRIORITY MUST pass through untouched.
                settings = settings(
                    suppressSourceForDigestAndSilent = true,
                    suppressedSourceApps = setOf(GMAIL_PACKAGE),
                    hidePersistentSourceNotifications = true,
                ),
                isPersistent = false,
                shouldBypassPersistentHiding = false,
                isProtectedSourceNotification = false,
            ),
        )

        assertTrue(
            "PRIORITY pass-through must NEVER cancel the source — that is the " +
                "user's explicit contract for letting the alert through",
            actions.cancelledKeys.isEmpty(),
        )
        assertEquals(
            "PRIORITY pass-through must NEVER post a replacement",
            0,
            actions.replacementCalls,
        )
        val saved = actions.savedNotifications.single()
        assertEquals(
            SourceNotificationSuppressionState.PRIORITY_KEPT,
            saved.notification.sourceSuppressionState,
        )
    }

    // -- Test scaffolding ----------------------------------------------------

    private fun railwayNotification(
        id: String,
        status: NotificationStatusUi,
        sourceSuppressionState: SourceNotificationSuppressionState,
    ): NotificationUiModel = NotificationUiModel(
        id = id,
        appName = "Gmail",
        packageName = GMAIL_PACKAGE,
        sender = "Railway",
        title = "Railway",
        body = "Build failed for stock-dashboard",
        receivedAtLabel = "방금",
        status = status,
        reasonTags = emptyList(),
        score = null,
        isBundled = false,
        isPersistent = false,
        alertLevel = AlertLevel.NONE,
        vibrationMode = VibrationMode.OFF,
        headsUpEnabled = false,
        lockScreenVisibility = LockScreenVisibilityMode.SECRET,
        sourceSuppressionState = sourceSuppressionState,
        replacementNotificationIssued = false,
    )

    private fun settings(
        suppressSourceForDigestAndSilent: Boolean = false,
        suppressedSourceApps: Set<String> = emptySet(),
        suppressedSourceAppsExcluded: Set<String> = emptySet(),
        hidePersistentSourceNotifications: Boolean = false,
    ): SmartNotiSettings = SmartNotiSettings(
        suppressSourceForDigestAndSilent = suppressSourceForDigestAndSilent,
        suppressedSourceApps = suppressedSourceApps,
        suppressedSourceAppsExcluded = suppressedSourceAppsExcluded,
        hidePersistentSourceNotifications = hidePersistentSourceNotifications,
    )

    private companion object {
        const val GMAIL_PACKAGE = "com.google.android.gm"
    }
}
