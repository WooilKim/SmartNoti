package com.smartnoti.app.notification

import com.smartnoti.app.data.settings.SmartNotiSettings
import com.smartnoti.app.domain.model.CapturedNotificationInput
import com.smartnoti.app.domain.model.Category
import com.smartnoti.app.domain.model.CategoryAction
import com.smartnoti.app.domain.model.NotificationDecision
import com.smartnoti.app.domain.model.NotificationStatusUi
import com.smartnoti.app.domain.model.RuleTypeUi
import com.smartnoti.app.domain.model.RuleUiModel
import com.smartnoti.app.domain.model.toDecision
import com.smartnoti.app.domain.usecase.DeliveryProfilePolicy
import com.smartnoti.app.domain.usecase.NotificationCaptureProcessor
import com.smartnoti.app.domain.usecase.NotificationClassifier
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * RED-phase test for plan
 * `docs/plans/2026-04-22-categories-runtime-wiring-fix.md` Task 1 step 4
 * (Drift #1 — listener injection).
 *
 * `SmartNotiNotificationListenerService.processNotification(...)` currently
 * reads `rulesRepository.currentRules()` but never calls
 * `categoriesRepository.currentCategories()` at the same call site.
 * Post-P1 the Classifier derives its action from Categories; a processor
 * call that receives an empty Category list always falls back to SILENT
 * (or a classifier-heuristic signal), even if the user has configured
 * rich Categories.
 *
 * To make this testable without spinning up an Android service, plan
 * Task 4 introduces a pure seam `NotificationProcessingCoordinator` (≤ 20
 * LOC) that the service delegates to. The coordinator is passed
 * functional ports for the two repository reads — the test asserts that
 * both ports are invoked and both lists reach `processor.process(...)`.
 *
 * Symbol `NotificationProcessingCoordinator` does not exist yet —
 * compile-fail is intentional. Task 4 turns this green.
 */
class SmartNotiNotificationListenerServiceCategoryInjectionTest {

    @Test
    fun coordinator_reads_categories_and_routes_notification_via_category_action() = runBlocking {
        val seededRule = RuleUiModel(
            id = "PERSON:Alice",
            title = "Alice",
            subtitle = "",
            type = RuleTypeUi.PERSON,
            enabled = true,
            matchValue = "Alice",
        )
        val seededCategory = Category(
            id = "cat-work",
            name = "업무",
            appPackageName = null,
            ruleIds = listOf("PERSON:Alice"),
            action = CategoryAction.PRIORITY,
            order = 0,
        )

        val processor = NotificationCaptureProcessor(
            classifier = NotificationClassifier(
                vipSenders = emptySet(),
                priorityKeywords = emptySet(),
                shoppingPackages = emptySet(),
            ),
            deliveryProfilePolicy = DeliveryProfilePolicy(),
        )

        val capturedCategories = mutableListOf<List<Category>>()
        val capturedRules = mutableListOf<List<RuleUiModel>>()

        val coordinator = NotificationProcessingCoordinator(
            loadRules = {
                capturedRules += listOf(seededRule)
                listOf(seededRule)
            },
            loadCategories = {
                capturedCategories += listOf(seededCategory)
                listOf(seededCategory)
            },
            loadSettings = { SmartNotiSettings() },
            processor = processor,
        )

        val input = CapturedNotificationInput(
            packageName = "com.kakao.talk",
            appName = "카카오톡",
            sender = "Alice",
            title = "Alice",
            body = "회의 5분 전",
            postedAtMillis = 1_700_000_000_000L,
            quietHours = false,
            duplicateCountInWindow = 0,
        )

        val ui = coordinator.process(input)

        // Categories were read at the same call site as Rules.
        assertEquals(1, capturedCategories.size)
        assertTrue(capturedCategories.single().any { it.id == "cat-work" })
        assertEquals(1, capturedRules.size)

        // The Category's PRIORITY action won (fails today because the
        // service call site does not forward Categories to processor.process).
        assertEquals(NotificationStatusUi.PRIORITY, ui.status)
        assertNotNull(ui.matchedRuleIds.firstOrNull { it == "PERSON:Alice" })
    }

    @Test
    fun coordinator_passes_non_empty_category_list_into_processor_even_when_no_rule_hits() = runBlocking {
        // Even when no rule matches the current notification, Categories must
        // still be threaded through — otherwise a sibling notification that
        // *would* have hit a rule-owning Category later in the same process
        // cannot depend on the call site having wired Categories correctly.
        val seededCategory = Category(
            id = "cat-work",
            name = "업무",
            appPackageName = null,
            ruleIds = listOf("PERSON:Alice"),
            action = CategoryAction.PRIORITY,
            order = 0,
        )
        val processor = NotificationCaptureProcessor(
            classifier = NotificationClassifier(
                vipSenders = emptySet(),
                priorityKeywords = emptySet(),
                shoppingPackages = emptySet(),
            ),
            deliveryProfilePolicy = DeliveryProfilePolicy(),
        )
        val seenCategories = mutableListOf<List<Category>>()
        val coordinator = NotificationProcessingCoordinator(
            loadRules = { emptyList() },
            loadCategories = {
                val snapshot = listOf(seededCategory)
                seenCategories += snapshot
                snapshot
            },
            loadSettings = { SmartNotiSettings() },
            processor = processor,
        )

        coordinator.process(
            CapturedNotificationInput(
                packageName = "com.weather.app",
                appName = "날씨",
                sender = null,
                title = "오늘 흐림",
                body = "강수 30%",
                postedAtMillis = 1_700_000_000_000L,
                quietHours = false,
                duplicateCountInWindow = 0,
            )
        )

        assertEquals(1, seenCategories.size)
        assertEquals("cat-work", seenCategories.single().single().id)
    }

    /**
     * Plan `2026-04-26-duplicate-threshold-window-settings.md` Task 4.
     *
     * Pins that the user-tunable `duplicateDigestThreshold` plumbed through
     * `CapturedNotificationInput.duplicateThreshold` reaches the classifier
     * and changes its DIGEST decision. With threshold = 2 a count-of-2
     * input trips DIGEST; with threshold = 5 the same count falls through
     * to SILENT. Pure unit-level seam — the listener field-level instance
     * is bypassed because the coordinator wires the input directly.
     */
    @Test
    fun coordinator_forwards_user_threshold_to_classifier_lowering_to_two() = runBlocking {
        val processor = NotificationCaptureProcessor(
            classifier = NotificationClassifier(
                vipSenders = emptySet(),
                priorityKeywords = emptySet(),
                shoppingPackages = emptySet(),
            ),
            deliveryProfilePolicy = DeliveryProfilePolicy(),
        )
        val coordinator = NotificationProcessingCoordinator(
            loadRules = { emptyList() },
            loadCategories = { emptyList() },
            loadSettings = { SmartNotiSettings(duplicateDigestThreshold = 2) },
            processor = processor,
        )

        val ui = coordinator.process(
            CapturedNotificationInput(
                packageName = "com.news.app",
                appName = "News",
                sender = null,
                title = "속보",
                body = "같은 알림이 또 도착했어요",
                postedAtMillis = 1_700_000_000_000L,
                quietHours = false,
                duplicateCountInWindow = 2,
                duplicateThreshold = 2,
            )
        )

        assertEquals(NotificationDecision.DIGEST, ui.status.toDecision())
    }

    @Test
    fun coordinator_forwards_user_threshold_to_classifier_raising_to_five() = runBlocking {
        val processor = NotificationCaptureProcessor(
            classifier = NotificationClassifier(
                vipSenders = emptySet(),
                priorityKeywords = emptySet(),
                shoppingPackages = emptySet(),
            ),
            deliveryProfilePolicy = DeliveryProfilePolicy(),
        )
        val coordinator = NotificationProcessingCoordinator(
            loadRules = { emptyList() },
            loadCategories = { emptyList() },
            loadSettings = { SmartNotiSettings(duplicateDigestThreshold = 5) },
            processor = processor,
        )

        val ui = coordinator.process(
            CapturedNotificationInput(
                packageName = "com.news.app",
                appName = "News",
                sender = null,
                title = "속보",
                body = "같은 알림이 또 도착했어요",
                postedAtMillis = 1_700_000_000_000L,
                quietHours = false,
                duplicateCountInWindow = 2,
                duplicateThreshold = 5,
            )
        )

        assertEquals(NotificationDecision.SILENT, ui.status.toDecision())
    }
}
