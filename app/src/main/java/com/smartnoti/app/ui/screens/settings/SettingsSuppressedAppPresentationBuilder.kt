package com.smartnoti.app.ui.screens.settings

import com.smartnoti.app.data.local.CapturedAppSelectionItem

class SettingsSuppressedAppPresentationBuilder {
    fun build(apps: List<RawSuppressedAppState>): SettingsSuppressedAppPresentation {
        val selectedItems = apps.filter(RawSuppressedAppState::isSelected)
            .sortedByDescending { it.app.notificationCount }
            .map { it.toPresentationItem() }
        val availableItems = apps.filterNot(RawSuppressedAppState::isSelected)
            .sortedByDescending { it.app.notificationCount }
            .map { it.toPresentationItem() }

        val groups = buildList {
            if (selectedItems.isNotEmpty()) {
                add(SettingsSuppressedAppGroup(title = "이미 선택한 앱", items = selectedItems))
            }
            if (availableItems.isNotEmpty()) {
                add(SettingsSuppressedAppGroup(title = "추가로 숨길 수 있는 앱", items = availableItems))
            }
        }

        val summary = "선택 ${selectedItems.size}개 · 추가 가능 ${availableItems.size}개"
        return SettingsSuppressedAppPresentation(summary = summary, groups = groups)
    }
}

data class RawSuppressedAppState(
    val app: CapturedAppSelectionItem,
    val isSelected: Boolean,
)

data class SettingsSuppressedAppPresentation(
    val summary: String,
    val groups: List<SettingsSuppressedAppGroup>,
)

data class SettingsSuppressedAppGroup(
    val title: String,
    val items: List<SettingsSuppressedAppItem>,
)

data class SettingsSuppressedAppItem(
    val packageName: String,
    val appName: String,
    val notificationCount: Long,
    val lastSeenLabel: String,
    val isSelected: Boolean,
)

private fun RawSuppressedAppState.toPresentationItem() = SettingsSuppressedAppItem(
    packageName = app.packageName,
    appName = app.appName,
    notificationCount = app.notificationCount,
    lastSeenLabel = app.lastSeenLabel,
    isSelected = isSelected,
)
