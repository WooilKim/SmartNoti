package com.smartnoti.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomePassthroughReviewCardStateTest {

    @Test
    fun empty_state_when_count_is_zero() {
        val state = HomePassthroughReviewCardState.from(count = 0)

        assertFalse(state.isActive)
        assertEquals(0, state.count)
        assertEquals("검토 대기 알림 없음", state.title)
        assertEquals(
            "SmartNoti 가 건드리지 않은 알림이 들어오면 여기에서 재분류할 수 있어요",
            state.body,
        )
        assertEquals("검토하기", state.actionLabel)
    }

    @Test
    fun active_state_when_count_is_positive() {
        val state = HomePassthroughReviewCardState.from(count = 3)

        assertTrue(state.isActive)
        assertEquals(3, state.count)
        assertEquals("SmartNoti 가 건드리지 않은 알림 3건", state.title)
        assertEquals(
            "이 판단이 맞는지 검토하고 필요하면 규칙으로 만들 수 있어요",
            state.body,
        )
        assertEquals("검토하기", state.actionLabel)
    }

    @Test
    fun active_state_formats_large_counts_literally() {
        val state = HomePassthroughReviewCardState.from(count = 42)

        assertTrue(state.isActive)
        assertEquals("SmartNoti 가 건드리지 않은 알림 42건", state.title)
    }

    @Test
    fun negative_count_is_clamped_to_zero_empty_state() {
        val state = HomePassthroughReviewCardState.from(count = -5)

        assertFalse(state.isActive)
        assertEquals(0, state.count)
        assertEquals("검토 대기 알림 없음", state.title)
    }
}
