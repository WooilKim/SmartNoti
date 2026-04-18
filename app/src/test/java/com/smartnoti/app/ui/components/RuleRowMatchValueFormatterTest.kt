package com.smartnoti.app.ui.components

import com.smartnoti.app.domain.model.RuleTypeUi
import org.junit.Assert.assertEquals
import org.junit.Test

class RuleRowMatchValueFormatterTest {

    @Test
    fun repeat_bundle_display_value_reads_like_user_threshold_copy() {
        val formatted = "5".toDisplayMatchValue(RuleTypeUi.REPEAT_BUNDLE)

        assertEquals("5회 이상 반복되면 적용", formatted)
    }
}
