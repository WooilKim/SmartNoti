package com.smartnoti.app.data.rules

import com.smartnoti.app.domain.model.RuleActionUi
import com.smartnoti.app.domain.model.RuleTypeUi
import com.smartnoti.app.domain.model.RuleUiModel
import org.junit.Assert.assertEquals
import org.junit.Test

class RuleStorageCodecTest {

    @Test
    fun rules_round_trip_through_codec() {
        val rules = listOf(
            RuleUiModel(
                id = "r1",
                title = "엄마",
                subtitle = "항상 바로 보기",
                type = RuleTypeUi.PERSON,
                action = RuleActionUi.ALWAYS_PRIORITY,
                enabled = true,
                matchValue = "엄마",
            ),
            RuleUiModel(
                id = "r2",
                title = "쿠팡",
                subtitle = "Digest로 묶기",
                type = RuleTypeUi.APP,
                action = RuleActionUi.DIGEST,
                enabled = false,
                matchValue = "com.coupang.mobile",
            ),
        )

        val encoded = RuleStorageCodec.encode(rules)
        val decoded = RuleStorageCodec.decode(encoded)

        assertEquals(rules, decoded)
    }
}
