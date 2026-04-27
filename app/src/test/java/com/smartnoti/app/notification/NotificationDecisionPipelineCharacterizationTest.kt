package com.smartnoti.app.notification

import com.smartnoti.app.data.settings.SmartNotiSettings
import com.smartnoti.app.domain.model.AlertLevel
import com.smartnoti.app.domain.model.LockScreenVisibilityMode
import com.smartnoti.app.domain.model.NotificationDecision
import com.smartnoti.app.domain.model.NotificationStatusUi
import com.smartnoti.app.domain.model.NotificationUiModel
import com.smartnoti.app.domain.model.SilentMode
import com.smartnoti.app.domain.model.SourceNotificationSuppressionState
import com.smartnoti.app.domain.model.VibrationMode
import com.smartnoti.app.domain.model.toDecision
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Plan `2026-04-27-refactor-listener-process-notification-extract.md` Task 1.
 *
 * Pins the production behavior of the post-classifier "decision → side-effect" branch
 * inside `SmartNotiNotificationListenerService.processNotification` (lines ~336-463
 * at the time of this test's authoring). Task 2 will extract this branch into a
 * `NotificationDecisionPipeline.dispatch(...)` helper; these tests must keep passing
 * unchanged after that extraction so the carve-out is provably behavior-preserving.
 *
 * The pipeline under test is defined inline in this file and mirrors the exact
 * statement order of the production code so the characterization is faithful.
 * Once Task 2 lands, swap [InlineDecisionPipeline] for the real
 * `NotificationDecisionPipeline` and the assertions below stay intact.
 */
class NotificationDecisionPipelineCharacterizationTest {

    @Test
    fun ignore_decision_cancels_source_saves_with_cancel_attempted_and_no_replacement() = runTest {
        val actions = RecordingSourceTrayActions()
        val pipeline = InlineDecisionPipeline(actions)

        val baseNotification = baseNotification(status = NotificationStatusUi.IGNORE)

        pipeline.dispatch(
            DispatchInput(
                baseNotification = baseNotification,
                sourceEntryKey = "com.example|123",
                packageName = "com.example",
                appName = "Example",
                postedAtMillis = 1_000L,
                contentSignature = "sig",
                settings = settings(),
                isPersistent = false,
                shouldBypassPersistentHiding = false,
                isProtectedSourceNotification = false,
            )
        )

        assertEquals(listOf("com.example|123"), actions.cancelledKeys)
        assertEquals(0, actions.replacementCalls)
        assertEquals(1, actions.savedNotifications.size)
        val saved = actions.savedNotifications.single()
        assertEquals(SourceNotificationSuppressionState.CANCEL_ATTEMPTED, saved.notification.sourceSuppressionState)
        assertFalse(saved.notification.replacementNotificationIssued)
        assertEquals("sig", saved.contentSignature)
        assertEquals(1_000L, saved.postedAtMillis)
        // IGNORE never auto-expands the suppressed-apps list.
        assertNull(actions.lastSuppressedAppsWrite)
    }

    @Test
    fun silent_unprotected_capture_archives_in_tray_without_cancel_or_replacement() = runTest {
        val actions = RecordingSourceTrayActions()
        val pipeline = InlineDecisionPipeline(actions)

        val baseNotification = baseNotification(status = NotificationStatusUi.SILENT)

        pipeline.dispatch(
            DispatchInput(
                baseNotification = baseNotification,
                sourceEntryKey = "com.example|silent",
                packageName = "com.example",
                appName = "Example",
                postedAtMillis = 2_000L,
                contentSignature = "sig-silent",
                settings = settings(
                    suppressSourceForDigestAndSilent = true,
                    suppressedSourceApps = setOf("com.example"),
                ),
                isPersistent = false,
                shouldBypassPersistentHiding = false,
                isProtectedSourceNotification = false,
            )
        )

        // Plan `silent-archive-vs-process-split` Task 2: a fresh non-persistent unprotected
        // SILENT capture lands as ARCHIVED, which keeps the source in the tray. Even with
        // `suppressSourceForDigestAndSilent = true` and the package opted in, the routing
        // policy short-circuits the cancel for ARCHIVED (`silentMode == ARCHIVED` →
        // `keepSourceInTray = true`). SILENT also never posts a replacement notification.
        assertTrue(actions.cancelledKeys.isEmpty())
        assertEquals(0, actions.replacementCalls)
        val saved = actions.savedNotifications.single()
        assertFalse(saved.notification.replacementNotificationIssued)
        // SILENT + capture path with non-persistent + non-protected → ARCHIVED.
        assertEquals(SilentMode.ARCHIVED, saved.notification.silentMode)
    }

    @Test
    fun silent_protected_source_skips_cancel_and_replacement_and_saves_without_replacement() = runTest {
        val actions = RecordingSourceTrayActions()
        val pipeline = InlineDecisionPipeline(actions)

        val baseNotification = baseNotification(status = NotificationStatusUi.SILENT)

        pipeline.dispatch(
            DispatchInput(
                baseNotification = baseNotification,
                sourceEntryKey = "com.example|protected",
                packageName = "com.example",
                appName = "Example",
                postedAtMillis = 3_000L,
                contentSignature = "sig-protected",
                settings = settings(
                    suppressSourceForDigestAndSilent = true,
                    suppressedSourceApps = setOf("com.example"),
                ),
                isPersistent = false,
                shouldBypassPersistentHiding = false,
                isProtectedSourceNotification = true,
            )
        )

        assertTrue(actions.cancelledKeys.isEmpty())
        assertEquals(0, actions.replacementCalls)
        val saved = actions.savedNotifications.single()
        assertFalse(saved.notification.replacementNotificationIssued)
        // Protected SILENT must not participate in the ARCHIVED split — silentMode
        // stays whatever the base classifier emitted (null here).
        assertNull(saved.notification.silentMode)
        // Protected → no auto-expansion write either.
        assertNull(actions.lastSuppressedAppsWrite)
    }

    @Test
    fun digest_persistent_with_hide_persistent_cancels_source_and_saves() = runTest {
        val actions = RecordingSourceTrayActions()
        val pipeline = InlineDecisionPipeline(actions)

        val baseNotification = baseNotification(status = NotificationStatusUi.DIGEST)

        pipeline.dispatch(
            DispatchInput(
                baseNotification = baseNotification,
                sourceEntryKey = "com.example|digest",
                packageName = "com.example",
                appName = "Example",
                postedAtMillis = 4_000L,
                contentSignature = "sig-digest",
                settings = settings(
                    suppressSourceForDigestAndSilent = false,
                    hidePersistentSourceNotifications = true,
                ),
                isPersistent = true,
                shouldBypassPersistentHiding = false,
                isProtectedSourceNotification = false,
            )
        )

        assertEquals(listOf("com.example|digest"), actions.cancelledKeys)
        // DIGEST + cancel → replacement notify is requested AND posted.
        assertEquals(1, actions.replacementCalls)
        val saved = actions.savedNotifications.single()
        assertEquals(SourceNotificationSuppressionState.CANCEL_ATTEMPTED, saved.notification.sourceSuppressionState)
        assertTrue(saved.notification.replacementNotificationIssued)
        assertTrue(saved.notification.isPersistent)
    }

    @Test
    fun priority_no_suppression_keeps_source_in_tray_and_saves_priority_kept() = runTest {
        val actions = RecordingSourceTrayActions()
        val pipeline = InlineDecisionPipeline(actions)

        val baseNotification = baseNotification(status = NotificationStatusUi.PRIORITY)

        pipeline.dispatch(
            DispatchInput(
                baseNotification = baseNotification,
                sourceEntryKey = "com.example|priority",
                packageName = "com.example",
                appName = "Example",
                postedAtMillis = 5_000L,
                contentSignature = "sig-priority",
                settings = settings(
                    suppressSourceForDigestAndSilent = true,
                    suppressedSourceApps = setOf("com.example"),
                ),
                isPersistent = false,
                shouldBypassPersistentHiding = false,
                isProtectedSourceNotification = false,
            )
        )

        assertTrue(actions.cancelledKeys.isEmpty())
        assertEquals(0, actions.replacementCalls)
        val saved = actions.savedNotifications.single()
        assertEquals(SourceNotificationSuppressionState.PRIORITY_KEPT, saved.notification.sourceSuppressionState)
        assertFalse(saved.notification.replacementNotificationIssued)
    }

    // -- Test scaffolding ----------------------------------------------------

    private fun baseNotification(status: NotificationStatusUi): NotificationUiModel =
        NotificationUiModel(
            id = "id-1",
            appName = "Example",
            packageName = "com.example",
            sender = null,
            title = "title",
            body = "body",
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
}

/**
 * Bundle of inputs the future `NotificationDecisionPipeline.dispatch(...)` will accept.
 * Defined here (test-only) so the characterization test compiles without depending on
 * the production type that Task 2 introduces. Field names and ordering match the plan's
 * Architecture paragraph so the eventual production type can be a drop-in rename.
 */
internal data class DispatchInput(
    val baseNotification: NotificationUiModel,
    val sourceEntryKey: String,
    val packageName: String,
    val appName: String,
    val postedAtMillis: Long,
    val contentSignature: String,
    val settings: SmartNotiSettings,
    val isPersistent: Boolean,
    val shouldBypassPersistentHiding: Boolean,
    val isProtectedSourceNotification: Boolean,
)

internal data class SavedNotificationCall(
    val notification: NotificationUiModel,
    val postedAtMillis: Long,
    val contentSignature: String,
)

/**
 * Fake [SourceTrayActions]-style port that captures cancel / replacement / save calls so
 * the characterization test can assert against them directly. Mirrors the surface
 * Task 2 will introduce as a real `SourceTrayActions` interface.
 */
internal class RecordingSourceTrayActions {
    val cancelledKeys = mutableListOf<String>()
    var replacementCalls: Int = 0
        private set
    val savedNotifications = mutableListOf<SavedNotificationCall>()
    var lastSuppressedAppsWrite: Set<String>? = null
        private set

    suspend fun cancelSource(key: String) {
        cancelledKeys += key
    }

    fun postReplacement() {
        replacementCalls += 1
    }

    suspend fun save(notification: NotificationUiModel, postedAtMillis: Long, contentSignature: String) {
        savedNotifications += SavedNotificationCall(notification, postedAtMillis, contentSignature)
    }

    fun setSuppressedApps(apps: Set<String>) {
        lastSuppressedAppsWrite = apps
    }
}

/**
 * Inline mirror of the production "decision → side-effect" branch in
 * [SmartNotiNotificationListenerService.processNotification] (lines ~336-463). Statement
 * order matches the production code so the characterization is faithful.
 *
 * Task 2 of the plan extracts the same logic into `NotificationDecisionPipeline`. When
 * that lands, this inline pipeline can be deleted and the test re-pointed at the real
 * helper without changing any assertion above.
 */
internal class InlineDecisionPipeline(
    private val actions: RecordingSourceTrayActions,
) {
    suspend fun dispatch(input: DispatchInput) {
        val baseNotification = input.baseNotification
        val decision = baseNotification.status.toDecision()
        val isPersistent = input.isPersistent
        val shouldBypassPersistentHiding = input.shouldBypassPersistentHiding
        val settings = input.settings

        // IGNORE early-return — plan `2026-04-21-ignore-tier-fourth-decision` Task 4.
        if (decision == NotificationDecision.IGNORE) {
            actions.cancelSource(input.sourceEntryKey)
            val ignoredNotification = baseNotification.copy(
                sourceSuppressionState = SourceNotificationSuppressionState.CANCEL_ATTEMPTED,
                replacementNotificationIssued = false,
                isPersistent = isPersistent,
            )
            actions.save(ignoredNotification, input.postedAtMillis, input.contentSignature)
            return
        }

        val shouldHidePersistentSourceNotification =
            (isPersistent && !shouldBypassPersistentHiding) && settings.hidePersistentSourceNotifications
        val isProtectedSourceNotification = input.isProtectedSourceNotification
        val autoExpandedApps = if (!isProtectedSourceNotification) {
            SuppressedSourceAppsAutoExpansionPolicy.expandedAppsOrNull(
                decision = decision,
                suppressSourceForDigestAndSilent = settings.suppressSourceForDigestAndSilent,
                packageName = input.packageName,
                currentApps = settings.suppressedSourceApps,
                excludedApps = settings.suppressedSourceAppsExcluded,
            )
        } else {
            null
        }
        if (autoExpandedApps != null) {
            actions.setSuppressedApps(autoExpandedApps)
        }
        val effectiveSuppressedApps = autoExpandedApps ?: settings.suppressedSourceApps
        val shouldSuppressSourceNotification = !isProtectedSourceNotification &&
            NotificationSuppressionPolicy.shouldSuppressSourceNotification(
                suppressDigestAndSilent = settings.suppressSourceForDigestAndSilent,
                suppressedApps = effectiveSuppressedApps,
                packageName = input.packageName,
                decision = decision,
            )
        val capturedSilentMode = SilentCaptureRoutingSelector.silentModeFor(
            decision = decision,
            isPersistent = isPersistent,
            shouldBypassPersistentHiding = shouldBypassPersistentHiding,
            isProtectedSourceNotification = isProtectedSourceNotification,
        )
        val sourceRouting = if (isProtectedSourceNotification) {
            SourceNotificationRouting(
                cancelSourceNotification = false,
                notifyReplacementNotification = false,
            )
        } else {
            SourceNotificationRoutingPolicy.route(
                decision = decision,
                hidePersistentSourceNotification = shouldHidePersistentSourceNotification,
                suppressSourceNotification = shouldSuppressSourceNotification,
                silentMode = capturedSilentMode,
            )
        }
        val suppressionState = SourceNotificationSuppressionStateResolver.resolve(
            decision = decision,
            suppressDigestAndSilent = settings.suppressSourceForDigestAndSilent,
            suppressedApps = settings.suppressedSourceApps,
            packageName = input.packageName,
            hidePersistentSourceNotifications = settings.hidePersistentSourceNotifications,
            isPersistent = isPersistent,
            bypassPersistentHiding = shouldBypassPersistentHiding,
            sourceRouting = sourceRouting,
        )
        var replacementNotificationPosted = false
        if (sourceRouting.cancelSourceNotification) {
            actions.cancelSource(input.sourceEntryKey)
        }
        if (sourceRouting.notifyReplacementNotification) {
            actions.postReplacement()
            replacementNotificationPosted = true
        }
        val notification = baseNotification.copy(
            sourceSuppressionState = suppressionState,
            replacementNotificationIssued = SourceNotificationSuppressionStateResolver.replacementNotificationRecorded(
                sourceRouting = sourceRouting,
                replacementNotificationPosted = replacementNotificationPosted,
            ),
            isPersistent = isPersistent,
            silentMode = capturedSilentMode ?: baseNotification.silentMode,
        )
        actions.save(notification, input.postedAtMillis, input.contentSignature)
    }
}
