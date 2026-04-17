package com.smartnoti.app.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Test

class InsightContextBadgeModelBuilderTest {

    private val builder = InsightContextBadgeModelBuilder()

    @Test
    fun builds_general_badge_model() {
        val model = builder.build(InsightDrillDownSource.GENERAL)

        assertEquals("일반 인사이트", model.label)
        assertEquals(InsightContextBadgeTone.GENERAL, model.tone)
    }

    @Test
    fun builds_suppression_badge_model() {
        val model = builder.build(InsightDrillDownSource.SUPPRESSION)

        assertEquals("숨김 인사이트", model.label)
        assertEquals(InsightContextBadgeTone.SUPPRESSION, model.tone)
    }
}
