package com.smartnoti.app.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InsightDrillDownReasonNavigationModelBuilderTest {

    private val builder = InsightDrillDownReasonNavigationModelBuilder()

    @Test
    fun marks_all_reason_rows_clickable_for_app_drill_down() {
        val models = builder.build(
            items = listOf(
                InsightDrillDownReasonBreakdownItem(
                    tag = "반복 알림",
                    count = 3,
                    shareFraction = 0.6f,
                    isTopReason = true,
                )
            ),
            currentReasonTag = null,
        )

        assertEquals(1, models.size)
        assertTrue(models[0].isClickable)
        assertEquals("탭해서 이 이유만 다시 보기", models[0].hintLabel)
    }

    @Test
    fun disables_self_navigation_for_current_reason_only() {
        val models = builder.build(
            items = listOf(
                InsightDrillDownReasonBreakdownItem(
                    tag = "쇼핑 앱",
                    count = 4,
                    shareFraction = 0.5f,
                    isTopReason = true,
                ),
                InsightDrillDownReasonBreakdownItem(
                    tag = "반복 알림",
                    count = 4,
                    shareFraction = 0.5f,
                    isTopReason = true,
                ),
            ),
            currentReasonTag = "쇼핑 앱",
        )

        assertFalse(models[0].isClickable)
        assertEquals("현재 보고 있는 이유", models[0].hintLabel)
        assertTrue(models[1].isClickable)
        assertEquals("탭해서 이 이유만 다시 보기", models[1].hintLabel)
    }
}
