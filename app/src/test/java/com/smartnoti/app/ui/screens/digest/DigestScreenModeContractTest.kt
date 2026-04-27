package com.smartnoti.app.ui.screens.digest

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contract: [DigestScreenMode] mirrors `HiddenScreenMode`'s
 * `Standalone | Embedded` shape so the same composable can serve both the
 * legacy `Routes.Digest` deep-link entry (where the in-screen `ScreenHeader`
 * provides the only context) and the inbox-unified Digest sub-tab embed
 * (where `InboxScreen` already supplies header + sort dropdown + tab row,
 * and any nested chrome is duplicate noise).
 *
 * Plan: `docs/plans/2026-04-27-inbox-unified-double-header-collapse.md`
 * Task 1 — pin the boolean header-rendering contract before the screen learns
 * about the mode.
 */
class DigestScreenModeContractTest {

    @Test
    fun standalone_renders_header() {
        assertTrue(
            "Standalone deep-link entry must keep the in-screen ScreenHeader",
            DigestScreenMode.Standalone.shouldRenderHeader(),
        )
    }

    @Test
    fun embedded_does_not_render_header() {
        assertFalse(
            "Embedded entry must defer to InboxScreen's outer header",
            DigestScreenMode.Embedded.shouldRenderHeader(),
        )
    }

    @Test
    fun standalone_and_embedded_are_distinct_modes() {
        assertNotEquals(DigestScreenMode.Standalone, DigestScreenMode.Embedded)
    }
}
