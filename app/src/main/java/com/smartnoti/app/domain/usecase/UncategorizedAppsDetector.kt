package com.smartnoti.app.domain.usecase

import com.smartnoti.app.domain.model.Category
import com.smartnoti.app.domain.model.NotificationUiModel
import com.smartnoti.app.domain.model.RuleTypeUi
import com.smartnoti.app.domain.model.RuleUiModel

/**
 * Plan `docs/plans/2026-04-22-categories-split-rules-actions.md` Phase P3
 * Task 10 — detects whether the Home "새 앱 분류 유도 카드" should be shown.
 *
 * Plan `docs/plans/2026-04-27-uncategorized-prompt-app-rule-coverage.md`
 * extends coverage so APP-type Rules referenced via `Category.ruleIds` also
 * count as "covered". KEYWORD / PERSON / SCHEDULE / REPEAT_BUNDLE Rules do
 * not contribute (intentional — they don't pin a single package).
 *
 * The card surfaces when there are **three or more distinct app packages**
 * that sent notifications in the last 7 days and are not yet pinned to any
 * Category — either via [Category.appPackageName] or via an APP-type Rule
 * referenced from [Category.ruleIds]. Tapping "나중에" triggers a 24-hour
 * snooze stored on [com.smartnoti.app.data.settings.SettingsRepository]; the
 * caller passes `snoozeUntilMillis` into [detect] so the detector stays a
 * pure function.
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
     * @param categories every persisted Category. Both [Category.appPackageName]
     *   and APP-type rules referenced via [Category.ruleIds] are used for
     *   coverage; keyword-only Categories still leave apps uncovered so a
     *   dedicated app-level Category can be proposed.
     * @param rules every persisted rule. Only [RuleTypeUi.APP] rules whose id
     *   appears in some Category's `ruleIds` contribute to coverage. Orphan
     *   APP rules (no owning Category) do **not** count — the classifier falls
     *   them through to SILENT, so the user's intent isn't "this package is
     *   classified".
     * @param nowMillis wall-clock "now" in epoch millis. Injected for tests.
     * @param snoozeUntilMillis epoch millis until which the prompt is
     *   suppressed. Zero (default) means "not snoozed".
     */
    fun detect(
        notifications: List<NotificationUiModel>,
        categories: List<Category>,
        rules: List<RuleUiModel> = emptyList(),
        nowMillis: Long,
        snoozeUntilMillis: Long,
    ): UncategorizedAppsDetection {
        if (nowMillis < snoozeUntilMillis) return UncategorizedAppsDetection.None

        val pinnedPackages = categories
            .mapNotNull { it.appPackageName?.trim()?.lowercase()?.takeIf(String::isNotEmpty) }

        val referencedRuleIds = categories.flatMap { it.ruleIds }.toSet()
        val appRulePackages = rules
            .asSequence()
            .filter { it.id in referencedRuleIds && it.type == RuleTypeUi.APP }
            .map { it.matchValue.trim().lowercase() }
            .filter(String::isNotEmpty)
            .toList()

        val coveredPackages: Set<String> = (pinnedPackages + appRulePackages).toSet()

        // Keep the most-recent notification per package so the sample labels
        // can be sorted newest-first without a second pass.
        val cutoff = nowMillis - SEVEN_DAYS_MILLIS
        val newestByPackage = linkedMapOf<String, NotificationUiModel>()
        for (n in notifications) {
            if (n.postedAtMillis < cutoff) continue
            val rawPkg = n.packageName
            if (rawPkg.isBlank()) continue
            val normalized = rawPkg.trim().lowercase()
            if (normalized in coveredPackages) continue
            val existing = newestByPackage[rawPkg]
            if (existing == null || n.postedAtMillis > existing.postedAtMillis) {
                newestByPackage[rawPkg] = n
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
