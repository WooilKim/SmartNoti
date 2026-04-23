package com.smartnoti.app.ui.screens.onboarding

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.smartnoti.app.data.categories.CategoriesRepository
import com.smartnoti.app.data.local.NotificationRepository
import com.smartnoti.app.data.rules.RulesRepository
import com.smartnoti.app.data.settings.SettingsRepository
import com.smartnoti.app.domain.model.CategoryAction
import com.smartnoti.app.domain.usecase.RuleDraftFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * RED-then-GREEN coverage for plan
 * `docs/plans/2026-04-23-onboarding-quick-start-seed-categories.md` Task 3.
 *
 * Pins the contract that `OnboardingQuickStartSettingsApplier.applySelection`
 * persists Categories alongside Rules in the same call. Before this plan, the
 * applier wrote to `RulesRepository` only — leaving the 분류 tab empty after
 * onboarding completed. The added wiring delegates Category construction to
 * [OnboardingQuickStartCategoryApplier] and upserts each result through
 * [CategoriesRepository.upsertCategory] so the deterministic ids
 * (`cat-onboarding-<presetId.lowercase>`) make repeat applications idempotent.
 */
@RunWith(RobolectricTestRunner::class)
class OnboardingQuickStartCategoriesWiringTest {

    private lateinit var context: Context
    private lateinit var rulesRepository: RulesRepository
    private lateinit var categoriesRepository: CategoriesRepository
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var notificationRepository: NotificationRepository
    private lateinit var applier: OnboardingQuickStartSettingsApplier

    @Before
    fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        RulesRepository.clearInstanceForTest()
        CategoriesRepository.clearInstanceForTest()
        SettingsRepository.clearInstanceForTest()
        rulesRepository = RulesRepository.getInstance(context)
        categoriesRepository = CategoriesRepository.getInstance(context)
        settingsRepository = SettingsRepository.getInstance(context)
        notificationRepository = NotificationRepository.getInstance(context)

        rulesRepository.replaceAllRules(emptyList())
        categoriesRepository.replaceAllCategories(emptyList())

        val ruleApplier = OnboardingQuickStartRuleApplier(RuleDraftFactory())
        val categoryApplier = OnboardingQuickStartCategoryApplier()
        applier = OnboardingQuickStartSettingsApplier(
            ruleApplier = ruleApplier,
            categoryApplier = categoryApplier,
        )
    }

    @After
    fun tearDown() {
        RulesRepository.clearInstanceForTest()
        CategoriesRepository.clearInstanceForTest()
        SettingsRepository.clearInstanceForTest()
    }

    @Test
    fun all_three_presets_seed_three_categories_in_canonical_order() = runBlocking {
        applier.applySelection(
            rulesRepository = rulesRepository,
            settingsRepository = settingsRepository,
            categoriesRepository = categoriesRepository,
            notificationRepository = notificationRepository,
            selectedPresetIds = setOf(
                OnboardingQuickStartPresetId.PROMO_QUIETING,
                OnboardingQuickStartPresetId.REPEAT_BUNDLING,
                OnboardingQuickStartPresetId.IMPORTANT_PRIORITY,
            ),
        )

        val persistedCategories = categoriesRepository.observeCategories().first()
        val persistedRules = rulesRepository.observeRules().first()

        assertEquals(
            listOf(
                "cat-onboarding-important_priority",
                "cat-onboarding-promo_quieting",
                "cat-onboarding-repeat_bundling",
            ),
            persistedCategories.map { it.id },
        )
        assertEquals(
            listOf(
                CategoryAction.PRIORITY,
                CategoryAction.DIGEST,
                CategoryAction.DIGEST,
            ),
            persistedCategories.map { it.action },
        )
        assertEquals(
            listOf("중요 알림", "프로모션 알림", "반복 알림"),
            persistedCategories.map { it.name },
        )
        assertTrue(persistedCategories.all { it.appPackageName == null })

        // Each Category's ruleIds must reference an actually persisted rule —
        // categories that point at phantom rule ids would silently no-op the
        // classifier hot path.
        val persistedRuleIds = persistedRules.map { it.id }.toSet()
        persistedCategories.forEach { category ->
            assertEquals(1, category.ruleIds.size)
            assertTrue(
                "Category ${category.id} references unknown rule ${category.ruleIds.single()}",
                category.ruleIds.single() in persistedRuleIds,
            )
        }
    }

    @Test
    fun selecting_a_single_preset_seeds_a_single_category() = runBlocking {
        applier.applySelection(
            rulesRepository = rulesRepository,
            settingsRepository = settingsRepository,
            categoriesRepository = categoriesRepository,
            notificationRepository = notificationRepository,
            selectedPresetIds = setOf(OnboardingQuickStartPresetId.IMPORTANT_PRIORITY),
        )

        val persistedCategories = categoriesRepository.observeCategories().first()
        assertEquals(1, persistedCategories.size)
        val only = persistedCategories.single()
        assertEquals("cat-onboarding-important_priority", only.id)
        assertEquals(CategoryAction.PRIORITY, only.action)
        assertNull(only.appPackageName)
    }

    @Test
    fun re_applying_the_same_selection_does_not_duplicate_categories() = runBlocking {
        val selection = setOf(
            OnboardingQuickStartPresetId.IMPORTANT_PRIORITY,
            OnboardingQuickStartPresetId.PROMO_QUIETING,
        )

        applier.applySelection(
            rulesRepository = rulesRepository,
            settingsRepository = settingsRepository,
            categoriesRepository = categoriesRepository,
            notificationRepository = notificationRepository,
            selectedPresetIds = selection,
        )
        val firstPass = categoriesRepository.observeCategories().first()

        applier.applySelection(
            rulesRepository = rulesRepository,
            settingsRepository = settingsRepository,
            categoriesRepository = categoriesRepository,
            notificationRepository = notificationRepository,
            selectedPresetIds = selection,
        )
        val secondPass = categoriesRepository.observeCategories().first()

        assertEquals(2, firstPass.size)
        assertEquals(2, secondPass.size)
        assertEquals(firstPass.map { it.id }, secondPass.map { it.id })
    }

    @Test
    fun empty_selection_does_not_seed_categories() = runBlocking {
        applier.applySelection(
            rulesRepository = rulesRepository,
            settingsRepository = settingsRepository,
            categoriesRepository = categoriesRepository,
            notificationRepository = notificationRepository,
            selectedPresetIds = emptySet(),
        )

        assertTrue(categoriesRepository.observeCategories().first().isEmpty())
    }
}
