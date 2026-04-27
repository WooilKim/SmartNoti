package com.smartnoti.app.data.categories

import com.smartnoti.app.domain.model.Category
import com.smartnoti.app.domain.model.CategoryAction

/**
 * Pure migration that bumps the canonical onboarding PROMO Category from
 * `action=SILENT` to `action=DIGEST` if and only if the user has never
 * explicitly chosen the action (`userModifiedAction == false`).
 *
 * Plan `docs/plans/2026-04-27-fix-issue-478-promo-prefix-precedence-and-bundle-by-default.md`
 * Task 3 (Bug B2 + M1 migration). Background: PR #486 captured a real
 * Galaxy S24 (`R3CY2058DLJ`) install where the onboarding PROMO_QUIETING
 * preset Category was persisted with `action=SILENT`. Auto-source-tray
 * expansion only fires for DIGEST today (`SuppressedSourceAppsAutoExpansionPolicy`),
 * so SILENT-classified ads remained in the system tray. The user chose
 * option B2 over B1/B3 (PR #486 thread, 2026-04-27 message "B2 로 진행해줘"):
 * change the seeded default to DIGEST and migrate eligible existing rows
 * silently on next launch.
 *
 * Eligibility criteria (must all hold):
 *
 *  - `id == PROMO_CATEGORY_ID` — the canonical seeder id, the only id this
 *    migration touches. Categories created via the rule-lift migration
 *    (`cat-from-rule-…`) or user-created Categories are out of scope.
 *  - `action == SILENT` — DIGEST/PRIORITY/IGNORE rows pass through.
 *  - `userModifiedAction == false` — the user has not been observed
 *    touching the action picker on this row.
 *
 * The runner side ([MigratePromoCategoryActionRunner]) gates the whole pass
 * behind a one-shot DataStore flag so subsequent cold starts short-circuit
 * even if the user later toggles the action back to SILENT.
 */
object MigratePromoCategoryAction {

    /**
     * Stable id produced by [com.smartnoti.app.ui.screens.onboarding.OnboardingQuickStartCategoryApplier]
     * for the `PROMO_QUIETING` preset. Hard-coded here (rather than imported
     * from the applier) so this pure migration stays free of UI-layer
     * dependencies and so a renaming refactor in the applier surfaces as a
     * deliberate update here.
     */
    const val PROMO_CATEGORY_ID: String = "cat-onboarding-promo_quieting"

    fun migrate(categories: List<Category>): List<Category> {
        return categories.map { category ->
            if (isEligible(category)) {
                category.copy(action = CategoryAction.DIGEST)
            } else {
                category
            }
        }
    }

    private fun isEligible(category: Category): Boolean {
        return category.id == PROMO_CATEGORY_ID &&
            category.action == CategoryAction.SILENT &&
            !category.userModifiedAction
    }
}
