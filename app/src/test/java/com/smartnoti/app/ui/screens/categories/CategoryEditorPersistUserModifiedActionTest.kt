package com.smartnoti.app.ui.screens.categories

import com.smartnoti.app.domain.model.Category
import com.smartnoti.app.domain.model.CategoryAction
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Plan `docs/plans/2026-04-27-fix-issue-478-promo-prefix-precedence-and-bundle-by-default.md`
 * Task 3 (Bug B2 + M1 migration).
 *
 * The CategoryEditor build path must stamp `userModifiedAction = true` on the
 * persisted Category whenever the user has touched the action picker, so the
 * Bug B2 PROMO bump migration (and any future preset-default migrations)
 * respect the explicit choice. Concretely:
 *
 *  - **New Category**: any save with a user-chosen action stamps the flag.
 *    Even if they happened to keep the prefilled default, the act of saving
 *    a brand-new Category is itself a user-driven choice.
 *  - **Edit existing**: the flag stamps to true if the action changes from
 *    the editing target. Saving the editor with the action unchanged keeps
 *    the previous flag — we do not "downgrade" a previously-true flag, but
 *    we also do not falsely promote a never-touched seed.
 *
 * The function under test is the pure builder
 * `buildCategoryFromDraft` extracted from `CategoryEditorScreen` so the
 * Compose-free unit can be exercised here; the orchestrator remains in
 * [CategoryEditorScreen].
 */
class CategoryEditorPersistUserModifiedActionTest {

    @Test
    fun new_category_save_stamps_user_modified_action_true() {
        val persisted = buildCategoryFromDraft(
            editing = null,
            name = "쿠팡",
            appPackageName = "com.coupang.mobile",
            selectedRuleIds = listOf("rule-app-coupang"),
            action = CategoryAction.PRIORITY,
            currentCategoriesCount = 0,
        )

        assertTrue(
            "Newly-created Categories must be marked userModifiedAction=true so " +
                "future preset-default migrations cannot overwrite them.",
            persisted.userModifiedAction,
        )
    }

    @Test
    fun edit_with_action_change_stamps_user_modified_action_true() {
        val existing = Category(
            id = "cat-onboarding-promo_quieting",
            name = "프로모션 알림",
            appPackageName = null,
            ruleIds = listOf("rule-promo"),
            action = CategoryAction.DIGEST,
            order = 1,
            userModifiedAction = false,
        )

        val persisted = buildCategoryFromDraft(
            editing = existing,
            name = existing.name,
            appPackageName = existing.appPackageName,
            selectedRuleIds = existing.ruleIds,
            action = CategoryAction.PRIORITY,
            currentCategoriesCount = 1,
        )

        assertTrue(
            "Changing the action via the editor must stamp userModifiedAction=true.",
            persisted.userModifiedAction,
        )
    }

    @Test
    fun edit_without_action_change_preserves_existing_flag_false() {
        val existing = Category(
            id = "cat-onboarding-promo_quieting",
            name = "프로모션 알림",
            appPackageName = null,
            ruleIds = listOf("rule-promo"),
            action = CategoryAction.SILENT,
            order = 1,
            userModifiedAction = false,
        )

        // User opens the editor, edits only the name, save with same action.
        val persisted = buildCategoryFromDraft(
            editing = existing,
            name = "프로모션",
            appPackageName = existing.appPackageName,
            selectedRuleIds = existing.ruleIds,
            action = existing.action,
            currentCategoriesCount = 1,
        )

        assertFalse(
            "Save with unchanged action must NOT promote a previously-untouched flag.",
            persisted.userModifiedAction,
        )
    }

    @Test
    fun edit_without_action_change_preserves_existing_flag_true() {
        val existing = Category(
            id = "cat-user-1",
            name = "쿠팡",
            appPackageName = "com.coupang.mobile",
            ruleIds = listOf("rule-app-coupang"),
            action = CategoryAction.PRIORITY,
            order = 0,
            userModifiedAction = true,
        )

        // User opens the editor, edits only the name, save with same action.
        val persisted = buildCategoryFromDraft(
            editing = existing,
            name = "쿠팡 배송",
            appPackageName = existing.appPackageName,
            selectedRuleIds = existing.ruleIds,
            action = existing.action,
            currentCategoriesCount = 1,
        )

        assertTrue(
            "Save with unchanged action must preserve a previously-true flag.",
            persisted.userModifiedAction,
        )
    }
}
