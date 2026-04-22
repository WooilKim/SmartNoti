package com.smartnoti.app.ui.components

import com.smartnoti.app.domain.model.RuleActionUi
import com.smartnoti.app.domain.model.RuleTypeUi
import com.smartnoti.app.domain.model.RuleUiModel
import org.junit.Assert.assertEquals
import org.junit.Test

class RuleRowMatchValueFormatterTest {

    private val builder = RuleRowDescriptionBuilder()

    @Test
    fun repeat_bundle_display_value_reads_like_user_threshold_copy() {
        val description = builder.build(
            rule = RuleUiModel(
                id = "repeat:5",
                title = "반복 푸시",
                subtitle = "unused",
                type = RuleTypeUi.REPEAT_BUNDLE,
                enabled = true,
                matchValue = "5",
            ),
            action = RuleActionUi.DIGEST,
        )

        assertEquals("같은 알림이 5회 이상 반복되면 Digest로 묶어요", description.primaryText)
        assertEquals("반복 기준", description.secondaryText)
    }
}
