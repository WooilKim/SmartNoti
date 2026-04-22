package com.smartnoti.app.notification

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.smartnoti.app.data.categories.CategoriesRepository
import com.smartnoti.app.data.rules.RulesRepository
import com.smartnoti.app.data.settings.SettingsRepository
import com.smartnoti.app.domain.model.Category
import com.smartnoti.app.domain.model.CategoryAction
import com.smartnoti.app.domain.model.RuleTypeUi
import com.smartnoti.app.domain.model.RuleUiModel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * RED-phase test for Drift 1 of plan
 * `docs/plans/2026-04-22-categories-runtime-wiring-fix.md` Task 1.
 *
 * Pins the contract that the listener's pre-classify read must pull Categories
 * alongside Rules. Without Categories, post-P1 classifier cannot resolve any
 * user-driven action and always falls back to SILENT.
 *
 * Task 1 ships [NotificationListenerClassificationSources] with the production
 * bug preserved (categories returned as `emptyList()`); this test fails
 * because the seeded PRIORITY Category never reaches the classifier input.
 *
 * Task 2 fixes [NotificationListenerClassificationSources.read] to actually
 * consult [CategoriesRepository] and wires the listener call site to use it.
 */
@RunWith(RobolectricTestRunner::class)
class SmartNotiNotificationListenerServiceCategoryInjectionTest {

    private lateinit var context: Context
    private lateinit var rulesRepository: RulesRepository
    private lateinit var categoriesRepository: CategoriesRepository
    private lateinit var settingsRepository: SettingsRepository

    @Before
    fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        rulesRepository = RulesRepository.getInstance(context)
        categoriesRepository = CategoriesRepository.getInstance(context)
        SettingsRepository.clearInstanceForTest()
        settingsRepository = SettingsRepository.getInstance(context)
        settingsRepository.clearAllForTest()
    }

    @After
    fun tearDown() {
        SettingsRepository.clearInstanceForTest()
    }

    @Test
    fun listener_classification_source_read_includes_categories_from_repository() = runBlocking {
        // Seed one matcher Rule + one Category that wraps it with PRIORITY
        // action. This mirrors the "user pinned a sender to Priority" state
        // expected after a feedback upsert.
        val rule = RuleUiModel(
            id = "person:엄마",
            title = "엄마",
            subtitle = "항상 바로 보기",
            type = RuleTypeUi.PERSON,
            enabled = true,
            matchValue = "엄마",
        )
        rulesRepository.replaceAllRules(listOf(rule))

        val category = Category(
            id = "cat-from-rule-person:엄마",
            name = "엄마",
            appPackageName = null,
            ruleIds = listOf(rule.id),
            action = CategoryAction.PRIORITY,
            order = 0,
        )
        categoriesRepository.replaceAllCategories(listOf(category))

        // Sanity — the repositories themselves hold the data. The drift is
        // purely at the listener's read call site.
        assertEquals(listOf(rule), rulesRepository.currentRules())
        assertEquals(listOf(category), categoriesRepository.currentCategories())

        val sources = NotificationListenerClassificationSources(
            rulesRepository = rulesRepository,
            categoriesRepository = categoriesRepository,
            settingsRepository = settingsRepository,
        )
        val snapshot = sources.read()

        assertEquals(
            "Listener classification read must include the user's Rules",
            listOf(rule),
            snapshot.rules,
        )
        // The load-bearing assertion for Drift 1: the listener's snapshot must
        // carry Categories into [NotificationCaptureProcessor.process], not
        // silently drop them (which would force classifier to SILENT default).
        assertTrue(
            "Listener classification read must include Categories from CategoriesRepository",
            snapshot.categories.isNotEmpty(),
        )
        assertEquals(
            listOf(category),
            snapshot.categories,
        )
    }
}
