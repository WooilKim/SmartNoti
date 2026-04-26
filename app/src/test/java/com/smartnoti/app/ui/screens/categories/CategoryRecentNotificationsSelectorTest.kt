package com.smartnoti.app.ui.screens.categories

import com.smartnoti.app.domain.model.Category
import com.smartnoti.app.domain.model.CategoryAction
import com.smartnoti.app.domain.model.NotificationStatusUi
import com.smartnoti.app.domain.model.NotificationUiModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Plan `docs/plans/2026-04-26-category-detail-recent-notifications-preview.md` Task 1.
 *
 * Pure-function contract for the CategoryDetail "최근 분류된 알림" preview
 * selector. Verifies the (filter on `category.ruleIds ∩ matchedRuleIds`)
 * + sort by `postedAtMillis` desc + `take(limit)` shape that the Detail
 * LazyColumn relies on.
 */
class CategoryRecentNotificationsSelectorTest {

    @Test
    fun empty_notifications_yields_empty_list() {
        val category = category(ruleIds = listOf("r1", "r2"))

        val result = CategoryRecentNotificationsSelector.select(
            category = category,
            notifications = emptyList(),
        )

        assertTrue(result.isEmpty())
    }

    @Test
    fun notifications_without_any_matched_rule_ids_yield_empty_list() {
        val category = category(ruleIds = listOf("r1", "r2"))
        val notifications = listOf(
            notification(id = "n1", postedAtMillis = 100, matchedRuleIds = emptyList()),
            notification(id = "n2", postedAtMillis = 200, matchedRuleIds = emptyList()),
        )

        val result = CategoryRecentNotificationsSelector.select(
            category = category,
            notifications = notifications,
        )

        assertTrue(result.isEmpty())
    }

    @Test
    fun returns_only_notifications_intersecting_category_rule_ids_sorted_desc() {
        val category = category(ruleIds = listOf("r1", "r2"))
        val notifications = listOf(
            notification(id = "n1", postedAtMillis = 100, matchedRuleIds = listOf("r1")),
            notification(id = "n2", postedAtMillis = 500, matchedRuleIds = listOf("r3")), // not matched
            notification(id = "n3", postedAtMillis = 300, matchedRuleIds = listOf("r2")),
            notification(id = "n4", postedAtMillis = 200, matchedRuleIds = listOf("r4")), // not matched
            notification(id = "n5", postedAtMillis = 400, matchedRuleIds = listOf("r1", "r3")),
            notification(id = "n6", postedAtMillis = 50, matchedRuleIds = emptyList()),    // empty
        )

        val result = CategoryRecentNotificationsSelector.select(
            category = category,
            notifications = notifications,
        )

        assertEquals(listOf("n5", "n3", "n1"), result.map { it.id })
    }

    @Test
    fun notification_matched_in_multiple_rule_ids_appears_only_once() {
        val category = category(ruleIds = listOf("r1"))
        val notifications = listOf(
            notification(id = "n1", postedAtMillis = 100, matchedRuleIds = listOf("r1", "r3")),
        )

        val result = CategoryRecentNotificationsSelector.select(
            category = category,
            notifications = notifications,
        )

        assertEquals(1, result.size)
        assertEquals("n1", result.first().id)
    }

    @Test
    fun default_limit_is_five_and_caps_to_most_recent() {
        val category = category(ruleIds = listOf("r1"))
        val notifications = (1..7).map { idx ->
            notification(id = "n$idx", postedAtMillis = idx * 100L, matchedRuleIds = listOf("r1"))
        }

        val result = CategoryRecentNotificationsSelector.select(
            category = category,
            notifications = notifications,
        )

        assertEquals(5, result.size)
        assertEquals(listOf("n7", "n6", "n5", "n4", "n3"), result.map { it.id })
    }

    @Test
    fun limit_five_with_exactly_five_matches_returns_all() {
        val category = category(ruleIds = listOf("r1"))
        val notifications = (1..5).map { idx ->
            notification(id = "n$idx", postedAtMillis = idx * 100L, matchedRuleIds = listOf("r1"))
        }

        val result = CategoryRecentNotificationsSelector.select(
            category = category,
            notifications = notifications,
            limit = 5,
        )

        assertEquals(5, result.size)
        assertEquals(listOf("n5", "n4", "n3", "n2", "n1"), result.map { it.id })
    }

    @Test
    fun limit_zero_returns_empty_list() {
        val category = category(ruleIds = listOf("r1"))
        val notifications = listOf(
            notification(id = "n1", postedAtMillis = 100, matchedRuleIds = listOf("r1")),
        )

        val result = CategoryRecentNotificationsSelector.select(
            category = category,
            notifications = notifications,
            limit = 0,
        )

        assertTrue(result.isEmpty())
    }

    @Test
    fun rule_less_category_returns_empty_list() {
        val category = category(ruleIds = emptyList())
        val notifications = listOf(
            notification(id = "n1", postedAtMillis = 100, matchedRuleIds = listOf("r1")),
        )

        val result = CategoryRecentNotificationsSelector.select(
            category = category,
            notifications = notifications,
        )

        assertTrue(result.isEmpty())
    }

    private fun category(
        ruleIds: List<String>,
        action: CategoryAction = CategoryAction.PRIORITY,
    ): Category = Category(
        id = "cat-1",
        name = "분류",
        appPackageName = null,
        ruleIds = ruleIds,
        action = action,
        order = 0,
    )

    private fun notification(
        id: String,
        postedAtMillis: Long,
        matchedRuleIds: List<String>,
    ): NotificationUiModel = NotificationUiModel(
        id = id,
        appName = "앱",
        packageName = "com.example",
        sender = null,
        title = "제목 $id",
        body = "본문 $id",
        receivedAtLabel = "방금",
        status = NotificationStatusUi.PRIORITY,
        reasonTags = emptyList(),
        postedAtMillis = postedAtMillis,
        matchedRuleIds = matchedRuleIds,
    )
}
