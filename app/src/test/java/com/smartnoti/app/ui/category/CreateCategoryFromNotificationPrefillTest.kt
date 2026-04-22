package com.smartnoti.app.ui.category

import com.smartnoti.app.domain.model.Category
import com.smartnoti.app.domain.model.CategoryAction
import com.smartnoti.app.domain.model.ClassificationInput
import com.smartnoti.app.domain.model.NotificationDecision
import com.smartnoti.app.domain.model.NotificationStatusUi
import com.smartnoti.app.domain.model.NotificationUiModel
import com.smartnoti.app.domain.model.RuleTypeUi
import com.smartnoti.app.domain.model.RuleUiModel
import com.smartnoti.app.domain.usecase.AssignNotificationToCategoryUseCase
import com.smartnoti.app.domain.usecase.CategoryEditorPrefill
import com.smartnoti.app.domain.usecase.NotificationClassifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * RED-phase tests for plan
 * `docs/plans/2026-04-22-categories-runtime-wiring-fix.md` Task 1 step 3.
 *
 * Contract: when the user picks "새 분류 만들기" from the "분류 변경" bottom
 * sheet, the entry point produces a [CategoryEditorPrefill] with:
 *   - `name` = sender (if non-blank) else appName
 *   - `appPackageName` = the notification's packageName when derivation falls
 *     back to APP (sender blank); null otherwise (keyword/person-only
 *     categories stay app-unpinned)
 *   - `pendingRule` = the same auto-rule the assign-to-existing flow would
 *     derive (PERSON:sender when sender non-blank, otherwise APP:packageName)
 *   - `defaultAction` = the **dynamic-opposite** of the notification's
 *     currently-classified action. Mapping:
 *       current DIGEST  → PRIORITY
 *       current SILENT  → PRIORITY
 *       current IGNORE  → PRIORITY
 *       current PRIORITY → DIGEST
 *     (Resolved 2026-04-22 in plan Risks.) When the notification has no
 *     owning Category (classifier fallback), default is PRIORITY.
 *
 * After the user "saves" the editor, the Categories list must include the
 * new Category and a future notification from the same sender must
 * classify via it.
 *
 * All symbols referenced here (`AssignNotificationToCategoryUseCase.
 * buildPrefillForNewCategory`, `CategoryEditorPrefill`,
 * `DynamicOppositeActionPolicy`) do not exist yet. Task 3 turns them
 * green.
 */
class CreateCategoryFromNotificationPrefillTest {

    @Test
    fun prefill_for_notification_with_sender_uses_person_rule_and_sender_name() {
        val notification = notification(sender = "Bob", packageName = "com.foo")

        val prefill: CategoryEditorPrefill =
            AssignNotificationToCategoryUseCase.buildPrefillForNewCategory(
                notification = notification,
                currentCategoryAction = null,
            )

        assertEquals("Bob", prefill.name)
        // Person-typed derivation does not pin the Category to an app —
        // that's a keyword/person Category, not an APP Category.
        assertNull(prefill.appPackageName)
        val rule = prefill.pendingRule
        assertNotNull(rule)
        assertEquals(RuleTypeUi.PERSON, rule.type)
        assertEquals("Bob", rule.matchValue)
        assertEquals("PERSON:Bob", rule.id)
    }

    @Test
    fun prefill_for_notification_without_sender_uses_app_rule_and_app_name() {
        val notification = notification(
            sender = null,
            packageName = "com.foo",
            appName = "Foo앱",
        )

        val prefill = AssignNotificationToCategoryUseCase.buildPrefillForNewCategory(
            notification = notification,
            currentCategoryAction = null,
        )

        assertEquals("Foo앱", prefill.name)
        assertEquals("com.foo", prefill.appPackageName)
        val rule = prefill.pendingRule
        assertEquals(RuleTypeUi.APP, rule.type)
        assertEquals("com.foo", rule.matchValue)
        assertEquals("APP:com.foo", rule.id)
    }

    @Test
    fun dynamic_opposite_default_from_digest_resolves_to_priority() {
        val prefill = AssignNotificationToCategoryUseCase.buildPrefillForNewCategory(
            notification = notification(sender = "Bob", packageName = "com.foo"),
            currentCategoryAction = CategoryAction.DIGEST,
        )
        assertEquals(CategoryAction.PRIORITY, prefill.defaultAction)
    }

    @Test
    fun dynamic_opposite_default_from_silent_resolves_to_priority() {
        val prefill = AssignNotificationToCategoryUseCase.buildPrefillForNewCategory(
            notification = notification(sender = "Bob", packageName = "com.foo"),
            currentCategoryAction = CategoryAction.SILENT,
        )
        assertEquals(CategoryAction.PRIORITY, prefill.defaultAction)
    }

    @Test
    fun dynamic_opposite_default_from_ignore_resolves_to_priority() {
        val prefill = AssignNotificationToCategoryUseCase.buildPrefillForNewCategory(
            notification = notification(sender = "Bob", packageName = "com.foo"),
            currentCategoryAction = CategoryAction.IGNORE,
        )
        assertEquals(CategoryAction.PRIORITY, prefill.defaultAction)
    }

    @Test
    fun dynamic_opposite_default_from_priority_resolves_to_digest() {
        val prefill = AssignNotificationToCategoryUseCase.buildPrefillForNewCategory(
            notification = notification(sender = "Bob", packageName = "com.foo"),
            currentCategoryAction = CategoryAction.PRIORITY,
        )
        assertEquals(CategoryAction.DIGEST, prefill.defaultAction)
    }

    @Test
    fun dynamic_opposite_default_when_no_current_category_is_priority() {
        // No owning Category (classifier fallback). Default still sensible —
        // the user asked to "make this important enough to own a Category",
        // so PRIORITY is the conservative opening action.
        val prefill = AssignNotificationToCategoryUseCase.buildPrefillForNewCategory(
            notification = notification(sender = "Bob", packageName = "com.foo"),
            currentCategoryAction = null,
        )
        assertEquals(CategoryAction.PRIORITY, prefill.defaultAction)
    }

    @Test
    fun after_save_new_category_future_sender_match_routes_via_it() {
        // Simulate the user accepting the prefill and saving as a new
        // Category. A subsequent notification from the same sender should
        // classify through the new Category.
        val notification = notification(sender = "Bob", packageName = "com.foo")
        val prefill = AssignNotificationToCategoryUseCase.buildPrefillForNewCategory(
            notification = notification,
            currentCategoryAction = null,
        )

        // What the editor save path eventually persists:
        val savedRule = prefill.pendingRule
        val savedCategory = Category(
            id = "cat-new-bob",
            name = prefill.name,
            appPackageName = prefill.appPackageName,
            ruleIds = listOf(savedRule.id),
            action = prefill.defaultAction, // user did not override
            order = 0,
        )

        val classifier = NotificationClassifier(
            vipSenders = emptySet(),
            priorityKeywords = emptySet(),
            shoppingPackages = emptySet(),
        )
        val classification = classifier.classify(
            input = ClassificationInput(
                sender = "Bob",
                packageName = "com.foo",
                title = "다음 날",
                body = "회의 리마인드",
            ),
            rules = listOf(savedRule),
            categories = listOf(savedCategory),
        )

        assertEquals(NotificationDecision.PRIORITY, classification.decision)
        assertEquals(listOf("PERSON:Bob"), classification.matchedRuleIds)
    }

    private fun notification(
        sender: String?,
        packageName: String,
        appName: String = "카카오톡",
    ) = NotificationUiModel(
        id = "n-1",
        appName = appName,
        packageName = packageName,
        sender = sender,
        title = sender ?: appName,
        body = "내용",
        receivedAtLabel = "방금",
        status = NotificationStatusUi.SILENT,
        reasonTags = emptyList(),
    )
}
