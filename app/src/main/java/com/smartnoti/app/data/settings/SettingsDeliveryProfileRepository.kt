package com.smartnoti.app.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.smartnoti.app.domain.model.InboxSortMode

/**
 * Sibling repository carved out of [SettingsRepository] (plan
 * `2026-04-27-refactor-settings-repository-facade-split.md`). Owns the
 * per-tier delivery profile — alert level, vibration mode, heads-up,
 * lock-screen visibility for each of priority/digest/silent — plus the
 * SmartNoti-posted replacement auto-dismiss tunables and the inbox sort
 * mode. The inbox sort mode and replacement auto-dismiss live here because
 * they are user-visible delivery / presentation knobs governed by the same
 * settings surface as the per-tier profile.
 */
internal class SettingsDeliveryProfileRepository(
    private val dataStore: DataStore<Preferences>,
) {
    suspend fun setPriorityAlertLevel(value: String) = setString(Keys.PRIORITY_ALERT_LEVEL, value)

    suspend fun setPriorityVibrationMode(value: String) = setString(Keys.PRIORITY_VIBRATION_MODE, value)

    suspend fun setPriorityHeadsUpEnabled(enabled: Boolean) = setBoolean(Keys.PRIORITY_HEADS_UP_ENABLED, enabled)

    suspend fun setPriorityLockScreenVisibility(value: String) = setString(Keys.PRIORITY_LOCK_SCREEN_VISIBILITY, value)

    suspend fun setDigestAlertLevel(value: String) = setString(Keys.DIGEST_ALERT_LEVEL, value)

    suspend fun setDigestVibrationMode(value: String) = setString(Keys.DIGEST_VIBRATION_MODE, value)

    suspend fun setDigestHeadsUpEnabled(enabled: Boolean) = setBoolean(Keys.DIGEST_HEADS_UP_ENABLED, enabled)

    suspend fun setDigestLockScreenVisibility(value: String) = setString(Keys.DIGEST_LOCK_SCREEN_VISIBILITY, value)

    suspend fun setSilentAlertLevel(value: String) = setString(Keys.SILENT_ALERT_LEVEL, value)

    suspend fun setSilentVibrationMode(value: String) = setString(Keys.SILENT_VIBRATION_MODE, value)

    suspend fun setSilentHeadsUpEnabled(enabled: Boolean) = setBoolean(Keys.SILENT_HEADS_UP_ENABLED, enabled)

    suspend fun setSilentLockScreenVisibility(value: String) = setString(Keys.SILENT_LOCK_SCREEN_VISIBILITY, value)

    /**
     * Plan `2026-04-27-tray-replacement-auto-dismiss-timeout.md` Task 3.
     *
     * Master toggle for auto-dismissing SmartNoti-posted replacement / summary
     * notifications. OFF restores legacy behavior (notification stays in the
     * tray until the user swipes). The notifier reads the latest snapshot via
     * [SettingsRepository.observeSettings] so the next post-after-the-write
     * picks up the change.
     */
    suspend fun setReplacementAutoDismissEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[Keys.REPLACEMENT_AUTO_DISMISS_ENABLED] = enabled
        }
    }

    /**
     * Plan `2026-04-27-tray-replacement-auto-dismiss-timeout.md` Task 3.
     *
     * Persists the auto-dismiss duration in minutes. The Settings UI offers a
     * `5 / 15 / 30 / 60 / 180` preset; the setter coerces to `>= 1` so a
     * programmatic 0 / negative value cannot disable the feature without the
     * user toggling [setReplacementAutoDismissEnabled] OFF.
     * `ReplacementNotificationTimeoutPolicy` additionally guards `<= 0` at
     * read time as defense-in-depth.
     */
    suspend fun setReplacementAutoDismissMinutes(minutes: Int) {
        dataStore.edit { prefs ->
            prefs[Keys.REPLACEMENT_AUTO_DISMISS_MINUTES] = minutes.coerceAtLeast(1)
        }
    }

    /**
     * Plan `2026-04-27-inbox-sort-by-priority-or-app.md` Task 2.
     *
     * Persists the user-selected inbox sort mode. Stored as the enum's `name`
     * so adding new modes is a backward-compatible append (existing stored
     * values still round-trip via [InboxSortMode.valueOf]).
     */
    suspend fun setInboxSortMode(mode: InboxSortMode) {
        dataStore.edit { prefs ->
            prefs[Keys.INBOX_SORT_MODE] = mode.name
        }
    }

    private suspend fun setString(key: Preferences.Key<String>, value: String) {
        dataStore.edit { prefs ->
            prefs[key] = value
        }
    }

    private suspend fun setBoolean(key: Preferences.Key<Boolean>, value: Boolean) {
        dataStore.edit { prefs ->
            prefs[key] = value
        }
    }

    internal object Keys {
        val PRIORITY_ALERT_LEVEL = stringPreferencesKey("priority_alert_level")
        val PRIORITY_VIBRATION_MODE = stringPreferencesKey("priority_vibration_mode")
        val PRIORITY_HEADS_UP_ENABLED = booleanPreferencesKey("priority_heads_up_enabled")
        val PRIORITY_LOCK_SCREEN_VISIBILITY = stringPreferencesKey("priority_lock_screen_visibility")
        val DIGEST_ALERT_LEVEL = stringPreferencesKey("digest_alert_level")
        val DIGEST_VIBRATION_MODE = stringPreferencesKey("digest_vibration_mode")
        val DIGEST_HEADS_UP_ENABLED = booleanPreferencesKey("digest_heads_up_enabled")
        val DIGEST_LOCK_SCREEN_VISIBILITY = stringPreferencesKey("digest_lock_screen_visibility")
        val SILENT_ALERT_LEVEL = stringPreferencesKey("silent_alert_level")
        val SILENT_VIBRATION_MODE = stringPreferencesKey("silent_vibration_mode")
        val SILENT_HEADS_UP_ENABLED = booleanPreferencesKey("silent_heads_up_enabled")
        val SILENT_LOCK_SCREEN_VISIBILITY = stringPreferencesKey("silent_lock_screen_visibility")
        // Plan `2026-04-27-tray-replacement-auto-dismiss-timeout.md` Task 3.
        // Master toggle (default ON) and duration in minutes (default 30) for
        // SmartNoti-posted replacement / summary auto-dismiss. The notifier
        // threads these into `ReplacementNotificationTimeoutPolicy` which
        // returns the timeout the builder hands to
        // `NotificationCompat.Builder.setTimeoutAfter`.
        val REPLACEMENT_AUTO_DISMISS_ENABLED = booleanPreferencesKey("replacement_auto_dismiss_enabled")
        val REPLACEMENT_AUTO_DISMISS_MINUTES = intPreferencesKey("replacement_auto_dismiss_minutes")
        // Plan `2026-04-27-inbox-sort-by-priority-or-app.md` Task 2.
        // User-selected inbox sort mode. Stored as `InboxSortMode.name`.
        // Default is materialized on first `applyPendingMigrations()` so the
        // on-disk state is stable for downstream observers.
        val INBOX_SORT_MODE = stringPreferencesKey("inbox_sort_mode")
    }
}
