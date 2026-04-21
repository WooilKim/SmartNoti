package com.smartnoti.app.notification

import android.app.Notification
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Tests-first for the reconnect sweep pipeline described in
 * docs/plans/2026-04-21-listener-reconnect-active-notification-sweep.md (Task 1).
 *
 * These tests intentionally target the future API surface:
 *   - ListenerReconnectActiveNotificationSweepCoordinator<T>
 *   - SweepDedupKey
 *
 * Task 2 will implement these types alongside the onboarding bootstrap so both
 * coordinators share `shouldProcess` + dedup semantics.
 */
@RunWith(RobolectricTestRunner::class)
class ListenerReconnectActiveNotificationSweepTest {

    @Test
    fun reconnect_sweep_processes_only_items_missing_from_store() = runBlocking {
        val stored = mutableSetOf(
            SweepDedupKey(
                packageName = "com.example.chat",
                contentSignature = "chat-msg-1",
                postTimeMillis = 1_000L,
            ),
        )
        val processed = mutableListOf<TestActiveNotification>()
        val sweep = newSweepCoordinator(
            processNotification = { processed += it },
            existsInStore = { key -> stored.contains(key) },
        )

        sweep.sweep(
            listOf(
                TestActiveNotification(
                    packageName = "com.example.chat",
                    title = "채팅",
                    body = "이미 저장된 메시지",
                    postTimeMillis = 1_000L,
                    contentSignature = "chat-msg-1",
                ),
                TestActiveNotification(
                    packageName = "com.example.mail",
                    title = "메일",
                    body = "새로 도착",
                    postTimeMillis = 2_000L,
                    contentSignature = "mail-new",
                ),
            )
        )

        assertEquals(
            listOf("com.example.mail|mail-new|2000"),
            processed.map { "${it.packageName}|${it.contentSignature}|${it.postTimeMillis}" },
        )
    }

    @Test
    fun reconnect_sweep_skips_smartnoti_self_and_ignored_capture_entries() = runBlocking {
        val processed = mutableListOf<TestActiveNotification>()
        val sweep = newSweepCoordinator(
            processNotification = { processed += it },
            existsInStore = { false },
        )

        sweep.sweep(
            listOf(
                TestActiveNotification(
                    packageName = "com.smartnoti.app",
                    title = "SmartNoti",
                    body = "replacement",
                    postTimeMillis = 1L,
                    contentSignature = "self-1",
                ),
                TestActiveNotification(
                    packageName = "com.example.chat",
                    title = "",
                    body = "",
                    flags = Notification.FLAG_GROUP_SUMMARY,
                    postTimeMillis = 2L,
                    contentSignature = "summary-1",
                ),
                TestActiveNotification(
                    packageName = "com.example.chat",
                    title = "새 메시지",
                    body = "본문",
                    postTimeMillis = 3L,
                    contentSignature = "chat-real",
                ),
            )
        )

        assertEquals(
            listOf("com.example.chat|chat-real"),
            processed.map { "${it.packageName}|${it.contentSignature}" },
        )
    }

    @Test
    fun sweep_defers_until_pending_onboarding_bootstrap_is_consumed() = runBlocking {
        // Scenario: onboarding bootstrap is pending at the moment reconnect fires.
        // Spec: the sweep must wait for the bootstrap to consume the queue. If the
        // bootstrap ends up processing a notification, the sweep's in-process dedup
        // set must skip it — a single notification is processed once total.
        val processed = mutableListOf<String>()
        val bootstrapPending = Flag(initial = true)
        val sweep = newSweepCoordinator(
            processNotification = { processed += "sweep:${it.contentSignature}" },
            existsInStore = { false },
            onboardingBootstrapPending = { bootstrapPending.value },
        )

        val activeNotifications = listOf(
            TestActiveNotification(
                packageName = "com.example.chat",
                title = "새 메시지",
                body = "첫 알림",
                postTimeMillis = 5L,
                contentSignature = "chat-A",
            ),
        )

        // Simulate the onboarding bootstrap running first: it processes the same
        // notification through the shared processor and then clears the pending
        // flag. The sweep records the key so subsequent sweep runs skip it.
        sweep.recordProcessedByBootstrap(
            SweepDedupKey(
                packageName = "com.example.chat",
                contentSignature = "chat-A",
                postTimeMillis = 5L,
            )
        )
        bootstrapPending.value = false

        sweep.sweep(activeNotifications)

        assertEquals(emptyList<String>(), processed)
    }

    @Test
    fun sweep_defers_while_onboarding_bootstrap_pending_then_runs_after_clearance() = runBlocking {
        // Scenario: sweep is called while bootstrap pending flag is still true and
        // bootstrap has not yet recorded any processed keys. Sweep must not touch
        // any notification on this call — the onboarding path owns the first pass.
        val processed = mutableListOf<String>()
        val bootstrapPending = Flag(initial = true)
        val sweep = newSweepCoordinator(
            processNotification = { processed += it.contentSignature },
            existsInStore = { false },
            onboardingBootstrapPending = { bootstrapPending.value },
        )

        val actives = listOf(
            TestActiveNotification(
                packageName = "com.example.mail",
                title = "메일",
                body = "본문",
                postTimeMillis = 100L,
                contentSignature = "mail-1",
            ),
        )

        sweep.sweep(actives)
        assertEquals(emptyList<String>(), processed)

        // After bootstrap clears the flag (without having processed anything),
        // the next sweep picks the item up.
        bootstrapPending.value = false
        sweep.sweep(actives)
        assertEquals(listOf("mail-1"), processed)
    }

    @Test
    fun rapid_repeated_sweeps_deduplicate_via_in_memory_key_set() = runBlocking {
        val processed = mutableListOf<String>()
        val sweep = newSweepCoordinator(
            processNotification = { processed += it.contentSignature },
            existsInStore = { false },
        )

        val actives = listOf(
            TestActiveNotification(
                packageName = "com.example.chat",
                title = "Chat",
                body = "hello",
                postTimeMillis = 10L,
                contentSignature = "chat-1",
            ),
            TestActiveNotification(
                packageName = "com.example.chat",
                title = "Chat",
                body = "world",
                postTimeMillis = 20L,
                contentSignature = "chat-2",
            ),
        )

        // Two quick reconnects in a row — emulates user toggling the permission
        // off/on rapidly. Expected: each notification processed exactly once.
        sweep.sweep(actives)
        sweep.sweep(actives)

        assertEquals(listOf("chat-1", "chat-2"), processed)
        assertTrue("coordinator should expose processed keys for diagnostics", sweep.processedKeySnapshot().size == 2)
    }

    // ---- helpers ----

    private fun newSweepCoordinator(
        processNotification: suspend (TestActiveNotification) -> Unit,
        existsInStore: suspend (SweepDedupKey) -> Boolean,
        onboardingBootstrapPending: suspend () -> Boolean = { false },
    ): ListenerReconnectActiveNotificationSweepCoordinator<TestActiveNotification> {
        return ListenerReconnectActiveNotificationSweepCoordinator(
            appPackageName = "com.smartnoti.app",
            packageNameOf = { it.packageName },
            titleOf = { it.title },
            bodyOf = { it.body },
            notificationFlagsOf = { it.flags },
            dedupKeyOf = {
                SweepDedupKey(
                    packageName = it.packageName,
                    contentSignature = it.contentSignature,
                    postTimeMillis = it.postTimeMillis,
                )
            },
            existsInStore = existsInStore,
            onboardingBootstrapPending = onboardingBootstrapPending,
            processNotification = processNotification,
        )
    }

    private data class TestActiveNotification(
        val packageName: String,
        val title: String,
        val body: String,
        val flags: Int = 0,
        val postTimeMillis: Long,
        val contentSignature: String,
    )

    private class Flag(initial: Boolean) {
        var value: Boolean = initial
    }
}
