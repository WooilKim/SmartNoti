package com.smartnoti.app.data.categories

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.smartnoti.app.data.settings.SettingsRepository
import com.smartnoti.app.domain.model.Category
import com.smartnoti.app.domain.model.CategoryAction
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Plan `docs/plans/2026-04-27-fix-issue-478-promo-prefix-precedence-and-bundle-by-default.md`
 * Task 3 (Bug B2 + M1 migration runner I/O glue).
 *
 * Verifies the I/O-facing runner that wires
 * [MigratePromoCategoryAction] to [CategoriesRepository] and the
 * one-shot DataStore flag in [SettingsRepository]. Three contracts:
 *
 *  - Fresh / unmigrated install: PROMO Category SILENT (`userModifiedAction=
 *    false`) is rewritten to DIGEST and the flag flips to true.
 *  - User-modified install: PROMO Category SILENT (`userModifiedAction=true`)
 *    is preserved; the flag still flips to true so subsequent cold starts
 *    short-circuit.
 *  - Idempotent: subsequent runs return [Result.AlreadyMigrated] without
 *    touching the Category list (cheap fast-skip).
 */
@RunWith(RobolectricTestRunner::class)
class MigratePromoCategoryActionRunnerTest {

    private lateinit var context: Context
    private lateinit var categoriesRepository: CategoriesRepository
    private lateinit var settingsRepository: SettingsRepository

    @Before
    fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        CategoriesRepository.clearInstanceForTest()
        SettingsRepository.clearInstanceForTest()
        categoriesRepository = CategoriesRepository.getInstance(context)
        settingsRepository = SettingsRepository.getInstance(context)
        categoriesRepository.replaceAllCategories(emptyList())
        settingsRepository.clearAllForTest()
    }

    @After
    fun tearDown() {
        CategoriesRepository.clearInstanceForTest()
        SettingsRepository.clearInstanceForTest()
    }

    @Test
    fun unmigrated_install_with_silent_promo_bumps_to_digest_and_marks_flag() = runBlocking {
        categoriesRepository.replaceAllCategories(
            listOf(
                Category(
                    id = "cat-onboarding-promo_quieting",
                    name = "프로모션 알림",
                    appPackageName = null,
                    ruleIds = listOf("rule-promo"),
                    action = CategoryAction.SILENT,
                    order = 1,
                    userModifiedAction = false,
                ),
            ),
        )

        val runner = MigratePromoCategoryActionRunner(
            categoriesRepository = categoriesRepository,
            settingsRepository = settingsRepository,
        )

        val result = runner.run()

        assertTrue("first run must report Migrated", result is MigratePromoCategoryActionRunner.Result.Migrated)
        val after = categoriesRepository.currentCategories()
        assertEquals(CategoryAction.DIGEST, after.single().action)
        assertTrue(settingsRepository.isPromoQuietingActionMigrationV3Applied())
    }

    @Test
    fun user_modified_silent_promo_is_preserved_and_flag_still_flips() = runBlocking {
        categoriesRepository.replaceAllCategories(
            listOf(
                Category(
                    id = "cat-onboarding-promo_quieting",
                    name = "프로모션 알림",
                    appPackageName = null,
                    ruleIds = listOf("rule-promo"),
                    action = CategoryAction.SILENT,
                    order = 1,
                    userModifiedAction = true,
                ),
            ),
        )

        val runner = MigratePromoCategoryActionRunner(
            categoriesRepository = categoriesRepository,
            settingsRepository = settingsRepository,
        )

        runner.run()

        val after = categoriesRepository.currentCategories()
        assertEquals(
            "User-explicit SILENT must be preserved",
            CategoryAction.SILENT,
            after.single().action,
        )
        assertTrue(
            "Flag still flips so we do not re-scan on every cold start",
            settingsRepository.isPromoQuietingActionMigrationV3Applied(),
        )
    }

    @Test
    fun second_run_is_already_migrated_and_does_not_touch_categories() = runBlocking {
        categoriesRepository.replaceAllCategories(
            listOf(
                Category(
                    id = "cat-onboarding-promo_quieting",
                    name = "프로모션 알림",
                    appPackageName = null,
                    ruleIds = listOf("rule-promo"),
                    action = CategoryAction.SILENT,
                    order = 1,
                    userModifiedAction = false,
                ),
            ),
        )
        val runner = MigratePromoCategoryActionRunner(
            categoriesRepository = categoriesRepository,
            settingsRepository = settingsRepository,
        )
        runner.run()
        // Simulate the user toggling back to SILENT post-migration. They
        // have now explicitly chosen SILENT (userModifiedAction=true).
        val firstRun = categoriesRepository.currentCategories().single()
        categoriesRepository.replaceAllCategories(
            listOf(firstRun.copy(action = CategoryAction.SILENT, userModifiedAction = true)),
        )

        val result = runner.run()

        assertTrue(
            "second run must report AlreadyMigrated",
            result is MigratePromoCategoryActionRunner.Result.AlreadyMigrated,
        )
        // Post-migration user choice is preserved — runner did not re-bump.
        assertEquals(
            CategoryAction.SILENT,
            categoriesRepository.currentCategories().single().action,
        )
    }
}
