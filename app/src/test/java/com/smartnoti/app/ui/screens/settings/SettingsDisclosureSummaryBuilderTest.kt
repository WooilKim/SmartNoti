package com.smartnoti.app.ui.screens.settings

import com.smartnoti.app.data.local.CapturedAppSelectionItem
import com.smartnoti.app.data.settings.SmartNotiSettings
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsDisclosureSummaryBuilderTest {

    private val builder = SettingsDisclosureSummaryBuilder()

    @Test
    fun delivery_profile_summary_tokens_use_human_readable_labels() {
        val tokens = builder.buildDeliveryProfileSummaryTokens(
            alertLevel = "LOUD",
            vibrationMode = "STRONG",
            headsUpEnabled = true,
            lockScreenVisibility = "PRIVATE",
        )

        assertEquals(
            DeliveryProfileSummaryTokens(
                alertLabel = "강함",
                vibrationLabel = "강하게",
                headsUpLabel = "Heads-up 켜짐",
                lockScreenLabel = "내용 숨김",
            ),
            tokens,
        )
    }

    @Test
    fun delivery_profile_summary_tokens_fall_back_to_raw_values_for_unknown_inputs() {
        val tokens = builder.buildDeliveryProfileSummaryTokens(
            alertLevel = "CUSTOM_ALERT",
            vibrationMode = "CUSTOM_VIBRATION",
            headsUpEnabled = false,
            lockScreenVisibility = "CUSTOM_LOCKSCREEN",
        )

        assertEquals(
            DeliveryProfileSummaryTokens(
                alertLabel = "CUSTOM_ALERT",
                vibrationLabel = "CUSTOM_VIBRATION",
                headsUpLabel = "Heads-up 꺼짐",
                lockScreenLabel = "CUSTOM_LOCKSCREEN",
            ),
            tokens,
        )
    }

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
    fun all_delivery_profiles_summary_includes_priority_digest_and_silent_context() {
        val summary = builder.buildAllDeliveryProfilesSummary(
            settings = SmartNotiSettings(
                priorityAlertLevel = "LOUD",
                priorityVibrationMode = "STRONG",
                priorityHeadsUpEnabled = true,
                priorityLockScreenVisibility = "PRIVATE",
                digestAlertLevel = "SOFT",
                digestVibrationMode = "LIGHT",
                digestHeadsUpEnabled = false,
                digestLockScreenVisibility = "PRIVATE",
                silentAlertLevel = "NONE",
                silentVibrationMode = "OFF",
                silentHeadsUpEnabled = false,
                silentLockScreenVisibility = "SECRET",
            )
        )

        assertEquals(
            "Priority 강함 · 강하게 · Heads-up 켜짐 · 내용 숨김 / Digest 보통 · 가볍게 · Heads-up 꺼짐 · 내용 숨김 / Silent 없음 · 끔 · Heads-up 꺼짐 · 숨김",
            summary,
        )
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

    @Test
    fun suppressed_apps_summary_omits_remainder_suffix_when_two_or_fewer_apps_exist() {
        assertEquals(
            "선택한 앱 1개 · 쿠팡 8건 · 토스 3건",
            builder.buildSuppressedAppsSummary(
                suppressEnabled = true,
                selectedCount = 1,
                availableApps = listOf(
                    app("쿠팡", 8),
                    app("토스", 3),
                ),
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
