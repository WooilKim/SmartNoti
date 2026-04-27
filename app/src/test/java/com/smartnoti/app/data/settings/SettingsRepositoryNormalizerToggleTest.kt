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
 * Plan `docs/plans/2026-04-27-fix-issue-488-signature-normalize-numbers-time.md`
 * Task 1 — RED round-trip for the new opt-in toggle
 * `SmartNotiSettings.normalizeNumericTokensInSignature` (default OFF).
 *
 * Tasks 2–4 introduce:
 *   - `SettingsDuplicateRepository.Keys.NORMALIZE_NUMERIC_TOKENS` DataStore key
 *   - `SettingsDuplicateRepository.setNormalizeNumericTokens(enabled)`
 *   - `SettingsRepository.setNormalizeNumericTokensInSignature(enabled)` facade
 *   - `SmartNotiSettings.normalizeNumericTokensInSignature: Boolean = false`
 *
 * Default OFF is the safety guarantee — fresh installs must see no behavior
 * change until the user opts in. No migration needed (default `false` matches
 * "key never set").
 */
@RunWith(RobolectricTestRunner::class)
class SettingsRepositoryNormalizerToggleTest {

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
    fun default_is_false_so_fresh_installs_see_no_behavior_change() = runBlocking {
        val settings = repository.observeSettings().first()

        assertFalse(
            "normalizeNumericTokensInSignature must default to false (safety-first)",
            settings.normalizeNumericTokensInSignature,
        )
    }

    @Test
    fun set_true_round_trips_through_data_store() = runBlocking {
        repository.setNormalizeNumericTokensInSignature(true)

        val settings = repository.observeSettings().first()
        assertTrue(
            "Setter must persist true and observeSettings must surface it",
            settings.normalizeNumericTokensInSignature,
        )
    }

    @Test
    fun set_false_round_trips_through_data_store() = runBlocking {
        // Set ON first, then OFF — pin that the setter handles both directions.
        repository.setNormalizeNumericTokensInSignature(true)
        repository.setNormalizeNumericTokensInSignature(false)

        val settings = repository.observeSettings().first()
        assertFalse(
            "Setter must persist false and observeSettings must surface it",
            settings.normalizeNumericTokensInSignature,
        )
    }

    @Test
    fun toggle_does_not_clobber_other_duplicate_settings() = runBlocking {
        // Flipping the new toggle must not reset the sibling tunables in the
        // same SettingsDuplicateRepository (threshold + window).
        repository.setDuplicateDigestThreshold(5)
        repository.setDuplicateWindowMinutes(20)
        repository.setNormalizeNumericTokensInSignature(true)

        val settings = repository.observeSettings().first()
        assertEquals(5, settings.duplicateDigestThreshold)
        assertEquals(20, settings.duplicateWindowMinutes)
        assertTrue(settings.normalizeNumericTokensInSignature)
    }

    @Test
    fun data_class_constructor_default_is_false() {
        val settings = SmartNotiSettings()

        assertFalse(
            "SmartNotiSettings() default constructor must yield false",
            settings.normalizeNumericTokensInSignature,
        )
    }

    @Test
    fun data_class_constructor_accepts_true() {
        val settings = SmartNotiSettings(normalizeNumericTokensInSignature = true)

        assertTrue(settings.normalizeNumericTokensInSignature)
    }
}
