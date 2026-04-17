package com.smartnoti.app.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeReasonBreakdownChartModelBuilderTest {

    private val builder = HomeReasonBreakdownChartModelBuilder()

    @Test
    fun scales_reason_share_against_total_reason_count() {
        val model = builder.build(
            listOf(
                HomeReasonInsight(tag = "쇼핑 앱", count = 4),
                HomeReasonInsight(tag = "반복 알림", count = 2),
                HomeReasonInsight(tag = "사용자 규칙", count = 2),
            ),
        )

        assertEquals(listOf(0.5f, 0.25f, 0.25f), model.items.map { item -> item.shareFraction })
        assertEquals(listOf(true, false, false), model.items.map { item -> item.isTopReason })
    }

    @Test
    fun returns_empty_model_for_empty_reason_list() {
        val model = builder.build(emptyList())

        assertEquals(emptyList<HomeReasonBreakdownItem>(), model.items)
    }
}
