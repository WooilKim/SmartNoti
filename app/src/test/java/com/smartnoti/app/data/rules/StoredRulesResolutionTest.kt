package com.smartnoti.app.data.rules

import com.smartnoti.app.domain.model.RuleActionUi
import com.smartnoti.app.domain.model.RuleTypeUi
import com.smartnoti.app.domain.model.RuleUiModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StoredRulesResolutionTest {

    @Test
    fun null_payload_returns_default_rules() {
        val rules = resolveStoredRules(null)

        assertEquals(listOf("엄마", "쿠팡", "인증번호"), rules.map { it.title })
    }

    @Test
    fun blank_payload_returns_explicitly_empty_rules() {
        val rules = resolveStoredRules("")

        assertTrue(rules.isEmpty())
    }

    @Test
    fun configured_rules_with_null_payload_return_empty_rules() {
        val rules = resolveConfiguredRules(null)

        assertTrue(rules.isEmpty())
    }

    @Test
    fun configured_rules_with_blank_payload_return_empty_rules() {
        val rules = resolveConfiguredRules("")

        assertTrue(rules.isEmpty())
    }

    @Test
    fun configured_rules_with_saved_payload_return_decoded_rules() {
        val encoded = RuleStorageCodec.encode(
            listOf(
                RuleUiModel(
                    id = "keyword:배송",
                    title = "배송",
                    subtitle = "항상 바로 보기",
                    type = RuleTypeUi.KEYWORD,
                    action = RuleActionUi.ALWAYS_PRIORITY,
                    enabled = true,
                    matchValue = "배송",
                ),
            ),
        )

        val rules = resolveConfiguredRules(encoded)

        assertEquals(listOf("배송"), rules.map { it.title })
    }
}
