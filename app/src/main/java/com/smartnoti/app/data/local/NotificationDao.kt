package com.smartnoti.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface NotificationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(notification: NotificationEntity)

    @Query("SELECT * FROM notifications ORDER BY postedAtMillis DESC")
    fun observeAll(): Flow<List<NotificationEntity>>

    @Query(
        """
        SELECT packageName, appName, MAX(postedAtMillis) AS lastPostedAtMillis, COUNT(*) AS notificationCount
        FROM notifications
        GROUP BY packageName, appName
        ORDER BY lastPostedAtMillis DESC
        """
    )
    fun observeCapturedApps(): Flow<List<CapturedAppOption>>

    @Query("SELECT COUNT(*) FROM notifications WHERE packageName = :packageName AND contentSignature = :contentSignature AND postedAtMillis >= :sinceMillis")
    suspend fun countRecentDuplicates(
        packageName: String,
        contentSignature: String,
        sinceMillis: Long,
    ): Int

    @Query(
        "SELECT COUNT(*) FROM notifications WHERE packageName = :packageName AND contentSignature = :contentSignature AND postedAtMillis = :postedAtMillis"
    )
    suspend fun countByContentSignature(
        packageName: String,
        contentSignature: String,
        postedAtMillis: Long,
    ): Int

    @Query(
        """
        DELETE FROM notifications
        WHERE id LIKE '%:ranker_group'
          AND TRIM(title) = ''
          AND TRIM(body) = ''
        """
    )
    suspend fun deleteLegacyBlankGroupSummaryRows(): Int

    @Query("DELETE FROM notifications WHERE status = 'SILENT'")
    suspend fun deleteAllSilent(): Int

    @Query("DELETE FROM notifications WHERE status = 'SILENT' AND packageName = :packageName")
    suspend fun deleteSilentByPackage(packageName: String): Int

    @Query("DELETE FROM notifications WHERE status = 'DIGEST' AND packageName = :packageName")
    suspend fun deleteDigestByPackage(packageName: String): Int

    @Query("DELETE FROM notifications WHERE status = 'IGNORE'")
    suspend fun deleteAllIgnored(): Int

    @Query("DELETE FROM notifications WHERE status = 'IGNORE' AND id IN (:ids)")
    suspend fun deleteIgnoredByIds(ids: Collection<String>): Int

    @Query("SELECT sourceEntryKey FROM notifications WHERE id = :id LIMIT 1")
    suspend fun sourceEntryKeyForId(id: String): String?

    /**
     * Plan
     * `docs/plans/2026-04-27-fix-issue-503-app-label-resolver-fallback-chain.md`
     * Task 4. DISTINCT packageNames whose persisted `appName` equals the
     * packageName itself (the Issue #503 raw-fallback regression).
     * `MigrateAppLabelRunner` consumes this list, re-runs each through
     * [com.smartnoti.app.notification.AppLabelResolver], and batch-updates
     * the rows whose new resolution differs.
     */
    @Query("SELECT DISTINCT packageName FROM notifications WHERE appName = packageName")
    suspend fun selectPackagesNeedingLabelResolution(): List<String>

    /**
     * Plan
     * `docs/plans/2026-04-27-fix-issue-503-app-label-resolver-fallback-chain.md`
     * Task 4. Bulk-update every row of the given packageName whose
     * `appName == packageName` to the freshly resolved label. The
     * `appName = packageName` predicate inside the WHERE clause is the
     * idempotent guard that prevents stomping a user-edited label even if
     * the runner is re-invoked outside its flag gate.
     */
    @Query("UPDATE notifications SET appName = :label WHERE packageName = :packageName AND appName = packageName")
    suspend fun updateAppLabel(packageName: String, label: String): Int

    /**
     * Plan
     * `docs/plans/2026-04-28-fix-issue-511-cancel-source-on-replacement.md`
     * Task 4. Returns rows whose SmartNoti replacement was posted but whose
     * source tray entry MIGHT still be live —
     * [com.smartnoti.app.data.local.MigrateOrphanedSourceCancellationRunner]
     * pairs each `sourceEntryKey` with
     * [com.smartnoti.app.notification.ActiveSourceNotificationInspector]
     * to confirm the entry is still in the tray before cancelling.
     *
     * Filters rows where:
     *  - `replacementNotificationIssued = 1` (only rows SmartNoti owns the
     *    replacement for; PRIORITY-style "let it through unmodified" rows
     *    have `replacementNotificationIssued = 0` and must NEVER be
     *    cancelled — that would silently delete a notification the user
     *    is actively expecting).
     *  - `sourceEntryKey IS NOT NULL` (legacy rows saved before the
     *    `sourceEntryKey` column existed cannot be cancelled — there is
     *    no key to pass to `cancelNotification`).
     */
    @Query(
        """
        SELECT sourceEntryKey FROM notifications
        WHERE replacementNotificationIssued = 1
          AND sourceEntryKey IS NOT NULL
        """
    )
    suspend fun selectOrphanedSourceCancellationKeys(): List<String>

    /**
     * Plan
     * `docs/plans/2026-04-28-fix-issue-525-high-volume-app-suggest-suppression.md`
     * Task 2. Project (packageName, appName, COUNT(*)) for every package
     * whose 7-day rolling row total meets [threshold] (= avgPerDay × windowDays
     * passed by [HighVolumeAppDetector]).
     *
     * PRIORITY-only packages are excluded by the inner subquery: a package
     * whose every in-window row has `status = 'PRIORITY'` is the user's
     * declared "wanted" signal and must never surface as a noise suggestion.
     * Mixed-status packages (PRIORITY + DIGEST + SILENT) are intentionally
     * INCLUDED in the count — every row is a user-perceived noise event.
     * The plan's Open Question §6 calls this v1 decision out for refinement.
     *
     * `appName` chosen via `MAX(appName)`: identical packageNames always
     * carry identical appName labels (the app capture side enforces this
     * via [AppLabelResolver]) so any aggregate works; `MAX` is the cheapest
     * one Room recognizes on a TEXT column.
     *
     * Sort: `COUNT(*) DESC` so the noisiest package surfaces first, with
     * `packageName ASC` as the tiebreak so two equal-count packages have a
     * deterministic order (the test pins this).
     */
    @Query(
        """
        SELECT packageName,
               MAX(appName) AS appName,
               COUNT(*) AS count
        FROM notifications
        WHERE postedAtMillis >= :sinceMillis
        GROUP BY packageName
        HAVING COUNT(*) >= :threshold
           AND SUM(CASE WHEN status = 'PRIORITY' THEN 0 ELSE 1 END) > 0
        ORDER BY COUNT(*) DESC, packageName ASC
        """
    )
    suspend fun countHighVolumeAppsSince(
        sinceMillis: Long,
        threshold: Int,
    ): List<HighVolumeAppRow>
}

/**
 * Plan
 * `docs/plans/2026-04-28-fix-issue-525-high-volume-app-suggest-suppression.md`
 * Task 2. Internal projection POJO for [NotificationDao.countHighVolumeAppsSince]
 * — Room maps the SQL columns by name. Lifted to top-level (instead of
 * `internal`) so Room's annotation processor finds it without companion-class
 * juggling; consumers should use [HighVolumeAppCandidate] which wraps this row
 * and adds `avgPerDay`.
 */
data class HighVolumeAppRow(
    val packageName: String,
    val appName: String,
    val count: Int,
)
