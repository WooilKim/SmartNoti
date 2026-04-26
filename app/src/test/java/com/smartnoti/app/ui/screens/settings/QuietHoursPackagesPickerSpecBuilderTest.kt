package com.smartnoti.app.ui.screens.settings

import com.smartnoti.app.data.local.CapturedAppSelectionItem
import com.smartnoti.app.data.settings.SmartNotiSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Plan `2026-04-26-quiet-hours-shopping-packages-user-extensible.md` Task 6.
 *
 * Pins the visibility / count copy / empty-set warning / item ordering for
 * the new "조용한 시간 대상 앱" sub-row + picker so the call-site
 * `OperationalSummaryCard` stays a thin renderer. Compose UI tests aren't
 * available in this module, so these decisions are extracted into a pure
 * builder we can unit-test (mirrors `QuietHoursWindowPickerSpecBuilder`).
 *
 * Decisions captured by the spec (per the plan's Architecture / Product
 * intent sections):
 *   - Sub-row is visible **only when** `quietHoursEnabled = true` so the
 *     user doesn't fiddle with a no-op control while the master switch
 *     is OFF (consistent with the start/end hour picker row).
 *   - Empty-set + master switch ON surfaces an inline warning copy — this
 *     is the explicit signal that quiet hours is silently no-op'ing.
 *   - Items merge the persisted set with the captured-app catalog so each
 *     selected row shows a human label + observed count when available.
 */
class QuietHoursPackagesPickerSpecBuilderTest {

    private val builder = QuietHoursPackagesPickerSpecBuilder()

    @Test
    fun spec_is_visible_when_quiet_hours_enabled() {
        val spec = builder.build(
            SmartNotiSettings(quietHoursEnabled = true),
            capturedApps = emptyList(),
        )

        assertTrue(spec.visible)
    }

    @Test
    fun spec_is_hidden_when_quiet_hours_disabled() {
        val spec = builder.build(
            SmartNotiSettings(quietHoursEnabled = false),
            capturedApps = emptyList(),
        )

        assertFalse(spec.visible)
    }

    @Test
    fun spec_count_matches_settings_set_size() {
        val spec = builder.build(
            SmartNotiSettings(
                quietHoursEnabled = true,
                quietHoursPackages = setOf("com.coupang.mobile", "com.baemin"),
            ),
            capturedApps = emptyList(),
        )

        assertEquals(2, spec.selectedCount)
        assertEquals(
            "row label summary should expose the live count so the user can confirm intent without opening the picker",
            "2개",
            QuietHoursPackagesPickerSpecBuilder.rowSummary(spec.selectedCount),
        )
    }

    @Test
    fun spec_warns_when_set_is_empty_while_master_switch_is_on() {
        val spec = builder.build(
            SmartNotiSettings(
                quietHoursEnabled = true,
                quietHoursPackages = emptySet(),
            ),
            capturedApps = emptyList(),
        )

        assertTrue(spec.visible)
        assertEquals(0, spec.selectedCount)
        assertNotNull(
            "empty set + master switch ON must surface the silently-no-op warning",
            spec.emptyWarning,
        )
        assertEquals(QuietHoursPackagesPickerSpecBuilder.EMPTY_WARNING, spec.emptyWarning)
    }

    @Test
    fun spec_does_not_warn_when_master_switch_is_off_even_if_set_is_empty() {
        val spec = builder.build(
            SmartNotiSettings(
                quietHoursEnabled = false,
                quietHoursPackages = emptySet(),
            ),
            capturedApps = emptyList(),
        )

        assertFalse(spec.visible)
        // No warning while hidden — the hint would be invisible noise.
        assertNull(spec.emptyWarning)
    }

    @Test
    fun spec_does_not_warn_when_set_is_non_empty() {
        val spec = builder.build(
            SmartNotiSettings(
                quietHoursEnabled = true,
                quietHoursPackages = setOf("com.coupang.mobile"),
            ),
            capturedApps = emptyList(),
        )

        assertNull(spec.emptyWarning)
    }

    @Test
    fun selected_items_use_captured_app_label_when_available() {
        val coupang = capturedApp(
            packageName = "com.coupang.mobile",
            appName = "Coupang",
            notificationCount = 12,
            lastSeenLabel = "1시간 전",
        )
        val spec = builder.build(
            SmartNotiSettings(
                quietHoursEnabled = true,
                quietHoursPackages = setOf("com.coupang.mobile"),
            ),
            capturedApps = listOf(coupang),
        )

        assertEquals(1, spec.items.size)
        val item = spec.items.single()
        assertEquals("com.coupang.mobile", item.packageName)
        assertEquals("Coupang", item.displayName)
        assertEquals("12건 · 1시간 전", item.supporting)
    }

    @Test
    fun selected_items_fall_back_to_packageName_when_captured_app_unknown() {
        val spec = builder.build(
            SmartNotiSettings(
                quietHoursEnabled = true,
                quietHoursPackages = setOf("com.aliexpress.buyer"),
            ),
            capturedApps = emptyList(),
        )

        val item = spec.items.single()
        assertEquals("com.aliexpress.buyer", item.packageName)
        assertEquals(
            "an unknown package falls back to its packageName so the row is still selectable for removal",
            "com.aliexpress.buyer",
            item.displayName,
        )
        assertNull(item.supporting)
    }

    @Test
    fun selected_items_are_sorted_deterministically_by_packageName() {
        val spec = builder.build(
            SmartNotiSettings(
                quietHoursEnabled = true,
                quietHoursPackages = setOf("com.zzz", "com.aaa", "com.mmm"),
            ),
            capturedApps = emptyList(),
        )

        assertEquals(
            "rows must not reshuffle on recomposition — sort by packageName",
            listOf("com.aaa", "com.mmm", "com.zzz"),
            spec.items.map { it.packageName },
        )
    }

    @Test
    fun candidates_exclude_already_selected_packages_and_sort_by_observed_count() {
        val coupang = capturedApp(packageName = "com.coupang.mobile", appName = "Coupang", notificationCount = 50)
        val baemin = capturedApp(packageName = "com.baemin", appName = "Baemin", notificationCount = 80)
        val ali = capturedApp(packageName = "com.aliexpress.buyer", appName = "AliExpress", notificationCount = 5)
        val spec = builder.build(
            SmartNotiSettings(
                quietHoursEnabled = true,
                quietHoursPackages = setOf("com.coupang.mobile"),
            ),
            capturedApps = listOf(coupang, baemin, ali),
        )

        assertEquals(
            "candidates exclude already-selected packages and surface noisier apps first",
            listOf("com.baemin", "com.aliexpress.buyer"),
            spec.candidates.map { it.packageName },
        )
    }

    private fun capturedApp(
        packageName: String,
        appName: String,
        notificationCount: Long = 0,
        lastSeenLabel: String = "",
    ): CapturedAppSelectionItem = CapturedAppSelectionItem(
        packageName = packageName,
        appName = appName,
        notificationCount = notificationCount,
        lastSeenLabel = lastSeenLabel,
    )
}
