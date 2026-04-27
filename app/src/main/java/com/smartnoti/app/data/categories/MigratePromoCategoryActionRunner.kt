package com.smartnoti.app.data.categories

import android.content.Context
import com.smartnoti.app.data.settings.SettingsRepository

/**
 * I/O-facing runner that wires [MigratePromoCategoryAction] to
 * [CategoriesRepository] and the one-shot DataStore flag in
 * [SettingsRepository].
 *
 * Plan `docs/plans/2026-04-27-fix-issue-478-promo-prefix-precedence-and-bundle-by-default.md`
 * Task 3 (Bug B2 + M1 migration). Mirrors the shape of
 * [MigrateRulesToCategoriesRunner] (gate by a Settings flag, do the work,
 * persist + flip the flag) so the two runners read the same in diffs.
 *
 * `run()` is idempotent twice over — once via the
 * `isPromoQuietingActionMigrationV3Applied` flag (fast skip on every cold
 * start after the first) and once via the pure migration's
 * `userModifiedAction == false` guard (re-running against a user-edited
 * row would still be a no-op even if the flag was missed).
 *
 * Failure handling: if the migration throws (e.g. DataStore I/O glitch),
 * the flag stays unflipped so a subsequent cold start retries. This matches
 * the existing `runRulesToCategoriesMigration` path in `MainActivity`.
 */
class MigratePromoCategoryActionRunner(
    private val categoriesRepository: CategoriesRepository,
    private val settingsRepository: SettingsRepository,
) {

    suspend fun run(): Result {
        if (settingsRepository.isPromoQuietingActionMigrationV3Applied()) {
            return Result.AlreadyMigrated
        }

        val before = categoriesRepository.currentCategories()
        val after = MigratePromoCategoryAction.migrate(before)
        if (after != before) {
            categoriesRepository.replaceAllCategories(after)
        }
        settingsRepository.setPromoQuietingActionMigrationV3Applied(true)
        val bumpedCount = after.indices.count { idx ->
            // A row that materially changed action between before/after.
            before[idx].action != after[idx].action
        }
        return Result.Migrated(bumpedCount = bumpedCount)
    }

    sealed class Result {
        object AlreadyMigrated : Result()
        data class Migrated(val bumpedCount: Int) : Result()
    }

    companion object {
        fun create(context: Context): MigratePromoCategoryActionRunner {
            val appContext = context.applicationContext
            return MigratePromoCategoryActionRunner(
                categoriesRepository = CategoriesRepository.getInstance(appContext),
                settingsRepository = SettingsRepository.getInstance(appContext),
            )
        }
    }
}
