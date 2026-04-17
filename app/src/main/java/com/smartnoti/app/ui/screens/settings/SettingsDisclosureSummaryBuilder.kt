package com.smartnoti.app.ui.screens.settings

import com.smartnoti.app.data.settings.SmartNotiSettings

class SettingsDisclosureSummaryBuilder {
    fun buildDeliveryProfileSummary(
        alertLevel: String,
        vibrationMode: String,
        headsUpEnabled: Boolean,
        lockScreenVisibility: String,
    ): String {
        val alertLabel = alertLevel.toAlertLevelOrNull()?.toKoreanLabel() ?: alertLevel
        val vibrationLabel = vibrationMode.toVibrationModeOrNull()?.toKoreanLabel() ?: vibrationMode
        val lockScreenLabel = lockScreenVisibility.toLockScreenVisibilityOrNull()?.toKoreanLabel() ?: lockScreenVisibility
        val headsUpLabel = if (headsUpEnabled) "Heads-up 켜짐" else "Heads-up 꺼짐"
        return listOf(alertLabel, vibrationLabel, headsUpLabel, lockScreenLabel).joinToString(" · ")
    }

    fun buildSuppressionAdvancedSummary(settings: SmartNotiSettings): String {
        return listOf(
            "목록 숨김 ${settings.hidePersistentNotifications.toOnOffKorean()}",
            "알림센터 숨김 ${settings.hidePersistentSourceNotifications.toOnOffKorean()}",
            "중요 알림 보호 ${settings.protectCriticalPersistentNotifications.toOnOffKorean()}",
        ).joinToString(" · ")
    }

    fun buildSuppressedAppsSummary(
        suppressEnabled: Boolean,
        selectedCount: Int,
        availableCount: Int,
    ): String {
        if (!suppressEnabled) {
            return "원본 알림 숨기기를 켜면 앱을 고를 수 있어요"
        }
        return "선택한 앱 ${selectedCount}개 · 캡처된 앱 ${availableCount}개"
    }
}

private fun String.toAlertLevelOrNull() = runCatching { enumValueOf<com.smartnoti.app.domain.model.AlertLevel>(trim().uppercase()) }.getOrNull()
private fun String.toVibrationModeOrNull() = runCatching { enumValueOf<com.smartnoti.app.domain.model.VibrationMode>(trim().uppercase()) }.getOrNull()
private fun String.toLockScreenVisibilityOrNull() = runCatching { enumValueOf<com.smartnoti.app.domain.model.LockScreenVisibilityMode>(trim().uppercase()) }.getOrNull()
private fun Boolean.toOnOffKorean(): String = if (this) "ON" else "OFF"
