package com.smartnoti.app.data.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Plan `docs/plans/2026-04-28-fix-issue-525-high-volume-app-suggest-suppression.md`
 * Task 1 (RED) — pin the contract for the two new DataStore-backed paths
 * that gate the Inbox high-volume-app suggestion card:
 *
 *  - `setSuggestedSuppressionDismissed(packageName, dismissed)` writes the
 *    sticky dismiss set so a `[무시]` tap permanently removes that
 *    packageName from future suggestion candidates.
 *  - `setSuggestedSuppressionSnoozeUntil(packageName, untilMillis)` writes
 *    a per-package snooze map so a `[나중에]` tap hides the card for the
 *    next 24 hours; passing `untilMillis = null` clears the entry.
 *  - Both fields surface through `observeSettings()` so consumers
 *    (`HighVolumeAppDetector`, `HighVolumeAppSuggestionPolicy`,
 *    `InboxScreen`) can react reactively without polling — the user's
 *    prompt calls this the `observeHighVolumeAppSuggestion()` flow contract.
 *
 * All tests must initially compile-fail because the two new
 * `SmartNotiSettings` fields and the two repository methods do not exist
 * yet.
 */
@RunWith(RobolectricTestRunner::class)
class SettingsRepositorySuggestedSuppressionTest {

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
    fun default_suggestedSuppressionDismissed_is_empty() = runBlocking {
        val settings = repository.observeSettings().first()
        assertEquals(emptySet<String>(), settings.suggestedSuppressionDismissed)
    }

    @Test
    fun default_suggestedSuppressionSnoozeUntil_is_empty() = runBlocking {
        val settings = repository.observeSettings().first()
        assertEquals(emptyMap<String, Long>(), settings.suggestedSuppressionSnoozeUntil)
    }

    @Test
    fun setSuggestedSuppressionDismissed_true_adds_to_dismissed_set() = runBlocking {
        repository.setSuggestedSuppressionDismissed("com.kakao.talk", dismissed = true)

        val settings = repository.observeSettings().first()
        assertTrue(
            "com.kakao.talk should be in dismissed set",
            "com.kakao.talk" in settings.suggestedSuppressionDismissed,
        )
    }

    @Test
    fun setSuggestedSuppressionDismissed_false_removes_from_dismissed_set() = runBlocking {
        repository.setSuggestedSuppressionDismissed("com.kakao.talk", dismissed = true)
        // Sanity check.
        assertTrue("com.kakao.talk" in repository.observeSettings().first().suggestedSuppressionDismissed)

        repository.setSuggestedSuppressionDismissed("com.kakao.talk", dismissed = false)

        val settings = repository.observeSettings().first()
        assertFalse(
            "com.kakao.talk should be removed",
            "com.kakao.talk" in settings.suggestedSuppressionDismissed,
        )
    }

    @Test
    fun setSuggestedSuppressionDismissed_is_idempotent_for_repeated_true_calls() = runBlocking {
        repository.setSuggestedSuppressionDismissed("com.foo", dismissed = true)
        repository.setSuggestedSuppressionDismissed("com.foo", dismissed = true)

        val settings = repository.observeSettings().first()
        assertEquals(setOf("com.foo"), settings.suggestedSuppressionDismissed)
    }

    @Test
    fun setSuggestedSuppressionDismissed_handles_multiple_packages() = runBlocking {
        repository.setSuggestedSuppressionDismissed("com.foo", dismissed = true)
        repository.setSuggestedSuppressionDismissed("com.bar", dismissed = true)
        repository.setSuggestedSuppressionDismissed("com.baz", dismissed = true)

        val all = repository.observeSettings().first().suggestedSuppressionDismissed
        assertEquals(setOf("com.foo", "com.bar", "com.baz"), all)

        repository.setSuggestedSuppressionDismissed("com.bar", dismissed = false)
        val after = repository.observeSettings().first().suggestedSuppressionDismissed
        assertEquals(setOf("com.foo", "com.baz"), after)
    }

    @Test
    fun setSuggestedSuppressionSnoozeUntil_writes_map_entry() = runBlocking {
        val until = 1_777_320_000_000L
        repository.setSuggestedSuppressionSnoozeUntil("com.kakao.talk", untilMillis = until)

        val settings = repository.observeSettings().first()
        assertEquals(until, settings.suggestedSuppressionSnoozeUntil["com.kakao.talk"])
    }

    @Test
    fun setSuggestedSuppressionSnoozeUntil_null_clears_entry() = runBlocking {
        val until = 1_777_320_000_000L
        repository.setSuggestedSuppressionSnoozeUntil("com.kakao.talk", untilMillis = until)
        // Sanity check.
        assertEquals(until, repository.observeSettings().first().suggestedSuppressionSnoozeUntil["com.kakao.talk"])

        repository.setSuggestedSuppressionSnoozeUntil("com.kakao.talk", untilMillis = null)

        val settings = repository.observeSettings().first()
        assertNull(
            "passing null untilMillis should remove the entry",
            settings.suggestedSuppressionSnoozeUntil["com.kakao.talk"],
        )
    }

    @Test
    fun setSuggestedSuppressionSnoozeUntil_handles_multiple_packages() = runBlocking {
        repository.setSuggestedSuppressionSnoozeUntil("com.foo", untilMillis = 1_000)
        repository.setSuggestedSuppressionSnoozeUntil("com.bar", untilMillis = 2_000)
        repository.setSuggestedSuppressionSnoozeUntil("com.baz", untilMillis = 3_000)

        val map = repository.observeSettings().first().suggestedSuppressionSnoozeUntil
        assertEquals(3, map.size)
        assertEquals(1_000L, map["com.foo"])
        assertEquals(2_000L, map["com.bar"])
        assertEquals(3_000L, map["com.baz"])

        // Clearing one entry leaves the others intact.
        repository.setSuggestedSuppressionSnoozeUntil("com.bar", untilMillis = null)
        val after = repository.observeSettings().first().suggestedSuppressionSnoozeUntil
        assertEquals(2, after.size)
        assertEquals(1_000L, after["com.foo"])
        assertNull(after["com.bar"])
        assertEquals(3_000L, after["com.baz"])
    }

    @Test
    fun setSuggestedSuppressionSnoozeUntil_overwrites_existing_value_for_same_package() = runBlocking {
        repository.setSuggestedSuppressionSnoozeUntil("com.foo", untilMillis = 1_000)
        repository.setSuggestedSuppressionSnoozeUntil("com.foo", untilMillis = 5_000)

        val map = repository.observeSettings().first().suggestedSuppressionSnoozeUntil
        assertEquals(1, map.size)
        assertEquals(5_000L, map["com.foo"])
    }

    @Test
    fun dismissed_and_snoozed_writes_are_independent() = runBlocking {
        repository.setSuggestedSuppressionDismissed("com.foo", dismissed = true)
        repository.setSuggestedSuppressionSnoozeUntil("com.bar", untilMillis = 1_000)

        val settings = repository.observeSettings().first()
        assertEquals(setOf("com.foo"), settings.suggestedSuppressionDismissed)
        assertEquals(mapOf("com.bar" to 1_000L), settings.suggestedSuppressionSnoozeUntil)
    }
}
