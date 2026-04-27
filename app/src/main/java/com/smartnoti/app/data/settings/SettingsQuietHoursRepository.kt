package com.smartnoti.app.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey

/**
 * Sibling repository carved out of [SettingsRepository] (plan
 * `2026-04-27-refactor-settings-repository-facade-split.md`). Owns the quiet
 * hours master switch, the start/end hour pair, and the user-extensible
 * candidate package set. The façade keeps a singleton instance and forwards
 * every public quiet-hours setter to this class verbatim, so caller code
 * keeps using `settingsRepository.setQuietHoursEnabled(...)` etc.
 */
internal class SettingsQuietHoursRepository(
    private val dataStore: DataStore<Preferences>,
) {
    suspend fun setEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[Keys.ENABLED] = enabled
        }
    }

    /**
     * Plan `2026-04-26-settings-quiet-hours-window-editor.md` Task 2.
     *
     * Persists the start hour of the quiet-hours window. Caller (Settings UI)
     * is responsible for keeping the value within `0..23`; the repository does
     * not validate so the model contract stays in lockstep with
     * `QuietHoursPolicy.startHour: Int`.
     */
    suspend fun setStartHour(hour: Int) {
        dataStore.edit { prefs ->
            prefs[Keys.START_HOUR] = hour
        }
    }

    /**
     * Plan `2026-04-26-settings-quiet-hours-window-editor.md` Task 2.
     *
     * Persists the end hour of the quiet-hours window. See
     * [setStartHour] for validation contract.
     */
    suspend fun setEndHour(hour: Int) {
        dataStore.edit { prefs ->
            prefs[Keys.END_HOUR] = hour
        }
    }

    /**
     * Plan `2026-04-26-quiet-hours-shopping-packages-user-extensible.md` Task 2.
     *
     * Bulk replace of the quiet-hours-eligible package set. The classifier
     * reads this set on every `processNotification` call (Task 4 wiring), so
     * the next notification after the write picks up the new value. Empty
     * set is allowed and means no quiet-hours candidates — the master switch
     * may still be ON but the branch never fires until the user adds a
     * package back. Mirrors `setSuppressedSourceApps` in atomicity.
     */
    suspend fun setPackages(packageNames: Set<String>) {
        dataStore.edit { prefs ->
            prefs[Keys.PACKAGES] = packageNames
        }
    }

    /**
     * Plan `2026-04-26-quiet-hours-shopping-packages-user-extensible.md` Task 2.
     *
     * Idempotent insertion: re-adding an existing member is a no-op (the
     * underlying Set semantics dedup). Used by the Settings picker's
     * "앱 추가" affordance for single-package additions.
     */
    suspend fun addPackage(packageName: String) {
        dataStore.edit { prefs ->
            val current = prefs[Keys.PACKAGES] ?: SmartNotiSettings.DEFAULT_QUIET_HOURS_PACKAGES
            if (packageName !in current) {
                prefs[Keys.PACKAGES] = current + packageName
            } else if (Keys.PACKAGES !in prefs) {
                // Materialize the default into the store so subsequent
                // observers see a stable on-disk value rather than the
                // implicit fallback. Mirrors the pattern in
                // `applyPendingMigrations()` for the suppress-source default.
                prefs[Keys.PACKAGES] = current
            }
        }
    }

    /**
     * Plan `2026-04-26-quiet-hours-shopping-packages-user-extensible.md` Task 2.
     *
     * Removes a single package from the set. Removing the last member leaves
     * an empty set (the UI surfaces an inline warning so the user knows the
     * branch is silently no-op'ing). Removing an unknown package is a no-op.
     */
    suspend fun removePackage(packageName: String) {
        dataStore.edit { prefs ->
            val current = prefs[Keys.PACKAGES] ?: SmartNotiSettings.DEFAULT_QUIET_HOURS_PACKAGES
            if (packageName in current) {
                prefs[Keys.PACKAGES] = current - packageName
            }
        }
    }

    internal object Keys {
        val ENABLED = booleanPreferencesKey("quiet_hours_enabled")
        val START_HOUR = intPreferencesKey("quiet_hours_start_hour")
        val END_HOUR = intPreferencesKey("quiet_hours_end_hour")
        val PACKAGES = stringSetPreferencesKey("quiet_hours_packages")
    }
}
