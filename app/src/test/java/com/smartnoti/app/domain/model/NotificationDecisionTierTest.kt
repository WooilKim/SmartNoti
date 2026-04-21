package com.smartnoti.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Task 1 (RED phase) of plan `2026-04-21-ignore-tier-fourth-decision`.
 *
 * These tests lock in the contract that a fourth classification tier — IGNORE
 * (무시) — must exist on three enums and round-trip via `enumValueOf`:
 *
 *  - [NotificationDecision.IGNORE]
 *  - [NotificationStatusUi.IGNORE]
 *  - [RuleActionUi.IGNORE]
 *
 * Reflection-based lookups (`enumValues()` / `enumValueOf()` with the literal
 * string "IGNORE") are used deliberately: they keep this file compilable today,
 * so `:app:assembleDebug` stays green and Task 2 can land the enum addition on
 * top. At runtime the tests fail — that is the intended RED signal.
 *
 * Task 2 adds `IGNORE` to each enum; these tests flip green without changes to
 * the test file itself.
 */
class NotificationDecisionTierTest {

    @Test
    fun notificationDecision_exposes_IGNORE_tier() {
        val names = enumValues<NotificationDecision>().map { it.name }
        assertTrue(
            "NotificationDecision must expose an IGNORE value (Task 2). Found: $names",
            names.contains("IGNORE"),
        )

        // Round-trip via the JVM enum helper — the canonical way persistence
        // layer code looks enums up from a stored string.
        val roundTrip = enumValueOf<NotificationDecision>("IGNORE")
        assertNotNull(roundTrip)
        assertEquals("IGNORE", roundTrip.name)
    }

    @Test
    fun notificationStatusUi_exposes_IGNORE_tier() {
        val names = enumValues<NotificationStatusUi>().map { it.name }
        assertTrue(
            "NotificationStatusUi must expose an IGNORE value (Task 2). Found: $names",
            names.contains("IGNORE"),
        )

        val roundTrip = enumValueOf<NotificationStatusUi>("IGNORE")
        assertNotNull(roundTrip)
        assertEquals("IGNORE", roundTrip.name)
    }

    @Test
    fun ruleActionUi_exposes_IGNORE_action() {
        val names = enumValues<RuleActionUi>().map { it.name }
        assertTrue(
            "RuleActionUi must expose an IGNORE action (Task 2). Found: $names",
            names.contains("IGNORE"),
        )

        val roundTrip = enumValueOf<RuleActionUi>("IGNORE")
        assertNotNull(roundTrip)
        assertEquals("IGNORE", roundTrip.name)
    }

    @Test
    fun notificationDecision_and_notificationStatusUi_IGNORE_names_match() {
        // Classifier output is converted to storage string; storage string is
        // parsed back into NotificationStatusUi. Name parity keeps that
        // implicit contract explicit.
        val decisionName = enumValueOf<NotificationDecision>("IGNORE").name
        val statusName = enumValueOf<NotificationStatusUi>("IGNORE").name
        assertEquals(decisionName, statusName)
    }
}
