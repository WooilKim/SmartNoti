package com.smartnoti.app.data.categories

import android.content.Context
import com.smartnoti.app.data.rules.RulesRepository
import com.smartnoti.app.data.settings.SettingsRepository
import com.smartnoti.app.domain.model.RuleActionUi

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
 * The legacy rule-action column disappeared from `RuleStorageCodec` in
 * Phase P1 Task 4, so the runner reads it directly via
 * [LegacyRuleActionReader] BEFORE anything re-encodes the Rule payload.
 */
class MigrateRulesToCategoriesRunner(
    private val rulesRepository: RulesRepository,
    private val categoriesRepository: CategoriesRepository,
    private val settingsRepository: SettingsRepository,
    private val legacyActionsLoader: suspend () -> Map<String, RuleActionUi>,
) {
    suspend fun run(): Result {
        if (settingsRepository.isRulesToCategoriesMigrated()) {
            return Result.AlreadyMigrated
        }

        val legacyActions = legacyActionsLoader()
        val rules = rulesRepository.currentRules()
        val existing = categoriesRepository.currentCategories()
        val merged = RuleToCategoryMigration.migrate(
            rules = rules,
            existingCategories = existing,
            legacyActions = legacyActions,
        )
        val createdCount = merged.size - existing.size

        categoriesRepository.replaceAllCategories(merged)
        settingsRepository.setRulesToCategoriesMigrated(true)

        return Result.Migrated(createdCount = createdCount)
    }

    sealed class Result {
        object AlreadyMigrated : Result()
        data class Migrated(val createdCount: Int) : Result()
    }

    companion object {
        fun create(context: Context): MigrateRulesToCategoriesRunner {
            val appContext = context.applicationContext
            val legacyReader = LegacyRuleActionReader(appContext)
            return MigrateRulesToCategoriesRunner(
                rulesRepository = RulesRepository.getInstance(appContext),
                categoriesRepository = CategoriesRepository.getInstance(appContext),
                settingsRepository = SettingsRepository.getInstance(appContext),
                legacyActionsLoader = { legacyReader.readRuleActions() },
            )
        }
    }
}
