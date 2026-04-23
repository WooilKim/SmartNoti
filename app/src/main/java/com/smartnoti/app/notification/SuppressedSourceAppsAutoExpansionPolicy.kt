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
    ): Set<String>? {
        if (!suppressSourceForDigestAndSilent) return null
        if (decision != NotificationDecision.DIGEST) return null
        if (packageName.isBlank()) return null
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
