package com.smartnoti.app.notification

import com.smartnoti.app.domain.model.NotificationDecision

/**
 * Decides whether a just-classified notification's package should be automatically added
 * to [com.smartnoti.app.data.settings.SmartNotiSettings.suppressedSourceApps].
 *
 * The per-app opt-in list was originally seeded at onboarding time from whatever
 * notifications happened to be in the tray, which leaves apps that surface promo/digest
 * content later stuck with the original source intact. When the user has turned on the
 * global `suppressSourceForDigestAndSilent` flag we treat that as "yes please hide these"
 * and self-expand the list rather than requiring the user to go back to Settings.
 */
internal object SuppressedSourceAppsAutoExpansionPolicy {

    fun expandedAppsOrNull(
        decision: NotificationDecision,
        suppressSourceForDigestAndSilent: Boolean,
        packageName: String,
        currentApps: Set<String>,
        excludedApps: Set<String> = emptySet(),
    ): Set<String>? {
        if (!suppressSourceForDigestAndSilent) return null
        if (decision != NotificationDecision.DIGEST) return null
        if (packageName.isBlank()) return null
        if (packageName in currentApps) return null
        // 사용자가 Settings 에서 명시적으로 끈 앱은 DIGEST 가 다시 와도 자동 추가하지 않음.
        // 다시 켜려면 같은 Settings 토글을 ON 으로 돌려야 하고, 토글 ON 은 이 exclusion
        // 엔트리도 함께 제거한다 (→ `SettingsRepository.toggleSuppressedSourceApp`).
        if (packageName in excludedApps) return null
        return currentApps + packageName
    }
}
