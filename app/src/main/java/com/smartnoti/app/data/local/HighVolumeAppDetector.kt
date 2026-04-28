package com.smartnoti.app.data.local

/**
 * Plan
 * `docs/plans/2026-04-28-fix-issue-525-high-volume-app-suggest-suppression.md`
 * Task 2.
 *
 * Reads the 7-day rolling notification volume per source app via
 * [NotificationDao.countHighVolumeAppsSince] and returns the candidates whose
 * average daily count meets the threshold, after filtering out:
 *  - packages already in `currentSuppressedSourceApps` (the user already
 *    bundled them — proposing again would be noise).
 *  - packages in `currentSuggestedSuppressionDismissed` (the user explicitly
 *    said "ignore" — a sticky permanent dismiss surface introduced by Task 5).
 *  - packages in `currentSuppressedSourceAppsExcluded` (the sticky-exclude
 *    set from #524 / digest-suppression — a single user intent, "don't bundle
 *    this app", spans both auto-expansion and suggestions).
 *
 * PRIORITY-only packages (every in-window row is `status = 'PRIORITY'`) are
 * filtered inside the SQL — see [NotificationDao.countHighVolumeAppsSince].
 *
 * Threshold semantics (test-pinned by [HighVolumeAppDetectorTest]): the
 * `avgPerDayThreshold` passed to [detect] is forwarded as the SQL `HAVING
 * COUNT(*) >= :threshold` floor — the test fixtures seed `count = 24` rows
 * for 네이버 and expect them to surface when `avgPerDayThreshold = 10` (i.e.
 * the threshold gates the in-window total directly, not a per-day rate
 * multiplied by `windowDays`). [avgPerDay] on the resulting candidate is
 * then `count.toDouble() / windowDays` so the card body can render the
 * intuitive "평균 N건/일" copy.
 */
class HighVolumeAppDetector(
    private val dao: NotificationDao,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    /**
     * @param avgPerDayThreshold minimum daily-average row count required to
     *   surface as a candidate. The default `10` matches the plan's
     *   R3CY2058DLJ snapshot (네이버 24/일, 카카오 20/일, … all surface).
     * @param windowDays size of the rolling window in days. The default `7`
     *   matches the plan body's "7-day rolling avg".
     * @param currentSuppressedSourceApps packages already configured for
     *   bundling — never proposed again.
     * @param currentSuggestedSuppressionDismissed packages the user has
     *   permanently dismissed via `[무시]` on a previous card — never proposed
     *   again.
     * @param currentSuppressedSourceAppsExcluded packages the user has
     *   sticky-excluded from auto-expansion — proposal would conflict with
     *   the user's stated intent, so never proposed.
     */
    suspend fun detect(
        avgPerDayThreshold: Int = 10,
        windowDays: Int = 7,
        currentSuppressedSourceApps: Set<String> = emptySet(),
        currentSuggestedSuppressionDismissed: Set<String> = emptySet(),
        currentSuppressedSourceAppsExcluded: Set<String> = emptySet(),
    ): List<HighVolumeAppCandidate> {
        val safeWindow = windowDays.coerceAtLeast(1)
        val sinceMillis = clock() - safeWindow.toLong() * 24L * 60L * 60L * 1000L
        val safeThreshold = avgPerDayThreshold.coerceAtLeast(0)

        val rows = dao.countHighVolumeAppsSince(
            sinceMillis = sinceMillis,
            threshold = safeThreshold,
        )

        // SQL has already filtered PRIORITY-only packages and applied the
        // total-window threshold + sort. Kotlin filter applies the three
        // user-state exclusion sets — the DAO does not see them.
        return rows.asSequence()
            .filter { it.packageName !in currentSuppressedSourceApps }
            .filter { it.packageName !in currentSuggestedSuppressionDismissed }
            .filter { it.packageName !in currentSuppressedSourceAppsExcluded }
            .map { row ->
                HighVolumeAppCandidate(
                    packageName = row.packageName,
                    appName = row.appName,
                    count = row.count,
                    avgPerDay = row.count.toDouble() / safeWindow.toDouble(),
                )
            }
            .toList()
    }
}
