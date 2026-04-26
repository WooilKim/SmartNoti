package com.smartnoti.app.data.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Plan `2026-04-24-duplicate-notifications-suppress-defaults-ac.md` Task 4.
 *
 * Verifies the one-shot v1 migration that flips
 * `suppressSourceForDigestAndSilent` to true on first launch (fresh or
 * upgrade). Three cases: fresh install, upgrade-from-OFF, and idempotency
 * after the user explicitly toggles OFF post-migration.
 */
@RunWith(RobolectricTestRunner::class)
class SettingsRepositoryMigrationTest {

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
    fun fresh_install_migration_writes_toggle_on_and_marks_applied() = runBlocking {
        // No stored value, no migration flag — fresh install.
        repository.applyPendingMigrations()

        val settings = repository.observeSettings().first()
        assertTrue(
            "Migration must leave the toggle ON for fresh installs",
            settings.suppressSourceForDigestAndSilent,
        )
    }

    @Test
    fun upgrade_from_explicit_off_overwrites_to_on() = runBlocking {
        // Simulate a pre-migration upgrade: the user previously persisted OFF.
        repository.setSuppressSourceForDigestAndSilent(false)
        assertFalse(repository.observeSettings().first().suppressSourceForDigestAndSilent)

        repository.applyPendingMigrations()

        val settings = repository.observeSettings().first()
        assertTrue(
            "Upgrade migration must overwrite the persisted OFF value",
            settings.suppressSourceForDigestAndSilent,
        )
    }

    @Test
    fun migration_is_idempotent_and_respects_post_migration_user_choice() = runBlocking {
        // First migration run.
        repository.applyPendingMigrations()
        assertTrue(repository.observeSettings().first().suppressSourceForDigestAndSilent)

        // Power user explicitly turns it off after the migration ran once.
        repository.setSuppressSourceForDigestAndSilent(false)
        assertFalse(repository.observeSettings().first().suppressSourceForDigestAndSilent)

        // A subsequent migration call (e.g. on next cold start) must NOT
        // re-flip the value — that would defeat the user's choice.
        repository.applyPendingMigrations()
        assertFalse(
            "Second migration call must short-circuit and respect the user's later OFF choice",
            repository.observeSettings().first().suppressSourceForDigestAndSilent,
        )
    }

    @Test
    fun migration_does_not_touch_unrelated_settings() = runBlocking {
        repository.setSuppressedSourceApps(setOf("com.example.first", "com.example.second"))

        repository.applyPendingMigrations()

        val settings = repository.observeSettings().first()
        assertEquals(
            setOf("com.example.first", "com.example.second"),
            settings.suppressedSourceApps,
        )
        assertTrue(settings.suppressSourceForDigestAndSilent)
    }

    /**
     * Plan `2026-04-27-tray-replacement-auto-dismiss-timeout.md` Task 3.
     *
     * v2 migration: stamps auto-dismiss defaults (ON / 30 min) on first run.
     */
    @Test
    fun fresh_install_v2_migration_stamps_auto_dismiss_defaults() = runBlocking {
        repository.applyPendingMigrations()

        val settings = repository.observeSettings().first()
        assertTrue(
            "Fresh install must see auto-dismiss enabled by default",
            settings.replacementAutoDismissEnabled,
        )
        assertEquals(
            "Fresh install must see the 30-minute default",
            30,
            settings.replacementAutoDismissMinutes,
        )
    }

    @Test
    fun v2_migration_is_idempotent_and_respects_post_migration_user_choice() = runBlocking {
        repository.applyPendingMigrations()
        assertTrue(repository.observeSettings().first().replacementAutoDismissEnabled)

        // Power user disables the feature after the first migration.
        repository.setReplacementAutoDismissEnabled(false)
        repository.setReplacementAutoDismissMinutes(15)

        // Subsequent migration call must NOT overwrite the user's choices.
        repository.applyPendingMigrations()

        val settings = repository.observeSettings().first()
        assertFalse(
            "Second migration must respect the user's later OFF choice",
            settings.replacementAutoDismissEnabled,
        )
        assertEquals(
            "Second migration must respect the user's later minutes choice",
            15,
            settings.replacementAutoDismissMinutes,
        )
    }

    @Test
    fun set_replacement_auto_dismiss_minutes_coerces_to_minimum_one() = runBlocking {
        repository.setReplacementAutoDismissMinutes(0)
        assertEquals(1, repository.observeSettings().first().replacementAutoDismissMinutes)

        repository.setReplacementAutoDismissMinutes(-7)
        assertEquals(1, repository.observeSettings().first().replacementAutoDismissMinutes)

        repository.setReplacementAutoDismissMinutes(60)
        assertEquals(60, repository.observeSettings().first().replacementAutoDismissMinutes)
    }
}
