package com.smartnoti.app.ui.screens.home

import com.smartnoti.app.domain.model.NotificationStatusUi
import com.smartnoti.app.domain.model.NotificationUiModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Plan `docs/plans/2026-04-22-inbox-denest-and-home-recent-truncate.md` Task 0.
 *
 * Pure-function contract for the Home "방금 정리된 알림" truncation. Verifies
 * the (visible, hiddenCount) split that the Home LazyColumn relies on to render
 * the cap + "전체 N건 보기 →" footer row.
 */
class HomeRecentNotificationsTruncationTest {

    @Test
    fun empty_input_yields_empty_visible_and_zero_hidden() {
        val view = HomeRecentNotificationsTruncation.truncate(
            recent = emptyList(),
            capacity = 5,
        )

        assertTrue(view.visible.isEmpty())
        assertEquals(0, view.hiddenCount)
    }

    @Test
    fun input_smaller_than_capacity_keeps_all_visible() {
        val recent = notifications(3)

        val view = HomeRecentNotificationsTruncation.truncate(
            recent = recent,
            capacity = 5,
        )

        assertEquals(3, view.visible.size)
        assertEquals(0, view.hiddenCount)
        assertEquals(recent, view.visible)
    }

    @Test
    fun input_equal_to_capacity_keeps_all_visible() {
        val recent = notifications(5)

        val view = HomeRecentNotificationsTruncation.truncate(
            recent = recent,
            capacity = 5,
        )

        assertEquals(5, view.visible.size)
        assertEquals(0, view.hiddenCount)
        assertEquals(recent, view.visible)
    }

    @Test
    fun input_larger_than_capacity_truncates_to_head_and_records_remainder() {
        val recent = notifications(12)

        val view = HomeRecentNotificationsTruncation.truncate(
            recent = recent,
            capacity = 5,
        )

        assertEquals(5, view.visible.size)
        assertEquals(7, view.hiddenCount)
        assertEquals(recent.take(5), view.visible)
    }

    @Test
    fun zero_capacity_hides_everything() {
        val recent = notifications(6)

        val view = HomeRecentNotificationsTruncation.truncate(
            recent = recent,
            capacity = 0,
        )

        assertTrue(view.visible.isEmpty())
        assertEquals(6, view.hiddenCount)
    }

    @Test
    fun negative_capacity_is_clamped_to_zero() {
        val recent = notifications(4)

        val view = HomeRecentNotificationsTruncation.truncate(
            recent = recent,
            capacity = -3,
        )

        assertTrue(view.visible.isEmpty())
        assertEquals(4, view.hiddenCount)
    }

    @Test
    fun default_capacity_is_five() {
        assertEquals(5, HomeRecentNotificationsTruncation.DEFAULT_CAPACITY)
    }

    private fun notifications(count: Int): List<NotificationUiModel> {
        return (1..count).map { idx ->
            NotificationUiModel(
                id = "n-$idx",
                appName = "앱$idx",
                packageName = "pkg.n$idx",
                sender = null,
                title = "제목$idx",
                body = "본문$idx",
                receivedAtLabel = "방금",
                status = NotificationStatusUi.PRIORITY,
                reasonTags = emptyList(),
                score = null,
                isBundled = false,
            )
        }
    }
}
