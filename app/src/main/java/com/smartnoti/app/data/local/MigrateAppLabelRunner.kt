package com.smartnoti.app.data.local

import android.content.Context
import com.smartnoti.app.data.settings.SettingsRepository
import com.smartnoti.app.notification.AndroidPackageLabelSource
import com.smartnoti.app.notification.AppLabelResolver

/**
 * Cold-start one-shot migration that re-resolves rows whose `appName ==
 * packageName` (the 16-package regression at the heart of Issue #503), plan
 * `docs/plans/2026-04-27-fix-issue-503-app-label-resolver-fallback-chain.md`
 * Task 4.
 *
 * Mirrors [com.smartnoti.app.data.categories.MigratePromoCategoryActionRunner]:
 *
 *   1. Gate on a DataStore flag
 *      ([SettingsRepository.isAppLabelResolutionMigrationV1Applied]). If
 *      true, return [Result.AlreadyMigrated] immediately so cold starts
 *      after the first short-circuit cheaply.
 *   2. Otherwise, ask the DAO for the DISTINCT packageNames whose rows
 *      have `appName == packageName`. Run each through the new
 *      [AppLabelResolver] (which honours the explicit fallback chain) and
 *      batch-`UPDATE` rows where the resolved label differs from the
 *      packageName. Rows where the resolver still returns `packageName`
 *      (genuine OEM background-service no-label case) are intentionally
 *      left as-is so a future launch can retry once the OS label
 *      recovers.
 *   3. Flip the flag at the end so the scan is skipped on every cold
 *      start after the first.
 *
 * Failure handling: if any step throws (DataStore I/O glitch, DAO
 * exception), the flag stays unflipped so a subsequent cold start retries —
 * matching the resilience of `runPromoCategoryActionMigration` in
 * `MainActivity`.
 */
class MigrateAppLabelRunner(
    private val notificationDao: NotificationDao,
    private val appLabelResolver: AppLabelResolver,
    private val settingsRepository: SettingsRepository,
) {

    suspend fun run(): Result {
        if (settingsRepository.isAppLabelResolutionMigrationV1Applied()) {
            return Result.AlreadyMigrated
        }

        val packages = notificationDao.selectPackagesNeedingLabelResolution()
        var bumpedCount = 0
        for (packageName in packages) {
            val resolved = appLabelResolver.resolve(packageName)
            if (resolved.isNotBlank() && resolved != packageName) {
                val updated = notificationDao.updateAppLabel(packageName, resolved)
                if (updated > 0) bumpedCount += updated
            }
            // else: resolver still echoes packageName (or returned blank).
            // Leave the row alone so the next cold start can retry once the
            // OS label recovers. The flag still flips below so we don't
            // re-scan every cold start — future capture re-resolution will
            // fix the row in-flight when the label becomes available.
        }
        settingsRepository.setAppLabelResolutionMigrationV1Applied(true)
        return Result.Migrated(bumpedRowCount = bumpedCount, scannedPackageCount = packages.size)
    }

    sealed class Result {
        object AlreadyMigrated : Result()
        data class Migrated(
            val bumpedRowCount: Int,
            val scannedPackageCount: Int,
        ) : Result()
    }

    companion object {
        fun create(context: Context): MigrateAppLabelRunner {
            val appContext = context.applicationContext
            val repository = NotificationRepository.getInstance(appContext)
            return MigrateAppLabelRunner(
                notificationDao = repository.dao,
                appLabelResolver = AppLabelResolver(
                    AndroidPackageLabelSource(appContext.packageManager),
                ),
                settingsRepository = SettingsRepository.getInstance(appContext),
            )
        }
    }
}
