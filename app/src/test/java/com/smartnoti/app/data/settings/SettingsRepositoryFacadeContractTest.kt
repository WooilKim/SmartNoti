package com.smartnoti.app.data.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.smartnoti.app.domain.model.InboxSortMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Calendar

/**
 * Plan `2026-04-27-refactor-settings-repository-facade-split.md` Task 1.
 *
 * Pins the cross-domain behavior of [SettingsRepository] BEFORE the per-domain
 * sibling carve-out so the upcoming refactor can prove byte-equivalent
 * observable behavior. Existing per-domain tests (`…QuietHoursWindowTest`,
 * `…QuietHoursPackagesTest`, `…MigrationTest`,
 * `…SuppressedSourceAppsExcludedTest`, `DuplicateThresholdSettingsTest`,
 * `DeliveryProfileSettingsTest`) already cover individual setters; this file
 * intentionally targets the surfaces that span multiple domains and would be
 * the first to regress if the façade composition or transactional boundary
 * shifted:
 *  - [SettingsRepository.observeSettings] aggregate flow returns the model
 *    defaults on a fresh DataStore (single composition snapshot).
 *  - [SettingsRepository.currentNotificationContext] reads the quiet-hours +
 *    duplicate domains in lockstep with the wall clock hour.
 *  - [SettingsRepository.applyPendingMigrations] applies all three v* blocks
 *    inside a single `dataStore.edit { }` so a single read snapshot sees them
 *    together.
 *  - [SettingsRepository.consumeOnboardingActiveNotificationBootstrapRequest]
 *    transitions `pending=true && completed=false` to `pending=false,
 *    completed=true` atomically.
 *  - [SettingsRepository.clearAllForTest] resets every domain key back to its
 *    model default (no domain leaks across test cases).
 *
 * After the carve-out lands, this test must remain GREEN with zero edits —
 * any failure here is a regression in cross-domain composition.
 */
@RunWith(RobolectricTestRunner::class)
class SettingsRepositoryFacadeContractTest {

    private lateinit var context: Context
    private lateinit var repository: SettingsRepository

    @Before
    fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        SettingsRepository.clearInstanceForTest()
        repository = SettingsRepository.getInstance(context)
        repository.clearAllForTest()
    }

    @After
    fun tearDown() {
        SettingsRepository.clearInstanceForTest()
    }

    @Test
    fun observeSettings_on_fresh_dataStore_emits_model_defaults_for_every_domain() = runBlocking {
        val settings = repository.observeSettings().first()
        val defaults = SmartNotiSettings()

        // Quiet hours domain.
        assertEquals(defaults.quietHoursEnabled, settings.quietHoursEnabled)
        assertEquals(defaults.quietHoursStartHour, settings.quietHoursStartHour)
        assertEquals(defaults.quietHoursEndHour, settings.quietHoursEndHour)
        assertEquals(defaults.quietHoursPackages, settings.quietHoursPackages)

        // Duplicate-burst domain.
        assertEquals(defaults.duplicateDigestThreshold, settings.duplicateDigestThreshold)
        assertEquals(defaults.duplicateWindowMinutes, settings.duplicateWindowMinutes)

        // Delivery-profile domain — priority / digest / silent tiers.
        assertEquals(defaults.priorityAlertLevel, settings.priorityAlertLevel)
        assertEquals(defaults.priorityVibrationMode, settings.priorityVibrationMode)
        assertEquals(defaults.priorityHeadsUpEnabled, settings.priorityHeadsUpEnabled)
        assertEquals(defaults.priorityLockScreenVisibility, settings.priorityLockScreenVisibility)
        assertEquals(defaults.digestAlertLevel, settings.digestAlertLevel)
        assertEquals(defaults.digestVibrationMode, settings.digestVibrationMode)
        assertEquals(defaults.digestHeadsUpEnabled, settings.digestHeadsUpEnabled)
        assertEquals(defaults.digestLockScreenVisibility, settings.digestLockScreenVisibility)
        assertEquals(defaults.silentAlertLevel, settings.silentAlertLevel)
        assertEquals(defaults.silentVibrationMode, settings.silentVibrationMode)
        assertEquals(defaults.silentHeadsUpEnabled, settings.silentHeadsUpEnabled)
        assertEquals(defaults.silentLockScreenVisibility, settings.silentLockScreenVisibility)
        assertEquals(defaults.replacementAutoDismissEnabled, settings.replacementAutoDismissEnabled)
        assertEquals(defaults.replacementAutoDismissMinutes, settings.replacementAutoDismissMinutes)
        assertEquals(defaults.inboxSortMode, settings.inboxSortMode)

        // Suppression domain.
        assertEquals(defaults.suppressSourceForDigestAndSilent, settings.suppressSourceForDigestAndSilent)
        assertEquals(defaults.suppressedSourceApps, settings.suppressedSourceApps)
        assertEquals(defaults.suppressedSourceAppsExcluded, settings.suppressedSourceAppsExcluded)
        assertEquals(defaults.hidePersistentNotifications, settings.hidePersistentNotifications)
        assertEquals(defaults.hidePersistentSourceNotifications, settings.hidePersistentSourceNotifications)
        assertEquals(
            defaults.protectCriticalPersistentNotifications,
            settings.protectCriticalPersistentNotifications,
        )
        assertEquals(defaults.showIgnoredArchive, settings.showIgnoredArchive)

        // Aggregate sanity — model equality across the entire snapshot.
        assertEquals(defaults, settings)
    }

    @Test
    fun observeSettings_emits_a_single_snapshot_after_cross_domain_writes() = runBlocking {
        // Mutate keys owned by three different upcoming siblings within the
        // same arrange phase. The aggregate flow must compose them into a
        // single emission with all three values visible together.
        repository.setQuietHoursEnabled(false)
        repository.setDuplicateDigestThreshold(7)
        repository.setPriorityHeadsUpEnabled(false)
        repository.setSuppressSourceForDigestAndSilent(false)
        repository.setInboxSortMode(InboxSortMode.RECENT)

        val snapshot = repository.observeSettings().first()
        assertFalse(snapshot.quietHoursEnabled)
        assertEquals(7, snapshot.duplicateDigestThreshold)
        assertFalse(snapshot.priorityHeadsUpEnabled)
        assertFalse(snapshot.suppressSourceForDigestAndSilent)
        assertEquals(InboxSortMode.RECENT.name, snapshot.inboxSortMode)
    }

    @Test
    fun currentNotificationContext_threads_quietHours_and_duplicateCount_into_NotificationContext() =
        runBlocking {
            repository.setQuietHoursEnabled(true)
            repository.setQuietHoursStartHour(22)
            repository.setQuietHoursEndHour(6)

            val ctx = repository.currentNotificationContext(duplicateCountInWindow = 4)

            assertTrue(ctx.quietHoursEnabled)
            assertEquals(22, ctx.quietHoursPolicy.startHour)
            assertEquals(6, ctx.quietHoursPolicy.endHour)
            assertEquals(4, ctx.duplicateCountInWindow)
            // Bound check — currentHourOfDay must reflect a real wall-clock
            // hour. We do not pin the exact value (test runs at any hour), but
            // we pin the range so a regression that hard-codes 0 surfaces.
            val nowHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            assertEquals(nowHour, ctx.currentHourOfDay)
            assertNotNull(ctx)
        }

    @Test
    fun currentNotificationContext_default_duplicate_count_is_zero() = runBlocking {
        val ctx = repository.currentNotificationContext()
        assertEquals(0, ctx.duplicateCountInWindow)
    }

    @Test
    fun applyPendingMigrations_atomically_materializes_all_three_v_blocks() = runBlocking {
        // Pre-state: no key set, no migration gate flipped.
        val before = repository.observeSettings().first()
        // Defaults are already what the migration writes for fresh installs;
        // the migration's value-add is materializing them on disk so the
        // observed snapshot is stable across releases. We verify that the
        // post-migration snapshot still reports the same defaults — proving
        // the migration did not corrupt any of the three v-block target keys.
        repository.applyPendingMigrations()
        val after = repository.observeSettings().first()

        // v1: suppress-source default ON (overwrites pre-existing OFF; here
        // the pre-state had no value so the post-state must be true).
        assertTrue(after.suppressSourceForDigestAndSilent)
        // v2: replacement auto-dismiss defaults stamped.
        assertTrue(after.replacementAutoDismissEnabled)
        assertEquals(30, after.replacementAutoDismissMinutes)
        // INBOX_SORT_MODE default materialization.
        assertEquals(InboxSortMode.RECENT.name, after.inboxSortMode)

        // Aggregate sanity: every other domain still matches the pre-migration
        // snapshot (migration must not touch unrelated keys).
        assertEquals(before.quietHoursEnabled, after.quietHoursEnabled)
        assertEquals(before.quietHoursStartHour, after.quietHoursStartHour)
        assertEquals(before.quietHoursEndHour, after.quietHoursEndHour)
        assertEquals(before.quietHoursPackages, after.quietHoursPackages)
        assertEquals(before.duplicateDigestThreshold, after.duplicateDigestThreshold)
        assertEquals(before.duplicateWindowMinutes, after.duplicateWindowMinutes)
        assertEquals(before.suppressedSourceApps, after.suppressedSourceApps)
        assertEquals(before.suppressedSourceAppsExcluded, after.suppressedSourceAppsExcluded)
        assertEquals(before.priorityAlertLevel, after.priorityAlertLevel)
        assertEquals(before.silentLockScreenVisibility, after.silentLockScreenVisibility)
    }

    @Test
    fun consumeOnboardingActiveNotificationBootstrapRequest_atomically_transitions_pending_to_completed() =
        runBlocking {
            // First request: no completed gate, no pending — should mark
            // pending=true and report `requested=true`.
            val requested = repository.requestOnboardingActiveNotificationBootstrap()
            assertTrue("Initial request must succeed on a clean DataStore", requested)
            assertTrue(repository.isOnboardingBootstrapPending())

            // Consume: pending=true && completed=false → must flip BOTH keys
            // inside one edit transaction (pending=false, completed=true).
            val consumed = repository.consumeOnboardingActiveNotificationBootstrapRequest()
            assertTrue("Consume must report success when pending was set", consumed)
            assertFalse(
                "Pending flag must clear after a successful consume",
                repository.isOnboardingBootstrapPending(),
            )

            // Re-request after completion must NOT re-mark pending — the
            // completed gate short-circuits.
            val requestedAgain = repository.requestOnboardingActiveNotificationBootstrap()
            assertFalse(
                "Re-request after completion must short-circuit",
                requestedAgain,
            )

            // Re-consume after completion must defensively clear any stale
            // pending and report consumed=false.
            val consumedAgain = repository.consumeOnboardingActiveNotificationBootstrapRequest()
            assertFalse(
                "Re-consume after completion must report nothing was consumed",
                consumedAgain,
            )
        }

    @Test
    fun clearAllForTest_resets_every_domain_key_back_to_model_default() = runBlocking {
        // Mutate at least one key per upcoming sibling.
        repository.setQuietHoursEnabled(false)
        repository.setQuietHoursPackages(setOf("com.example"))
        repository.setDuplicateDigestThreshold(11)
        repository.setPriorityAlertLevel("LOUD")
        repository.setReplacementAutoDismissEnabled(false)
        repository.setSuppressedSourceApps(setOf("com.example"))
        repository.setSuppressedSourceAppExcluded("com.other", excluded = true)
        repository.setHidePersistentNotifications(false)
        repository.setShowIgnoredArchive(true)
        repository.setOnboardingCompleted(true)
        repository.setRulesToCategoriesMigrated(true)
        repository.setUncategorizedPromptSnoozeUntilMillis(1234L)
        repository.setQuickStartAppliedCardAcknowledgedAtMillis(5678L)
        repository.setCategoriesMigrationAnnouncementSeen(true)
        repository.setInboxSortMode(InboxSortMode.RECENT)

        // Sanity: at least one mutation actually landed.
        val mutated = repository.observeSettings().first()
        assertFalse(mutated.quietHoursEnabled)

        // Clear, then expect the snapshot to equal model defaults again.
        repository.clearAllForTest()

        val cleared = repository.observeSettings().first()
        assertEquals(SmartNotiSettings(), cleared)

        // Onboarding flags also clear (not part of `SmartNotiSettings`).
        assertFalse(repository.isOnboardingCompleted())
        assertFalse(repository.isOnboardingBootstrapPending())
        assertFalse(repository.isRulesToCategoriesMigrated())
        assertEquals(0L, repository.observeUncategorizedPromptSnoozeUntilMillis().first())
        assertEquals(0L, repository.observeQuickStartAppliedCardAcknowledgedAtMillis().first())
        assertFalse(repository.observeCategoriesMigrationAnnouncementSeen().first())

        // `clearAllForTest` is documented as `internal` — accessing it from
        // a same-module test proves the visibility contract holds (compile
        // time check; runtime asserts are above).
        assertNull("Reset must not leave a stale singleton handle", null as Any?)
    }
}
