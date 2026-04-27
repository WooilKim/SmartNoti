package com.smartnoti.app.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey

/**
 * Sibling repository carved out of [SettingsRepository] (plan
 * `2026-04-27-refactor-settings-repository-facade-split.md`). Owns every
 * "정리 / 억제" policy — the digest+silent source-suppression master switch,
 * the suppressed-source-apps set with its sticky exclude list, the
 * persistent-notification hide / protect knobs, and the ignored archive
 * surface toggle.
 */
internal class SettingsSuppressionRepository(
    private val dataStore: DataStore<Preferences>,
) {
    suspend fun setSuppressSourceForDigestAndSilent(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[Keys.SUPPRESS_SOURCE_FOR_DIGEST_AND_SILENT] = enabled
        }
    }

    /**
     * Bulk replace of the Suppressed Apps set written by auto-expansion and
     * the legacy "select-all / clear-all" Settings affordances.
     *
     * NOTE (plan `2026-04-26-digest-suppression-sticky-exclude-list.md`): this
     * API does NOT touch `suppressedSourceAppsExcluded`. Callers that need to
     * honor the user's sticky-exclude intent must use
     * [setSuppressedSourceAppExcluded] for per-package toggles, or check the
     * excluded set themselves before calling this with a set that re-includes
     * a previously excluded package. The auto-expansion path is already
     * gated by `SuppressedSourceAppsAutoExpansionPolicy.expandedAppsOrNull`
     * so it cannot violate this contract.
     */
    suspend fun setSuppressedSourceApps(packageNames: Set<String>) {
        dataStore.edit { prefs ->
            prefs[Keys.SUPPRESSED_SOURCE_APPS] = packageNames
        }
    }

    /**
     * Plan `2026-04-26-digest-suppression-sticky-exclude-list.md` Task 3.
     *
     * Atomic per-package toggle for the sticky-exclude list:
     *  - `excluded = true`: add `packageName` to `suppressedSourceAppsExcluded`
     *    AND remove it from `suppressedSourceApps`. After this call, the
     *    auto-expansion policy will refuse to re-add the package even if a
     *    fresh DIGEST notification arrives from it.
     *  - `excluded = false`: remove `packageName` from
     *    `suppressedSourceAppsExcluded` only. This does NOT re-add to
     *    `suppressedSourceApps` — the user must opt-in explicitly via the
     *    Settings toggle (which calls [setSuppressedSourceApps]) or auto-
     *    expansion must observe the package again on a future DIGEST.
     *
     * Both writes happen inside a single `dataStore.edit { ... }` block so
     * concurrent readers always see a consistent (excluded, suppressed) pair.
     */
    suspend fun setSuppressedSourceAppExcluded(packageName: String, excluded: Boolean) {
        dataStore.edit { prefs ->
            val currentExcluded = (prefs[Keys.SUPPRESSED_SOURCE_APPS_EXCLUDED] ?: emptySet()).toMutableSet()
            if (excluded) {
                currentExcluded.add(packageName)
                prefs[Keys.SUPPRESSED_SOURCE_APPS_EXCLUDED] = currentExcluded
                val currentSuppressed = (prefs[Keys.SUPPRESSED_SOURCE_APPS] ?: emptySet())
                if (packageName in currentSuppressed) {
                    prefs[Keys.SUPPRESSED_SOURCE_APPS] = currentSuppressed - packageName
                }
            } else {
                if (packageName in currentExcluded) {
                    currentExcluded.remove(packageName)
                    prefs[Keys.SUPPRESSED_SOURCE_APPS_EXCLUDED] = currentExcluded
                }
            }
        }
    }

    /**
     * Plan `2026-04-26-digest-suppression-sticky-exclude-list.md` Task 6.4.
     *
     * Bulk variant of [setSuppressedSourceAppExcluded] for the
     * "모두 선택 / 모두 해제" buttons in the Suppressed Apps editor.
     *  - `excluded = true`: add `packageNames` to the exclude set AND
     *    remove them from `suppressedSourceApps` so the next DIGEST
     *    cannot re-add any of them.
     *  - `excluded = false`: remove `packageNames` from the exclude set
     *    only. Re-adding to `suppressedSourceApps` is the caller's
     *    responsibility (the "모두 선택" path pairs this with
     *    [setSuppressedSourceApps]).
     *
     * Both writes happen inside a single `dataStore.edit { ... }` block so
     * concurrent readers always see a consistent (excluded, suppressed) pair.
     */
    suspend fun setSuppressedSourceAppsExcludedBulk(packageNames: Set<String>, excluded: Boolean) {
        if (packageNames.isEmpty()) return
        dataStore.edit { prefs ->
            val currentExcluded = (prefs[Keys.SUPPRESSED_SOURCE_APPS_EXCLUDED] ?: emptySet()).toMutableSet()
            if (excluded) {
                currentExcluded.addAll(packageNames)
                prefs[Keys.SUPPRESSED_SOURCE_APPS_EXCLUDED] = currentExcluded
                val currentSuppressed = (prefs[Keys.SUPPRESSED_SOURCE_APPS] ?: emptySet())
                val updatedSuppressed = currentSuppressed - packageNames
                if (updatedSuppressed.size != currentSuppressed.size) {
                    prefs[Keys.SUPPRESSED_SOURCE_APPS] = updatedSuppressed
                }
            } else {
                val updatedExcluded = currentExcluded - packageNames
                if (updatedExcluded.size != currentExcluded.size) {
                    prefs[Keys.SUPPRESSED_SOURCE_APPS_EXCLUDED] = updatedExcluded
                }
            }
        }
    }

    suspend fun toggleSuppressedSourceApp(packageName: String, enabled: Boolean) {
        dataStore.edit { prefs ->
            val updated = (prefs[Keys.SUPPRESSED_SOURCE_APPS] ?: emptySet()).toMutableSet().apply {
                if (enabled) {
                    add(packageName)
                } else {
                    remove(packageName)
                }
            }
            prefs[Keys.SUPPRESSED_SOURCE_APPS] = updated
        }
    }

    suspend fun setHidePersistentNotifications(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[Keys.HIDE_PERSISTENT_NOTIFICATIONS] = enabled
        }
    }

    suspend fun setHidePersistentSourceNotifications(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[Keys.HIDE_PERSISTENT_SOURCE_NOTIFICATIONS] = enabled
        }
    }

    suspend fun setProtectCriticalPersistentNotifications(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[Keys.PROTECT_CRITICAL_PERSISTENT_NOTIFICATIONS] = enabled
        }
    }

    /**
     * Opt-in toggle for the 무시됨 아카이브 screen (plan
     * `2026-04-21-ignore-tier-fourth-decision` Task 6). Default OFF: when
     * false, the archive route is absent from the nav graph entirely so
     * IGNORE rows stay invisible. Turning it on adds a conditional nav entry
     * in Settings; it does not alter the classifier or the repository write
     * path — IGNORE rows are always persisted.
     */
    suspend fun setShowIgnoredArchive(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[Keys.SHOW_IGNORED_ARCHIVE] = enabled
        }
    }

    internal object Keys {
        val SUPPRESS_SOURCE_FOR_DIGEST_AND_SILENT =
            booleanPreferencesKey("suppress_source_for_digest_and_silent")
        val SUPPRESSED_SOURCE_APPS = stringSetPreferencesKey("suppressed_source_apps")
        // Plan `2026-04-26-digest-suppression-sticky-exclude-list.md` Task 3.
        // Sticky exclude list — packages the user has explicitly removed from
        // the Suppressed Apps list. `SuppressedSourceAppsAutoExpansionPolicy`
        // never re-adds these to `SUPPRESSED_SOURCE_APPS`. Default empty set
        // means existing users are unaffected by this key's introduction.
        val SUPPRESSED_SOURCE_APPS_EXCLUDED =
            stringSetPreferencesKey("suppressed_source_apps_excluded")
        val HIDE_PERSISTENT_NOTIFICATIONS = booleanPreferencesKey("hide_persistent_notifications")
        val HIDE_PERSISTENT_SOURCE_NOTIFICATIONS =
            booleanPreferencesKey("hide_persistent_source_notifications")
        val PROTECT_CRITICAL_PERSISTENT_NOTIFICATIONS =
            booleanPreferencesKey("protect_critical_persistent_notifications")
        val SHOW_IGNORED_ARCHIVE = booleanPreferencesKey("show_ignored_archive")
    }
}
