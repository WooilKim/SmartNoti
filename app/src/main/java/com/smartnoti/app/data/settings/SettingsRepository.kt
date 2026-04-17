package com.smartnoti.app.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
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
    fun observeSettings(): Flow<SmartNotiSettings> {
        val defaults = SmartNotiSettings()
        return context.dataStore.data.map { prefs ->
            SmartNotiSettings(
                quietHoursEnabled = prefs[QUIET_HOURS_ENABLED] ?: defaults.quietHoursEnabled,
                quietHoursStartHour = prefs[QUIET_HOURS_START_HOUR] ?: defaults.quietHoursStartHour,
                quietHoursEndHour = prefs[QUIET_HOURS_END_HOUR] ?: defaults.quietHoursEndHour,
                digestHours = defaults.digestHours,
                priorityAlertLevel = prefs[PRIORITY_ALERT_LEVEL] ?: defaults.priorityAlertLevel,
                priorityVibrationMode = prefs[PRIORITY_VIBRATION_MODE] ?: defaults.priorityVibrationMode,
                priorityHeadsUpEnabled = prefs[PRIORITY_HEADS_UP_ENABLED] ?: defaults.priorityHeadsUpEnabled,
                priorityLockScreenVisibility = prefs[PRIORITY_LOCK_SCREEN_VISIBILITY] ?: defaults.priorityLockScreenVisibility,
                digestAlertLevel = prefs[DIGEST_ALERT_LEVEL] ?: defaults.digestAlertLevel,
                digestVibrationMode = prefs[DIGEST_VIBRATION_MODE] ?: defaults.digestVibrationMode,
                digestHeadsUpEnabled = prefs[DIGEST_HEADS_UP_ENABLED] ?: defaults.digestHeadsUpEnabled,
                digestLockScreenVisibility = prefs[DIGEST_LOCK_SCREEN_VISIBILITY] ?: defaults.digestLockScreenVisibility,
                silentAlertLevel = prefs[SILENT_ALERT_LEVEL] ?: defaults.silentAlertLevel,
                silentVibrationMode = prefs[SILENT_VIBRATION_MODE] ?: defaults.silentVibrationMode,
                silentHeadsUpEnabled = prefs[SILENT_HEADS_UP_ENABLED] ?: defaults.silentHeadsUpEnabled,
                silentLockScreenVisibility = prefs[SILENT_LOCK_SCREEN_VISIBILITY] ?: defaults.silentLockScreenVisibility,
                suppressSourceForDigestAndSilent = prefs[SUPPRESS_SOURCE_FOR_DIGEST_AND_SILENT] ?: defaults.suppressSourceForDigestAndSilent,
                suppressedSourceApps = prefs[SUPPRESSED_SOURCE_APPS] ?: defaults.suppressedSourceApps,
                hidePersistentNotifications = prefs[HIDE_PERSISTENT_NOTIFICATIONS] ?: defaults.hidePersistentNotifications,
                hidePersistentSourceNotifications = prefs[HIDE_PERSISTENT_SOURCE_NOTIFICATIONS] ?: defaults.hidePersistentSourceNotifications,
                protectCriticalPersistentNotifications = prefs[PROTECT_CRITICAL_PERSISTENT_NOTIFICATIONS] ?: defaults.protectCriticalPersistentNotifications,
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

    suspend fun setQuietHoursEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[QUIET_HOURS_ENABLED] = enabled
        }
    }

    suspend fun setSuppressSourceForDigestAndSilent(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[SUPPRESS_SOURCE_FOR_DIGEST_AND_SILENT] = enabled
        }
    }

    suspend fun setPriorityAlertLevel(value: String) = setString(PRIORITY_ALERT_LEVEL, value)

    suspend fun setPriorityVibrationMode(value: String) = setString(PRIORITY_VIBRATION_MODE, value)

    suspend fun setPriorityHeadsUpEnabled(enabled: Boolean) = setBoolean(PRIORITY_HEADS_UP_ENABLED, enabled)

    suspend fun setPriorityLockScreenVisibility(value: String) = setString(PRIORITY_LOCK_SCREEN_VISIBILITY, value)

    suspend fun setDigestAlertLevel(value: String) = setString(DIGEST_ALERT_LEVEL, value)

    suspend fun setDigestVibrationMode(value: String) = setString(DIGEST_VIBRATION_MODE, value)

    suspend fun setDigestHeadsUpEnabled(enabled: Boolean) = setBoolean(DIGEST_HEADS_UP_ENABLED, enabled)

    suspend fun setDigestLockScreenVisibility(value: String) = setString(DIGEST_LOCK_SCREEN_VISIBILITY, value)

    suspend fun setSilentAlertLevel(value: String) = setString(SILENT_ALERT_LEVEL, value)

    suspend fun setSilentVibrationMode(value: String) = setString(SILENT_VIBRATION_MODE, value)

    suspend fun setSilentHeadsUpEnabled(enabled: Boolean) = setBoolean(SILENT_HEADS_UP_ENABLED, enabled)

    suspend fun setSilentLockScreenVisibility(value: String) = setString(SILENT_LOCK_SCREEN_VISIBILITY, value)

    suspend fun setSuppressedSourceApps(packageNames: Set<String>) {
        context.dataStore.edit { prefs ->
            prefs[SUPPRESSED_SOURCE_APPS] = packageNames
        }
    }

    suspend fun setHidePersistentNotifications(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[HIDE_PERSISTENT_NOTIFICATIONS] = enabled
        }
    }

    suspend fun setHidePersistentSourceNotifications(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[HIDE_PERSISTENT_SOURCE_NOTIFICATIONS] = enabled
        }
    }

    suspend fun setProtectCriticalPersistentNotifications(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[PROTECT_CRITICAL_PERSISTENT_NOTIFICATIONS] = enabled
        }
    }

    suspend fun toggleSuppressedSourceApp(packageName: String, enabled: Boolean) {
        context.dataStore.edit { prefs ->
            val updated = (prefs[SUPPRESSED_SOURCE_APPS] ?: emptySet()).toMutableSet().apply {
                if (enabled) {
                    add(packageName)
                } else {
                    remove(packageName)
                }
            }
            prefs[SUPPRESSED_SOURCE_APPS] = updated
        }
    }

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

    internal suspend fun clearAllForTest() {
        context.dataStore.edit { prefs ->
            prefs.clear()
        }
    }

    private suspend fun setString(key: androidx.datastore.preferences.core.Preferences.Key<String>, value: String) {
        context.dataStore.edit { prefs ->
            prefs[key] = value
        }
    }

    private suspend fun setBoolean(key: androidx.datastore.preferences.core.Preferences.Key<Boolean>, value: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[key] = value
        }
    }

    companion object {
        private val QUIET_HOURS_ENABLED = booleanPreferencesKey("quiet_hours_enabled")
        private val QUIET_HOURS_START_HOUR = intPreferencesKey("quiet_hours_start_hour")
        private val QUIET_HOURS_END_HOUR = intPreferencesKey("quiet_hours_end_hour")
        private val PRIORITY_ALERT_LEVEL = stringPreferencesKey("priority_alert_level")
        private val PRIORITY_VIBRATION_MODE = stringPreferencesKey("priority_vibration_mode")
        private val PRIORITY_HEADS_UP_ENABLED = booleanPreferencesKey("priority_heads_up_enabled")
        private val PRIORITY_LOCK_SCREEN_VISIBILITY = stringPreferencesKey("priority_lock_screen_visibility")
        private val DIGEST_ALERT_LEVEL = stringPreferencesKey("digest_alert_level")
        private val DIGEST_VIBRATION_MODE = stringPreferencesKey("digest_vibration_mode")
        private val DIGEST_HEADS_UP_ENABLED = booleanPreferencesKey("digest_heads_up_enabled")
        private val DIGEST_LOCK_SCREEN_VISIBILITY = stringPreferencesKey("digest_lock_screen_visibility")
        private val SILENT_ALERT_LEVEL = stringPreferencesKey("silent_alert_level")
        private val SILENT_VIBRATION_MODE = stringPreferencesKey("silent_vibration_mode")
        private val SILENT_HEADS_UP_ENABLED = booleanPreferencesKey("silent_heads_up_enabled")
        private val SILENT_LOCK_SCREEN_VISIBILITY = stringPreferencesKey("silent_lock_screen_visibility")
        private val SUPPRESS_SOURCE_FOR_DIGEST_AND_SILENT = booleanPreferencesKey("suppress_source_for_digest_and_silent")
        private val SUPPRESSED_SOURCE_APPS = stringSetPreferencesKey("suppressed_source_apps")
        private val HIDE_PERSISTENT_NOTIFICATIONS = booleanPreferencesKey("hide_persistent_notifications")
        private val HIDE_PERSISTENT_SOURCE_NOTIFICATIONS = booleanPreferencesKey("hide_persistent_source_notifications")
        private val PROTECT_CRITICAL_PERSISTENT_NOTIFICATIONS = booleanPreferencesKey("protect_critical_persistent_notifications")
        private val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")

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
