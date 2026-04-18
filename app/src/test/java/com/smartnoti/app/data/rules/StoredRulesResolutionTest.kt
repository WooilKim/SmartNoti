package com.smartnoti.app.data.rules

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
}
