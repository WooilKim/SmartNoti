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
 * Plan `2026-04-26-digest-suppression-sticky-exclude-list.md` Task 4.
 *
 * Pins the contract for the new sticky-exclude API:
 * `setSuppressedSourceAppExcluded(packageName, excluded)` writes to a separate
 * `suppressedSourceAppsExcluded` set so that auto-expansion (driven by
 * `SuppressedSourceAppsAutoExpansionPolicy`) cannot re-add a package the user
 * explicitly removed from the Suppressed Apps list.
 *
 * Behaviors covered here:
 *  - excluded=true adds to the excluded set AND removes from
 *    `suppressedSourceApps` atomically.
 *  - excluded=false removes from the excluded set but does NOT re-add to
 *    `suppressedSourceApps` (the user must explicitly opt-in via the
 *    existing `setSuppressedSourceApps(...)` API or the Settings toggle).
 *  - The default for `suppressedSourceAppsExcluded` is empty (no migration).
 *  - Multiple packages and idempotent repeated calls behave as expected.
 */
@RunWith(RobolectricTestRunner::class)
class SettingsRepositorySuppressedSourceAppsExcludedTest {

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
    fun default_suppressedSourceAppsExcluded_is_empty() = runBlocking {
        val settings = repository.observeSettings().first()
        assertEquals(emptySet<String>(), settings.suppressedSourceAppsExcluded)
    }

    @Test
    fun setSuppressedSourceAppExcluded_true_adds_to_excluded_and_removes_from_suppressed() = runBlocking {
        repository.setSuppressedSourceApps(setOf("com.foo", "com.bar"))

        repository.setSuppressedSourceAppExcluded("com.foo", excluded = true)

        val settings = repository.observeSettings().first()
        assertTrue("com.foo should be in excluded set", "com.foo" in settings.suppressedSourceAppsExcluded)
        assertFalse(
            "com.foo should be removed from suppressedSourceApps",
            "com.foo" in settings.suppressedSourceApps,
        )
        assertTrue(
            "Other entries should be preserved in suppressedSourceApps",
            "com.bar" in settings.suppressedSourceApps,
        )
    }

    @Test
    fun setSuppressedSourceAppExcluded_false_removes_from_excluded_without_readding_to_suppressed() = runBlocking {
        repository.setSuppressedSourceApps(setOf("com.foo"))
        repository.setSuppressedSourceAppExcluded("com.foo", excluded = true)

        // Sanity check after the previous excluded=true.
        val midSettings = repository.observeSettings().first()
        assertTrue("com.foo" in midSettings.suppressedSourceAppsExcluded)
        assertFalse("com.foo" in midSettings.suppressedSourceApps)

        repository.setSuppressedSourceAppExcluded("com.foo", excluded = false)

        val settings = repository.observeSettings().first()
        assertFalse(
            "com.foo should be removed from excluded set",
            "com.foo" in settings.suppressedSourceAppsExcluded,
        )
        assertFalse(
            "excluded=false alone should NOT re-add to suppressedSourceApps",
            "com.foo" in settings.suppressedSourceApps,
        )
    }

    @Test
    fun setSuppressedSourceAppExcluded_true_works_when_app_not_in_suppressedSourceApps() = runBlocking {
        // Empty suppressedSourceApps is the default opt-out semantic. The user
        // can still mark an app as sticky-excluded so future auto-expansion
        // never re-adds it.
        repository.setSuppressedSourceAppExcluded("com.foo", excluded = true)

        val settings = repository.observeSettings().first()
        assertTrue("com.foo" in settings.suppressedSourceAppsExcluded)
        assertEquals(emptySet<String>(), settings.suppressedSourceApps)
    }

    @Test
    fun setSuppressedSourceAppExcluded_is_idempotent_for_repeated_true_calls() = runBlocking {
        repository.setSuppressedSourceAppExcluded("com.foo", excluded = true)
        repository.setSuppressedSourceAppExcluded("com.foo", excluded = true)

        val settings = repository.observeSettings().first()
        assertEquals(setOf("com.foo"), settings.suppressedSourceAppsExcluded)
    }

    @Test
    fun setSuppressedSourceAppExcluded_false_on_unknown_app_is_noop() = runBlocking {
        repository.setSuppressedSourceAppExcluded("com.never-excluded", excluded = false)

        val settings = repository.observeSettings().first()
        assertEquals(emptySet<String>(), settings.suppressedSourceAppsExcluded)
    }

    @Test
    fun setSuppressedSourceAppExcluded_handles_multiple_packages() = runBlocking {
        repository.setSuppressedSourceAppExcluded("com.foo", excluded = true)
        repository.setSuppressedSourceAppExcluded("com.bar", excluded = true)
        repository.setSuppressedSourceAppExcluded("com.baz", excluded = true)

        val settings = repository.observeSettings().first()
        assertEquals(
            setOf("com.foo", "com.bar", "com.baz"),
            settings.suppressedSourceAppsExcluded,
        )

        repository.setSuppressedSourceAppExcluded("com.bar", excluded = false)
        val after = repository.observeSettings().first()
        assertEquals(
            setOf("com.foo", "com.baz"),
            after.suppressedSourceAppsExcluded,
        )
    }
}
