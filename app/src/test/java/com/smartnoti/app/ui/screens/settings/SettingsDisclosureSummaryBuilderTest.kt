package com.smartnoti.app.ui.screens.settings

import com.smartnoti.app.data.local.CapturedAppSelectionItem
import com.smartnoti.app.data.settings.SmartNotiSettings
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsDisclosureSummaryBuilderTest {

    private val builder = SettingsDisclosureSummaryBuilder()

    @Test
    fun delivery_profile_summary_uses_human_readable_labels() {
        val summary = builder.buildDeliveryProfileSummary(
            alertLevel = "LOUD",
            vibrationMode = "STRONG",
            headsUpEnabled = true,
            lockScreenVisibility = "PRIVATE",
        )

        assertEquals("강함 · 강하게 · Heads-up 켜짐 · 내용 숨김", summary)
    }

    @Test
    fun suppression_advanced_summary_reflects_toggle_states() {
        val summary = builder.buildSuppressionAdvancedSummary(
            SmartNotiSettings(
                hidePersistentNotifications = true,
                hidePersistentSourceNotifications = false,
                protectCriticalPersistentNotifications = true,
            )
        )

        assertEquals("목록 숨김 ON · 알림센터 숨김 OFF · 중요 알림 보호 ON", summary)
    }

    @Test
    fun suppressed_apps_summary_handles_disabled_and_selected_states() {
        assertEquals(
            "원본 알림 숨기기를 켜면 앱을 고를 수 있어요",
            builder.buildSuppressedAppsSummary(
                suppressEnabled = false,
                selectedCount = 0,
                availableApps = availableApps(count = 4),
            )
        )

        assertEquals(
            "선택한 앱 2개 · 쿠팡 8건 · 토스 3건 외 3개",
            builder.buildSuppressedAppsSummary(
                suppressEnabled = true,
                selectedCount = 2,
                availableApps = listOf(
                    app("쿠팡", 8),
                    app("토스", 3),
                    app("지메일", 2),
                    app("슬랙", 1),
                    app("카카오톡", 1),
                ),
            )
        )
    }

    @Test
    fun suppressed_apps_summary_uses_empty_state_when_enabled_without_apps() {
        assertEquals(
            "아직 캡처된 앱이 없어요",
            builder.buildSuppressedAppsSummary(
                suppressEnabled = true,
                selectedCount = 0,
                availableApps = emptyList(),
            )
        )
    }

    private fun availableApps(count: Int): List<CapturedAppSelectionItem> =
        (1..count).map { index -> app(appName = "앱$index", count = index.toLong()) }

    private fun app(appName: String, count: Long) = CapturedAppSelectionItem(
        packageName = "pkg.$appName",
        appName = appName,
        notificationCount = count,
        lastSeenLabel = "방금",
    )
}
