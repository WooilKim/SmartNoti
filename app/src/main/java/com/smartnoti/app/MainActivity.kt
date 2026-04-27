package com.smartnoti.app

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.lifecycleScope
import com.smartnoti.app.data.categories.MigratePromoCategoryActionRunner
import com.smartnoti.app.data.categories.MigrateRulesToCategoriesRunner
import com.smartnoti.app.data.local.MigrateAppLabelRunner
import com.smartnoti.app.data.local.MigrateOrphanedSourceCancellationRunner
import com.smartnoti.app.data.settings.SettingsRepository
import com.smartnoti.app.navigation.AppNavHost
import com.smartnoti.app.navigation.ReplacementNotificationEntry
import com.smartnoti.app.navigation.ReplacementNotificationEntryRoutes
import com.smartnoti.app.navigation.Routes
import com.smartnoti.app.notification.SilentHiddenSummaryNotifier
import com.smartnoti.app.notification.SmartNotiNotifier
import com.smartnoti.app.ui.screens.categories.components.LocalAppLabelLookup
import com.smartnoti.app.ui.screens.categories.components.PackageManagerAppLabelLookup
import com.smartnoti.app.ui.theme.SmartNotiTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val pendingNotificationEntry = mutableStateOf<ReplacementNotificationEntry?>(null)
    private val pendingDeepLinkRoute = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingNotificationEntry.value = intent?.extractReplacementNotificationEntry()
        pendingDeepLinkRoute.value = intent?.extractDeepLinkRoute()
        runSettingsMigrations()
        runRulesToCategoriesMigration()
        runPromoCategoryActionMigration()
        runAppLabelResolutionMigration()
        runOrphanedSourceCancellationMigration()
        setContent {
            SmartNotiTheme {
                // Plan `2026-04-25-category-chip-app-label-lookup.md` Task 3:
                // bind the production AppLabelLookup at the setContent
                // boundary so every Categories surface (chips today, more
                // surfaces in follow-up plans) reads `LocalAppLabelLookup
                // .current` without re-instantiating PackageManager
                // wrappers per recomposition.
                val packageManager = LocalContext.current.packageManager
                val appLabelLookup = remember(packageManager) {
                    PackageManagerAppLabelLookup(packageManager)
                }
                CompositionLocalProvider(LocalAppLabelLookup provides appLabelLookup) {
                    AppNavHost(
                        pendingNotificationEntry = pendingNotificationEntry.value,
                        onPendingNotificationConsumed = { pendingNotificationEntry.value = null },
                        pendingDeepLinkRoute = pendingDeepLinkRoute.value,
                        onPendingDeepLinkRouteConsumed = { pendingDeepLinkRoute.value = null },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingNotificationEntry.value = intent.extractReplacementNotificationEntry()
        pendingDeepLinkRoute.value = intent.extractDeepLinkRoute()
    }

    /**
     * First-launch-post-upgrade hook for plan
     * `docs/plans/2026-04-22-categories-split-rules-actions.md` Phase P1 Task 3.
     *
     * Fires every cold start but short-circuits cheaply once
     * `SettingsRepository.rulesToCategoriesMigrated` is true. Runs on the
     * Activity's lifecycle scope so it cannot leak past the process and uses
     * the shared repository singletons so the Category list observable by
     * the rest of the app sees the migrated state within one DataStore
     * edit cycle.
     */
    /**
     * Plan `2026-04-24-duplicate-notifications-suppress-defaults-ac.md` Task 4.
     * One-shot migration that flips `suppressSourceForDigestAndSilent` to true
     * on first launch (fresh or upgrade). Idempotent — guarded by the
     * `suppress_source_migration_v1_applied` DataStore key inside
     * `SettingsRepository.applyPendingMigrations`. The listener service also
     * runs this on `onListenerConnected` so the migration completes before any
     * post-upgrade notification is processed even if the user reaches the
     * listener path before opening the activity.
     */
    private fun runSettingsMigrations() {
        val repository = SettingsRepository.getInstance(applicationContext)
        lifecycleScope.launch {
            runCatching { repository.applyPendingMigrations() }
                .onFailure { error ->
                    Log.e(TAG, "Settings migrations failed", error)
                }
        }
    }

    private fun runRulesToCategoriesMigration() {
        val runner = MigrateRulesToCategoriesRunner.create(applicationContext)
        lifecycleScope.launch {
            runCatching { runner.run() }
                .onFailure { error ->
                    Log.e(TAG, "Rules -> Categories migration failed", error)
                }
        }
    }

    /**
     * Plan `docs/plans/2026-04-27-fix-issue-478-promo-prefix-precedence-and-bundle-by-default.md`
     * Task 3 (Bug B2 + M1 migration). One-shot pass that bumps the seeded
     * onboarding PROMO Category from `action=SILENT` to `action=DIGEST` for
     * existing installs so source-tray auto-expansion fires for KCC-marked
     * `(광고)` notifications. User-modified rows (`userModifiedAction=true`)
     * are preserved. Idempotent — gated by
     * `SettingsRepository.isPromoQuietingActionMigrationV3Applied()`.
     */
    private fun runPromoCategoryActionMigration() {
        val runner = MigratePromoCategoryActionRunner.create(applicationContext)
        lifecycleScope.launch {
            runCatching { runner.run() }
                .onFailure { error ->
                    Log.e(TAG, "PROMO Category action migration failed", error)
                }
        }
    }

    /**
     * Plan
     * `docs/plans/2026-04-27-fix-issue-503-app-label-resolver-fallback-chain.md`
     * Task 4. Cold-start one-shot pass that re-resolves the 16-package
     * `appName == packageName` regression rows via the new
     * [com.smartnoti.app.notification.AppLabelResolver] (explicit fallback
     * chain). Idempotent — gated by
     * `SettingsRepository.isAppLabelResolutionMigrationV1Applied()`.
     */
    private fun runAppLabelResolutionMigration() {
        val runner = MigrateAppLabelRunner.create(applicationContext)
        lifecycleScope.launch {
            runCatching { runner.run() }
                .onFailure { error ->
                    Log.e(TAG, "App label resolution migration failed", error)
                }
        }
    }

    /**
     * Plan
     * `docs/plans/2026-04-28-fix-issue-511-cancel-source-on-replacement.md`
     * Task 4. Cold-start one-shot pass that recovers the existing Railway
     * cohort — rows whose `replacementNotificationIssued = 1` AND whose
     * source notification is still live in the system tray. Idempotent —
     * gated by
     * `SettingsRepository.isMigrateOrphanedSourceCancellationV1Applied()`.
     * Risks R4: when the listener service is not yet bound the runner
     * defers without flipping the flag, so the next cold start retries.
     */
    private fun runOrphanedSourceCancellationMigration() {
        val runner = MigrateOrphanedSourceCancellationRunner.create(applicationContext)
        lifecycleScope.launch {
            runCatching { runner.run() }
                .onFailure { error ->
                    Log.e(TAG, "Orphaned source cancellation migration failed", error)
                }
        }
    }

    private fun Intent.extractReplacementNotificationEntry(): ReplacementNotificationEntry? {
        val notificationId = getStringExtra(SmartNotiNotifier.EXTRA_NOTIFICATION_ID)
            ?.takeIf { it.isNotBlank() }
            ?: return null
        val parentRoute = ReplacementNotificationEntryRoutes.sanitize(
            getStringExtra(SmartNotiNotifier.EXTRA_PARENT_ROUTE)
        )
        return ReplacementNotificationEntry(
            notificationId = notificationId,
            parentRoute = parentRoute,
        )
    }

    private fun Intent.extractDeepLinkRoute(): String? {
        val raw = getStringExtra(SmartNotiNotifier.EXTRA_DEEP_LINK_ROUTE)?.takeIf { it.isNotBlank() }
            ?: return null
        // `raw` is a short token (only "hidden" today) — we hydrate it into the real
        // `Routes.Hidden.create(...)` URL so the query-param-aware NavHost composable
        // registration picks up sender/packageName from the tray group-summary extras.
        return when (raw) {
            SilentHiddenSummaryNotifier.ROUTE_HIDDEN -> Routes.Hidden.create(
                sender = getStringExtra(SilentHiddenSummaryNotifier.EXTRA_DEEP_LINK_SENDER),
                packageName = getStringExtra(SilentHiddenSummaryNotifier.EXTRA_DEEP_LINK_PACKAGE_NAME),
            )
            else -> null
        }
    }

    private companion object {
        private const val TAG = "MainActivity"
    }
}
