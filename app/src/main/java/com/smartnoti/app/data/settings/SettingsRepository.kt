package com.smartnoti.app.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.smartnoti.app.domain.model.NotificationContext
import com.smartnoti.app.domain.usecase.QuietHoursPolicy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.Calendar

private val Context.dataStore by preferencesDataStore(name = "smartnoti_settings")

class SettingsRepository private constructor(
    private val context: Context,
) {
    fun observeSettings(): Flow<SmartNotiSettings> {
        return context.dataStore.data.map { prefs ->
            SmartNotiSettings(
                quietHoursEnabled = prefs[QUIET_HOURS_ENABLED] ?: true,
                quietHoursStartHour = prefs[QUIET_HOURS_START_HOUR] ?: 23,
                quietHoursEndHour = prefs[QUIET_HOURS_END_HOUR] ?: 7,
                digestHours = listOf(12, 18, 21),
                suppressSourceForDigestAndSilent = prefs[SUPPRESS_SOURCE_FOR_DIGEST_AND_SILENT] ?: false,
            )
        }
    }

    suspend fun currentNotificationContext(duplicateCountInWindow: Int = 0): NotificationContext {
        val settings = observeSettings().first()
        val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return NotificationContext(
            quietHoursEnabled = settings.quietHoursEnabled,
            quietHoursPolicy = QuietHoursPolicy(
                startHour = settings.quietHoursStartHour,
                endHour = settings.quietHoursEndHour,
            ),
            currentHourOfDay = currentHour,
            duplicateCountInWindow = duplicateCountInWindow,
        )
    }

    suspend fun setQuietHoursEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[QUIET_HOURS_ENABLED] = enabled
        }
    }

    suspend fun setSuppressSourceForDigestAndSilent(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[SUPPRESS_SOURCE_FOR_DIGEST_AND_SILENT] = enabled
        }
    }

    fun observeOnboardingCompleted(): Flow<Boolean> {
        return context.dataStore.data.map { prefs ->
            prefs[ONBOARDING_COMPLETED] ?: false
        }
    }

    suspend fun isOnboardingCompleted(): Boolean {
        return observeOnboardingCompleted().first()
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[ONBOARDING_COMPLETED] = completed
        }
    }

    companion object {
        private val QUIET_HOURS_ENABLED = booleanPreferencesKey("quiet_hours_enabled")
        private val QUIET_HOURS_START_HOUR = intPreferencesKey("quiet_hours_start_hour")
        private val QUIET_HOURS_END_HOUR = intPreferencesKey("quiet_hours_end_hour")
        private val SUPPRESS_SOURCE_FOR_DIGEST_AND_SILENT = booleanPreferencesKey("suppress_source_for_digest_and_silent")
        private val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")

        @Volatile private var instance: SettingsRepository? = null

        fun getInstance(context: Context): SettingsRepository {
            return instance ?: synchronized(this) {
                instance ?: SettingsRepository(context.applicationContext).also { instance = it }
            }
        }
    }
}
