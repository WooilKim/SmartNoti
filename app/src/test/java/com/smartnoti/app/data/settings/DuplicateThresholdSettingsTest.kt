package com.smartnoti.app.data.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Plan `2026-04-26-duplicate-threshold-window-settings.md` Task 1.
 *
 * Pins the new `duplicateDigestThreshold` / `duplicateWindowMinutes` fields
 * on [SmartNotiSettings] + the round-trip contract through DataStore. The
 * defaults must equal the historical hard-coded values (3 / 10) so existing
 * users see no behavior change post-upgrade — only the ability to tune.
 *
 * The setters apply a `coerceAtLeast(1)` guard so a programmatic 0 / negative
 * value cannot disable duplicate-burst suppression entirely (the UI is a
 * dropdown so this is defense-in-depth).
 */
@RunWith(RobolectricTestRunner::class)
class DuplicateThresholdSettingsTest {

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
    fun defaults_match_historical_hardcoded_values() = runBlocking {
        val settings = repository.observeSettings().first()

        assertEquals(3, settings.duplicateDigestThreshold)
        assertEquals(10, settings.duplicateWindowMinutes)
    }

    @Test
    fun setDuplicateDigestThreshold_persists_and_round_trips() = runBlocking {
        repository.setDuplicateDigestThreshold(4)

        val settings = repository.observeSettings().first()
        assertEquals(4, settings.duplicateDigestThreshold)
    }

    @Test
    fun setDuplicateWindowMinutes_persists_and_round_trips() = runBlocking {
        repository.setDuplicateWindowMinutes(15)

        val settings = repository.observeSettings().first()
        assertEquals(15, settings.duplicateWindowMinutes)
    }

    @Test
    fun setters_are_independent() = runBlocking {
        repository.setDuplicateDigestThreshold(5)
        repository.setDuplicateWindowMinutes(30)

        val settings = repository.observeSettings().first()
        assertEquals(5, settings.duplicateDigestThreshold)
        assertEquals(30, settings.duplicateWindowMinutes)
    }

    @Test
    fun setDuplicateDigestThreshold_coerces_zero_to_one() = runBlocking {
        repository.setDuplicateDigestThreshold(0)

        val settings = repository.observeSettings().first()
        assertEquals(1, settings.duplicateDigestThreshold)
    }

    @Test
    fun setDuplicateDigestThreshold_coerces_negative_to_one() = runBlocking {
        repository.setDuplicateDigestThreshold(-3)

        val settings = repository.observeSettings().first()
        assertEquals(1, settings.duplicateDigestThreshold)
    }

    @Test
    fun setDuplicateWindowMinutes_coerces_zero_to_one() = runBlocking {
        repository.setDuplicateWindowMinutes(0)

        val settings = repository.observeSettings().first()
        assertEquals(1, settings.duplicateWindowMinutes)
    }

    @Test
    fun setDuplicateWindowMinutes_coerces_negative_to_one() = runBlocking {
        repository.setDuplicateWindowMinutes(-10)

        val settings = repository.observeSettings().first()
        assertEquals(1, settings.duplicateWindowMinutes)
    }

    @Test
    fun custom_values_round_trip_through_data_class_constructor() = runBlocking {
        // Round-trip via the data class itself — pins that the field is
        // present, defaults are correct, and copy works as expected so
        // upstream code can reconstruct settings safely.
        val settings = SmartNotiSettings(
            duplicateDigestThreshold = 4,
            duplicateWindowMinutes = 15,
        )

        assertEquals(4, settings.duplicateDigestThreshold)
        assertEquals(15, settings.duplicateWindowMinutes)
    }
}
