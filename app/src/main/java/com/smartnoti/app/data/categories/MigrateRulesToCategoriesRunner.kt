package com.smartnoti.app.data.categories

import com.smartnoti.app.data.rules.RulesRepository
import com.smartnoti.app.data.settings.SettingsRepository

/**
 * I/O-facing runner that glues [RuleToCategoryMigration] to the three
 * repositories it touches on first launch post-upgrade. Plan
 * `docs/plans/2026-04-22-categories-split-rules-actions.md` Phase P1 Task 3.
 *
 * `run()` is idempotent twice over — once via the
 * `SettingsRepository.rulesToCategoriesMigrated` flag (fast skip on every
 * cold start after the first) and once via the stable `cat-from-rule-<id>`
 * category id scheme (re-running against the same data produces the same
 * state even if the flag was missed).
 *
 * Application bootstrap (or any lazy entry point that needs a Category
 * graph) should invoke `run()` and ignore the boolean result; callers that
 * want to surface "we just migrated N rules" can read the return value.
 */
class MigrateRulesToCategoriesRunner(
    private val rulesRepository: RulesRepository,
    private val categoriesRepository: CategoriesRepository,
    private val settingsRepository: SettingsRepository,
) {
    suspend fun run(): Result {
        if (settingsRepository.isRulesToCategoriesMigrated()) {
            return Result.AlreadyMigrated
        }

        val rules = rulesRepository.currentRules()
        val existing = categoriesRepository.currentCategories()
        val merged = RuleToCategoryMigration.migrate(rules = rules, existingCategories = existing)
        val createdCount = merged.size - existing.size

        categoriesRepository.replaceAllCategories(merged)
        settingsRepository.setRulesToCategoriesMigrated(true)

        return Result.Migrated(createdCount = createdCount)
    }

    sealed class Result {
        object AlreadyMigrated : Result()
        data class Migrated(val createdCount: Int) : Result()
    }
}
