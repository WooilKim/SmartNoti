package com.smartnoti.app.data.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartNotiSettingsTest {

    // Plan `2026-04-24-duplicate-notifications-suppress-defaults-ac.md` Task 3.
    // The default `SmartNotiSettings()` instance is the contract for
    // first-launch users (and, after the one-shot migration in Task 4, for
    // upgraded users too): the global suppress-source toggle must be ON, and
    // `suppressedSourceApps` must be empty so the new "empty = all packages
    // opt-in" semantic in `NotificationSuppressionPolicy` actually fires.

    @Test
    fun default_settings_have_suppress_source_for_digest_and_silent_enabled() {
        val defaults = SmartNotiSettings()

        assertTrue(
            "Fresh-install users must default to suppress-source ON so DIGEST/SILENT do not show twice",
            defaults.suppressSourceForDigestAndSilent,
        )
    }

    @Test
    fun default_settings_have_empty_suppressed_source_apps() {
        val defaults = SmartNotiSettings()

        assertEquals(
            "Default empty set is intentional: combined with the toggle ON, the policy treats every captured package as opted-in",
            emptySet<String>(),
            defaults.suppressedSourceApps,
        )
    }
}
