package com.smartnoti.app

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.lifecycleScope
import com.smartnoti.app.data.categories.MigrateRulesToCategoriesRunner
import com.smartnoti.app.data.settings.SettingsRepository
import com.smartnoti.app.navigation.AppNavHost
import com.smartnoti.app.navigation.ReplacementNotificationEntry
import com.smartnoti.app.navigation.ReplacementNotificationEntryRoutes
import com.smartnoti.app.navigation.Routes
import com.smartnoti.app.notification.SilentHiddenSummaryNotifier
import com.smartnoti.app.notification.SmartNotiNotifier
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
        setContent {
            SmartNotiTheme {
                AppNavHost(
                    pendingNotificationEntry = pendingNotificationEntry.value,
                    onPendingNotificationConsumed = { pendingNotificationEntry.value = null },
                    pendingDeepLinkRoute = pendingDeepLinkRoute.value,
                    onPendingDeepLinkRouteConsumed = { pendingDeepLinkRoute.value = null },
                )
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
