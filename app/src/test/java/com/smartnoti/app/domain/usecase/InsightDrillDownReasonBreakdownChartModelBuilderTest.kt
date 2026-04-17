package com.smartnoti.app.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InsightDrillDownReasonBreakdownChartModelBuilderTest {

    private val builder = InsightDrillDownReasonBreakdownChartModelBuilder()

    @Test
    fun builds_share_bars_from_top_reasons() {
        val model = builder.build(
            listOf(
                HomeReasonInsight(tag = "쇼핑 앱", count = 4),
                HomeReasonInsight(tag = "반복 알림", count = 2),
            )
        )

        assertEquals(2, model.items.size)
        assertEquals("쇼핑 앱", model.items[0].tag)
        assertEquals(0.67f, model.items[0].shareFraction)
        assertTrue(model.items[0].isTopReason)
        assertEquals(0.33f, model.items[1].shareFraction)
        assertFalse(model.items[1].isTopReason)
    }

    @Test
    fun returns_empty_when_reason_counts_are_zero() {
        val model = builder.build(
            listOf(
                HomeReasonInsight(tag = "쇼핑 앱", count = 0),
            )
        )

        assertTrue(model.items.isEmpty())
    }
}
