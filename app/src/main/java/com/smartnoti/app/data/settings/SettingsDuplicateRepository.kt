package com.smartnoti.app.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
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

    /**
     * Plan `2026-04-27-fix-issue-488-signature-normalize-numbers-time.md`
     * Task 4. Persists the opt-in normalizer toggle. Default OFF semantics
     * are achieved by the absence of the key in DataStore (the
     * `SettingsRepository.observeSettings` mapper falls back to the
     * `SmartNotiSettings.normalizeNumericTokensInSignature = false` data-class
     * default), so no migration is required.
     */
    suspend fun setNormalizeNumericTokens(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[Keys.NORMALIZE_NUMERIC_TOKENS] = enabled
        }
    }

    internal object Keys {
        val DIGEST_THRESHOLD = intPreferencesKey("duplicate_digest_threshold")
        val WINDOW_MINUTES = intPreferencesKey("duplicate_window_minutes")
        // Plan `2026-04-27-fix-issue-488-signature-normalize-numbers-time.md`
        // Task 4. Boolean DataStore key — default OFF lives in the data class.
        val NORMALIZE_NUMERIC_TOKENS = booleanPreferencesKey("duplicate_normalize_numeric_tokens")
    }
}
