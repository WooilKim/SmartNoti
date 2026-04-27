package com.smartnoti.app.data.local

import android.content.Context
import com.smartnoti.app.data.settings.SettingsRepository

/**
 * Cold-start one-shot migration that recovers the **current Railway
 * cohort** for plan
 * `docs/plans/2026-04-28-fix-issue-511-cancel-source-on-replacement.md`
 * Task 4 — users whose DB rows already record
 * `replacementNotificationIssued = 1` (after the Task 3 cancel-after-post
 * routing fix) AND whose source notification is still live in the system
 * tray. Without this runner, those trapped duplicates linger forever
 * because Task 3's routing change only fixes captures going forward.
 *
 * Mirrors [MigrateAppLabelRunner]:
 *
 *   1. Gate on a DataStore flag
 *      ([SettingsRepository.isMigrateOrphanedSourceCancellationV1Applied]).
 *      If true, return [Result.AlreadyMigrated] immediately so cold starts
 *      after the first short-circuit cheaply.
 *   2. Otherwise, ask the [ActiveSourceNotificationInspector] whether the
 *      listener service is currently bound. If not, return
 *      [Result.DeferredListenerNotBound] WITHOUT flipping the flag
 *      (Risks R4 — the listener-only `cancelNotification(key)` API
 *      requires a live service, so a future cold start retries).
 *   3. Pull the orphan cohort from the DAO
 *      ([NotificationDao.selectOrphanedSourceCancellationKeys]). For each
 *      key, ask the inspector whether the source is still active in the
 *      tray; if yes, invoke [SourceCancellationGateway.cancel]. Skip rows
 *      whose source has already been cleared — no harm, just avoids a
 *      misleading "cancelled N orphan sources" log.
 *   4. Flip the flag at the end so the scan is skipped on every cold
 *      start after the first.
 *
 * Failure handling: if the DAO throws or the gateway throws, the flag
 * stays unflipped so a subsequent cold start retries — matching the
 * resilience of [MigrateAppLabelRunner].
 */
class MigrateOrphanedSourceCancellationRunner(
    private val notificationDao: NotificationDao,
    private val inspector: ActiveSourceNotificationInspector,
    private val gateway: SourceCancellationGateway,
    private val settingsRepository: SettingsRepository,
) {

    suspend fun run(): Result {
        if (settingsRepository.isMigrateOrphanedSourceCancellationV1Applied()) {
            return Result.AlreadyMigrated
        }

        if (!inspector.isListenerBound()) {
            // Risks R4: the listener-only `cancelNotification(key)` API
            // requires a live NotificationListenerService instance. The
            // flag stays unflipped so a future cold start (when the
            // listener has bound) retries.
            return Result.DeferredListenerNotBound
        }

        val orphanedKeys = notificationDao.selectOrphanedSourceCancellationKeys()
        var cancelledCount = 0
        var skippedAlreadyClearedCount = 0
        for (key in orphanedKeys) {
            if (inspector.isSourceKeyActive(key)) {
                gateway.cancel(key)
                cancelledCount += 1
            } else {
                // Source already cleared from the tray (user dismissed,
                // OS cleaned it up, etc.). Skip the cancel to avoid a
                // misleading "cancelled N orphan sources" log metric.
                skippedAlreadyClearedCount += 1
            }
        }

        settingsRepository.setMigrateOrphanedSourceCancellationV1Applied(true)
        return Result.Migrated(
            cancelledCount = cancelledCount,
            skippedAlreadyClearedCount = skippedAlreadyClearedCount,
            scannedRowCount = orphanedKeys.size,
        )
    }

    sealed class Result {
        object AlreadyMigrated : Result()
        object DeferredListenerNotBound : Result()
        data class Migrated(
            val cancelledCount: Int,
            val skippedAlreadyClearedCount: Int,
            val scannedRowCount: Int,
        ) : Result()
    }

    companion object {
        fun create(context: Context): MigrateOrphanedSourceCancellationRunner {
            val appContext = context.applicationContext
            val repository = NotificationRepository.getInstance(appContext)
            return MigrateOrphanedSourceCancellationRunner(
                notificationDao = repository.dao,
                inspector = ListenerActiveSourceNotificationInspector(),
                gateway = ListenerSourceCancellationGateway(),
                settingsRepository = SettingsRepository.getInstance(appContext),
            )
        }
    }
}
