package com.smartnoti.app.domain.usecase

import com.smartnoti.app.domain.model.DigestGroupUiModel
import com.smartnoti.app.domain.model.InboxSortMode
import com.smartnoti.app.domain.model.NotificationStatusUi
import com.smartnoti.app.domain.model.NotificationUiModel
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests for [InboxSortPlanner] — plan
 * `docs/plans/2026-04-27-inbox-sort-by-priority-or-app.md` Task 1.
 *
 * The planner is a pure helper that re-orders flat row lists and digest
 * group lists by user-selected mode. The DB query layer is untouched.
 */
class InboxSortPlannerTest {

    private val planner = InboxSortPlanner()

    // --- sortFlatRows ---

    @Test
    fun sortFlatRows_recent_orders_by_postedAt_descending() {
        val rows = listOf(
            row(id = "a", postedAtMillis = 1L),
            row(id = "b", postedAtMillis = 3L),
            row(id = "c", postedAtMillis = 2L),
        )

        val sorted = planner.sortFlatRows(rows, InboxSortMode.RECENT)

        assertEquals(listOf("b", "c", "a"), sorted.map { it.id })
    }

    @Test
    fun sortFlatRows_importance_orders_priority_then_digest_then_silent_then_recent_within_status() {
        val rows = listOf(
            row(id = "p1", postedAtMillis = 1L, status = NotificationStatusUi.PRIORITY),
            row(id = "d1", postedAtMillis = 3L, status = NotificationStatusUi.DIGEST),
            row(id = "s1", postedAtMillis = 2L, status = NotificationStatusUi.SILENT),
            row(id = "d2", postedAtMillis = 4L, status = NotificationStatusUi.DIGEST),
        )

        val sorted = planner.sortFlatRows(rows, InboxSortMode.IMPORTANCE)

        // PRIORITY first, then DIGEST descending by time (d2=4, d1=3), then SILENT.
        assertEquals(listOf("p1", "d2", "d1", "s1"), sorted.map { it.id })
    }

    @Test
    fun sortFlatRows_byApp_orders_by_appName_lowercase_then_recent() {
        val rows = listOf(
            row(id = "slack-old", appName = "Slack", postedAtMillis = 1L),
            row(id = "gmail", appName = "Gmail", postedAtMillis = 5L),
            row(id = "coupang", appName = "Coupang", postedAtMillis = 7L),
            row(id = "slack-new", appName = "Slack", postedAtMillis = 8L),
        )

        val sorted = planner.sortFlatRows(rows, InboxSortMode.BY_APP)

        // Coupang -> Gmail -> Slack(new=8) -> Slack(old=1).
        assertEquals(
            listOf("coupang", "gmail", "slack-new", "slack-old"),
            sorted.map { it.id },
        )
    }

    @Test
    fun sortFlatRows_recent_empty_returns_empty() {
        assertEquals(emptyList<NotificationUiModel>(), planner.sortFlatRows(emptyList(), InboxSortMode.RECENT))
    }

    // --- sortGroups ---

    @Test
    fun sortGroups_recent_orders_by_max_postedAt_descending() {
        val a = group(id = "A", appName = "ZAlpha", items = listOf(row(postedAtMillis = 10L), row(postedAtMillis = 4L)))
        val b = group(id = "B", appName = "Bravo", items = listOf(row(postedAtMillis = 5L)))
        val c = group(id = "C", appName = "Charlie", items = listOf(row(postedAtMillis = 8L)))

        val sorted = planner.sortGroups(listOf(a, b, c), InboxSortMode.RECENT)

        // A=10, C=8, B=5
        assertEquals(listOf("A", "C", "B"), sorted.map { it.id })
    }

    @Test
    fun sortGroups_byApp_orders_by_appName_lowercase_ascending() {
        val a = group(id = "A", appName = "Slack", items = listOf(row(postedAtMillis = 10L)))
        val b = group(id = "B", appName = "Coupang", items = listOf(row(postedAtMillis = 5L)))
        val c = group(id = "C", appName = "Gmail", items = listOf(row(postedAtMillis = 8L)))

        val sorted = planner.sortGroups(listOf(a, b, c), InboxSortMode.BY_APP)

        // coupang -> gmail -> slack
        assertEquals(listOf("B", "C", "A"), sorted.map { it.id })
    }

    @Test
    fun sortGroups_importance_with_uniform_status_matches_recent() {
        val a = group(id = "A", appName = "ZAlpha", items = listOf(row(postedAtMillis = 10L, status = NotificationStatusUi.DIGEST)))
        val b = group(id = "B", appName = "Bravo", items = listOf(row(postedAtMillis = 5L, status = NotificationStatusUi.DIGEST)))
        val c = group(id = "C", appName = "Charlie", items = listOf(row(postedAtMillis = 8L, status = NotificationStatusUi.DIGEST)))

        val sortedImportance = planner.sortGroups(listOf(a, b, c), InboxSortMode.IMPORTANCE).map { it.id }
        val sortedRecent = planner.sortGroups(listOf(a, b, c), InboxSortMode.RECENT).map { it.id }

        assertEquals(sortedRecent, sortedImportance)
    }

    @Test
    fun sortGroups_empty_returns_empty() {
        assertEquals(emptyList<DigestGroupUiModel>(), planner.sortGroups(emptyList(), InboxSortMode.BY_APP))
    }

    // --- helpers ---

    private fun row(
        id: String = "n-${java.util.UUID.randomUUID()}",
        appName: String = "TestApp",
        postedAtMillis: Long = 0L,
        status: NotificationStatusUi = NotificationStatusUi.DIGEST,
    ): NotificationUiModel = NotificationUiModel(
        id = id,
        appName = appName,
        packageName = "com.example.${appName.lowercase()}",
        sender = null,
        title = "title",
        body = "body",
        receivedAtLabel = "now",
        status = status,
        reasonTags = emptyList(),
        postedAtMillis = postedAtMillis,
    )

    private fun group(
        id: String,
        appName: String,
        items: List<NotificationUiModel>,
    ): DigestGroupUiModel = DigestGroupUiModel(
        id = id,
        appName = appName,
        count = items.size,
        summary = "summary",
        items = items,
    )
}
