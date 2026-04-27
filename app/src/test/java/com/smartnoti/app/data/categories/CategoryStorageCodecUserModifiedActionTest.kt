package com.smartnoti.app.data.categories

import com.smartnoti.app.domain.model.Category
import com.smartnoti.app.domain.model.CategoryAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * RED-first contract for plan
 * `docs/plans/2026-04-27-fix-issue-478-promo-prefix-precedence-and-bundle-by-default.md`
 * Task 3 (Bug B2 + M1 migration).
 *
 * Pins the behavior of the new `userModifiedAction` column on
 * [com.smartnoti.app.data.categories.CategoryStorageCodec] used by the
 * Bug B2 migration runner to decide whether the user has explicitly set the
 * Category action (and therefore must be respected) vs. still carrying a
 * preset/seeded default (eligible for the SILENT → DIGEST one-shot bump).
 *
 * Backward-compat: legacy 6-column payloads (pre-Task-3 installs) decode with
 * `userModifiedAction = false` so the migration runner can act on them. New
 * payloads write a 7th column with the boolean.
 */
class CategoryStorageCodecUserModifiedActionTest {

    @Test
    fun encode_then_decode_round_trips_user_modified_action_true() {
        val original = listOf(
            Category(
                id = "cat-onboarding-promo_quieting",
                name = "프로모션 알림",
                appPackageName = null,
                ruleIds = listOf("rule-promo"),
                action = CategoryAction.SILENT,
                order = 1,
                userModifiedAction = true,
            ),
        )

        val decoded = CategoryStorageCodec.decode(CategoryStorageCodec.encode(original))

        assertEquals(1, decoded.size)
        assertTrue(
            "userModifiedAction=true must round-trip through encode → decode",
            decoded.single().userModifiedAction,
        )
    }

    @Test
    fun encode_then_decode_round_trips_user_modified_action_false() {
        val original = listOf(
            Category(
                id = "cat-onboarding-promo_quieting",
                name = "프로모션 알림",
                appPackageName = null,
                ruleIds = listOf("rule-promo"),
                action = CategoryAction.SILENT,
                order = 1,
                userModifiedAction = false,
            ),
        )

        val decoded = CategoryStorageCodec.decode(CategoryStorageCodec.encode(original))

        assertFalse(
            "userModifiedAction=false must round-trip through encode → decode",
            decoded.single().userModifiedAction,
        )
    }

    @Test
    fun decoder_treats_legacy_6_column_payload_as_user_modified_false() {
        // Pre-Task-3 installs persisted 6 columns (no userModifiedAction).
        // Build the legacy payload by hand so the decoder must do the
        // backward-compat fallback. URL-encoding mirrors the codec's
        // per-cell escape.
        val legacyLine = listOf(
            urlEncode("cat-onboarding-promo_quieting"),
            urlEncode("프로모션 알림"),
            urlEncode("\u0000"), // NULL_APP_PACKAGE sentinel
            urlEncode("SILENT"),
            urlEncode("1"),
            urlEncode("rule-promo"),
        ).joinToString("|")

        val decoded = CategoryStorageCodec.decode(legacyLine)

        assertEquals(1, decoded.size)
        val only = decoded.single()
        assertEquals(CategoryAction.SILENT, only.action)
        assertFalse(
            "Legacy 6-column payload must decode with userModifiedAction = false",
            only.userModifiedAction,
        )
    }

    @Test
    fun default_user_modified_action_is_false_for_new_categories() {
        // Source-level guarantee — adding a Category without specifying the
        // flag must default to false so the migration can still bump the
        // SILENT preset to DIGEST.
        val cat = Category(
            id = "cat-x",
            name = "x",
            appPackageName = null,
            ruleIds = emptyList(),
            action = CategoryAction.SILENT,
            order = 0,
        )
        assertFalse(cat.userModifiedAction)
    }

    private fun urlEncode(s: String): String {
        return java.net.URLEncoder.encode(s, java.nio.charset.StandardCharsets.UTF_8.toString())
    }
}
