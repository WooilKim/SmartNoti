package com.smartnoti.app.domain.usecase

import com.smartnoti.app.domain.model.Category
import com.smartnoti.app.domain.model.CategoryAction
import com.smartnoti.app.domain.model.ClassificationInput
import com.smartnoti.app.domain.model.NotificationDecision
import com.smartnoti.app.domain.model.RuleTypeUi
import com.smartnoti.app.domain.model.RuleUiModel
import com.smartnoti.app.domain.model.toDecision
import com.smartnoti.app.notification.NotificationSuppressionPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Plan `docs/plans/2026-04-22-categories-split-rules-actions.md` Phase P2
 * Task 7 — smoke tests that exercise the **Notifier-side** consumers with
 * each of the four [CategoryAction] values end-to-end.
 *
 * These tests are the contract that the notifier hot path (listener service
 * + [NotificationSuppressionPolicy] + `SmartNotiNotifier`) reads a
 * `Category.action`, not `Rule.action`. We route the category through the
 * classifier so the same code path the service uses is exercised.
 */
class CategoryActionNotifierRoutingTest {

    private val classifier = NotificationClassifier(
        vipSenders = emptySet(),
        priorityKeywords = emptySet(),
        shoppingPackages = emptySet(),
    )

    private val rule = RuleUiModel(
        id = "r-app",
        title = "앱",
        subtitle = "분류",
        type = RuleTypeUi.APP,
        enabled = true,
        matchValue = "com.test.app",
    )

    @Test
    fun priority_category_action_routes_to_priority_decision_and_never_suppresses_source() {
        val decision = classifyWith(CategoryAction.PRIORITY)
        assertEquals(NotificationDecision.PRIORITY, decision)

        // Source tray is never suppressed for PRIORITY even when the user
        // opted into source suppression — the replacement alert would silence
        // a notification the user explicitly wants to see.
        val suppressed = NotificationSuppressionPolicy.shouldSuppressSourceNotification(
            suppressDigestAndSilent = true,
            suppressedApps = setOf("com.test.app"),
            packageName = "com.test.app",
            decision = decision,
        )
        assertFalse(suppressed)
    }

    @Test
    fun digest_category_action_routes_to_digest_decision_and_honors_suppression_opt_in() {
        val decision = classifyWith(CategoryAction.DIGEST)
        assertEquals(NotificationDecision.DIGEST, decision)

        // With the user opt-in on AND the app explicitly added, DIGEST
        // suppresses its source tray entry.
        val suppressed = NotificationSuppressionPolicy.shouldSuppressSourceNotification(
            suppressDigestAndSilent = true,
            suppressedApps = setOf("com.test.app"),
            packageName = "com.test.app",
            decision = decision,
        )
        assertTrue(suppressed)
    }

    @Test
    fun silent_category_action_routes_to_silent_decision_and_honors_suppression_opt_in() {
        val decision = classifyWith(CategoryAction.SILENT)
        assertEquals(NotificationDecision.SILENT, decision)

        val suppressed = NotificationSuppressionPolicy.shouldSuppressSourceNotification(
            suppressDigestAndSilent = true,
            suppressedApps = setOf("com.test.app"),
            packageName = "com.test.app",
            decision = decision,
        )
        assertTrue(suppressed)
    }

    @Test
    fun ignore_category_action_routes_to_ignore_decision_and_always_suppresses_source() {
        val decision = classifyWith(CategoryAction.IGNORE)
        assertEquals(NotificationDecision.IGNORE, decision)

        // IGNORE suppresses the source tray unconditionally — even when the
        // user's per-app opt-in is OFF. This is the plan
        // `2026-04-21-ignore-tier-fourth-decision` contract preserved by
        // Phase P2 Task 7.
        val suppressedWithoutOptIn = NotificationSuppressionPolicy.shouldSuppressSourceNotification(
            suppressDigestAndSilent = false,
            suppressedApps = emptySet(),
            packageName = "com.test.app",
            decision = decision,
        )
        assertTrue(suppressedWithoutOptIn)
    }

    @Test
    fun category_action_to_decision_maps_every_enum_value() {
        // Pin the canonical mapping so a future enum add cannot silently
        // drift between the classifier and direct consumers.
        assertEquals(NotificationDecision.PRIORITY, CategoryAction.PRIORITY.toDecision())
        assertEquals(NotificationDecision.DIGEST, CategoryAction.DIGEST.toDecision())
        assertEquals(NotificationDecision.SILENT, CategoryAction.SILENT.toDecision())
        assertEquals(NotificationDecision.IGNORE, CategoryAction.IGNORE.toDecision())
    }

    private fun classifyWith(action: CategoryAction): NotificationDecision {
        val category = Category(
            id = "cat-$action",
            name = "cat-$action",
            appPackageName = null,
            ruleIds = listOf(rule.id),
            action = action,
            order = 0,
        )
        return classifier.classify(
            input = ClassificationInput(packageName = "com.test.app"),
            rules = listOf(rule),
            categories = listOf(category),
        ).decision
    }
}
