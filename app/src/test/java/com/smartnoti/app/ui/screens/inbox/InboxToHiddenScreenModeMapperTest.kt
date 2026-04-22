package com.smartnoti.app.ui.screens.inbox

import com.smartnoti.app.domain.model.SilentMode
import com.smartnoti.app.ui.screens.hidden.HiddenScreenMode
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

/**
 * Plan `docs/plans/2026-04-22-inbox-denest-and-home-recent-truncate.md` Task 0.
 *
 * Pure-function mapping from the Inbox outer tab selection to the Hidden screen
 * embed mode. The Digest tab is intentionally outside this mapper's domain —
 * Inbox renders DigestScreen directly, so calling `mapToMode(Digest)` is a
 * programmer error.
 */
class InboxToHiddenScreenModeMapperTest {

    @Test
    fun maps_archived_tab_to_embedded_archived_mode() {
        val mode = InboxToHiddenScreenModeMapper.mapToMode(InboxTab.Archived)

        assertEquals(HiddenScreenMode.Embedded(SilentMode.ARCHIVED), mode)
    }

    @Test
    fun maps_processed_tab_to_embedded_processed_mode() {
        val mode = InboxToHiddenScreenModeMapper.mapToMode(InboxTab.Processed)

        assertEquals(HiddenScreenMode.Embedded(SilentMode.PROCESSED), mode)
    }

    @Test
    fun digest_tab_is_not_a_valid_input_and_throws() {
        try {
            InboxToHiddenScreenModeMapper.mapToMode(InboxTab.Digest)
            fail("Expected IllegalStateException for Digest tab")
        } catch (expected: IllegalStateException) {
            // Pass — Digest is not delegated to HiddenNotificationsScreen.
        }
    }
}
