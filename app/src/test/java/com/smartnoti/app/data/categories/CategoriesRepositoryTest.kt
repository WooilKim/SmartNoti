package com.smartnoti.app.data.categories

import com.smartnoti.app.domain.model.Category
import com.smartnoti.app.domain.model.CategoryAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * RED-phase tests for plan
 * `docs/plans/2026-04-22-categories-split-rules-actions.md` Phase P1 Task 1.
 *
 * Mirrors how [com.smartnoti.app.data.rules.RulesRepository] is tested today
 * — via its pure codec/helper functions rather than instantiating the
 * DataStore-backed class. Task 2 is expected to land:
 *
 *  - `CategoryStorageCodec` with `encode` / `decode` that round-trips the
 *    full Category list (including an empty `appPackageName`, multiple
 *    `ruleIds`, all four `CategoryAction` values, and `order`).
 *  - `resolveStoredCategories(payload: String?)` helper that returns an
 *    empty list for `null` or blank payloads (no default seed — unlike
 *    Rules, Categories are only created via migration or user action).
 *  - `moveCategory(categories, categoryId, direction)` pure function with
 *    a direct-neighbor swap (simpler than `moveRule` because Categories
 *    have no override tiering).
 *
 * All assertions below fail to compile today because none of the symbols
 * exist yet. Task 2 makes them green.
 */
class CategoriesRepositoryTest {

    @Test
    fun encode_then_decode_round_trips_full_category_list() {
        val original = listOf(
            Category(
                id = "cat-1",
                name = "엄마",
                appPackageName = null,
                ruleIds = listOf("rule-person-mom"),
                action = CategoryAction.PRIORITY,
                order = 0,
            ),
            Category(
                id = "cat-2",
                name = "쿠팡",
                appPackageName = "com.coupang.mobile",
                ruleIds = listOf("rule-app-coupang", "rule-keyword-delivery"),
                action = CategoryAction.DIGEST,
                order = 1,
            ),
            Category(
                id = "cat-3",
                name = "광고",
                appPackageName = null,
                ruleIds = listOf("rule-keyword-ad"),
                action = CategoryAction.IGNORE,
                order = 2,
            ),
            Category(
                id = "cat-4",
                name = "조용",
                appPackageName = null,
                ruleIds = emptyList(),
                action = CategoryAction.SILENT,
                order = 3,
            ),
        )

        val encoded = CategoryStorageCodec.encode(original)
        val decoded = CategoryStorageCodec.decode(encoded)

        assertEquals(original, decoded)
    }

    @Test
    fun decode_returns_empty_list_for_blank_payload() {
        assertTrue(CategoryStorageCodec.decode("").isEmpty())
    }

    @Test
    fun decode_returns_empty_list_for_broken_payload() {
        assertTrue(CategoryStorageCodec.decode("this-is-not-a-category-line").isEmpty())
    }

    @Test
    fun resolve_stored_categories_returns_empty_for_null_payload() {
        // Unlike RulesRepository which seeds default rules on first run,
        // Categories are only created via the migration pass or user action,
        // so a null (never-written) DataStore should yield zero categories.
        assertTrue(resolveStoredCategories(null).isEmpty())
    }

    @Test
    fun resolve_stored_categories_returns_empty_for_blank_payload() {
        assertTrue(resolveStoredCategories("").isEmpty())
    }

    @Test
    fun resolve_stored_categories_decodes_persisted_payload() {
        val payload = CategoryStorageCodec.encode(
            listOf(
                Category(
                    id = "cat-keep",
                    name = "유지",
                    appPackageName = null,
                    ruleIds = listOf("rule-a"),
                    action = CategoryAction.PRIORITY,
                    order = 0,
                ),
            ),
        )

        val resolved = resolveStoredCategories(payload)

        assertEquals(1, resolved.size)
        assertEquals("cat-keep", resolved.first().id)
    }

    @Test
    fun move_up_swaps_category_with_previous_neighbor() {
        val result = moveCategory(
            categories = sampleCategories(),
            categoryId = "cat-2",
            direction = CategoryMoveDirection.UP,
        )

        assertEquals(listOf("cat-2", "cat-1", "cat-3"), result.map { it.id })
    }

    @Test
    fun move_down_swaps_category_with_next_neighbor() {
        val result = moveCategory(
            categories = sampleCategories(),
            categoryId = "cat-1",
            direction = CategoryMoveDirection.DOWN,
        )

        assertEquals(listOf("cat-2", "cat-1", "cat-3"), result.map { it.id })
    }

    @Test
    fun move_up_at_top_is_noop() {
        val original = sampleCategories()

        val result = moveCategory(
            categories = original,
            categoryId = "cat-1",
            direction = CategoryMoveDirection.UP,
        )

        assertEquals(original.map { it.id }, result.map { it.id })
    }

    @Test
    fun move_down_at_bottom_is_noop() {
        val original = sampleCategories()

        val result = moveCategory(
            categories = original,
            categoryId = "cat-3",
            direction = CategoryMoveDirection.DOWN,
        )

        assertEquals(original.map { it.id }, result.map { it.id })
    }

    @Test
    fun move_unknown_id_is_noop() {
        val original = sampleCategories()

        val result = moveCategory(
            categories = original,
            categoryId = "cat-missing",
            direction = CategoryMoveDirection.UP,
        )

        assertEquals(original.map { it.id }, result.map { it.id })
    }

    @Test
    fun same_rule_id_may_appear_in_multiple_categories() {
        // Plan Phase P1 Task 1 step 2: rule id may belong to multiple
        // categories. The storage layer must round-trip this without
        // deduplicating or rejecting the duplication.
        val shared = listOf(
            Category(
                id = "cat-a",
                name = "Cat A",
                appPackageName = null,
                ruleIds = listOf("rule-shared"),
                action = CategoryAction.PRIORITY,
                order = 0,
            ),
            Category(
                id = "cat-b",
                name = "Cat B",
                appPackageName = null,
                ruleIds = listOf("rule-shared"),
                action = CategoryAction.DIGEST,
                order = 1,
            ),
        )

        val decoded = CategoryStorageCodec.decode(CategoryStorageCodec.encode(shared))

        assertEquals(2, decoded.size)
        assertEquals(listOf("rule-shared"), decoded[0].ruleIds)
        assertEquals(listOf("rule-shared"), decoded[1].ruleIds)
    }

    @Test
    fun empty_app_package_name_survives_round_trip_as_null() {
        // Categories with `appPackageName == null` are common (keyword-only
        // categories). The codec must not coerce null → empty string on the
        // round trip.
        val original = listOf(
            Category(
                id = "cat-null-app",
                name = "키워드만",
                appPackageName = null,
                ruleIds = listOf("rule-keyword-only"),
                action = CategoryAction.SILENT,
                order = 0,
            ),
        )

        val decoded = CategoryStorageCodec.decode(CategoryStorageCodec.encode(original))

        assertEquals(1, decoded.size)
        assertNull(decoded.first().appPackageName)
    }

    @Test
    fun all_category_action_values_round_trip_through_codec() {
        val categories = CategoryAction.values().mapIndexed { index, action ->
            Category(
                id = "cat-$action",
                name = action.name,
                appPackageName = null,
                ruleIds = listOf("rule-$action"),
                action = action,
                order = index,
            )
        }

        val decoded = CategoryStorageCodec.decode(CategoryStorageCodec.encode(categories))

        assertEquals(categories, decoded)
        // Spot-check that IGNORE survives — historically the riskiest enum
        // value because it was added after the initial Rule action set.
        assertNotNull(decoded.firstOrNull { it.action == CategoryAction.IGNORE })
    }

    private fun sampleCategories(): List<Category> = listOf(
        Category(
            id = "cat-1",
            name = "첫번째",
            appPackageName = null,
            ruleIds = listOf("rule-1"),
            action = CategoryAction.PRIORITY,
            order = 0,
        ),
        Category(
            id = "cat-2",
            name = "두번째",
            appPackageName = null,
            ruleIds = listOf("rule-2"),
            action = CategoryAction.DIGEST,
            order = 1,
        ),
        Category(
            id = "cat-3",
            name = "세번째",
            appPackageName = null,
            ruleIds = listOf("rule-3"),
            action = CategoryAction.SILENT,
            order = 2,
        ),
    )
}
