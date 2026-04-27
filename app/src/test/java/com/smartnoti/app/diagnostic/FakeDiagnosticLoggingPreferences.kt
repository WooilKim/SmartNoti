package com.smartnoti.app.diagnostic

/**
 * In-memory [DiagnosticLoggingPreferencesReader] for the Task 1 failing-test
 * suite. Mutating [enabled] / [rawTitleBody] flips the next read synchronously
 * — production binds the same interface to a DataStore Preferences-backed
 * implementation in Task 2.
 */
internal class FakeDiagnosticLoggingPreferences(
    @Volatile var enabled: Boolean = false,
    @Volatile var rawTitleBody: Boolean = false,
) : DiagnosticLoggingPreferencesReader {

    override fun isEnabled(): Boolean = enabled

    override fun isRawTitleBodyEnabled(): Boolean = rawTitleBody
}
