package com.smartnoti.app.notification

import com.smartnoti.app.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Failing tests for plan
 * `docs/plans/2026-04-27-fix-issue-510-replacement-icon-source-action-overlay.md`
 * Task 1 (Issue #510).
 *
 * Pins the [ReplacementActionIcon] enum + 3 vector drawable assets that
 * Task 3 will add. The enum maps each SmartNoti replacement action to a
 * distinct small-icon drawable resource so tray rows make the action
 * visually identifiable at a glance:
 *
 *   - DIGEST   → `R.drawable.ic_replacement_digest`   (inbox glyph)
 *   - SILENT   → `R.drawable.ic_replacement_silent`   (volume_off glyph)
 *   - PRIORITY → `R.drawable.ic_replacement_priority` (notifications / priority_high glyph)
 *
 * These tests are RED on `main` because (a) the [ReplacementActionIcon]
 * enum class does not exist yet, and (b) the three drawable resources are
 * not yet declared under `app/src/main/res/drawable/`. Both compile
 * failures (unresolved type + unresolved `R.drawable.*`) ARE the intended
 * RED signal — see the plan "Files (new)" list (Task 3) and the plan
 * Task 1 carve-out that compile errors count as RED for the failing-test
 * gate.
 *
 * The notifier-level wiring tests
 * ([SmartNotiNotifierIconTest], [SilentHiddenSummaryNotifierIconTest])
 * additionally cover that each notifier uses the correct enum value at
 * its call site — the assertions here only pin the enum→resourceId map
 * so a future drawable rename or enum reorder fails loudly in one place
 * rather than as an opaque tray-rendering regression.
 */
class ReplacementActionIconTest {

    @Test
    fun digest_maps_to_inbox_drawable() {
        assertEquals(
            R.drawable.ic_replacement_digest,
            ReplacementActionIcon.DIGEST.drawableRes,
        )
    }

    @Test
    fun silent_maps_to_volume_off_drawable() {
        assertEquals(
            R.drawable.ic_replacement_silent,
            ReplacementActionIcon.SILENT.drawableRes,
        )
    }

    @Test
    fun priority_maps_to_notifications_drawable() {
        assertEquals(
            R.drawable.ic_replacement_priority,
            ReplacementActionIcon.PRIORITY.drawableRes,
        )
    }

    @Test
    fun each_action_carries_a_distinct_drawable_resource() {
        // Guards against a future copy-paste regression where two enum
        // entries share the same drawable id and the tray silently
        // collapses two actions back into one indistinguishable glyph —
        // exactly the user complaint in Issue #510.
        val digest = ReplacementActionIcon.DIGEST.drawableRes
        val silent = ReplacementActionIcon.SILENT.drawableRes
        val priority = ReplacementActionIcon.PRIORITY.drawableRes

        assertNotEquals("DIGEST and SILENT must use distinct drawables", digest, silent)
        assertNotEquals("DIGEST and PRIORITY must use distinct drawables", digest, priority)
        assertNotEquals("SILENT and PRIORITY must use distinct drawables", silent, priority)
    }
}
