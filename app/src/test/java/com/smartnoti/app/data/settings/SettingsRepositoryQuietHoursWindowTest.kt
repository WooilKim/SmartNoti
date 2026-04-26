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
 * Plan `2026-04-26-settings-quiet-hours-window-editor.md` Task 1.
 *
 * Verifies the new `setQuietHoursStartHour` / `setQuietHoursEndHour` setters
 * persist the hour through DataStore and round-trip via `observeSettings()`.
 * Existing keys (`QUIET_HOURS_START_HOUR` / `QUIET_HOURS_END_HOUR`) and the
 * read path are already wired; only the setters were missing — this test
 * pins the new contract.
 */
@RunWith(RobolectricTestRunner::class)
class SettingsRepositoryQuietHoursWindowTest {

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
    fun setQuietHoursStartHour_persists_and_round_trips() = runBlocking {
        repository.setQuietHoursStartHour(2)

        val settings = repository.observeSettings().first()
        assertEquals(2, settings.quietHoursStartHour)
    }

    @Test
    fun setQuietHoursEndHour_persists_and_round_trips() = runBlocking {
        repository.setQuietHoursEndHour(5)

        val settings = repository.observeSettings().first()
        assertEquals(5, settings.quietHoursEndHour)
    }

    @Test
    fun setQuietHoursStartHour_accepts_boundary_values() = runBlocking {
        repository.setQuietHoursStartHour(0)
        assertEquals(0, repository.observeSettings().first().quietHoursStartHour)

        repository.setQuietHoursStartHour(23)
        assertEquals(23, repository.observeSettings().first().quietHoursStartHour)
    }

    @Test
    fun setQuietHoursEndHour_accepts_boundary_values() = runBlocking {
        repository.setQuietHoursEndHour(0)
        assertEquals(0, repository.observeSettings().first().quietHoursEndHour)

        repository.setQuietHoursEndHour(23)
        assertEquals(23, repository.observeSettings().first().quietHoursEndHour)
    }

    @Test
    fun start_and_end_setters_are_independent() = runBlocking {
        repository.setQuietHoursStartHour(11)
        repository.setQuietHoursEndHour(13)

        val settings = repository.observeSettings().first()
        assertEquals(11, settings.quietHoursStartHour)
        assertEquals(13, settings.quietHoursEndHour)
    }
}
