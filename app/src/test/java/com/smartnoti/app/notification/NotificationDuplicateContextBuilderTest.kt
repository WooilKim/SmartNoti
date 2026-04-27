package com.smartnoti.app.notification

import com.smartnoti.app.data.settings.SmartNotiSettings
import com.smartnoti.app.domain.usecase.LiveDuplicateCountTracker
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Plan `2026-04-27-refactor-listener-process-notification-extract.md` Task 3.
 *
 * Pins two contracts of the extracted [NotificationDuplicateContextBuilder]:
 *   1. The window comes from the supplied [SmartNotiSettings] snapshot every
 *      call, so a Settings dropdown change reaches the very next notification
 *      without a process restart (plan
 *      `2026-04-26-duplicate-threshold-window-settings.md` Task 4 contract).
 *   2. Persistent notifications append the `|persistent:<pkg>:<id>` suffix to
 *      the content signature so a single sticky notification (e.g. media
 *      playback, foreground service) is never collapsed across packages or
 *      ids.
 */
class NotificationDuplicateContextBuilderTest {

    @Test
    fun window_minutes_setting_change_takes_effect_on_next_call() = runTest {
        val tracker = LiveDuplicateCountTracker()
        var lastSinceMillis: Long = -1
        val builder = NotificationDuplicateContextBuilder(
            tracker = tracker,
            persistedDuplicateCount = { _, _, sinceMillis ->
                lastSinceMillis = sinceMillis
                0
            },
        )

        // First call: 5 minute window -> windowStart = postTime - 5*60_000.
        val postTime = 10_000_000L
        builder.build(
            packageName = "com.example",
            notificationId = 1,
            sourceEntryKey = "k-1",
            postTimeMillis = postTime,
            title = "t",
            body = "b",
            settings = SmartNotiSettings(duplicateWindowMinutes = 5),
            isPersistent = false,
        )
        val sinceForFiveMin = lastSinceMillis

        // Second call with the same post time but 30 minute window -> a much
        // older `sinceMillis` immediately, no restart needed.
        builder.build(
            packageName = "com.example",
            notificationId = 2,
            sourceEntryKey = "k-2",
            postTimeMillis = postTime,
            title = "t",
            body = "b",
            settings = SmartNotiSettings(duplicateWindowMinutes = 30),
            isPersistent = false,
        )
        val sinceForThirtyMin = lastSinceMillis

        assertNotEquals(
            "Window dropdown change must reach the next call (5min vs 30min)",
            sinceForFiveMin,
            sinceForThirtyMin,
        )
        assertTrue(
            "30min window must look further back than the 5min window",
            sinceForThirtyMin < sinceForFiveMin,
        )
    }

    @Test
    fun persistent_appends_pkg_id_suffix_to_signature_non_persistent_does_not() = runTest {
        val tracker = LiveDuplicateCountTracker()
        val builder = NotificationDuplicateContextBuilder(
            tracker = tracker,
            persistedDuplicateCount = { _, _, _ -> 0 },
        )
        val settings = SmartNotiSettings(duplicateWindowMinutes = 10)

        val nonPersistent = builder.build(
            packageName = "com.example",
            notificationId = 42,
            sourceEntryKey = "k-non",
            postTimeMillis = 1_000L,
            title = "Hi",
            body = "Body",
            settings = settings,
            isPersistent = false,
        )
        val persistent = builder.build(
            packageName = "com.example",
            notificationId = 42,
            sourceEntryKey = "k-pers",
            postTimeMillis = 1_000L,
            title = "Hi",
            body = "Body",
            settings = settings,
            isPersistent = true,
        )

        assertFalse(
            "Non-persistent signature must not contain persistent suffix",
            nonPersistent.contentSignature.contains("|persistent:"),
        )
        assertTrue(
            "Persistent signature must end with |persistent:<pkg>:<id>",
            persistent.contentSignature.endsWith("|persistent:com.example:42"),
        )
    }

    @Test
    fun persisted_count_lookup_is_invoked_with_packages_signature_and_window_start() = runTest {
        val tracker = LiveDuplicateCountTracker()
        var observedPackage: String? = null
        var observedSignature: String? = null
        var observedSince: Long = -1
        val builder = NotificationDuplicateContextBuilder(
            tracker = tracker,
            persistedDuplicateCount = { pkg, sig, since ->
                observedPackage = pkg
                observedSignature = sig
                observedSince = since
                3
            },
        )

        val ctx = builder.build(
            packageName = "com.example",
            notificationId = 7,
            sourceEntryKey = "k-7",
            postTimeMillis = 2_000_000L,
            title = "title",
            body = "body",
            settings = SmartNotiSettings(duplicateWindowMinutes = 10),
            isPersistent = false,
        )

        assertEquals("com.example", observedPackage)
        assertEquals(ctx.contentSignature, observedSignature)
        assertEquals(ctx.duplicateWindowStart, observedSince)
    }
}
