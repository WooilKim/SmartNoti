package com.smartnoti.app.data.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Plan `docs/plans/2026-04-28-fix-issue-526-sender-aware-classification-rules.md`
 * Task 5 — pin the round-trip semantics for the new
 * `SmartNotiSettings.senderSuggestionEnabled` toggle (default ON).
 *
 * The toggle gates the `SenderRuleSuggestionCard` surfaced on the
 * notification Detail screen (Task 7 wires the consumer). Default ON
 * matches the plan's "learning acceleration" intent — fresh installs see
 * the card immediately; the absence of the DataStore key resolves to the
 * data-class default (`true`) the same way `NORMALIZE_NUMERIC_TOKENS`
 * does, so no migration is required.
 */
@RunWith(RobolectricTestRunner::class)
class SettingsRepositorySenderSuggestionToggleTest {

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
    fun default_is_true_so_fresh_installs_see_the_card() = runBlocking {
        val settings = repository.observeSettings().first()

        assertTrue(
            "senderSuggestionEnabled must default to true (plan v1 default ON)",
            settings.senderSuggestionEnabled,
        )
    }

    @Test
    fun set_false_round_trips_through_data_store() = runBlocking {
        repository.setSenderSuggestionEnabled(false)

        val settings = repository.observeSettings().first()
        assertFalse(
            "Setter must persist false and observeSettings must surface it",
            settings.senderSuggestionEnabled,
        )
    }

    @Test
    fun set_true_round_trips_through_data_store() = runBlocking {
        // Flip OFF first, then back ON — pin both directions.
        repository.setSenderSuggestionEnabled(false)
        repository.setSenderSuggestionEnabled(true)

        val settings = repository.observeSettings().first()
        assertTrue(
            "Setter must persist true and observeSettings must surface it",
            settings.senderSuggestionEnabled,
        )
    }

    @Test
    fun data_class_constructor_default_is_true() {
        val settings = SmartNotiSettings()

        assertTrue(
            "SmartNotiSettings() default constructor must yield true",
            settings.senderSuggestionEnabled,
        )
    }

    @Test
    fun data_class_constructor_accepts_false() {
        val settings = SmartNotiSettings(senderSuggestionEnabled = false)

        assertFalse(settings.senderSuggestionEnabled)
    }
}
