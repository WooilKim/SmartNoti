package com.smartnoti.app.domain.usecase

import com.smartnoti.app.domain.model.Category
import com.smartnoti.app.domain.model.CategoryAction
import com.smartnoti.app.domain.model.ClassificationInput
import com.smartnoti.app.domain.model.NotificationDecision
import com.smartnoti.app.domain.model.NotificationStatusUi
import com.smartnoti.app.domain.model.NotificationUiModel
import com.smartnoti.app.domain.model.RuleTypeUi
import com.smartnoti.app.domain.model.RuleUiModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * RED-phase tests for plan
 * `docs/plans/2026-04-22-categories-runtime-wiring-fix.md` Task 1 steps 1–2.
 *
 * The new `AssignNotificationToCategoryUseCase` replaces the four per-action
 * Detail buttons. When the user taps "기존 분류에 포함 → <catId>" the use case
 * must:
 *   1. Derive an auto-rule from the notification: PERSON matching `sender`
 *      when sender is non-blank; otherwise APP matching `packageName`. Rule
 *      id is deterministic (`"${TYPE}:$matchValue"`) so repeat assignments
 *      are idempotent.
 *   2. Upsert that rule into the Rules store.
 *   3. Append the rule id to the target Category's `ruleIds` (dedup).
 *   4. Leave the target Category's `action`, `name`, `order`, `appPackageName`
 *      untouched.
 *
 * A downstream classifier pass is exercised here too so we catch the
 * full "future notifications from the same sender/app route via this
 * Category" contract, not just the storage mutation.
 *
 * All symbols referenced here (`AssignNotificationToCategoryUseCase`,
 * `AssignNotificationToCategoryUseCase.Ports`,
 * `CategoriesRepository.appendRuleIdToCategory`) do not exist yet — the
 * file compile-fails intentionally. Task 3 turns them green.
 */
class AssignNotificationToCategoryUseCaseTest {

    @Test
    fun assign_to_existing_with_sender_upserts_person_rule_and_appends_to_category() = runBlocking {
        val notification = notification(sender = "Alice", packageName = "com.kakao.talk")
        val fixture = fixture(
            categories = listOf(
                Category(
                    id = "cat-work",
                    name = "업무",
                    appPackageName = null,
                    ruleIds = emptyList(),
                    action = CategoryAction.PRIORITY,
                    order = 0,
                ),
            ),
        )

        fixture.useCase.assignToExisting(notification, categoryId = "cat-work")

        // (a) rule upserted with deterministic id + PERSON type
        assertEquals(1, fixture.rules.size)
        val upserted = fixture.rules.single()
        assertEquals(RuleTypeUi.PERSON, upserted.type)
        assertEquals("Alice", upserted.matchValue)
        assertEquals("PERSON:Alice", upserted.id)

        // (b) category ruleIds now contains the new rule id
        val catAfter = fixture.categories.first { it.id == "cat-work" }
        assertTrue("PERSON:Alice" in catAfter.ruleIds)

        // (c) untouched fields
        assertEquals("업무", catAfter.name)
        assertEquals(CategoryAction.PRIORITY, catAfter.action)
        assertEquals(0, catAfter.order)
        assertNull(catAfter.appPackageName)
    }

    @Test
    fun assign_to_existing_is_idempotent_when_rule_already_in_category() = runBlocking {
        val notification = notification(sender = "Alice", packageName = "com.kakao.talk")
        val preExistingRule = RuleUiModel(
            id = "PERSON:Alice",
            title = "Alice",
            subtitle = "",
            type = RuleTypeUi.PERSON,
            enabled = true,
            matchValue = "Alice",
        )
        val fixture = fixture(
            rules = listOf(preExistingRule),
            categories = listOf(
                Category(
                    id = "cat-work",
                    name = "업무",
                    appPackageName = null,
                    ruleIds = listOf("PERSON:Alice"),
                    action = CategoryAction.PRIORITY,
                    order = 0,
                ),
            ),
        )

        fixture.useCase.assignToExisting(notification, categoryId = "cat-work")

        assertEquals(1, fixture.rules.size)
        val catAfter = fixture.categories.first { it.id == "cat-work" }
        assertEquals(listOf("PERSON:Alice"), catAfter.ruleIds)
    }

    @Test
    fun assign_to_existing_with_blank_sender_upserts_app_rule() = runBlocking {
        val notification = notification(sender = null, packageName = "com.kakao.talk")
        val fixture = fixture(
            categories = listOf(
                Category(
                    id = "cat-chat",
                    name = "채팅앱",
                    appPackageName = null,
                    ruleIds = emptyList(),
                    action = CategoryAction.DIGEST,
                    order = 1,
                ),
            ),
        )

        fixture.useCase.assignToExisting(notification, categoryId = "cat-chat")

        val rule = fixture.rules.single()
        assertEquals(RuleTypeUi.APP, rule.type)
        assertEquals("com.kakao.talk", rule.matchValue)
        assertEquals("APP:com.kakao.talk", rule.id)
        val catAfter = fixture.categories.first { it.id == "cat-chat" }
        assertTrue("APP:com.kakao.talk" in catAfter.ruleIds)
        assertEquals(CategoryAction.DIGEST, catAfter.action)
    }

    @Test
    fun after_assign_future_notification_from_same_sender_classifies_to_target_category_action() = runBlocking {
        // End-to-end: assign to a PRIORITY category; run a *different* later
        // notification (new title/body) from the same sender through the
        // classifier pipeline; expect the category's action to win.
        val first = notification(sender = "Alice", packageName = "com.kakao.talk")
        val fixture = fixture(
            categories = listOf(
                Category(
                    id = "cat-work",
                    name = "업무",
                    appPackageName = null,
                    ruleIds = emptyList(),
                    action = CategoryAction.PRIORITY,
                    order = 0,
                ),
            ),
        )

        fixture.useCase.assignToExisting(first, categoryId = "cat-work")

        val classifier = NotificationClassifier(
            vipSenders = emptySet(),
            priorityKeywords = emptySet(),
            shoppingPackages = emptySet(),
        )
        val classification = classifier.classify(
            input = ClassificationInput(
                sender = "Alice",
                packageName = "com.kakao.talk",
                title = "다른 내용",
                body = "다음 미팅 시간",
            ),
            rules = fixture.rules,
            categories = fixture.categories,
        )

        assertEquals(NotificationDecision.PRIORITY, classification.decision)
        assertEquals(listOf("PERSON:Alice"), classification.matchedRuleIds)
    }

    @Test
    fun assign_to_existing_preserves_other_categories() = runBlocking {
        // Touching cat-work must not mutate a bystander Category.
        val notification = notification(sender = "Alice", packageName = "com.kakao.talk")
        val fixture = fixture(
            categories = listOf(
                Category(
                    id = "cat-work",
                    name = "업무",
                    appPackageName = null,
                    ruleIds = emptyList(),
                    action = CategoryAction.PRIORITY,
                    order = 0,
                ),
                Category(
                    id = "cat-other",
                    name = "기타",
                    appPackageName = null,
                    ruleIds = listOf("some-other-rule"),
                    action = CategoryAction.SILENT,
                    order = 1,
                ),
            ),
        )

        fixture.useCase.assignToExisting(notification, categoryId = "cat-work")

        val bystanderAfter = fixture.categories.first { it.id == "cat-other" }
        assertEquals(listOf("some-other-rule"), bystanderAfter.ruleIds)
        assertEquals(CategoryAction.SILENT, bystanderAfter.action)
    }

    private fun notification(sender: String?, packageName: String) = NotificationUiModel(
        id = "n-1",
        appName = "카카오톡",
        packageName = packageName,
        sender = sender,
        title = sender ?: "뉴스",
        body = "내용",
        receivedAtLabel = "방금",
        status = NotificationStatusUi.SILENT,
        reasonTags = emptyList(),
    )

    /**
     * Convenience wrapper that wires an in-memory fake Ports implementation
     * into [AssignNotificationToCategoryUseCase].
     *
     * The `useCase` here is bound to lambdas that mutate the local lists, so
     * assertions can read [rules] / [categories] back without needing a
     * Context-backed repository.
     */
    private class Fixture(
        val useCase: AssignNotificationToCategoryUseCase,
        val rulesState: MutableList<RuleUiModel>,
        val categoriesState: MutableList<Category>,
    ) {
        val rules: List<RuleUiModel> get() = rulesState.toList()
        val categories: List<Category> get() = categoriesState.toList()
    }

    private fun fixture(
        rules: List<RuleUiModel> = emptyList(),
        categories: List<Category> = emptyList(),
    ): Fixture {
        val rulesState = rules.toMutableList()
        val categoriesState = categories.toMutableList()
        val ports = object : AssignNotificationToCategoryUseCase.Ports {
            override suspend fun upsertRule(rule: RuleUiModel) {
                val idx = rulesState.indexOfFirst { it.id == rule.id }
                if (idx >= 0) rulesState[idx] = rule else rulesState += rule
            }

            override suspend fun appendRuleIdToCategory(categoryId: String, ruleId: String) {
                val idx = categoriesState.indexOfFirst { it.id == categoryId }
                if (idx < 0) return
                val current = categoriesState[idx]
                if (ruleId in current.ruleIds) return
                categoriesState[idx] = current.copy(ruleIds = current.ruleIds + ruleId)
            }
        }
        return Fixture(
            useCase = AssignNotificationToCategoryUseCase(ports = ports),
            rulesState = rulesState,
            categoriesState = categoriesState,
        )
    }
}
