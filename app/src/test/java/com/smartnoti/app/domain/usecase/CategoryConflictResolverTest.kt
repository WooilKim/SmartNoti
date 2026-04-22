package com.smartnoti.app.domain.usecase

import com.smartnoti.app.domain.model.Category
import com.smartnoti.app.domain.model.CategoryAction
import com.smartnoti.app.domain.model.RuleTypeUi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Tests for [CategoryConflictResolver] — plan
 * `docs/plans/2026-04-22-categories-split-rules-actions.md` Phase P2 Task 6.
 *
 * The resolver picks the winning Category given:
 *  1. Rule-type specificity ladder (APP > KEYWORD > PERSON > SCHEDULE >
 *     REPEAT_BUNDLE). A Category's specificity equals the highest ladder
 *     rank among its member rules that actually fired.
 *  2. App-pin bonus: `appPackageName != null` adds a tier on top of the
 *     rule-type ladder, so an app-pinned Category beats any Category whose
 *     best matched rule is not app-pinned, regardless of rule type.
 *  3. Drag-order tie-break within the same specificity tier: lowest `order`
 *     wins (= top of the 분류 tab).
 *
 * The tie-break drag-order contract is also exercised by [CategoryTieBreakTest]
 * (from Phase P1 Task 1); this file focuses on the rule-type ladder and the
 * app-pin interaction.
 */
class CategoryConflictResolverTest {

    private val resolver = CategoryConflictResolver()

    @Test
    fun empty_match_returns_null() {
        assertNull(
            resolver.resolve(
                matched = emptyList(),
                allCategories = emptyList(),
                matchedRuleTypes = emptyMap(),
            )
        )
    }

    @Test
    fun single_match_is_returned_as_is() {
        val only = Category(
            id = "cat-solo",
            name = "단독",
            appPackageName = null,
            ruleIds = listOf("r-solo"),
            action = CategoryAction.DIGEST,
            order = 0,
        )

        val winner = resolver.resolve(
            matched = listOf(only),
            allCategories = listOf(only),
            matchedRuleTypes = mapOf("r-solo" to RuleTypeUi.KEYWORD),
        )

        assertEquals("cat-solo", winner?.id)
    }

    @Test
    fun app_rule_type_beats_keyword_rule_type_regardless_of_order() {
        val appCategory = cat(id = "cat-app", order = 5, ruleIds = listOf("r-app"))
        val keywordCategory = cat(id = "cat-keyword", order = 0, ruleIds = listOf("r-kw"))

        val winner = resolver.resolve(
            matched = listOf(keywordCategory, appCategory),
            allCategories = listOf(keywordCategory, appCategory),
            matchedRuleTypes = mapOf(
                "r-app" to RuleTypeUi.APP,
                "r-kw" to RuleTypeUi.KEYWORD,
            ),
        )

        assertEquals("cat-app", winner?.id)
    }

    @Test
    fun keyword_rule_type_beats_person_rule_type() {
        val keywordCategory = cat(id = "cat-kw", order = 5, ruleIds = listOf("r-kw"))
        val personCategory = cat(id = "cat-person", order = 0, ruleIds = listOf("r-person"))

        val winner = resolver.resolve(
            matched = listOf(personCategory, keywordCategory),
            allCategories = listOf(personCategory, keywordCategory),
            matchedRuleTypes = mapOf(
                "r-kw" to RuleTypeUi.KEYWORD,
                "r-person" to RuleTypeUi.PERSON,
            ),
        )

        assertEquals("cat-kw", winner?.id)
    }

    @Test
    fun person_rule_type_beats_schedule_rule_type() {
        val personCategory = cat(id = "cat-person", order = 5, ruleIds = listOf("r-person"))
        val scheduleCategory = cat(id = "cat-schedule", order = 0, ruleIds = listOf("r-schedule"))

        val winner = resolver.resolve(
            matched = listOf(scheduleCategory, personCategory),
            allCategories = listOf(scheduleCategory, personCategory),
            matchedRuleTypes = mapOf(
                "r-person" to RuleTypeUi.PERSON,
                "r-schedule" to RuleTypeUi.SCHEDULE,
            ),
        )

        assertEquals("cat-person", winner?.id)
    }

    @Test
    fun schedule_rule_type_beats_repeat_bundle_rule_type() {
        val scheduleCategory = cat(id = "cat-schedule", order = 5, ruleIds = listOf("r-schedule"))
        val repeatCategory = cat(id = "cat-repeat", order = 0, ruleIds = listOf("r-repeat"))

        val winner = resolver.resolve(
            matched = listOf(repeatCategory, scheduleCategory),
            allCategories = listOf(repeatCategory, scheduleCategory),
            matchedRuleTypes = mapOf(
                "r-schedule" to RuleTypeUi.SCHEDULE,
                "r-repeat" to RuleTypeUi.REPEAT_BUNDLE,
            ),
        )

        assertEquals("cat-schedule", winner?.id)
    }

    @Test
    fun app_pin_bonus_beats_higher_ranked_rule_type_without_pin() {
        // The KEYWORD-rule Category wins the ladder if app-pin is ignored,
        // but the SCHEDULE-rule Category's `appPackageName` bonus pushes it
        // above the keyword-only rival. The plan says app-pin is a separate
        // bonus tier on top of the rule-type ladder.
        val pinnedSchedule = cat(
            id = "cat-pinned-schedule",
            order = 5,
            ruleIds = listOf("r-schedule"),
            appPackageName = "com.kakao.talk",
        )
        val keywordOnly = cat(id = "cat-keyword", order = 0, ruleIds = listOf("r-kw"))

        val winner = resolver.resolve(
            matched = listOf(keywordOnly, pinnedSchedule),
            allCategories = listOf(keywordOnly, pinnedSchedule),
            matchedRuleTypes = mapOf(
                "r-kw" to RuleTypeUi.KEYWORD,
                "r-schedule" to RuleTypeUi.SCHEDULE,
            ),
        )

        assertEquals("cat-pinned-schedule", winner?.id)
    }

    @Test
    fun drag_order_tie_break_applies_only_within_same_specificity_tier() {
        // Two app-pinned Categories tie on specificity because both have
        // app-pin bonus AND both matched via KEYWORD rules. Drag order
        // decides.
        val upper = cat(
            id = "cat-upper",
            order = 0,
            ruleIds = listOf("r-kw-1"),
            appPackageName = "com.kakao.talk",
        )
        val lower = cat(
            id = "cat-lower",
            order = 1,
            ruleIds = listOf("r-kw-2"),
            appPackageName = "com.kakao.talk",
        )

        val winner = resolver.resolve(
            matched = listOf(lower, upper),
            allCategories = listOf(upper, lower),
            matchedRuleTypes = mapOf(
                "r-kw-1" to RuleTypeUi.KEYWORD,
                "r-kw-2" to RuleTypeUi.KEYWORD,
            ),
        )

        assertEquals("cat-upper", winner?.id)
    }

    @Test
    fun category_specificity_uses_highest_ranked_matched_rule_among_its_members() {
        // A Category whose rule set includes both an APP rule (high rank) and
        // a REPEAT_BUNDLE rule (low rank) beats a Category whose matched rule
        // is KEYWORD-only — even if only the APP rule is among the matched
        // set, the Category's specificity is APP.
        val mixedCategory = cat(
            id = "cat-mixed",
            order = 5,
            ruleIds = listOf("r-app", "r-repeat"),
        )
        val keywordCategory = cat(id = "cat-keyword", order = 0, ruleIds = listOf("r-kw"))

        val winner = resolver.resolve(
            matched = listOf(keywordCategory, mixedCategory),
            allCategories = listOf(keywordCategory, mixedCategory),
            matchedRuleTypes = mapOf(
                "r-app" to RuleTypeUi.APP,
                "r-kw" to RuleTypeUi.KEYWORD,
                // r-repeat did not match, so the mixed Category's specificity
                // comes entirely from r-app (APP).
            ),
        )

        assertEquals("cat-mixed", winner?.id)
    }

    @Test
    fun ignore_vs_silent_still_defers_to_specificity_not_action() {
        // IGNORE has no hard-coded precedence over SILENT — specificity
        // decides. Here the SILENT Category matches via APP (higher rank)
        // while the IGNORE Category matches via KEYWORD.
        val silentApp = cat(id = "cat-silent-app", order = 5, ruleIds = listOf("r-app"))
            .copy(action = CategoryAction.SILENT)
        val ignoreKeyword = cat(id = "cat-ignore-kw", order = 0, ruleIds = listOf("r-kw"))
            .copy(action = CategoryAction.IGNORE)

        val winner = resolver.resolve(
            matched = listOf(ignoreKeyword, silentApp),
            allCategories = listOf(ignoreKeyword, silentApp),
            matchedRuleTypes = mapOf(
                "r-app" to RuleTypeUi.APP,
                "r-kw" to RuleTypeUi.KEYWORD,
            ),
        )

        assertEquals(CategoryAction.SILENT, winner?.action)
    }

    private fun cat(
        id: String,
        order: Int,
        ruleIds: List<String>,
        appPackageName: String? = null,
    ): Category = Category(
        id = id,
        name = id,
        appPackageName = appPackageName,
        ruleIds = ruleIds,
        action = CategoryAction.PRIORITY,
        order = order,
    )
}
