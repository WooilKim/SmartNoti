package com.smartnoti.app.domain.usecase

import com.smartnoti.app.domain.model.Category
import com.smartnoti.app.domain.model.CategoryAction
import com.smartnoti.app.domain.model.NotificationStatusUi
import com.smartnoti.app.domain.model.NotificationUiModel
import com.smartnoti.app.domain.model.RuleActionUi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Test

/**
 * RED-phase tests for Drift 2 of plan
 * `docs/plans/2026-04-22-categories-runtime-wiring-fix.md` Task 1.
 *
 * Pin the CONTRACT that feedback taps ("중요로 고정" / "Digest로 유지" / "조용히
 * 유지" / "무시") resolve to a Category mutation — not just a Rule upsert.
 * Post-P1, Rules are pure matchers; a Rule with no Category pointing at it
 * has zero effect on classification, so feedback must also create or update
 * a Category whose `action` drives future routing.
 *
 * These tests assume Task 2 introduces a new method on
 * [NotificationFeedbackPolicy] (e.g. `applyActionToCategory(...)`) that
 * returns (or upserts) both a Rule and a Category. Today the method does
 * not exist, so the tests fail to compile — which is the RED signal.
 *
 * NOTE: the "multi-rule Category flip" case is deliberately @Ignore'd —
 * see the plan's open question at Risks > "Update in place vs. stack action".
 */
class NotificationFeedbackPolicyCategoryUpsertTest {

    private val policy = NotificationFeedbackPolicy()

    @Test
    fun feedback_creates_category_when_no_existing_category_wraps_the_rule() {
        val notification = sampleNotification(sender = "엄마")
        val rule = policy.toRule(notification, RuleActionUi.ALWAYS_PRIORITY)

        // No pre-existing Category — feedback must create one pointing at the
        // generated Rule with the chosen action. Order is "append to bottom"
        // (max(existing.order) + 1, or 0 when the list is empty) so explicit
        // user-ordered Categories still win specificity ties.
        val result = policy.applyActionToCategory(
            notification = notification,
            action = RuleActionUi.ALWAYS_PRIORITY,
            existingCategories = emptyList(),
        )

        assertEquals(rule, result.rule)
        val category = result.category
        assertNotNull("Feedback must produce a Category for classifier routing", category)
        assertEquals(CategoryAction.PRIORITY, category!!.action)
        assertTrue(
            "New Category must reference the generated rule id",
            category.ruleIds.contains(rule.id),
        )
        assertEquals(0, category.order)
    }

    @Test
    fun feedback_updates_single_rule_category_action_in_place() {
        val notification = sampleNotification(sender = "엄마")
        val rule = policy.toRule(notification, RuleActionUi.ALWAYS_PRIORITY)

        // Pre-seed a Category that already wraps the generated rule with a
        // DIGEST action (as if a prior feedback had landed it there).
        val existing = Category(
            id = "cat-existing",
            name = "엄마",
            appPackageName = null,
            ruleIds = listOf(rule.id),
            action = CategoryAction.DIGEST,
            order = 3,
        )

        val result = policy.applyActionToCategory(
            notification = notification,
            action = RuleActionUi.ALWAYS_PRIORITY,
            existingCategories = listOf(existing),
        )

        assertEquals(rule, result.rule)
        val category = result.category
        assertNotNull(category)
        // Update-in-place: same id, same order, same name — only action flips.
        assertEquals(existing.id, category!!.id)
        assertEquals(existing.name, category.name)
        assertEquals(existing.order, category.order)
        assertEquals(CategoryAction.PRIORITY, category.action)
        assertTrue(category.ruleIds.contains(rule.id))
    }

    @Test
    fun feedback_new_category_order_is_max_plus_one() {
        val notification = sampleNotification(sender = "광고봇")
        val existingCategories = listOf(
            Category(
                id = "cat-a",
                name = "기존 A",
                appPackageName = null,
                ruleIds = listOf("unrelated:rule"),
                action = CategoryAction.SILENT,
                order = 5,
            ),
            Category(
                id = "cat-b",
                name = "기존 B",
                appPackageName = null,
                ruleIds = listOf("unrelated:rule-2"),
                action = CategoryAction.DIGEST,
                order = 7,
            ),
        )

        val result = policy.applyActionToCategory(
            notification = notification,
            action = RuleActionUi.IGNORE,
            existingCategories = existingCategories,
        )

        val newCategory = result.category
        assertNotNull(newCategory)
        assertEquals(8, newCategory!!.order)
        assertEquals(CategoryAction.IGNORE, newCategory.action)
    }

    // TODO(plan: 2026-04-22-categories-runtime-wiring-fix — Risks > "Update
    // in place vs. stack action"). Open product question: if the matched
    // Category wraps 5 Rules, does flipping action affect all 5 senders
    // (current plan default) or should we create a new 1-rule Category to
    // avoid side effects? Un-ignore once the user picks a side.
    @Ignore("pending user decision on multi-rule feedback — see plan Risks > 'Update in place vs. stack action'")
    @Test
    fun feedback_on_multi_rule_category_behavior_is_decided_in_task_2() {
        // Deliberately empty — placeholder so git history records that this
        // case is known-open and will get its own GREEN test once the
        // product question resolves.
    }

    private fun sampleNotification(sender: String? = "엄마") = NotificationUiModel(
        id = "n1",
        appName = "카카오톡",
        packageName = if (sender == null) "com.news.app" else "com.kakao.talk",
        sender = sender,
        title = sender ?: "뉴스 속보",
        body = "알림 본문",
        receivedAtLabel = "방금",
        status = NotificationStatusUi.SILENT,
        reasonTags = listOf("원본"),
        score = null,
        isBundled = false,
    )
}
