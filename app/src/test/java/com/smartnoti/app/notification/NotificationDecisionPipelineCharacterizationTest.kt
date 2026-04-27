package com.smartnoti.app.notification

import com.smartnoti.app.data.settings.SmartNotiSettings
import com.smartnoti.app.domain.model.AlertLevel
import com.smartnoti.app.domain.model.DeliveryProfile
import com.smartnoti.app.domain.model.LockScreenVisibilityMode
import com.smartnoti.app.domain.model.NotificationDecision
import com.smartnoti.app.domain.model.NotificationStatusUi
import com.smartnoti.app.domain.model.NotificationUiModel
import com.smartnoti.app.domain.model.SilentMode
import com.smartnoti.app.domain.model.SourceNotificationSuppressionState
import com.smartnoti.app.domain.model.VibrationMode
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Plan `2026-04-27-refactor-listener-process-notification-extract.md` Task 1
 * (initial pinning) → Task 2 (re-pointed at production helper).
 *
 * Pins the production behavior of the post-classifier "decision → side-effect"
 * branch that previously lived inline in
 * `SmartNotiNotificationListenerService.processNotification`. Since Task 2
 * landed, the assertions exercise the real
 * [NotificationDecisionPipeline] / [SourceTrayActions] surface so the
 * extraction is provably behavior-preserving — the same five outcome
 * branches that pinned the inline code now pin the helper.
 */
class NotificationDecisionPipelineCharacterizationTest {

    @Test
    fun ignore_decision_cancels_source_saves_with_cancel_attempted_and_no_replacement() = runTest {
        val actions = RecordingSourceTrayActions()
        val pipeline = NotificationDecisionPipeline(actions)

        val baseNotification = baseNotification(status = NotificationStatusUi.IGNORE)

        pipeline.dispatch(
            NotificationDecisionPipeline.DispatchInput(
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
        val pipeline = NotificationDecisionPipeline(actions)

        val baseNotification = baseNotification(status = NotificationStatusUi.SILENT)

        pipeline.dispatch(
            NotificationDecisionPipeline.DispatchInput(
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
        val pipeline = NotificationDecisionPipeline(actions)

        val baseNotification = baseNotification(status = NotificationStatusUi.SILENT)

        pipeline.dispatch(
            NotificationDecisionPipeline.DispatchInput(
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
        val pipeline = NotificationDecisionPipeline(actions)

        val baseNotification = baseNotification(status = NotificationStatusUi.DIGEST)

        pipeline.dispatch(
            NotificationDecisionPipeline.DispatchInput(
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
        val pipeline = NotificationDecisionPipeline(actions)

        val baseNotification = baseNotification(status = NotificationStatusUi.PRIORITY)

        pipeline.dispatch(
            NotificationDecisionPipeline.DispatchInput(
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

internal data class SavedNotificationCall(
    val notification: NotificationUiModel,
    val postedAtMillis: Long,
    val contentSignature: String,
)

/**
 * Production [SourceTrayActions] fake. Captures cancel / replacement / save
 * / setSuppressedApps calls so the characterization test can assert against
 * them directly.
 */
internal class RecordingSourceTrayActions : SourceTrayActions {
    val cancelledKeys = mutableListOf<String>()
    var replacementCalls: Int = 0
        private set
    val savedNotifications = mutableListOf<SavedNotificationCall>()
    var lastSuppressedAppsWrite: Set<String>? = null
        private set

    override suspend fun cancelSource(key: String) {
        cancelledKeys += key
    }

    override fun postReplacement(
        decision: NotificationDecision,
        packageName: String,
        appName: String,
        title: String,
        body: String,
        notificationId: String,
        reasonTags: List<String>,
        settings: SmartNotiSettings,
        deliveryProfile: DeliveryProfile,
    ) {
        replacementCalls += 1
    }

    override suspend fun setSuppressedApps(apps: Set<String>) {
        lastSuppressedAppsWrite = apps
    }

    override suspend fun save(
        notification: NotificationUiModel,
        postedAtMillis: Long,
        contentSignature: String,
    ) {
        savedNotifications += SavedNotificationCall(notification, postedAtMillis, contentSignature)
    }
}
