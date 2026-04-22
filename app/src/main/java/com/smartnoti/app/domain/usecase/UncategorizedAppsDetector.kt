package com.smartnoti.app.domain.usecase

import com.smartnoti.app.domain.model.Category
import com.smartnoti.app.domain.model.NotificationUiModel

/**
 * Plan `docs/plans/2026-04-22-categories-split-rules-actions.md` Phase P3
 * Task 10 — detects whether the Home "새 앱 분류 유도 카드" should be shown.
 *
 * The card surfaces when there are **three or more distinct app packages**
 * that sent notifications in the last 7 days and are not yet pinned to any
 * Category via `appPackageName`. Tapping "나중에" triggers a 24-hour snooze
 * stored on [com.smartnoti.app.data.settings.SettingsRepository]; the caller
 * passes `snoozeUntilMillis` into [detect] so the detector stays a pure
 * function.
 *
 * The sample labels shown in the card body are the three most recently active
 * uncovered apps, ordered newest-first.
 */
class UncategorizedAppsDetector {

    /**
     * @param notifications full notification feed (the detector filters by age
     *   internally). Pass the same list Home renders so the detector matches
     *   "new apps the user is actually seeing". IGNORE-status rows are OK to
     *   include — the detector keys off `packageName`, not status.
     * @param categories every persisted Category. Only [Category.appPackageName]
     *   is used for coverage; keyword-scoped Categories still leave apps uncovered
     *   so a dedicated app-level Category can be proposed.
     * @param nowMillis wall-clock "now" in epoch millis. Injected for tests.
     * @param snoozeUntilMillis epoch millis until which the prompt is
     *   suppressed. Zero (default) means "not snoozed".
     */
    fun detect(
        notifications: List<NotificationUiModel>,
        categories: List<Category>,
        nowMillis: Long,
        snoozeUntilMillis: Long,
    ): UncategorizedAppsDetection {
        if (nowMillis < snoozeUntilMillis) return UncategorizedAppsDetection.None

        val coveredPackages = categories
            .mapNotNull { it.appPackageName?.trim()?.takeIf(String::isNotEmpty) }
            .toSet()

        // Keep the most-recent notification per package so the sample labels
        // can be sorted newest-first without a second pass.
        val cutoff = nowMillis - SEVEN_DAYS_MILLIS
        val newestByPackage = linkedMapOf<String, NotificationUiModel>()
        for (n in notifications) {
            if (n.postedAtMillis < cutoff) continue
            val pkg = n.packageName
            if (pkg.isBlank()) continue
            if (pkg in coveredPackages) continue
            val existing = newestByPackage[pkg]
            if (existing == null || n.postedAtMillis > existing.postedAtMillis) {
                newestByPackage[pkg] = n
            }
        }

        if (newestByPackage.size < THRESHOLD) return UncategorizedAppsDetection.None

        val ordered = newestByPackage.values.sortedByDescending { it.postedAtMillis }
        val top = ordered.take(SAMPLE_SIZE)
        return UncategorizedAppsDetection.Prompt(
            uncoveredCount = newestByPackage.size,
            sampleAppLabels = top.map { it.appName },
            samplePackageNames = top.map { it.packageName },
        )
    }

    companion object {
        /** Minimum distinct uncovered apps before the card shows. */
        const val THRESHOLD = 3

        /** How many app labels the card body names inline. */
        const val SAMPLE_SIZE = 3

        /** Look-back window for "recently active" apps. */
        const val SEVEN_DAYS_MILLIS: Long = 7L * 24L * 60L * 60L * 1000L
    }
}

/**
 * Result of an [UncategorizedAppsDetector] invocation. [Prompt] contains the
 * data the Home card renders; [None] means Home should hide the card.
 */
sealed class UncategorizedAppsDetection {
    data object None : UncategorizedAppsDetection()

    data class Prompt(
        val uncoveredCount: Int,
        val sampleAppLabels: List<String>,
        val samplePackageNames: List<String>,
    ) : UncategorizedAppsDetection()
}
