package com.smartnoti.app.ui.screens.categories

/**
 * Pure helper for the CategoryDetail "최근 분류된 알림" row's relative-time
 * copy. Plan `docs/plans/2026-04-26-category-detail-recent-notifications-preview.md`
 * Task 3.
 *
 * Buckets:
 *   - delta < 60s          → "방금"
 *   - delta < 60m          → "${minutes}분 전"
 *   - delta < 24h          → "${hours}시간 전"
 *   - delta otherwise      → "${days}일 전" (no absolute-date fallback;
 *                            preview only shows ~5 most recent rows so very
 *                            old entries are inherently rare here)
 *
 * Negative deltas (clock skew, "future" notifications) are treated as
 * "방금" so the UI never renders a "-3분 전" string.
 */
fun formatRelative(nowMillis: Long, eventMillis: Long): String {
    val delta = nowMillis - eventMillis
    if (delta < 60_000L) return "방금"
    val minutes = delta / 60_000L
    if (minutes < 60L) return "${minutes}분 전"
    val hours = minutes / 60L
    if (hours < 24L) return "${hours}시간 전"
    val days = hours / 24L
    return "${days}일 전"
}
