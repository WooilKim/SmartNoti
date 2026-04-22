package com.smartnoti.app.data.rules

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.smartnoti.app.data.categories.CategoriesRepository
import com.smartnoti.app.domain.model.Category
import com.smartnoti.app.domain.model.CategoryAction
import com.smartnoti.app.domain.model.RuleTypeUi
import com.smartnoti.app.domain.model.RuleUiModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * RED-phase test for Drift 3 of plan
 * `docs/plans/2026-04-22-categories-runtime-wiring-fix.md` Task 1.
 *
 * Contract: deleting a Rule must cascade into every Category that referenced
 * its id via `ruleIds`. Without the cascade, Categories accumulate orphan
 * ids — classifier tolerates the miss but the 분류 tab surfaces phantom
 * rows and future Category math (specificity ladder, tie-break) operates on
 * stale state.
 *
 * Today `RulesRepository.deleteRule` only touches its own DataStore key, so
 * this test fails: after deleting `r1`, the Categories still list `r1`.
 *
 * Task 3 introduces `CategoriesRepository.onRuleDeleted(ruleId)` and wires
 * `RulesRepository.deleteRule` to invoke it after the Rule-side persist.
 * Category rows with empty `ruleIds` after cascade MUST be preserved (the
 * user sees a "rule-less" Category to edit, not a silent disappearance).
 */
@RunWith(RobolectricTestRunner::class)
class RulesRepositoryDeleteCascadeTest {

    private lateinit var context: Context
    private lateinit var rulesRepository: RulesRepository
    private lateinit var categoriesRepository: CategoriesRepository

    @Before
    fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        rulesRepository = RulesRepository.getInstance(context)
        categoriesRepository = CategoriesRepository.getInstance(context)
        // Clear any leaked state from prior tests in the same JVM run.
        rulesRepository.replaceAllRules(emptyList())
        categoriesRepository.replaceAllCategories(emptyList())
    }

    @Test
    fun deleting_rule_strips_its_id_from_every_category_rule_ids() = runBlocking {
        val r1 = RuleUiModel(
            id = "r1",
            title = "r1",
            subtitle = "",
            type = RuleTypeUi.PERSON,
            enabled = true,
            matchValue = "엄마",
        )
        val r2 = RuleUiModel(
            id = "r2",
            title = "r2",
            subtitle = "",
            type = RuleTypeUi.APP,
            enabled = true,
            matchValue = "com.example.app",
        )
        rulesRepository.replaceAllRules(listOf(r1, r2))

        val catA = Category(
            id = "catA",
            name = "A",
            appPackageName = null,
            ruleIds = listOf("r1"),
            action = CategoryAction.PRIORITY,
            order = 0,
        )
        val catB = Category(
            id = "catB",
            name = "B",
            appPackageName = null,
            ruleIds = listOf("r1", "r2"),
            action = CategoryAction.DIGEST,
            order = 1,
        )
        categoriesRepository.replaceAllCategories(listOf(catA, catB))

        rulesRepository.deleteRule("r1")

        assertEquals(
            "Rule r1 must be removed from Rules storage",
            listOf(r2),
            rulesRepository.currentRules(),
        )

        val categoriesAfter = categoriesRepository.currentCategories()
        assertEquals(
            "Cascade must preserve both Categories (empty ruleIds is allowed — do not drop the row)",
            2,
            categoriesAfter.size,
        )
        val catAAfter = categoriesAfter.first { it.id == "catA" }
        val catBAfter = categoriesAfter.first { it.id == "catB" }
        assertEquals(
            "catA.ruleIds must no longer contain r1",
            emptyList<String>(),
            catAAfter.ruleIds,
        )
        assertEquals(
            "catB.ruleIds must no longer contain r1 (but must still contain r2)",
            listOf("r2"),
            catBAfter.ruleIds,
        )
    }
}
