package com.smartnoti.app.ui.screens.hidden

import com.smartnoti.app.domain.model.SilentMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Plan `docs/plans/2026-04-28-meta-inbox-organized-feel-overhaul.md` finding
 * **F5** — "summary card on 보관 중 / 처리됨 says the same thing twice".
 *
 * Pins the summary slot composition for [HiddenNotificationsScreen] so a
 * future PR cannot silently re-introduce the count-restatement summary card
 * inside the inbox-unified embedded host (which gave the user three count
 * surfaces — outer tab row + summary card + per-group chip — for the same
 * number).
 *
 * Standalone deep-link entries keep the full summary card; embedded entries
 * collapse it to a compact caption + a small clear-all text button.
 */
class HiddenSummaryCardSpecTest {

    @Test
    fun standalone_keeps_full_summary_card() {
        // Standalone has no outer tab-row context, so the summary card is the
        // only count signal — keep it.
        assertTrue(
            "Standalone host must keep the full summary card",
            HiddenSummaryCardSpec.fullSummaryCardVisible(HiddenScreenMode.Standalone()),
        )
    }

    @Test
    fun embedded_drops_full_summary_card_for_both_silent_modes() {
        // F5 fix: outer InboxTabRow segment already declares the count
        // (`보관 중 · 11건` / `처리됨 · 11건`); a summary card directly below
        // that says `1개 앱에서 11건을 보관 중이에요.` is pure restatement.
        assertFalse(
            "Embedded ARCHIVED must NOT render the full summary card",
            HiddenSummaryCardSpec.fullSummaryCardVisible(
                HiddenScreenMode.Embedded(SilentMode.ARCHIVED),
            ),
        )
        assertFalse(
            "Embedded PROCESSED must NOT render the full summary card",
            HiddenSummaryCardSpec.fullSummaryCardVisible(
                HiddenScreenMode.Embedded(SilentMode.PROCESSED),
            ),
        )
    }

    @Test
    fun embedded_caption_keeps_helper_copy_per_silent_mode() {
        // The contextual hint (helper line) is preserved verbatim — only the
        // count restatement and the heavyweight surface go.
        assertEquals(
            HiddenSummaryCardSpec.ARCHIVED_CAPTION,
            HiddenSummaryCardSpec.embeddedCaptionFor(
                HiddenScreenMode.Embedded(SilentMode.ARCHIVED),
                SilentMode.ARCHIVED,
            ),
        )
        assertEquals(
            HiddenSummaryCardSpec.PROCESSED_CAPTION,
            HiddenSummaryCardSpec.embeddedCaptionFor(
                HiddenScreenMode.Embedded(SilentMode.PROCESSED),
                SilentMode.PROCESSED,
            ),
        )
    }

    @Test
    fun embedded_caption_must_not_restate_the_count() {
        // Defense-in-depth: a future copy edit that sneaks the count back into
        // the caption (e.g., "11건의 보관 알림 ...") would re-introduce the
        // exact 3x repetition F5 was filed to remove. The caption must be
        // count-free phrasing only.
        val captions = listOf(
            HiddenSummaryCardSpec.ARCHIVED_CAPTION,
            HiddenSummaryCardSpec.PROCESSED_CAPTION,
        )
        for (caption in captions) {
            assertFalse(
                "F5: embedded caption must not contain a count digit ('$caption')",
                caption.any { it.isDigit() },
            )
            assertFalse(
                "F5: embedded caption must not contain '건' count suffix ('$caption')",
                caption.contains("건"),
            )
        }
    }

    @Test
    fun standalone_caption_is_null_because_the_full_card_owns_the_helper_copy() {
        // In standalone the helper text already lives as the second line of
        // the SmartSurfaceCard; surfacing it as a separate caption would
        // duplicate it. Embedded is the only host that promotes the caption.
        assertNull(
            HiddenSummaryCardSpec.embeddedCaptionFor(
                HiddenScreenMode.Standalone(),
                SilentMode.ARCHIVED,
            ),
        )
        assertNull(
            HiddenSummaryCardSpec.embeddedCaptionFor(
                HiddenScreenMode.Standalone(),
                SilentMode.PROCESSED,
            ),
        )
    }

    @Test
    fun embedded_clear_all_label_is_compact_text_button_copy() {
        // Bulk-clear is a non-trivial action; F5 demands we keep it even after
        // demoting the surface. The label is intentionally shorter than the
        // legacy `전체 숨긴 알림 모두 지우기` (which was sized to fill an
        // OutlinedButton); the compact text-button doesn't need the qualifier
        // because the host context (보관 중 / 처리됨 sub-tab) already supplies
        // it.
        val embeddedLabel =
            HiddenSummaryCardSpec.embeddedClearAllLabel(HiddenScreenMode.Embedded(SilentMode.ARCHIVED))
        assertNotNull("Embedded host must expose a clear-all label", embeddedLabel)
        assertEquals("전체 모두 지우기", embeddedLabel)
    }

    @Test
    fun standalone_clear_all_label_is_null_because_the_outlined_button_remains() {
        // Standalone keeps the original OutlinedButton inside the full summary
        // card; the embedded text-button slot is therefore unused there.
        assertNull(
            HiddenSummaryCardSpec.embeddedClearAllLabel(HiddenScreenMode.Standalone()),
        )
    }
}
