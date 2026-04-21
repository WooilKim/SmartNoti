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

    @Test
    fun decode_returns_empty_list_for_invalid_payload_line() {
        val decoded = RuleStorageCodec.decode("broken-payload")

        assertEquals(emptyList<RuleUiModel>(), decoded)
    }

    @Test
    fun override_of_field_round_trips_through_codec() {
        // Plan rules-ux-v2-inbox-restructure Phase C Task 1: the new
        // `overrideOf` field (override chain) must survive encode + decode.
        val rules = listOf(
            RuleUiModel(
                id = "base",
                title = "결제",
                subtitle = "항상 바로 보기",
                type = RuleTypeUi.KEYWORD,
                action = RuleActionUi.ALWAYS_PRIORITY,
                enabled = true,
                matchValue = "결제",
                overrideOf = null,
            ),
            RuleUiModel(
                id = "override",
                title = "결제 광고",
                subtitle = "조용히",
                type = RuleTypeUi.KEYWORD,
                action = RuleActionUi.SILENT,
                enabled = true,
                matchValue = "광고",
                overrideOf = "base",
            ),
        )

        val decoded = RuleStorageCodec.decode(RuleStorageCodec.encode(rules))

        assertEquals(rules, decoded)
    }

    @Test
    fun decode_tolerates_legacy_payload_without_override_field() {
        // Rules persisted before Phase C only have 7 columns. The new decoder
        // must treat missing trailing columns as `overrideOf = null` rather
        // than dropping the row.
        val legacyLine = listOf(
            "legacy",
            "Legacy",
            "subtitle",
            "KEYWORD",
            "ALWAYS_PRIORITY",
            "true",
            "matchvalue",
        ).joinToString("|") { java.net.URLEncoder.encode(it, "UTF-8") }

        val decoded = RuleStorageCodec.decode(legacyLine)

        assertEquals(1, decoded.size)
        assertEquals("legacy", decoded[0].id)
        assertEquals(null, decoded[0].overrideOf)
    }
}
