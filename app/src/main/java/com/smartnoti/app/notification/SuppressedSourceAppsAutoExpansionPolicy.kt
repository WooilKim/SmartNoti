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
 *
 * Plan `2026-04-26-digest-suppression-sticky-exclude-list.md`: 사용자가 Settings 에서
 * 명시적으로 uncheck 한 앱은 [excludedApps] 에 sticky 하게 보존되고, 본 정책은 그
 * 앱의 auto-expansion 을 항상 차단한다. 다른 앱은 영향을 받지 않는다.
 */
internal object SuppressedSourceAppsAutoExpansionPolicy {

    fun expandedAppsOrNull(
        decision: NotificationDecision,
        suppressSourceForDigestAndSilent: Boolean,
        packageName: String,
        currentApps: Set<String>,
        excludedApps: Set<String>,
    ): Set<String>? {
        if (!suppressSourceForDigestAndSilent) return null
        if (decision != NotificationDecision.DIGEST) return null
        if (packageName.isBlank()) return null
        // Plan `2026-04-26-digest-suppression-sticky-exclude-list.md` Task 2:
        // 사용자가 Settings 에서 명시적으로 uncheck 한 앱은 sticky 하게 제외.
        if (packageName in excludedApps) return null
        if (packageName in currentApps) return null
        // Plan `2026-04-24-duplicate-notifications-suppress-defaults-ac.md`
        // Task 2 / Risks Q1: when `currentApps` is empty,
        // `NotificationSuppressionPolicy` already treats every package as
        // opted-in (opt-out semantics). Auto-adding a single app here would
        // silently flip the meaning to "allow-list of one" and shrink the
        // suppression scope. Skip the expansion in that case so empty stays
        // empty until the user explicitly narrows the scope from Settings.
        if (currentApps.isEmpty()) return null
        return currentApps + packageName
    }
}
