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
 * RED-phase tests for plan
 * `docs/plans/2026-04-22-categories-split-rules-actions.md` Phase P1 Task 3.
 *
 * Exercises the pure migration computation — the caller-glue that reads from
 * [com.smartnoti.app.data.rules.RulesRepository], writes to
 * [CategoriesRepository], and toggles the
 * `SettingsRepository.migrationV2CategoriesComplete` flag is covered by
 * [MigrateRulesToCategoriesRunnerTest] once Task 3 ships. This test only
 * pins the shape of the generated Category list.
 */
class RuleToCategoryMigrationTest {

    @Test
    fun migrates_each_rule_to_one_category_preserving_action_and_match_value() {
        val rules = listOf(
            RuleUiModel(
                id = "rule-person-mom",
                title = "엄마",
                subtitle = "항상 바로 보기",
                type = RuleTypeUi.PERSON,
                action = RuleActionUi.ALWAYS_PRIORITY,
                enabled = true,
                matchValue = "엄마",
            ),
            RuleUiModel(
                id = "rule-app-coupang",
                title = "쿠팡",
                subtitle = "Digest로 묶기",
                type = RuleTypeUi.APP,
                action = RuleActionUi.DIGEST,
                enabled = true,
                matchValue = "com.coupang.mobile",
            ),
            RuleUiModel(
                id = "rule-keyword-ad",
                title = "광고",
                subtitle = "무시",
                type = RuleTypeUi.KEYWORD,
                action = RuleActionUi.IGNORE,
                enabled = true,
                matchValue = "광고",
            ),
            RuleUiModel(
                id = "rule-keyword-otp",
                title = "인증번호",
                subtitle = "즉시 전달",
                type = RuleTypeUi.KEYWORD,
                action = RuleActionUi.ALWAYS_PRIORITY,
                enabled = true,
                matchValue = "인증번호",
            ),
        )

        val migrated = RuleToCategoryMigration.migrate(
            rules = rules,
            existingCategories = emptyList(),
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
        // Action is inherited from the rule.
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
            RuleUiModel(
                id = "rule-1",
                title = "A",
                subtitle = "",
                type = RuleTypeUi.KEYWORD,
                action = RuleActionUi.DIGEST,
                enabled = true,
                matchValue = "A",
            ),
            RuleUiModel(
                id = "rule-2",
                title = "B",
                subtitle = "",
                type = RuleTypeUi.APP,
                action = RuleActionUi.SILENT,
                enabled = true,
                matchValue = "com.example.b",
            ),
        )

        val first = RuleToCategoryMigration.migrate(
            rules = rules,
            existingCategories = emptyList(),
        )
        val second = RuleToCategoryMigration.migrate(
            rules = rules,
            existingCategories = first,
        )

        // Idempotent: no duplicates, same content.
        assertEquals(first, second)
        assertEquals(2, second.size)
        // Still stable ids.
        assertEquals("cat-from-rule-rule-1", second[0].id)
        assertEquals("cat-from-rule-rule-2", second[1].id)
    }

    @Test
    fun migration_preserves_existing_user_categories_and_appends_missing_ones() {
        // User has already manually created one Category ahead of a rule-based
        // migration — that Category must survive untouched while the rule
        // scan appends the not-yet-migrated ones.
        val preexistingUserCategory = Category(
            id = "user-made-cat",
            name = "내가만든분류",
            appPackageName = null,
            ruleIds = listOf("rule-shared"),
            action = CategoryAction.PRIORITY,
            order = 0,
        )
        val rules = listOf(
            RuleUiModel(
                id = "rule-shared",
                title = "공유",
                subtitle = "",
                type = RuleTypeUi.KEYWORD,
                action = RuleActionUi.DIGEST,
                enabled = true,
                matchValue = "공유",
            ),
            RuleUiModel(
                id = "rule-new",
                title = "신규",
                subtitle = "",
                type = RuleTypeUi.KEYWORD,
                action = RuleActionUi.SILENT,
                enabled = true,
                matchValue = "신규",
            ),
        )

        val migrated = RuleToCategoryMigration.migrate(
            rules = rules,
            existingCategories = listOf(preexistingUserCategory),
        )

        // Pre-existing user-made category still present, unmodified.
        val preserved = migrated.firstOrNull { it.id == "user-made-cat" }
        assertNotNull(preserved)
        assertEquals("내가만든분류", preserved!!.name)
        assertEquals(CategoryAction.PRIORITY, preserved.action)
        // Rule-derived categories for rule-shared + rule-new are appended.
        // rule-shared is a DIFFERENT category from user-made-cat because the
        // migration keys by rule id, not by membership — they coexist.
        assertTrue(migrated.any { it.id == "cat-from-rule-rule-shared" })
        assertTrue(migrated.any { it.id == "cat-from-rule-rule-new" })
        // Total size = 1 preexisting + 2 rule-derived.
        assertEquals(3, migrated.size)
    }

    @Test
    fun migration_skips_rules_without_action_gracefully() {
        // Defensive — if a future storage bug produces a rule row that
        // somehow carries no usable action (legacy data / partial decode),
        // the migration should NOT crash. We simulate this by emitting the
        // CONTEXTUAL action which maps to no Category action in the current
        // enum set — treated as "skip this rule" rather than shoehorning
        // into SILENT.
        val rules = listOf(
            RuleUiModel(
                id = "rule-ok",
                title = "ok",
                subtitle = "",
                type = RuleTypeUi.KEYWORD,
                action = RuleActionUi.DIGEST,
                enabled = true,
                matchValue = "ok",
            ),
            RuleUiModel(
                id = "rule-orphan",
                title = "orphan",
                subtitle = "",
                type = RuleTypeUi.KEYWORD,
                action = RuleActionUi.CONTEXTUAL,
                enabled = true,
                matchValue = "orphan",
            ),
        )

        val migrated = RuleToCategoryMigration.migrate(
            rules = rules,
            existingCategories = emptyList(),
        )

        // Orphan rule dropped, OK rule migrated.
        assertEquals(1, migrated.size)
        assertEquals("cat-from-rule-rule-ok", migrated.first().id)
        assertFalse(migrated.any { it.id.endsWith("rule-orphan") })
    }

    @Test
    fun re_running_migration_after_new_rule_added_only_creates_the_missing_category() {
        // Simulate a user upgrade path: after first migration, the user adds a
        // new rule. A second migration pass should be a no-op for the old
        // rules and create exactly one new Category for the new rule.
        val initialRules = listOf(
            RuleUiModel(
                id = "rule-a",
                title = "A",
                subtitle = "",
                type = RuleTypeUi.KEYWORD,
                action = RuleActionUi.ALWAYS_PRIORITY,
                enabled = true,
                matchValue = "A",
            ),
        )
        val firstPass = RuleToCategoryMigration.migrate(
            rules = initialRules,
            existingCategories = emptyList(),
        )

        val expandedRules = initialRules + RuleUiModel(
            id = "rule-b",
            title = "B",
            subtitle = "",
            type = RuleTypeUi.APP,
            action = RuleActionUi.SILENT,
            enabled = true,
            matchValue = "com.b",
        )

        val secondPass = RuleToCategoryMigration.migrate(
            rules = expandedRules,
            existingCategories = firstPass,
        )

        assertEquals(2, secondPass.size)
        // First pass category untouched.
        assertEquals(firstPass.first(), secondPass.first { it.id == "cat-from-rule-rule-a" })
        // Second pass category created with correct order.
        val added = secondPass.first { it.id == "cat-from-rule-rule-b" }
        assertEquals(1, added.order)
        assertEquals(CategoryAction.SILENT, added.action)
        assertEquals("com.b", added.appPackageName)
    }

}
