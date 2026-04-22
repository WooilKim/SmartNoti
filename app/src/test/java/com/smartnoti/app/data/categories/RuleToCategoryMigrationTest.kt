package com.smartnoti.app.data.categories

import com.smartnoti.app.domain.model.Category
import com.smartnoti.app.domain.model.CategoryAction
import com.smartnoti.app.domain.model.RuleActionUi
import com.smartnoti.app.domain.model.RuleTypeUi
import com.smartnoti.app.domain.model.RuleUiModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for plan `docs/plans/2026-04-22-categories-split-rules-actions.md`
 * Phase P1 Task 3 + Task 4. The migration is purely a function of
 * `(rules, existingCategories, legacyActions)` — I/O glue lives in
 * [MigrateRulesToCategoriesRunner].
 */
class RuleToCategoryMigrationTest {

    @Test
    fun migrates_each_rule_to_one_category_preserving_action_and_match_value() {
        val rules = listOf(
            rule(id = "rule-person-mom", type = RuleTypeUi.PERSON, matchValue = "엄마"),
            rule(id = "rule-app-coupang", type = RuleTypeUi.APP, matchValue = "com.coupang.mobile"),
            rule(id = "rule-keyword-ad", type = RuleTypeUi.KEYWORD, matchValue = "광고"),
            rule(id = "rule-keyword-otp", type = RuleTypeUi.KEYWORD, matchValue = "인증번호"),
        )
        val legacyActions = mapOf(
            "rule-person-mom" to RuleActionUi.ALWAYS_PRIORITY,
            "rule-app-coupang" to RuleActionUi.DIGEST,
            "rule-keyword-ad" to RuleActionUi.IGNORE,
            "rule-keyword-otp" to RuleActionUi.ALWAYS_PRIORITY,
        )

        val migrated = RuleToCategoryMigration.migrate(
            rules = rules,
            existingCategories = emptyList(),
            legacyActions = legacyActions,
        )

        assertEquals(4, migrated.size)
        // 1:1 mapping with stable id prefix.
        assertEquals("cat-from-rule-rule-person-mom", migrated[0].id)
        assertEquals("cat-from-rule-rule-app-coupang", migrated[1].id)
        assertEquals("cat-from-rule-rule-keyword-ad", migrated[2].id)
        assertEquals("cat-from-rule-rule-keyword-otp", migrated[3].id)
        // Name = matchValue
        assertEquals("엄마", migrated[0].name)
        assertEquals("com.coupang.mobile", migrated[1].name)
        // ruleIds = [rule.id]
        assertEquals(listOf("rule-person-mom"), migrated[0].ruleIds)
        // Action is inherited from the legacy map.
        assertEquals(CategoryAction.PRIORITY, migrated[0].action)
        assertEquals(CategoryAction.DIGEST, migrated[1].action)
        assertEquals(CategoryAction.IGNORE, migrated[2].action)
        assertEquals(CategoryAction.PRIORITY, migrated[3].action)
        // APP-type rules get appPackageName set to matchValue; other types leave it null.
        assertEquals("com.coupang.mobile", migrated[1].appPackageName)
        assertEquals(null, migrated[0].appPackageName)
        assertEquals(null, migrated[2].appPackageName)
        // Sequential order starting at 0.
        assertEquals(0, migrated[0].order)
        assertEquals(1, migrated[1].order)
        assertEquals(2, migrated[2].order)
        assertEquals(3, migrated[3].order)
    }

    @Test
    fun running_migration_twice_produces_the_same_state() {
        val rules = listOf(
            rule(id = "rule-1", type = RuleTypeUi.KEYWORD, matchValue = "A"),
            rule(id = "rule-2", type = RuleTypeUi.APP, matchValue = "com.example.b"),
        )
        val legacyActions = mapOf(
            "rule-1" to RuleActionUi.DIGEST,
            "rule-2" to RuleActionUi.SILENT,
        )

        val first = RuleToCategoryMigration.migrate(
            rules = rules,
            existingCategories = emptyList(),
            legacyActions = legacyActions,
        )
        val second = RuleToCategoryMigration.migrate(
            rules = rules,
            existingCategories = first,
            legacyActions = legacyActions,
        )

        // Idempotent: no duplicates, same content.
        assertEquals(first, second)
        assertEquals(2, second.size)
        assertEquals("cat-from-rule-rule-1", second[0].id)
        assertEquals("cat-from-rule-rule-2", second[1].id)
    }

    @Test
    fun migration_preserves_existing_user_categories_and_appends_missing_ones() {
        val preexistingUserCategory = Category(
            id = "user-made-cat",
            name = "내가만든분류",
            appPackageName = null,
            ruleIds = listOf("rule-shared"),
            action = CategoryAction.PRIORITY,
            order = 0,
        )
        val rules = listOf(
            rule(id = "rule-shared", type = RuleTypeUi.KEYWORD, matchValue = "공유"),
            rule(id = "rule-new", type = RuleTypeUi.KEYWORD, matchValue = "신규"),
        )
        val legacyActions = mapOf(
            "rule-shared" to RuleActionUi.DIGEST,
            "rule-new" to RuleActionUi.SILENT,
        )

        val migrated = RuleToCategoryMigration.migrate(
            rules = rules,
            existingCategories = listOf(preexistingUserCategory),
            legacyActions = legacyActions,
        )

        val preserved = migrated.firstOrNull { it.id == "user-made-cat" }
        assertNotNull(preserved)
        assertEquals("내가만든분류", preserved!!.name)
        assertEquals(CategoryAction.PRIORITY, preserved.action)
        assertTrue(migrated.any { it.id == "cat-from-rule-rule-shared" })
        assertTrue(migrated.any { it.id == "cat-from-rule-rule-new" })
        assertEquals(3, migrated.size)
    }

    @Test
    fun migration_skips_rules_without_legacy_action_gracefully() {
        val rules = listOf(
            rule(id = "rule-ok", type = RuleTypeUi.KEYWORD, matchValue = "ok"),
            rule(id = "rule-orphan", type = RuleTypeUi.KEYWORD, matchValue = "orphan"),
        )
        val legacyActions = mapOf(
            "rule-ok" to RuleActionUi.DIGEST,
            // rule-orphan intentionally absent — simulates a rule whose legacy
            // action column was unreadable / missing.
        )

        val migrated = RuleToCategoryMigration.migrate(
            rules = rules,
            existingCategories = emptyList(),
            legacyActions = legacyActions,
        )

        assertEquals(1, migrated.size)
        assertEquals("cat-from-rule-rule-ok", migrated.first().id)
        assertFalse(migrated.any { it.id.endsWith("rule-orphan") })
    }

    @Test
    fun migration_skips_contextual_rules() {
        // CONTEXTUAL has no Category.action analogue (plan Phase P1 Task 3
        // Risk note: IGNORE 는 전이, CONTEXTUAL 은 skip). Verify explicitly.
        val rules = listOf(
            rule(id = "rule-ctx", type = RuleTypeUi.KEYWORD, matchValue = "ctx"),
        )
        val legacyActions = mapOf("rule-ctx" to RuleActionUi.CONTEXTUAL)

        val migrated = RuleToCategoryMigration.migrate(
            rules = rules,
            existingCategories = emptyList(),
            legacyActions = legacyActions,
        )

        assertTrue(migrated.isEmpty())
    }

    @Test
    fun re_running_migration_after_new_rule_added_only_creates_the_missing_category() {
        val initialRules = listOf(
            rule(id = "rule-a", type = RuleTypeUi.KEYWORD, matchValue = "A"),
        )
        val initialActions = mapOf("rule-a" to RuleActionUi.ALWAYS_PRIORITY)
        val firstPass = RuleToCategoryMigration.migrate(
            rules = initialRules,
            existingCategories = emptyList(),
            legacyActions = initialActions,
        )

        val expandedRules = initialRules + rule(id = "rule-b", type = RuleTypeUi.APP, matchValue = "com.b")
        val expandedActions = initialActions + ("rule-b" to RuleActionUi.SILENT)

        val secondPass = RuleToCategoryMigration.migrate(
            rules = expandedRules,
            existingCategories = firstPass,
            legacyActions = expandedActions,
        )

        assertEquals(2, secondPass.size)
        assertEquals(firstPass.first(), secondPass.first { it.id == "cat-from-rule-rule-a" })
        val added = secondPass.first { it.id == "cat-from-rule-rule-b" }
        assertEquals(1, added.order)
        assertEquals(CategoryAction.SILENT, added.action)
        assertEquals("com.b", added.appPackageName)
    }

    private fun rule(
        id: String,
        type: RuleTypeUi,
        matchValue: String,
    ) = RuleUiModel(
        id = id,
        title = matchValue,
        subtitle = "",
        type = type,
        enabled = true,
        matchValue = matchValue,
    )
}
