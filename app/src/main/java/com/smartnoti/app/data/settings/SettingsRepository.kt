package com.smartnoti.app.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.smartnoti.app.domain.model.InboxSortMode
import com.smartnoti.app.domain.model.NotificationContext
import com.smartnoti.app.domain.usecase.QuietHoursPolicy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.Calendar

private val Context.dataStore by preferencesDataStore(name = "smartnoti_settings")

class SettingsRepository private constructor(
    private val context: Context,
) {
    // Plan `2026-04-27-refactor-settings-repository-facade-split.md`: every
    // sibling shares the single DataStore instance owned by the façade so
    // they all hit the same on-disk file (no per-sibling lazy init path).
    private val dataStore = context.dataStore
    private val quietHours = SettingsQuietHoursRepository(dataStore)
    private val duplicate = SettingsDuplicateRepository(dataStore)
    private val deliveryProfile = SettingsDeliveryProfileRepository(dataStore)
    private val suppression = SettingsSuppressionRepository(dataStore)

    fun observeSettings(): Flow<SmartNotiSettings> {
        val defaults = SmartNotiSettings()
        return dataStore.data.map { prefs ->
            SmartNotiSettings(
                quietHoursEnabled = prefs[SettingsQuietHoursRepository.Keys.ENABLED] ?: defaults.quietHoursEnabled,
                quietHoursStartHour = prefs[SettingsQuietHoursRepository.Keys.START_HOUR] ?: defaults.quietHoursStartHour,
                quietHoursEndHour = prefs[SettingsQuietHoursRepository.Keys.END_HOUR] ?: defaults.quietHoursEndHour,
                digestHours = defaults.digestHours,
                priorityAlertLevel = prefs[SettingsDeliveryProfileRepository.Keys.PRIORITY_ALERT_LEVEL] ?: defaults.priorityAlertLevel,
                priorityVibrationMode = prefs[SettingsDeliveryProfileRepository.Keys.PRIORITY_VIBRATION_MODE] ?: defaults.priorityVibrationMode,
                priorityHeadsUpEnabled = prefs[SettingsDeliveryProfileRepository.Keys.PRIORITY_HEADS_UP_ENABLED] ?: defaults.priorityHeadsUpEnabled,
                priorityLockScreenVisibility = prefs[SettingsDeliveryProfileRepository.Keys.PRIORITY_LOCK_SCREEN_VISIBILITY] ?: defaults.priorityLockScreenVisibility,
                digestAlertLevel = prefs[SettingsDeliveryProfileRepository.Keys.DIGEST_ALERT_LEVEL] ?: defaults.digestAlertLevel,
                digestVibrationMode = prefs[SettingsDeliveryProfileRepository.Keys.DIGEST_VIBRATION_MODE] ?: defaults.digestVibrationMode,
                digestHeadsUpEnabled = prefs[SettingsDeliveryProfileRepository.Keys.DIGEST_HEADS_UP_ENABLED] ?: defaults.digestHeadsUpEnabled,
                digestLockScreenVisibility = prefs[SettingsDeliveryProfileRepository.Keys.DIGEST_LOCK_SCREEN_VISIBILITY] ?: defaults.digestLockScreenVisibility,
                silentAlertLevel = prefs[SettingsDeliveryProfileRepository.Keys.SILENT_ALERT_LEVEL] ?: defaults.silentAlertLevel,
                silentVibrationMode = prefs[SettingsDeliveryProfileRepository.Keys.SILENT_VIBRATION_MODE] ?: defaults.silentVibrationMode,
                silentHeadsUpEnabled = prefs[SettingsDeliveryProfileRepository.Keys.SILENT_HEADS_UP_ENABLED] ?: defaults.silentHeadsUpEnabled,
                silentLockScreenVisibility = prefs[SettingsDeliveryProfileRepository.Keys.SILENT_LOCK_SCREEN_VISIBILITY] ?: defaults.silentLockScreenVisibility,
                suppressSourceForDigestAndSilent = prefs[SettingsSuppressionRepository.Keys.SUPPRESS_SOURCE_FOR_DIGEST_AND_SILENT] ?: defaults.suppressSourceForDigestAndSilent,
                suppressedSourceApps = prefs[SettingsSuppressionRepository.Keys.SUPPRESSED_SOURCE_APPS] ?: defaults.suppressedSourceApps,
                suppressedSourceAppsExcluded = prefs[SettingsSuppressionRepository.Keys.SUPPRESSED_SOURCE_APPS_EXCLUDED] ?: defaults.suppressedSourceAppsExcluded,
                hidePersistentNotifications = prefs[SettingsSuppressionRepository.Keys.HIDE_PERSISTENT_NOTIFICATIONS] ?: defaults.hidePersistentNotifications,
                hidePersistentSourceNotifications = prefs[SettingsSuppressionRepository.Keys.HIDE_PERSISTENT_SOURCE_NOTIFICATIONS] ?: defaults.hidePersistentSourceNotifications,
                protectCriticalPersistentNotifications = prefs[SettingsSuppressionRepository.Keys.PROTECT_CRITICAL_PERSISTENT_NOTIFICATIONS] ?: defaults.protectCriticalPersistentNotifications,
                showIgnoredArchive = prefs[SettingsSuppressionRepository.Keys.SHOW_IGNORED_ARCHIVE] ?: defaults.showIgnoredArchive,
                duplicateDigestThreshold = prefs[SettingsDuplicateRepository.Keys.DIGEST_THRESHOLD] ?: defaults.duplicateDigestThreshold,
                duplicateWindowMinutes = prefs[SettingsDuplicateRepository.Keys.WINDOW_MINUTES] ?: defaults.duplicateWindowMinutes,
                quietHoursPackages = prefs[SettingsQuietHoursRepository.Keys.PACKAGES] ?: defaults.quietHoursPackages,
                replacementAutoDismissEnabled = prefs[SettingsDeliveryProfileRepository.Keys.REPLACEMENT_AUTO_DISMISS_ENABLED]
                    ?: defaults.replacementAutoDismissEnabled,
                replacementAutoDismissMinutes = prefs[SettingsDeliveryProfileRepository.Keys.REPLACEMENT_AUTO_DISMISS_MINUTES]
                    ?: defaults.replacementAutoDismissMinutes,
                inboxSortMode = prefs[SettingsDeliveryProfileRepository.Keys.INBOX_SORT_MODE] ?: defaults.inboxSortMode,
            )
        }
    }

    suspend fun currentNotificationContext(duplicateCountInWindow: Int = 0): NotificationContext {
        val settings = observeSettings().first()
        val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return NotificationContext(
            quietHoursEnabled = settings.quietHoursEnabled,
            quietHoursPolicy = QuietHoursPolicy(
                startHour = settings.quietHoursStartHour,
                endHour = settings.quietHoursEndHour,
            ),
            currentHourOfDay = currentHour,
            duplicateCountInWindow = duplicateCountInWindow,
        )
    }

    suspend fun setQuietHoursEnabled(enabled: Boolean) = quietHours.setEnabled(enabled)

    suspend fun setQuietHoursStartHour(hour: Int) = quietHours.setStartHour(hour)

    suspend fun setQuietHoursEndHour(hour: Int) = quietHours.setEndHour(hour)

    suspend fun setDuplicateDigestThreshold(threshold: Int) = duplicate.setDigestThreshold(threshold)

    suspend fun setDuplicateWindowMinutes(minutes: Int) = duplicate.setWindowMinutes(minutes)

    suspend fun setQuietHoursPackages(packageNames: Set<String>) = quietHours.setPackages(packageNames)

    suspend fun addQuietHoursPackage(packageName: String) = quietHours.addPackage(packageName)

    suspend fun removeQuietHoursPackage(packageName: String) = quietHours.removePackage(packageName)

    suspend fun setInboxSortMode(mode: InboxSortMode) = deliveryProfile.setInboxSortMode(mode)

    suspend fun setSuppressSourceForDigestAndSilent(enabled: Boolean) = suppression.setSuppressSourceForDigestAndSilent(enabled)

    /**
     * One-shot data migrations. Safe to call multiple times — each migration
     * is gated by its own boolean key so subsequent invocations short-circuit.
     *
     * Plan `2026-04-24-duplicate-notifications-suppress-defaults-ac.md` Task 4:
     * the v1 migration writes `suppressSourceForDigestAndSilent = true` once
     * for every install (fresh or upgraded). Fresh installs already get the
     * default-true from `SmartNotiSettings`, but writing it explicitly makes
     * the on-disk state match what `observeSettings()` will report on the
     * first read and prevents flicker if a future change splits read/default
     * paths. Upgraded users who had previously toggled the flag OFF have
     * their value overwritten — accepted trade-off documented in the plan
     * (the old default created the duplicate-notification symptom).
     *
     * Call early (e.g. from `MainActivity.onCreate` or
     * `SmartNotiNotificationListenerService.onListenerConnected`) so the
     * migration completes before the listener processes its first
     * notification post-upgrade.
     */
    suspend fun applyPendingMigrations() {
        context.dataStore.edit { prefs ->
            if (prefs[SUPPRESS_SOURCE_MIGRATION_V1_APPLIED] != true) {
                prefs[SettingsSuppressionRepository.Keys.SUPPRESS_SOURCE_FOR_DIGEST_AND_SILENT] = true
                prefs[SUPPRESS_SOURCE_MIGRATION_V1_APPLIED] = true
            }
            // Plan `2026-04-27-tray-replacement-auto-dismiss-timeout.md` Task 3.
            // v2 one-shot migration: stamp the auto-dismiss defaults (ON / 30
            // min) once for every install — fresh installs already get these
            // from `SmartNotiSettings` defaults, but materializing them on disk
            // makes `observeSettings()` reflect a stable value before the user
            // ever opens the toggle. Existing users see the default ON since
            // they have never seen this surface before. Once the user touches
            // either control, their explicit choice persists and the gated
            // re-run short-circuits (idempotent).
            if (prefs[REPLACEMENT_AUTO_DISMISS_MIGRATION_V2_APPLIED] != true) {
                val defaults = SmartNotiSettings()
                prefs[SettingsDeliveryProfileRepository.Keys.REPLACEMENT_AUTO_DISMISS_ENABLED] =
                    defaults.replacementAutoDismissEnabled
                prefs[SettingsDeliveryProfileRepository.Keys.REPLACEMENT_AUTO_DISMISS_MINUTES] =
                    defaults.replacementAutoDismissMinutes
                prefs[REPLACEMENT_AUTO_DISMISS_MIGRATION_V2_APPLIED] = true
            }
            // Plan `2026-04-27-inbox-sort-by-priority-or-app.md` Task 2.
            // Materialize the inbox sort mode default so on-disk state matches
            // what `observeSettings()` reports on the first read. Skipped if
            // the user has already chosen a mode (key already present).
            if (prefs[SettingsDeliveryProfileRepository.Keys.INBOX_SORT_MODE] == null) {
                prefs[SettingsDeliveryProfileRepository.Keys.INBOX_SORT_MODE] = InboxSortMode.RECENT.name
            }
        }
    }

    suspend fun setReplacementAutoDismissEnabled(enabled: Boolean) =
        deliveryProfile.setReplacementAutoDismissEnabled(enabled)

    suspend fun setReplacementAutoDismissMinutes(minutes: Int) =
        deliveryProfile.setReplacementAutoDismissMinutes(minutes)

    suspend fun setPriorityAlertLevel(value: String) = deliveryProfile.setPriorityAlertLevel(value)

    suspend fun setPriorityVibrationMode(value: String) = deliveryProfile.setPriorityVibrationMode(value)

    suspend fun setPriorityHeadsUpEnabled(enabled: Boolean) = deliveryProfile.setPriorityHeadsUpEnabled(enabled)

    suspend fun setPriorityLockScreenVisibility(value: String) = deliveryProfile.setPriorityLockScreenVisibility(value)

    suspend fun setDigestAlertLevel(value: String) = deliveryProfile.setDigestAlertLevel(value)

    suspend fun setDigestVibrationMode(value: String) = deliveryProfile.setDigestVibrationMode(value)

    suspend fun setDigestHeadsUpEnabled(enabled: Boolean) = deliveryProfile.setDigestHeadsUpEnabled(enabled)

    suspend fun setDigestLockScreenVisibility(value: String) = deliveryProfile.setDigestLockScreenVisibility(value)

    suspend fun setSilentAlertLevel(value: String) = deliveryProfile.setSilentAlertLevel(value)

    suspend fun setSilentVibrationMode(value: String) = deliveryProfile.setSilentVibrationMode(value)

    suspend fun setSilentHeadsUpEnabled(enabled: Boolean) = deliveryProfile.setSilentHeadsUpEnabled(enabled)

    suspend fun setSilentLockScreenVisibility(value: String) = deliveryProfile.setSilentLockScreenVisibility(value)

    suspend fun setSuppressedSourceApps(packageNames: Set<String>) = suppression.setSuppressedSourceApps(packageNames)

    suspend fun setSuppressedSourceAppExcluded(packageName: String, excluded: Boolean) =
        suppression.setSuppressedSourceAppExcluded(packageName, excluded)

    suspend fun setSuppressedSourceAppsExcludedBulk(packageNames: Set<String>, excluded: Boolean) =
        suppression.setSuppressedSourceAppsExcludedBulk(packageNames, excluded)

    suspend fun setHidePersistentNotifications(enabled: Boolean) = suppression.setHidePersistentNotifications(enabled)

    suspend fun setHidePersistentSourceNotifications(enabled: Boolean) =
        suppression.setHidePersistentSourceNotifications(enabled)

    suspend fun setProtectCriticalPersistentNotifications(enabled: Boolean) =
        suppression.setProtectCriticalPersistentNotifications(enabled)

    suspend fun setShowIgnoredArchive(enabled: Boolean) = suppression.setShowIgnoredArchive(enabled)

    suspend fun toggleSuppressedSourceApp(packageName: String, enabled: Boolean) =
        suppression.toggleSuppressedSourceApp(packageName, enabled)

    fun observeOnboardingCompleted(): Flow<Boolean> {
        return context.dataStore.data.map { prefs ->
            prefs[ONBOARDING_COMPLETED] ?: false
        }
    }

    suspend fun isOnboardingCompleted(): Boolean {
        return observeOnboardingCompleted().first()
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[ONBOARDING_COMPLETED] = completed
        }
    }

    suspend fun requestOnboardingActiveNotificationBootstrap(): Boolean {
        var requested = false
        context.dataStore.edit { prefs ->
            val completed = prefs[ONBOARDING_ACTIVE_NOTIFICATION_BOOTSTRAP_COMPLETED] ?: false
            val pending = prefs[ONBOARDING_ACTIVE_NOTIFICATION_BOOTSTRAP_PENDING] ?: false
            if (!completed && !pending) {
                prefs[ONBOARDING_ACTIVE_NOTIFICATION_BOOTSTRAP_PENDING] = true
                requested = true
            }
        }
        return requested
    }

    /**
     * Reader-only poll for the onboarding bootstrap pending flag.
     * Lets the listener-reconnect sweep coordinator defer while the bootstrap
     * path still owns the first pass over active notifications.
     */
    suspend fun isOnboardingBootstrapPending(): Boolean {
        return context.dataStore.data.map { prefs ->
            prefs[ONBOARDING_ACTIVE_NOTIFICATION_BOOTSTRAP_PENDING] ?: false
        }.first()
    }

    /**
     * First-launch-post-upgrade gate for the Rules → Categories migration
     * pass (plan `docs/plans/2026-04-22-categories-split-rules-actions.md`
     * Phase P1 Task 3). False until the migration has successfully run at
     * least once; once flipped, the [com.smartnoti.app.data.categories.RuleToCategoryMigration]
     * caller short-circuits and never scans again.
     *
     * The flag complements the idempotent `cat-from-rule-<ruleId>` id scheme
     * — even if a crash tore down the app mid-migration we would re-run
     * safely, but this flag avoids the repeated scan on every cold start.
     */
    fun observeRulesToCategoriesMigrated(): Flow<Boolean> {
        return context.dataStore.data.map { prefs ->
            prefs[RULES_TO_CATEGORIES_MIGRATED] ?: false
        }
    }

    suspend fun isRulesToCategoriesMigrated(): Boolean {
        return observeRulesToCategoriesMigrated().first()
    }

    suspend fun setRulesToCategoriesMigrated(migrated: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[RULES_TO_CATEGORIES_MIGRATED] = migrated
        }
    }

    /**
     * Epoch-millis deadline for the Home "새 앱 분류 유도 카드" snooze (plan
     * `docs/plans/2026-04-22-categories-split-rules-actions.md` Phase P3 Task
     * 10). Zero means "never snoozed". Tapping "나중에" on the card writes
     * `now + 24h` here; the [UncategorizedAppsDetector] consults this when
     * deciding whether to re-emit a Prompt.
     */
    fun observeUncategorizedPromptSnoozeUntilMillis(): Flow<Long> {
        return context.dataStore.data.map { prefs ->
            prefs[UNCATEGORIZED_PROMPT_SNOOZE_UNTIL_MILLIS] ?: 0L
        }
    }

    suspend fun setUncategorizedPromptSnoozeUntilMillis(millis: Long) {
        context.dataStore.edit { prefs ->
            prefs[UNCATEGORIZED_PROMPT_SNOOZE_UNTIL_MILLIS] = millis
        }
    }

    /**
     * Tracks when the Home `HomeQuickStartAppliedCard` was last acknowledged
     * so the Task 10 Home declutter can drop the card after 7 days or after an
     * explicit tap-to-ack. Zero means "not acknowledged yet".
     */
    fun observeQuickStartAppliedCardAcknowledgedAtMillis(): Flow<Long> {
        return context.dataStore.data.map { prefs ->
            prefs[QUICK_START_APPLIED_CARD_ACKNOWLEDGED_AT_MILLIS] ?: 0L
        }
    }

    suspend fun setQuickStartAppliedCardAcknowledgedAtMillis(millis: Long) {
        context.dataStore.edit { prefs ->
            prefs[QUICK_START_APPLIED_CARD_ACKNOWLEDGED_AT_MILLIS] = millis
        }
    }

    /**
     * One-shot gate for the Task 11 categories-migration announcement modal:
     * false until the user dismisses the "규칙은 이제 '분류' 안에서 편집합니다"
     * notice, after which it flips permanently.
     */
    fun observeCategoriesMigrationAnnouncementSeen(): Flow<Boolean> {
        return context.dataStore.data.map { prefs ->
            prefs[CATEGORIES_MIGRATION_ANNOUNCEMENT_SEEN] ?: false
        }
    }

    suspend fun setCategoriesMigrationAnnouncementSeen(seen: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[CATEGORIES_MIGRATION_ANNOUNCEMENT_SEEN] = seen
        }
    }

    suspend fun consumeOnboardingActiveNotificationBootstrapRequest(): Boolean {
        var consumed = false
        context.dataStore.edit { prefs ->
            val completed = prefs[ONBOARDING_ACTIVE_NOTIFICATION_BOOTSTRAP_COMPLETED] ?: false
            val pending = prefs[ONBOARDING_ACTIVE_NOTIFICATION_BOOTSTRAP_PENDING] ?: false
            when {
                pending && !completed -> {
                    prefs[ONBOARDING_ACTIVE_NOTIFICATION_BOOTSTRAP_PENDING] = false
                    prefs[ONBOARDING_ACTIVE_NOTIFICATION_BOOTSTRAP_COMPLETED] = true
                    consumed = true
                }
                completed -> {
                    prefs[ONBOARDING_ACTIVE_NOTIFICATION_BOOTSTRAP_PENDING] = false
                }
            }
        }
        return consumed
    }

    internal suspend fun clearAllForTest() {
        context.dataStore.edit { prefs ->
            prefs.clear()
        }
    }

    companion object {
        private val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        private val ONBOARDING_ACTIVE_NOTIFICATION_BOOTSTRAP_PENDING =
            booleanPreferencesKey("onboarding_active_notification_bootstrap_pending")
        private val ONBOARDING_ACTIVE_NOTIFICATION_BOOTSTRAP_COMPLETED =
            booleanPreferencesKey("onboarding_active_notification_bootstrap_completed")
        private val RULES_TO_CATEGORIES_MIGRATED =
            booleanPreferencesKey("rules_to_categories_migrated")
        private val UNCATEGORIZED_PROMPT_SNOOZE_UNTIL_MILLIS =
            longPreferencesKey("uncategorized_prompt_snooze_until_millis")
        private val QUICK_START_APPLIED_CARD_ACKNOWLEDGED_AT_MILLIS =
            longPreferencesKey("quick_start_applied_card_acknowledged_at_millis")
        private val CATEGORIES_MIGRATION_ANNOUNCEMENT_SEEN =
            booleanPreferencesKey("categories_migration_announcement_seen")
        // Plan `2026-04-24-duplicate-notifications-suppress-defaults-ac.md`
        // Task 4: gate for the suppress-source default-on migration. Once
        // true, `applyPendingMigrations()` no-ops and we respect whatever the
        // user has set since.
        private val SUPPRESS_SOURCE_MIGRATION_V1_APPLIED =
            booleanPreferencesKey("suppress_source_migration_v1_applied")
        // Gate for the v2 one-shot migration that stamps the auto-dismiss
        // defaults. Once true, the migration short-circuits and respects any
        // user-driven changes.
        private val REPLACEMENT_AUTO_DISMISS_MIGRATION_V2_APPLIED =
            booleanPreferencesKey("replacement_auto_dismiss_migration_v2_applied")

        @Volatile private var instance: SettingsRepository? = null

        fun getInstance(context: Context): SettingsRepository {
            return instance ?: synchronized(this) {
                instance ?: SettingsRepository(context.applicationContext).also { instance = it }
            }
        }

        internal fun clearInstanceForTest() {
            instance = null
        }
    }
}
