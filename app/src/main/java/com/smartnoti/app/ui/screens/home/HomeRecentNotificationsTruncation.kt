package com.smartnoti.app.ui.screens.home

import com.smartnoti.app.domain.model.NotificationUiModel

/**
 * Pure-function truncator for the Home "방금 정리된 알림" section. Plan
 * `docs/plans/2026-04-22-inbox-denest-and-home-recent-truncate.md` Task 1.
 *
 * Home is meant to be a one-screen summary; rendering the entire `observeAllFiltered`
 * stream would turn it into a copy of 정리함. We keep the most recent
 * [DEFAULT_CAPACITY] entries and surface the remainder as a single
 * "전체 N건 보기 →" footer row that deep-links into 정리함.
 */
internal object HomeRecentNotificationsTruncation {
    /**
     * Default cap for Home's recent list. Matches the rough viewport budget of
     * StatPill + 4–5 cards on a typical phone. Internal const so we can A/B
     * tune without touching call sites.
     */
    const val DEFAULT_CAPACITY: Int = 5

    /**
     * @property visible The notifications that should be rendered as cards.
     * @property hiddenCount How many additional notifications were omitted —
     *   used to drive the "전체 N건 보기 →" footer row's count.
     */
    data class View(
        val visible: List<NotificationUiModel>,
        val hiddenCount: Int,
    )

    fun truncate(
        recent: List<NotificationUiModel>,
        capacity: Int = DEFAULT_CAPACITY,
    ): View {
        val safeCapacity = capacity.coerceAtLeast(0)
        val visible = recent.take(safeCapacity)
        val hiddenCount = (recent.size - visible.size).coerceAtLeast(0)
        return View(visible = visible, hiddenCount = hiddenCount)
    }
}
