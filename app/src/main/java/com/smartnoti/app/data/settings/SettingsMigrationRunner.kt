package com.smartnoti.app.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.smartnoti.app.domain.model.InboxSortMode

/**
 * Sibling owner for [SettingsRepository]'s `applyPendingMigrations()`
 * (plan `2026-04-27-refactor-settings-repository-facade-split.md`). The
 * runner mutates keys that belong to other siblings (suppression /
 * delivery profile) so it imports their `Keys` objects directly, but the
 * three migration blocks always run inside a single `dataStore.edit { }`
 * transaction so concurrent readers either see the pre-migration state
 * or the post-migration snapshot — never a partial mix.
 *
 * Adding a new migration: introduce a new `*_MIGRATION_V*_APPLIED` gate
 * key in this file's `Keys` object, then add a guarded block at the end
 * of the existing `dataStore.edit { ... }`. Do not split the transaction.
 */
internal class SettingsMigrationRunner(
    private val dataStore: DataStore<Preferences>,
) {
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
        dataStore.edit { prefs ->
            if (prefs[Keys.SUPPRESS_SOURCE_MIGRATION_V1_APPLIED] != true) {
                prefs[SettingsSuppressionRepository.Keys.SUPPRESS_SOURCE_FOR_DIGEST_AND_SILENT] = true
                prefs[Keys.SUPPRESS_SOURCE_MIGRATION_V1_APPLIED] = true
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
            if (prefs[Keys.REPLACEMENT_AUTO_DISMISS_MIGRATION_V2_APPLIED] != true) {
                val defaults = SmartNotiSettings()
                prefs[SettingsDeliveryProfileRepository.Keys.REPLACEMENT_AUTO_DISMISS_ENABLED] =
                    defaults.replacementAutoDismissEnabled
                prefs[SettingsDeliveryProfileRepository.Keys.REPLACEMENT_AUTO_DISMISS_MINUTES] =
                    defaults.replacementAutoDismissMinutes
                prefs[Keys.REPLACEMENT_AUTO_DISMISS_MIGRATION_V2_APPLIED] = true
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

    internal object Keys {
        // Plan `2026-04-24-duplicate-notifications-suppress-defaults-ac.md`
        // Task 4: gate for the suppress-source default-on migration. Once
        // true, `applyPendingMigrations()` no-ops and we respect whatever the
        // user has set since.
        val SUPPRESS_SOURCE_MIGRATION_V1_APPLIED =
            booleanPreferencesKey("suppress_source_migration_v1_applied")
        // Gate for the v2 one-shot migration that stamps the auto-dismiss
        // defaults. Once true, the migration short-circuits and respects any
        // user-driven changes.
        val REPLACEMENT_AUTO_DISMISS_MIGRATION_V2_APPLIED =
            booleanPreferencesKey("replacement_auto_dismiss_migration_v2_applied")
    }
}
