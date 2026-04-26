package com.smartnoti.app.ui.screens.settings

import com.smartnoti.app.data.local.CapturedAppSelectionItem

/**
 * Builds the grouped + sorted presentation for the Suppressed Apps editor.
 *
 * Plan `2026-04-26-digest-suppression-sticky-exclude-list.md` Task 6:
 * `isSelected` is no longer a single membership check. Callers used to pass
 * `app.packageName in suppressedSourceApps`, but with the sticky-exclude set
 * an explicitly-unchecked package must render as `isSelected = false` even
 * when [suppressedSourceApps] is empty (the "all apps opt-in by default"
 * semantic). The [build] overload that accepts `suppressedSourceApps` and
 * `excludedApps` encapsulates that derivation in one place so the Settings
 * UI and tests share the same logic.
 *
 * Effective `isSelected` rule:
 *  - `false` when `packageName` is in [excludedApps] (sticky exclude wins).
 *  - Otherwise `true` when [suppressedSourceApps] is empty (default opt-in
 *    semantic — `NotificationSuppressionPolicy` treats empty as
 *    "all packages opted in").
 *  - Otherwise `true` only when `packageName` is in [suppressedSourceApps].
 */
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

    /**
     * Convenience overload that derives `isSelected` from the two persisted
     * sets. See class KDoc for the full rule. Kept as a thin wrapper around
     * [build] so the existing pure-list API stays test-able in isolation.
     */
    fun build(
        capturedApps: List<CapturedAppSelectionItem>,
        suppressedSourceApps: Set<String>,
        excludedApps: Set<String>,
    ): SettingsSuppressedAppPresentation {
        return build(
            apps = capturedApps.map { app ->
                RawSuppressedAppState(
                    app = app,
                    isSelected = isEffectivelySelected(
                        packageName = app.packageName,
                        suppressedSourceApps = suppressedSourceApps,
                        excludedApps = excludedApps,
                    ),
                )
            },
        )
    }

    companion object {
        fun isEffectivelySelected(
            packageName: String,
            suppressedSourceApps: Set<String>,
            excludedApps: Set<String>,
        ): Boolean {
            if (packageName in excludedApps) return false
            if (suppressedSourceApps.isEmpty()) return true
            return packageName in suppressedSourceApps
        }
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
