package com.smartnoti.app.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
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

    /**
     * Plan `2026-04-28-fix-issue-524-tray-orphan-cleanup-button.md` Task 4.
     *
     * Persist the user's "다시 묻지 않기" decision from the tray-cleanup
     * confirm dialog. Default `false` so first-time users always get the
     * confirm dialog (matches the plan Open Question R1 default — 100+
     * cancels are non-trivially reversible). Power users that flip the
     * checkbox once skip every subsequent confirm; the checkbox is the
     * ONLY surface that writes this key (no separate Settings row), so an
     * accidental flip is a one-tap recovery (untick on the next dialog).
     */
    suspend fun setTrayCleanupSkipConfirm(skip: Boolean) {
        dataStore.edit { prefs ->
            prefs[Keys.TRAY_CLEANUP_SKIP_CONFIRM_V1] = skip
        }
    }

    /**
     * Plan `2026-04-28-fix-issue-525-high-volume-app-suggest-suppression.md`
     * Task 5. Atomic per-package toggle for the sticky-permanent dismiss
     * set behind the `[무시]` button on [InboxSuggestionCard].
     *
     *  - `dismissed = true` adds [packageName] to the set so
     *    [HighVolumeAppDetector] never proposes it again.
     *  - `dismissed = false` removes it (recovery path; v1 has no UI for
     *    this but the API exists for completeness + tests).
     *
     * Idempotent: repeated `true` calls converge on the same final state
     * (the test pins this).
     */
    suspend fun setSuggestedSuppressionDismissed(packageName: String, dismissed: Boolean) {
        dataStore.edit { prefs ->
            val current = (prefs[Keys.SUGGESTED_SUPPRESSION_DISMISSED] ?: emptySet()).toMutableSet()
            if (dismissed) {
                current.add(packageName)
            } else {
                current.remove(packageName)
            }
            prefs[Keys.SUGGESTED_SUPPRESSION_DISMISSED] = current
        }
    }

    /**
     * Plan `2026-04-28-fix-issue-525-high-volume-app-suggest-suppression.md`
     * Task 5. Atomic per-package edit for the 24h-snooze map behind the
     * `[나중에]` button on [InboxSuggestionCard].
     *
     *  - `untilMillis != null` writes / overwrites the entry with that
     *    expiry timestamp.
     *  - `untilMillis == null` removes the entry (clear path).
     *
     * Persistence format: a single `stringPreferencesKey` storing a
     * pipe-delimited `pkg=until|pkg=until` payload. DataStore Preferences
     * does not support a native `Map<String, Long>` and proto DataStore is
     * out of scope (plan §Risks). Empty-map writes the empty string. The
     * encode / decode helpers are private to this class.
     */
    suspend fun setSuggestedSuppressionSnoozeUntil(packageName: String, untilMillis: Long?) {
        dataStore.edit { prefs ->
            val current = decodeSnoozeMap(prefs[Keys.SUGGESTED_SUPPRESSION_SNOOZE_UNTIL])
                .toMutableMap()
            if (untilMillis == null) {
                current.remove(packageName)
            } else {
                current[packageName] = untilMillis
            }
            prefs[Keys.SUGGESTED_SUPPRESSION_SNOOZE_UNTIL] = encodeSnoozeMap(current)
        }
    }

    /**
     * Plan `2026-04-28-fix-issue-526-sender-aware-classification-rules.md`
     * Task 5. Persists the user's decision for surfacing the
     * `SenderRuleSuggestionCard` on the notification Detail screen. Default
     * ON semantics live in `SmartNotiSettings.senderSuggestionEnabled = true`
     * — the absence of this key resolves to that default exactly the way
     * `NORMALIZE_NUMERIC_TOKENS` does, so no migration is required. The
     * setter is a single boolean write because the toggle has no sibling
     * state to keep in sync.
     */
    suspend fun setSenderSuggestionEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[Keys.SENDER_SUGGESTION_ENABLED] = enabled
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
        // Plan `2026-04-28-fix-issue-524-tray-orphan-cleanup-button.md` Task 4.
        // Persisted "다시 묻지 않기" flag for the Settings → 트레이 정리 confirm
        // dialog. Default `false`. The `_v1` suffix is reserved for a future
        // schema bump if the dialog gains additional state we want to migrate
        // (e.g. per-package skip lists).
        val TRAY_CLEANUP_SKIP_CONFIRM_V1 =
            booleanPreferencesKey("tray_cleanup_skip_confirm_v1")
        // Plan `2026-04-28-fix-issue-525-high-volume-app-suggest-suppression.md`
        // Task 5. Sticky-permanent dismiss set keyed by packageName.
        val SUGGESTED_SUPPRESSION_DISMISSED =
            stringSetPreferencesKey("suggested_suppression_dismissed")
        // Plan `2026-04-28-fix-issue-525-high-volume-app-suggest-suppression.md`
        // Task 5. 24h-snooze map persisted as a `pkg=until|pkg2=until2` string
        // payload. See the encode / decode helpers in [SettingsSuppressionRepository]
        // for the format. DataStore Preferences cannot hold a Map natively.
        val SUGGESTED_SUPPRESSION_SNOOZE_UNTIL =
            stringPreferencesKey("suggested_suppression_snooze_until")
        // Plan `2026-04-28-fix-issue-526-sender-aware-classification-rules.md`
        // Task 5. Master toggle for the SenderRuleSuggestionCard. Default ON
        // lives in the data class — absence of this key resolves to `true`.
        // The `_v1` suffix is reserved for future schema bumps if the toggle
        // gains additional state we want to migrate (e.g. per-app overrides).
        val SENDER_SUGGESTION_ENABLED =
            booleanPreferencesKey("sender_suggestion_enabled_v1")
    }

    companion object {
        /**
         * Plan `2026-04-28-fix-issue-525-high-volume-app-suggest-suppression.md`
         * Task 5. Decode the `pkg=until|pkg2=until2` payload into a map.
         * Tolerant of empty / null / malformed input — drops bad entries
         * silently rather than throwing so an upgrade from a future format
         * cannot brick the inbox.
         */
        internal fun decodeSnoozeMap(raw: String?): Map<String, Long> {
            if (raw.isNullOrEmpty()) return emptyMap()
            val out = LinkedHashMap<String, Long>()
            raw.split('|').forEach { entry ->
                if (entry.isBlank()) return@forEach
                val eq = entry.indexOf('=')
                if (eq <= 0 || eq == entry.lastIndex) return@forEach
                val pkg = entry.substring(0, eq)
                val untilStr = entry.substring(eq + 1)
                val until = untilStr.toLongOrNull() ?: return@forEach
                out[pkg] = until
            }
            return out
        }

        /**
         * Plan `2026-04-28-fix-issue-525-high-volume-app-suggest-suppression.md`
         * Task 5. Inverse of [decodeSnoozeMap]. Empty map → empty string so
         * the DataStore read-side never sees `null` after a write.
         */
        internal fun encodeSnoozeMap(map: Map<String, Long>): String {
            if (map.isEmpty()) return ""
            return map.entries.joinToString(separator = "|") { (pkg, until) -> "$pkg=$until" }
        }
    }
}
