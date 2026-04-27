package com.smartnoti.app.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey

/**
 * Sibling repository carved out of [SettingsRepository] (plan
 * `2026-04-27-refactor-settings-repository-facade-split.md`). Owns the
 * duplicate-burst heuristic tunables that the listener consults on every
 * `processNotification` call.
 */
internal class SettingsDuplicateRepository(
    private val dataStore: DataStore<Preferences>,
) {
    /**
     * Plan `2026-04-26-duplicate-threshold-window-settings.md` Task 2.
     *
     * Persists the minimum duplicate count that promotes a notification to
     * DIGEST in the base heuristic (when no rule / priority keyword matches).
     * The setter coerces to `>= 1` so a programmatic 0 / negative value
     * cannot effectively disable duplicate-burst suppression entirely. The UI
     * is a dropdown so this is defense-in-depth.
     */
    suspend fun setDigestThreshold(threshold: Int) {
        dataStore.edit { prefs ->
            prefs[Keys.DIGEST_THRESHOLD] = threshold.coerceAtLeast(1)
        }
    }

    /**
     * Plan `2026-04-26-duplicate-threshold-window-settings.md` Task 2.
     *
     * Persists the rolling-window length (minutes) that
     * `DuplicateNotificationPolicy` uses to count repeats. Coerces to `>= 1`
     * for the same reason as [setDigestThreshold].
     */
    suspend fun setWindowMinutes(minutes: Int) {
        dataStore.edit { prefs ->
            prefs[Keys.WINDOW_MINUTES] = minutes.coerceAtLeast(1)
        }
    }

    internal object Keys {
        val DIGEST_THRESHOLD = intPreferencesKey("duplicate_digest_threshold")
        val WINDOW_MINUTES = intPreferencesKey("duplicate_window_minutes")
    }
}
