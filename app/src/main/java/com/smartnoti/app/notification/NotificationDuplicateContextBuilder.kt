package com.smartnoti.app.notification

import com.smartnoti.app.data.settings.SmartNotiSettings
import com.smartnoti.app.domain.usecase.DuplicateNotificationPolicy
import com.smartnoti.app.domain.usecase.LiveDuplicateCountTracker

/**
 * Plan `docs/plans/2026-04-27-refactor-listener-process-notification-extract.md`
 * Task 3.
 *
 * Pure builder that lifts the "duplicate context" stage of
 * `SmartNotiNotificationListenerService.processNotification` into a unit-
 * testable seam.
 *
 * Responsibilities:
 *   - Build a fresh [DuplicateNotificationPolicy] per call from the supplied
 *     [SmartNotiSettings.duplicateWindowMinutes] so a Settings dropdown
 *     change reaches the very next notification (plan
 *     `2026-04-26-duplicate-threshold-window-settings.md` Task 4 contract).
 *   - Compute the persistent-aware content signature.
 *   - Drive [LiveDuplicateCountTracker] + the [persistedDuplicateCount]
 *     functional port (the listener wires it to
 *     `NotificationRepository.countRecentDuplicates`) to produce the rolling
 *     window count.
 *
 * Side effects (signature computation + tracker recording + repository read)
 * are unchanged from the inline implementation; only call-site shape moves.
 * The repository read is exposed as a functional port so the builder can be
 * exercised in pure-Kotlin tests without spinning up Room.
 */
internal class NotificationDuplicateContextBuilder(
    private val tracker: LiveDuplicateCountTracker,
    private val persistedDuplicateCount: suspend (
        packageName: String,
        contentSignature: String,
        sinceMillis: Long,
    ) -> Int,
) {
    data class DuplicateContext(
        val contentSignature: String,
        val duplicateCount: Int,
        val duplicateWindowStart: Long,
    )

    suspend fun build(
        packageName: String,
        notificationId: Int,
        sourceEntryKey: String,
        postTimeMillis: Long,
        title: String,
        body: String,
        settings: SmartNotiSettings,
        isPersistent: Boolean,
    ): DuplicateContext {
        val policy = DuplicateNotificationPolicy(
            windowMillis = settings.duplicateWindowMinutes * 60_000L,
        )
        // Plan `2026-04-27-fix-issue-488-signature-normalize-numbers-time.md`
        // Task 3. The normalizer runs AFTER `policy.contentSignature` (which
        // lower-cases + collapses whitespace) and BEFORE the persistent suffix
        // concat — the suffix already encodes per-notification identity, so we
        // must not collapse digit/time tokens inside it. Toggle is per-call
        // because `settings` flows in fresh on each `build` invocation, so a
        // Settings-screen flip reaches the next notification immediately.
        val normalizer = ContentSignatureNormalizer(
            enabled = settings.normalizeNumericTokensInSignature,
        )
        val baseSignature = normalizer.normalize(
            policy.contentSignature(
                title = title,
                body = body,
            )
        )
        val contentSignature = baseSignature +
            if (isPersistent) "|persistent:$packageName:$notificationId" else ""
        val duplicateWindowStart = policy.windowStart(postTimeMillis)
        val duplicateCount = tracker.recordAndCount(
            packageName = packageName,
            contentSignature = contentSignature,
            sourceEntryKey = sourceEntryKey,
            postedAtMillis = postTimeMillis,
            windowStartMillis = duplicateWindowStart,
            persistedDuplicateCount = persistedDuplicateCount(
                packageName,
                contentSignature,
                duplicateWindowStart,
            ),
        )
        return DuplicateContext(
            contentSignature = contentSignature,
            duplicateCount = duplicateCount,
            duplicateWindowStart = duplicateWindowStart,
        )
    }
}
