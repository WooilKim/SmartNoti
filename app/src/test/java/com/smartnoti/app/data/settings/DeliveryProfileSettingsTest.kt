package com.smartnoti.app.data.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DeliveryProfileSettingsTest {

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
    fun observe_settings_exposes_delivery_profile_defaults() = runBlocking {
        val settings = repository.observeSettings().first()

        assertEquals("LOUD", settings.priorityAlertLevel)
        assertEquals("STRONG", settings.priorityVibrationMode)
        assertEquals(true, settings.priorityHeadsUpEnabled)
        assertEquals("PRIVATE", settings.priorityLockScreenVisibility)
        assertEquals("SOFT", settings.digestAlertLevel)
        assertEquals("LIGHT", settings.digestVibrationMode)
        assertEquals(false, settings.digestHeadsUpEnabled)
        assertEquals("PRIVATE", settings.digestLockScreenVisibility)
        assertEquals("NONE", settings.silentAlertLevel)
        assertEquals("OFF", settings.silentVibrationMode)
        assertEquals(false, settings.silentHeadsUpEnabled)
        assertEquals("SECRET", settings.silentLockScreenVisibility)
    }

    @Test
    fun dedicated_delivery_profile_setters_persist_new_values() = runBlocking {
        repository.setPriorityAlertLevel("QUIET")
        repository.setPriorityVibrationMode("LIGHT")
        repository.setPriorityHeadsUpEnabled(false)
        repository.setPriorityLockScreenVisibility("PUBLIC")
        repository.setDigestAlertLevel("LOUD")
        repository.setDigestVibrationMode("STRONG")
        repository.setDigestHeadsUpEnabled(true)
        repository.setDigestLockScreenVisibility("SECRET")
        repository.setSilentAlertLevel("QUIET")
        repository.setSilentVibrationMode("LIGHT")
        repository.setSilentHeadsUpEnabled(true)
        repository.setSilentLockScreenVisibility("PRIVATE")

        val settings = repository.observeSettings().first()

        assertEquals("QUIET", settings.priorityAlertLevel)
        assertEquals("LIGHT", settings.priorityVibrationMode)
        assertEquals(false, settings.priorityHeadsUpEnabled)
        assertEquals("PUBLIC", settings.priorityLockScreenVisibility)
        assertEquals("LOUD", settings.digestAlertLevel)
        assertEquals("STRONG", settings.digestVibrationMode)
        assertEquals(true, settings.digestHeadsUpEnabled)
        assertEquals("SECRET", settings.digestLockScreenVisibility)
        assertEquals("QUIET", settings.silentAlertLevel)
        assertEquals("LIGHT", settings.silentVibrationMode)
        assertEquals(true, settings.silentHeadsUpEnabled)
        assertEquals("PRIVATE", settings.silentLockScreenVisibility)
    }

    @Test
    fun existing_persistent_defaults_are_preserved() = runBlocking {
        val settings = repository.observeSettings().first()

        assertTrue(settings.hidePersistentNotifications)
        assertTrue(settings.protectCriticalPersistentNotifications)
    }
}
