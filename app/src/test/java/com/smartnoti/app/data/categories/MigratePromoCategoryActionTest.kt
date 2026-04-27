package com.smartnoti.app.data.categories

import com.smartnoti.app.domain.model.Category
import com.smartnoti.app.domain.model.CategoryAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * RED-first contract for plan
 * `docs/plans/2026-04-27-fix-issue-478-promo-prefix-precedence-and-bundle-by-default.md`
 * Task 3 (Bug B2 + M1 migration).
 *
 * Pure-function migration that bumps any seeded PROMO Category whose
 * `userModifiedAction` is still false from `action=SILENT` to `action=DIGEST`.
 * The pure shape mirrors [RuleToCategoryMigration] — I/O glue for reading the
 * one-shot DataStore flag + writing the merged list lives in a sibling
 * runner that depends on this object.
 *
 * **Definition of a "PROMO Category" for this migration**: id matches the
 * canonical onboarding seeder id `cat-onboarding-promo_quieting`. We
 * deliberately do not match by name (locale-sensitive), by ruleIds (user
 * may have edited membership), or by keyword content (the resolver, not the
 * Category, owns keyword semantics). The id is the stable handle the seeder
 * produces and the only id this migration touches.
 *
 * **Why migration applies even though the seeder already emits DIGEST today**:
 * older installs whose Categories were created via the
 * `MigrateRulesToCategoriesRunner` rule-lift path (or via a hypothetical
 * older onboarding revision) may carry SILENT for the promo Category. Bug B2
 * landed on the user's device in exactly this shape (PR #486
 * issuecomment-4327574021). The migration closes that historical drift
 * without disturbing user-edited rows.
 */
class MigratePromoCategoryActionTest {

    @Test
    fun bumps_promo_category_silent_to_digest_when_user_has_not_modified_action() {
        val before = listOf(
            promoCategory(action = CategoryAction.SILENT, userModifiedAction = false),
        )

        val after = MigratePromoCategoryAction.migrate(before)

        assertEquals(1, after.size)
        assertEquals(
            "PROMO Category with userModifiedAction=false AND action=SILENT must " +
                "be bumped to DIGEST so source-tray auto-expansion fires for ads.",
            CategoryAction.DIGEST,
            after.single().action,
        )
        assertFalse(
            "Migration must NOT touch userModifiedAction — that flag tracks " +
                "explicit user choice, not seeder/migration writes.",
            after.single().userModifiedAction,
        )
    }

    @Test
    fun preserves_promo_category_silent_when_user_has_modified_action() {
        val before = listOf(
            promoCategory(action = CategoryAction.SILENT, userModifiedAction = true),
        )

        val after = MigratePromoCategoryAction.migrate(before)

        assertEquals(
            "userModifiedAction=true means the user explicitly chose SILENT; " +
                "migration must respect that choice and leave the row untouched.",
            CategoryAction.SILENT,
            after.single().action,
        )
        assertTrue(after.single().userModifiedAction)
    }

    @Test
    fun does_not_touch_promo_category_already_digest() {
        val before = listOf(
            promoCategory(action = CategoryAction.DIGEST, userModifiedAction = false),
        )

        val after = MigratePromoCategoryAction.migrate(before)

        assertEquals(CategoryAction.DIGEST, after.single().action)
        // Identity-equal — nothing was rewritten.
        assertSame(before.single(), after.single())
    }

    @Test
    fun does_not_touch_promo_category_priority_or_ignore() {
        // Defensive: even if a prior bug or unusual user state put PROMO
        // into PRIORITY/IGNORE, the migration only handles the SILENT case
        // (its sole purpose). PRIORITY/IGNORE pass through unchanged.
        val before = listOf(
            promoCategory(
                id = "cat-onboarding-promo_quieting-priority",
                action = CategoryAction.PRIORITY,
                userModifiedAction = false,
            ),
            promoCategory(
                id = "cat-onboarding-promo_quieting-ignore",
                action = CategoryAction.IGNORE,
                userModifiedAction = false,
            ),
        )

        val after = MigratePromoCategoryAction.migrate(before)

        assertEquals(CategoryAction.PRIORITY, after[0].action)
        assertEquals(CategoryAction.IGNORE, after[1].action)
    }

    @Test
    fun does_not_touch_non_promo_categories() {
        val important = Category(
            id = "cat-onboarding-important_priority",
            name = "중요 알림",
            appPackageName = null,
            ruleIds = listOf("rule-imp"),
            action = CategoryAction.SILENT, // anomalously SILENT
            order = 0,
            userModifiedAction = false,
        )
        val repeatBundling = Category(
            id = "cat-onboarding-repeat_bundling",
            name = "반복 알림",
            appPackageName = null,
            ruleIds = listOf("rule-repeat"),
            action = CategoryAction.SILENT, // anomalously SILENT
            order = 2,
            userModifiedAction = false,
        )
        val userCustom = Category(
            id = "cat-user-1738400000000",
            name = "마케팅",
            appPackageName = null,
            ruleIds = listOf("rule-x"),
            action = CategoryAction.SILENT,
            order = 3,
            userModifiedAction = false,
        )
        val ruleLifted = Category(
            id = "cat-from-rule-rule-keyword-ad",
            name = "광고",
            appPackageName = null,
            ruleIds = listOf("rule-keyword-ad"),
            action = CategoryAction.SILENT,
            order = 4,
            userModifiedAction = false,
        )
        val before = listOf(important, repeatBundling, userCustom, ruleLifted)

        val after = MigratePromoCategoryAction.migrate(before)

        assertEquals(before, after)
    }

    @Test
    fun migration_is_idempotent() {
        val before = listOf(
            promoCategory(action = CategoryAction.SILENT, userModifiedAction = false),
        )

        val pass1 = MigratePromoCategoryAction.migrate(before)
        val pass2 = MigratePromoCategoryAction.migrate(pass1)

        assertEquals(pass1, pass2)
        assertEquals(CategoryAction.DIGEST, pass2.single().action)
    }

    @Test
    fun mixed_install_only_bumps_eligible_promo_row() {
        val promoEligible = promoCategory(
            id = "cat-onboarding-promo_quieting",
            action = CategoryAction.SILENT,
            userModifiedAction = false,
        )
        val important = Category(
            id = "cat-onboarding-important_priority",
            name = "중요 알림",
            appPackageName = null,
            ruleIds = listOf("rule-imp"),
            action = CategoryAction.PRIORITY,
            order = 0,
            userModifiedAction = false,
        )
        val before = listOf(important, promoEligible)

        val after = MigratePromoCategoryAction.migrate(before)

        assertEquals(2, after.size)
        // Important untouched.
        assertSame(important, after[0])
        // Promo bumped.
        assertEquals(CategoryAction.DIGEST, after[1].action)
        assertEquals(promoEligible.id, after[1].id)
        assertEquals(promoEligible.name, after[1].name)
        assertEquals(promoEligible.ruleIds, after[1].ruleIds)
        assertEquals(promoEligible.order, after[1].order)
    }

    private fun promoCategory(
        id: String = "cat-onboarding-promo_quieting",
        action: CategoryAction,
        userModifiedAction: Boolean,
    ): Category = Category(
        id = id,
        name = "프로모션 알림",
        appPackageName = null,
        ruleIds = listOf("rule-onboarding-promo_quieting"),
        action = action,
        order = 1,
        userModifiedAction = userModifiedAction,
    )
}
