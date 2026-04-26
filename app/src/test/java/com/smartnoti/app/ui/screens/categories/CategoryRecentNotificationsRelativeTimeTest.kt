package com.smartnoti.app.ui.screens.categories

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Plan `docs/plans/2026-04-26-category-detail-recent-notifications-preview.md`
 * Task 3. Pure-helper unit test for the CategoryDetail recent-notifications
 * row's relative-time copy.
 */
class CategoryRecentNotificationsRelativeTimeTest {

    private val now = 1_000_000_000_000L

    @Test
    fun zero_delta_is_just_now() {
        assertEquals("방금", formatRelative(now, now))
    }

    @Test
    fun delta_under_one_minute_is_just_now() {
        assertEquals("방금", formatRelative(now, now - 30_000L))
        assertEquals("방금", formatRelative(now, now - 59_000L))
    }

    @Test
    fun future_delta_is_treated_as_just_now() {
        // Defensive — clock skew should not produce "-3분 전".
        assertEquals("방금", formatRelative(now, now + 60_000L))
    }

    @Test
    fun delta_under_one_hour_is_minutes() {
        assertEquals("1분 전", formatRelative(now, now - 60_000L))
        assertEquals("3분 전", formatRelative(now, now - 3 * 60_000L))
        assertEquals("59분 전", formatRelative(now, now - 59 * 60_000L))
    }

    @Test
    fun delta_under_one_day_is_hours() {
        assertEquals("1시간 전", formatRelative(now, now - 60 * 60_000L))
        assertEquals("5시간 전", formatRelative(now, now - 5 * 60 * 60_000L))
        assertEquals("23시간 전", formatRelative(now, now - 23 * 60 * 60_000L))
    }

    @Test
    fun delta_under_one_week_is_days() {
        assertEquals("1일 전", formatRelative(now, now - 24 * 60 * 60_000L))
        assertEquals("3일 전", formatRelative(now, now - 3 * 24 * 60 * 60_000L))
        assertEquals("6일 전", formatRelative(now, now - 6 * 24 * 60 * 60_000L))
    }

    @Test
    fun delta_over_one_week_remains_days() {
        // Plan keeps it simple — no fallback to absolute date for now.
        assertEquals("7일 전", formatRelative(now, now - 7 * 24 * 60 * 60_000L))
        assertEquals("30일 전", formatRelative(now, now - 30 * 24 * 60 * 60_000L))
    }
}
