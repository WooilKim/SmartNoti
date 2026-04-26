package com.smartnoti.app.ui.screens.categories

import com.smartnoti.app.domain.model.Category
import com.smartnoti.app.domain.model.NotificationUiModel

/**
 * Pure-function selector for the CategoryDetail "최근 분류된 알림" preview.
 * Plan `docs/plans/2026-04-26-category-detail-recent-notifications-preview.md`
 * Task 2.
 *
 * Filters [notifications] down to those whose [NotificationUiModel.matchedRuleIds]
 * intersects [Category.ruleIds], then sorts by [NotificationUiModel.postedAtMillis]
 * descending and caps at [limit]. Source data already lives in the notification
 * stream — no storage migration required (`matchedRuleIds` is persisted via
 * `NotificationEntityMapper.ruleHitIds`).
 *
 * Defensive on edge inputs:
 *   - `limit <= 0` → empty list
 *   - empty `category.ruleIds` → empty list
 *   - notifications without any `matchedRuleIds` → filtered out
 */
object CategoryRecentNotificationsSelector {

    /** Default cap matching `HomeRecentNotificationsTruncation.DEFAULT_CAPACITY`. */
    const val DEFAULT_LIMIT: Int = 5

    fun select(
        category: Category,
        notifications: List<NotificationUiModel>,
        limit: Int = DEFAULT_LIMIT,
    ): List<NotificationUiModel> {
        if (limit <= 0) return emptyList()
        if (category.ruleIds.isEmpty()) return emptyList()
        val ruleIdSet = category.ruleIds.toSet()
        return notifications
            .asSequence()
            .filter { notification -> notification.matchedRuleIds.any(ruleIdSet::contains) }
            .sortedByDescending { it.postedAtMillis }
            .take(limit)
            .toList()
    }
}
