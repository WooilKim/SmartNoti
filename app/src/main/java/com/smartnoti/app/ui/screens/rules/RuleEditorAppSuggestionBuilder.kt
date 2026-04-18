package com.smartnoti.app.ui.screens.rules

import com.smartnoti.app.data.local.CapturedAppSelectionItem

data class RuleEditorAppSuggestion(
    val packageName: String,
    val appName: String,
    val supportingLabel: String,
)

class RuleEditorAppSuggestionBuilder {
    fun build(capturedApps: List<CapturedAppSelectionItem>): List<RuleEditorAppSuggestion> {
        return capturedApps
            .sortedWith(
                compareByDescending<CapturedAppSelectionItem> { it.notificationCount }
                    .thenBy { it.appName }
            )
            .take(6)
            .map { app ->
                RuleEditorAppSuggestion(
                    packageName = app.packageName,
                    appName = app.appName,
                    supportingLabel = "알림 ${app.notificationCount}건 · ${app.lastSeenLabel}",
                )
            }
    }
}
