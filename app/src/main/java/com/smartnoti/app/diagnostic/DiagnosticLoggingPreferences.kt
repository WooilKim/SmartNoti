package com.smartnoti.app.diagnostic

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

/**
 * Plan `docs/plans/2026-04-27-fix-issue-480-diagnostic-log-file-export.md`
 * Task 2.
 *
 * Read-side contract that [DiagnosticLogger] queries on every call to decide
 * whether to write at all (and whether to redact title/body). Kept narrow so
 * the production [DiagnosticLoggingPreferences] DataStore-backed
 * implementation and the test fake share the same surface.
 *
 * Both reads must be cheap and synchronous — the hot-path logger calls
 * `isEnabled()` before allocating any entry object so that the OFF case
 * stays no-allocation.
 */
interface DiagnosticLoggingPreferencesReader {
    fun isEnabled(): Boolean
    fun isRawTitleBodyEnabled(): Boolean
}

private val Context.diagnosticDataStore by preferencesDataStore(name = "smartnoti_diagnostic")

/**
 * DataStore-backed production implementation. Backed by a separate DataStore
 * file (`smartnoti_diagnostic`) so the diagnostic toggles never collide with
 * the main `smartnoti_settings` schema and so a future feature can wipe one
 * without the other.
 *
 * The `isEnabled` / `isRawTitleBodyEnabled` accessors are cached in
 * [@Volatile] fields refreshed off the observe Flow — this matches the
 * "no allocation when disabled" contract demanded by the hot-path call sites
 * (the listener service is on the NotificationListenerService dispatch
 * thread, so a synchronous DataStore read per notification would be
 * unacceptable).
 */
class DiagnosticLoggingPreferences private constructor(context: Context) :
    DiagnosticLoggingPreferencesReader {

    private val dataStore = context.applicationContext.diagnosticDataStore

    @Volatile
    private var cachedEnabled: Boolean = false

    @Volatile
    private var cachedRawTitleBody: Boolean = false

    init {
        // Seed the cache synchronously on first construction so the first
        // logger call already sees the persisted value. Subsequent updates
        // happen via setEnabled / setRawTitleBodyEnabled which both write
        // through the cache before persisting.
        try {
            val (enabled, rawTitleBody) = runBlocking {
                val prefs = dataStore.data.first()
                Pair(
                    prefs[Keys.ENABLED] ?: false,
                    prefs[Keys.RAW_TITLE_BODY] ?: false,
                )
            }
            cachedEnabled = enabled
            cachedRawTitleBody = rawTitleBody
        } catch (_: Throwable) {
            // Datastore not ready (eg. stripped test environment); leave cache
            // at the default `false` and let observe* / setEnabled refresh
            // when the user opts in.
        }
    }

    override fun isEnabled(): Boolean = cachedEnabled

    override fun isRawTitleBodyEnabled(): Boolean = cachedRawTitleBody

    fun observeEnabled(): Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[Keys.ENABLED] ?: false
    }

    fun observeRawTitleBody(): Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[Keys.RAW_TITLE_BODY] ?: false
    }

    suspend fun setEnabled(enabled: Boolean) {
        cachedEnabled = enabled
        dataStore.edit { prefs ->
            prefs[Keys.ENABLED] = enabled
        }
    }

    suspend fun setRawTitleBodyEnabled(enabled: Boolean) {
        cachedRawTitleBody = enabled
        dataStore.edit { prefs ->
            prefs[Keys.RAW_TITLE_BODY] = enabled
        }
    }

    private object Keys {
        val ENABLED = booleanPreferencesKey("diagnostic_logging_enabled")
        val RAW_TITLE_BODY = booleanPreferencesKey("diagnostic_logging_raw_title_body")
    }

    companion object {
        @Volatile
        private var instance: DiagnosticLoggingPreferences? = null

        fun getInstance(context: Context): DiagnosticLoggingPreferences {
            val existing = instance
            if (existing != null) return existing
            return synchronized(this) {
                val again = instance
                if (again != null) {
                    again
                } else {
                    val created = DiagnosticLoggingPreferences(context.applicationContext)
                    instance = created
                    created
                }
            }
        }
    }
}
