package com.smartnoti.app.notification

import com.smartnoti.app.data.settings.SmartNotiSettings
import com.smartnoti.app.diagnostic.DiagnosticLogger
import com.smartnoti.app.domain.model.AlertLevel
import com.smartnoti.app.domain.model.LockScreenVisibilityMode
import com.smartnoti.app.domain.model.NotificationStatusUi
import com.smartnoti.app.domain.model.NotificationUiModel
import com.smartnoti.app.domain.model.VibrationMode
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Plan `docs/plans/2026-04-27-fix-issue-480-diagnostic-log-file-export.md` Task 1.
 *
 * Failing-test gate (P1 release-prep) for the route-stage diagnostic hook in
 * [NotificationDecisionPipeline]:
 *
 *  - When the pipeline dispatches a non-IGNORE decision that triggers a
 *    cancel + replacement (DIGEST routing on a non-protected source), the
 *    bound [DiagnosticLogger] receives exactly one `route` call with the
 *    final cancel / replacement / channel / mode tuple.
 *  - When the logger is `isEnabled = false`, the pipeline must not call
 *    `logRoute` (no-allocation contract). The recording fake distinguishes
 *    "called while disabled" from "not called at all" so this is an
 *    independently verifiable assertion.
 *
 * Re-uses the [RecordingSourceTrayActions] fixture from
 * [NotificationDecisionPipelineCharacterizationTest] so the pipeline still
 * persists rows etc. — only the logger contract is asserted here.
 */
class NotificationDecisionPipelineDiagnosticHookTest {

    @Test
    fun dispatch_emits_one_route_log_line_when_logger_enabled() = runTest {
        val actions = RecordingSourceTrayActions()
        val logger = RecordingDiagnosticLogger(enabled = true)
        val pipeline = NotificationDecisionPipeline(actions, logger)

        pipeline.dispatch(
            NotificationDecisionPipeline.DispatchInput(
                baseNotification = baseNotification(NotificationStatusUi.DIGEST),
                sourceEntryKey = "com.example|digest",
                packageName = "com.example",
                appName = "Example",
                postedAtMillis = 1_000L,
                contentSignature = "sig-digest",
                settings = SmartNotiSettings(
                    suppressSourceForDigestAndSilent = true,
                    suppressedSourceApps = setOf("com.example"),
                ),
                isPersistent = false,
                shouldBypassPersistentHiding = false,
                isProtectedSourceNotification = false,
            )
        )

        assertEquals(
            "logger must record exactly one route call when enabled",
            1,
            logger.routeCalls.size,
        )
        val routeCall = logger.routeCalls.single()
        assertEquals("com.example", routeCall.packageName)
        // DIGEST routing on a non-protected, opted-in source cancels + posts replacement.
        assertEquals(true, routeCall.sourceCancelled)
        assertEquals(true, routeCall.replacementPosted)
        assertEquals("DIGEST", routeCall.targetMode)
    }

    @Test
    fun dispatch_does_not_emit_route_log_when_logger_disabled() = runTest {
        val actions = RecordingSourceTrayActions()
        val logger = RecordingDiagnosticLogger(enabled = false)
        val pipeline = NotificationDecisionPipeline(actions, logger)

        pipeline.dispatch(
            NotificationDecisionPipeline.DispatchInput(
                baseNotification = baseNotification(NotificationStatusUi.DIGEST),
                sourceEntryKey = "com.example|digest",
                packageName = "com.example",
                appName = "Example",
                postedAtMillis = 1_000L,
                contentSignature = "sig-digest",
                settings = SmartNotiSettings(
                    suppressSourceForDigestAndSilent = true,
                    suppressedSourceApps = setOf("com.example"),
                ),
                isPersistent = false,
                shouldBypassPersistentHiding = false,
                isProtectedSourceNotification = false,
            )
        )

        assertEquals(0, logger.routeCalls.size)
    }

    @Test
    fun dispatch_emits_route_log_for_ignore_decision_when_enabled() = runTest {
        val actions = RecordingSourceTrayActions()
        val logger = RecordingDiagnosticLogger(enabled = true)
        val pipeline = NotificationDecisionPipeline(actions, logger)

        pipeline.dispatch(
            NotificationDecisionPipeline.DispatchInput(
                baseNotification = baseNotification(NotificationStatusUi.IGNORE),
                sourceEntryKey = "com.example|ignore",
                packageName = "com.example",
                appName = "Example",
                postedAtMillis = 5_000L,
                contentSignature = "sig-ignore",
                settings = SmartNotiSettings(),
                isPersistent = false,
                shouldBypassPersistentHiding = false,
                isProtectedSourceNotification = false,
            )
        )

        // IGNORE shortcut also cancels source + saves; the diagnostic log
        // should record the routing decision so a user reproduction shows
        // "this notification was IGNORE'd".
        assertEquals(1, logger.routeCalls.size)
        val routeCall = logger.routeCalls.single()
        assertEquals(true, routeCall.sourceCancelled)
        assertEquals(false, routeCall.replacementPosted)
        assertEquals("IGNORE", routeCall.targetMode)
    }

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
}

/**
 * Recording fake for [DiagnosticLogger] used by Task 1's failing-test gate.
 * Mirrors [RecordingSourceTrayActions]'s explicit-list pattern instead of
 * pulling in mockk so the new tests do not add a dependency the project
 * does not currently use.
 */
internal class RecordingDiagnosticLogger(
    private val enabled: Boolean,
) : DiagnosticLogger {

    data class CaptureCall(
        val packageName: String,
        val title: String,
        val originalKey: String,
    )

    data class ClassifyCall(
        val packageName: String,
        val ruleHits: List<String>,
        val decision: String,
        val reasonTags: List<String>,
        val elapsedMs: Long,
    )

    data class RouteCall(
        val packageName: String,
        val sourceCancelled: Boolean,
        val replacementPosted: Boolean,
        val channelId: String?,
        val targetMode: String,
    )

    data class ErrorCall(
        val at: String,
        val throwable: Throwable,
    )

    val captureCalls = mutableListOf<CaptureCall>()
    val classifyCalls = mutableListOf<ClassifyCall>()
    val routeCalls = mutableListOf<RouteCall>()
    val errorCalls = mutableListOf<ErrorCall>()

    override fun isEnabled(): Boolean = enabled

    override fun logCapture(packageName: String, title: String, originalKey: String) {
        if (!enabled) return
        captureCalls += CaptureCall(packageName, title, originalKey)
    }

    override fun logClassify(
        packageName: String,
        ruleHits: List<String>,
        decision: String,
        reasonTags: List<String>,
        elapsedMs: Long,
    ) {
        if (!enabled) return
        classifyCalls += ClassifyCall(packageName, ruleHits, decision, reasonTags, elapsedMs)
    }

    override fun logRoute(
        packageName: String,
        sourceCancelled: Boolean,
        replacementPosted: Boolean,
        channelId: String?,
        targetMode: String,
    ) {
        if (!enabled) return
        routeCalls += RouteCall(packageName, sourceCancelled, replacementPosted, channelId, targetMode)
    }

    override fun logError(at: String, t: Throwable) {
        if (!enabled) return
        errorCalls += ErrorCall(at, t)
    }
}
