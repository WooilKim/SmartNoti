package com.smartnoti.app.data.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.smartnoti.app.domain.model.InboxSortMode
import com.smartnoti.app.domain.model.NotificationContext
import com.smartnoti.app.domain.usecase.QuietHoursPolicy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.Calendar

private val Context.dataStore by preferencesDataStore(name = "smartnoti_settings")

/**
 * Façade aggregating five per-domain sibling repositories plus a
 * [SettingsMigrationRunner] (plan
 * `2026-04-27-refactor-settings-repository-facade-split.md`). Holds the
 * single DataStore instance for the module, owns the cross-domain reads
 * ([observeSettings], [currentNotificationContext]) and the test-only
 * `clearAllForTest`, and delegates every other public setter / observer
 * 1-line to the appropriate sibling. Public signatures match the
 * pre-refactor contract so the 41 caller sites do not change at all.
 */
class SettingsRepository private constructor(
    context: Context,
) {
    // Plan `2026-04-27-refactor-settings-repository-facade-split.md`: every
    // sibling shares the single DataStore instance owned by the façade so
    // they all hit the same on-disk file (no per-sibling lazy init path).
    private val dataStore = context.dataStore
    private val quietHours = SettingsQuietHoursRepository(dataStore)
    private val duplicate = SettingsDuplicateRepository(dataStore)
    private val deliveryProfile = SettingsDeliveryProfileRepository(dataStore)
    private val suppression = SettingsSuppressionRepository(dataStore)
    private val onboarding = SettingsOnboardingRepository(dataStore)
    private val migrationRunner = SettingsMigrationRunner(dataStore)

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
                normalizeNumericTokensInSignature = prefs[SettingsDuplicateRepository.Keys.NORMALIZE_NUMERIC_TOKENS]
                    ?: defaults.normalizeNumericTokensInSignature,
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

    // QuietHours sibling delegates
    suspend fun setQuietHoursEnabled(enabled: Boolean) = quietHours.setEnabled(enabled)
    suspend fun setQuietHoursStartHour(hour: Int) = quietHours.setStartHour(hour)
    suspend fun setQuietHoursEndHour(hour: Int) = quietHours.setEndHour(hour)
    suspend fun setQuietHoursPackages(packageNames: Set<String>) = quietHours.setPackages(packageNames)
    suspend fun addQuietHoursPackage(packageName: String) = quietHours.addPackage(packageName)
    suspend fun removeQuietHoursPackage(packageName: String) = quietHours.removePackage(packageName)

    // Duplicate sibling delegates
    suspend fun setDuplicateDigestThreshold(threshold: Int) = duplicate.setDigestThreshold(threshold)
    suspend fun setDuplicateWindowMinutes(minutes: Int) = duplicate.setWindowMinutes(minutes)
    suspend fun setNormalizeNumericTokensInSignature(enabled: Boolean) =
        duplicate.setNormalizeNumericTokens(enabled)

    // DeliveryProfile sibling delegates
    suspend fun setInboxSortMode(mode: InboxSortMode) = deliveryProfile.setInboxSortMode(mode)
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

    // Suppression sibling delegates
    suspend fun setSuppressSourceForDigestAndSilent(enabled: Boolean) =
        suppression.setSuppressSourceForDigestAndSilent(enabled)
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

    // Onboarding sibling delegates
    fun observeOnboardingCompleted(): Flow<Boolean> = onboarding.observeOnboardingCompleted()
    suspend fun isOnboardingCompleted(): Boolean = onboarding.isOnboardingCompleted()
    suspend fun setOnboardingCompleted(completed: Boolean) = onboarding.setOnboardingCompleted(completed)
    suspend fun requestOnboardingActiveNotificationBootstrap(): Boolean =
        onboarding.requestOnboardingActiveNotificationBootstrap()
    suspend fun isOnboardingBootstrapPending(): Boolean = onboarding.isOnboardingBootstrapPending()
    suspend fun consumeOnboardingActiveNotificationBootstrapRequest(): Boolean =
        onboarding.consumeOnboardingActiveNotificationBootstrapRequest()
    fun observeRulesToCategoriesMigrated(): Flow<Boolean> = onboarding.observeRulesToCategoriesMigrated()
    suspend fun isRulesToCategoriesMigrated(): Boolean = onboarding.isRulesToCategoriesMigrated()
    suspend fun setRulesToCategoriesMigrated(migrated: Boolean) = onboarding.setRulesToCategoriesMigrated(migrated)
    fun observeUncategorizedPromptSnoozeUntilMillis(): Flow<Long> =
        onboarding.observeUncategorizedPromptSnoozeUntilMillis()
    suspend fun setUncategorizedPromptSnoozeUntilMillis(millis: Long) =
        onboarding.setUncategorizedPromptSnoozeUntilMillis(millis)
    fun observeQuickStartAppliedCardAcknowledgedAtMillis(): Flow<Long> =
        onboarding.observeQuickStartAppliedCardAcknowledgedAtMillis()
    suspend fun setQuickStartAppliedCardAcknowledgedAtMillis(millis: Long) =
        onboarding.setQuickStartAppliedCardAcknowledgedAtMillis(millis)
    fun observeCategoriesMigrationAnnouncementSeen(): Flow<Boolean> =
        onboarding.observeCategoriesMigrationAnnouncementSeen()
    suspend fun setCategoriesMigrationAnnouncementSeen(seen: Boolean) =
        onboarding.setCategoriesMigrationAnnouncementSeen(seen)
    suspend fun isPromoQuietingActionMigrationV3Applied(): Boolean =
        onboarding.isPromoQuietingActionMigrationV3Applied()
    suspend fun setPromoQuietingActionMigrationV3Applied(applied: Boolean) =
        onboarding.setPromoQuietingActionMigrationV3Applied(applied)
    suspend fun isAppLabelResolutionMigrationV1Applied(): Boolean =
        onboarding.isAppLabelResolutionMigrationV1Applied()
    suspend fun setAppLabelResolutionMigrationV1Applied(applied: Boolean) =
        onboarding.setAppLabelResolutionMigrationV1Applied(applied)

    // MigrationRunner delegate — single `dataStore.edit` transaction inside
    // the runner keeps v1 + v2 + INBOX_SORT default materialization atomic.
    suspend fun applyPendingMigrations() = migrationRunner.applyPendingMigrations()

    internal suspend fun clearAllForTest() {
        dataStore.edit { prefs ->
            prefs.clear()
        }
    }

    companion object {
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
