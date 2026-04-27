package com.smartnoti.app.domain.usecase

import com.smartnoti.app.data.settings.SmartNotiSettings
import com.smartnoti.app.domain.model.Category
import com.smartnoti.app.domain.model.CategoryAction
import com.smartnoti.app.domain.model.ClassificationInput
import com.smartnoti.app.domain.model.NotificationDecision
import com.smartnoti.app.domain.model.RuleTypeUi
import com.smartnoti.app.domain.model.RuleUiModel
import com.smartnoti.app.notification.ContentSignatureNormalizer
import com.smartnoti.app.notification.NotificationDuplicateContextBuilder
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Plan `docs/plans/2026-04-27-fix-issue-488-signature-normalize-numbers-time.md`
 * Task 1 — RED end-to-end through the classifier.
 *
 * Builds the tracker → context-builder → classifier chain that runs in
 * production for every captured notification. With
 * [SmartNotiSettings.normalizeNumericTokensInSignature] true and a
 * `repeat_bundle:3` rule wired into a DIGEST Category, the 3rd 네이버페이
 * 포인트뽑기 fixture (8/12/16/28/1,234원) must trigger the rule because all
 * five collapse into a single normalized signature → tracker reports
 * `duplicateCountInWindow >= 3`.
 *
 * With the toggle false, every fixture has a unique signature → tracker
 * reports 1 per call → the rule never fires → result falls through to SILENT
 * (no other matching rules).
 */
class NotificationClassifierRepeatBundleNormalizerTest {

    private val classifier = NotificationClassifier(
        vipSenders = emptySet(),
        priorityKeywords = emptySet(),
        shoppingPackages = emptySet(),
    )

    private val repeatBundleRule = RuleUiModel(
        id = "r-repeat-bundle-3",
        title = "반복 알림 묶기",
        subtitle = "3회 이상이면 모음",
        type = RuleTypeUi.REPEAT_BUNDLE,
        enabled = true,
        matchValue = "3",
    )
    private val digestCategory = Category(
        id = "cat-repeat-digest",
        name = "반복 알림",
        appPackageName = null,
        ruleIds = listOf(repeatBundleRule.id),
        action = CategoryAction.DIGEST,
        order = 0,
    )

    private val pointPickupBodies = listOf(
        "8원이 적립되었어요",
        "12원이 적립되었어요",
        "16원이 적립되었어요",
        "28원이 적립되었어요",
        "1,234원이 적립되었어요",
    )

    @Test
    fun repeat_bundle_3_rule_fires_for_5_amount_only_fixtures_when_normalizer_on() = runTest {
        val tracker = LiveDuplicateCountTracker()
        val builder = NotificationDuplicateContextBuilder(
            tracker = tracker,
            persistedDuplicateCount = { _, _, _ -> 0 },
        )
        val settingsOn = SmartNotiSettings(
            duplicateWindowMinutes = 10,
            normalizeNumericTokensInSignature = true,
        )

        val decisions = mutableListOf<NotificationDecision>()
        pointPickupBodies.forEachIndexed { index, body ->
            val ctx = builder.build(
                packageName = "com.nhnent.payapp",
                notificationId = 1_000 + index,
                sourceEntryKey = "k-$index",
                postTimeMillis = 1_700_000_000_000L + index,
                title = "[현장결제]",
                body = body,
                settings = settingsOn,
                isPersistent = false,
            )
            val classification = classifier.classify(
                input = ClassificationInput(
                    packageName = "com.nhnent.payapp",
                    title = "[현장결제]",
                    body = body,
                    duplicateCountInWindow = ctx.duplicateCount,
                ),
                rules = listOf(repeatBundleRule),
                categories = listOf(digestCategory),
            )
            decisions += classification.decision
        }

        // First two notifications fall under the threshold (count = 1, 2).
        // From the 3rd onward, repeat_bundle:3 must fire → DIGEST.
        assertEquals(
            "From the 3rd 포인트뽑기 fixture onward, repeat_bundle:3 must demote to DIGEST",
            listOf(
                NotificationDecision.SILENT,
                NotificationDecision.SILENT,
                NotificationDecision.DIGEST,
                NotificationDecision.DIGEST,
                NotificationDecision.DIGEST,
            ),
            decisions,
        )
    }

    @Test
    fun repeat_bundle_3_rule_does_not_fire_when_normalizer_off() = runTest {
        val tracker = LiveDuplicateCountTracker()
        val builder = NotificationDuplicateContextBuilder(
            tracker = tracker,
            persistedDuplicateCount = { _, _, _ -> 0 },
        )
        val settingsOff = SmartNotiSettings(
            duplicateWindowMinutes = 10,
            normalizeNumericTokensInSignature = false,
        )

        val decisions = mutableListOf<NotificationDecision>()
        pointPickupBodies.forEachIndexed { index, body ->
            val ctx = builder.build(
                packageName = "com.nhnent.payapp",
                notificationId = 2_000 + index,
                sourceEntryKey = "k-off-$index",
                postTimeMillis = 1_700_000_000_000L + index,
                title = "[현장결제]",
                body = body,
                settings = settingsOff,
                isPersistent = false,
            )
            val classification = classifier.classify(
                input = ClassificationInput(
                    packageName = "com.nhnent.payapp",
                    title = "[현장결제]",
                    body = body,
                    duplicateCountInWindow = ctx.duplicateCount,
                ),
                rules = listOf(repeatBundleRule),
                categories = listOf(digestCategory),
            )
            decisions += classification.decision
        }

        // Toggle OFF: each call has a unique signature → count is always 1 →
        // repeat_bundle:3 never fires → fallthrough to SILENT (default branch).
        assertEquals(
            "Toggle OFF must reproduce the bug — every call lands in the SILENT default branch",
            List(pointPickupBodies.size) { NotificationDecision.SILENT },
            decisions,
        )
    }

    // Reference (kept inline for code review): the normalizer dependency is
    // exercised through `NotificationDuplicateContextBuilder.build`, which
    // (per Task 3 wiring) reads `settings.normalizeNumericTokensInSignature`.
    // No direct call to `ContentSignatureNormalizer` is needed here, but the
    // import is retained to keep the compile error pointing at the seam if
    // the production wiring is later moved.
    @Suppress("unused")
    private val unusedNormalizerReference = ContentSignatureNormalizer::class.java
}
