package com.smartnoti.app.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InsightBreakdownRowAffordanceBuilderTest {

    private val builder = InsightBreakdownRowAffordanceBuilder()

    @Test
    fun builds_clickable_affordance() {
        val model = builder.build(isClickable = true, isCurrent = false)

        assertTrue(model.showChevron)
        assertEquals("탭해서 자세히 보기", model.hintLabel)
        assertFalse(model.emphasizeCurrent)
    }

    @Test
    fun builds_current_affordance_without_chevron() {
        val model = builder.build(isClickable = false, isCurrent = true)

        assertFalse(model.showChevron)
        assertEquals("현재 보고 있는 항목", model.hintLabel)
        assertTrue(model.emphasizeCurrent)
    }

    @Test
    fun builds_static_affordance_for_non_clickable_non_current_rows() {
        val model = builder.build(isClickable = false, isCurrent = false)

        assertFalse(model.showChevron)
        assertEquals("참고용 요약", model.hintLabel)
        assertFalse(model.emphasizeCurrent)
    }
}
