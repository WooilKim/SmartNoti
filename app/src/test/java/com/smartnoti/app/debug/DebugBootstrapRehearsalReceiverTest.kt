package com.smartnoti.app.debug

import android.app.Notification
import android.os.Bundle
import android.os.Process
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Locks the contract for plan
 * `docs/plans/2026-04-27-onboarding-bootstrap-non-destructive-recipe.md`
 * Task 1.
 *
 * The rehearsal entry point must:
 * 1. Run the production bootstrap pipeline (`processNotification` for
 *    every active notification not filtered by [shouldProcess]) exactly
 *    once per invocation, regardless of whether the production
 *    onboarding bootstrap pending flag has already been consumed.
 * 2. Record each processed notification via the dedup-recording hook
 *    (mirroring the production
 *    `ListenerReconnectActiveNotificationSweepCoordinator.recordProcessedByBootstrap`
 *    contract) so a follow-up reconnect sweep does not double-process.
 * 3. Filter SmartNoti's own self notifications via the
 *    `ActiveStatusBarNotificationBootstrapper.shouldProcess` rule and
 *    surface the processed/skipped counts to the caller for logcat
 *    emission.
 * 4. Survive the empty-tray case (`processed=0`) without throwing.
 */
@RunWith(RobolectricTestRunner::class)
class DebugBootstrapRehearsalReceiverTest {

    @Test
    fun rehearse_invokes_process_for_every_eligible_active_notification() = runBlocking {
        val processed = mutableListOf<String>()
        val recordedDedupIds = mutableListOf<Int>()
        val actives = arrayOf(
            sbn(packageName = "com.example.bank", key = "bank#1", title = "Bank", body = "인증번호 000000"),
            sbn(packageName = "com.example.promo", key = "promo#1", title = "Promo", body = "광고 배너"),
        )

        val result = DebugBootstrapRehearsal.rehearse(
            appPackageName = "com.smartnoti.app",
            activeNotifications = actives,
            recordProcessedByBootstrap = { recordedDedupIds += it.id },
            processNotification = { processed += "${it.packageName}#${it.id}" },
        )

        assertEquals(
            listOf(
                "com.example.bank#${"bank#1".hashCode()}",
                "com.example.promo#${"promo#1".hashCode()}",
            ),
            processed,
        )
        assertEquals(2, result.processedCount)
        assertEquals(0, result.skippedCount)
        assertEquals(
            "every processed notification must also be recorded into the sweep dedup set",
            2,
            recordedDedupIds.size,
        )
    }

    @Test
    fun rehearse_runs_independently_of_production_onboarding_flag_state() = runBlocking {
        // The rehearsal entry point must not consult or mutate the
        // production `SettingsRepository.isOnboardingBootstrapPending`
        // flag — the very purpose of the hook is to re-exercise the
        // pipeline _after_ the one-shot flag has been consumed by a
        // real cold-launch. We assert the contract by verifying that
        // two consecutive invocations both run the bootstrap pipeline
        // (production semantics would refuse the second run).
        val processed = mutableListOf<String>()
        val actives = arrayOf(
            sbn(packageName = "com.example.bank", key = "bank#1", title = "Bank", body = "인증번호 000000"),
        )

        val first = DebugBootstrapRehearsal.rehearse(
            appPackageName = "com.smartnoti.app",
            activeNotifications = actives,
            recordProcessedByBootstrap = {},
            processNotification = { processed += "${it.packageName}#${it.id}#first" },
        )
        val second = DebugBootstrapRehearsal.rehearse(
            appPackageName = "com.smartnoti.app",
            activeNotifications = actives,
            recordProcessedByBootstrap = {},
            processNotification = { processed += "${it.packageName}#${it.id}#second" },
        )

        assertEquals(1, first.processedCount)
        assertEquals(1, second.processedCount)
        assertTrue(processed.any { it.endsWith("#first") })
        assertTrue(processed.any { it.endsWith("#second") })
    }

    @Test
    fun rehearse_with_empty_tray_emits_zero_counts_without_throwing() = runBlocking {
        val processed = mutableListOf<String>()
        val recordedDedupIds = mutableListOf<Int>()

        val result = DebugBootstrapRehearsal.rehearse(
            appPackageName = "com.smartnoti.app",
            activeNotifications = emptyArray(),
            recordProcessedByBootstrap = { recordedDedupIds += it.id },
            processNotification = { processed += "${it.packageName}#${it.id}" },
        )

        assertEquals(0, result.processedCount)
        assertEquals(0, result.skippedCount)
        assertTrue(processed.isEmpty())
        assertTrue(recordedDedupIds.isEmpty())
    }

    @Test
    fun rehearse_skips_self_notifications_and_blank_group_summaries() = runBlocking {
        val processed = mutableListOf<String>()
        val actives = arrayOf(
            sbn(packageName = "com.smartnoti.app", key = "self#1", title = "SmartNoti", body = "replacement"),
            sbn(
                packageName = "com.example.chat",
                key = "summary#1",
                title = "",
                body = "",
                flags = Notification.FLAG_GROUP_SUMMARY,
            ),
            sbn(packageName = "com.example.chat", key = "chat#1", title = "새 메시지", body = "본문"),
        )

        val result = DebugBootstrapRehearsal.rehearse(
            appPackageName = "com.smartnoti.app",
            activeNotifications = actives,
            recordProcessedByBootstrap = {},
            processNotification = { processed += "${it.packageName}#${it.id}" },
        )

        assertEquals(
            listOf("com.example.chat#${"chat#1".hashCode()}"),
            processed,
        )
        assertEquals(1, result.processedCount)
        assertEquals(
            "self notification + blank group summary must both count as skipped",
            2,
            result.skippedCount,
        )
    }

    private fun sbn(
        packageName: String,
        key: String,
        title: String,
        body: String,
        flags: Int = 0,
    ): StatusBarNotification {
        val notification = Notification().apply {
            extras = Bundle().apply {
                putString(Notification.EXTRA_TITLE, title)
                putString(Notification.EXTRA_TEXT, body)
            }
            this.flags = flags
        }
        return StatusBarNotification(
            packageName,
            packageName,
            key.hashCode(),
            key,
            0,
            0,
            0,
            notification,
            Process.myUserHandle(),
            System.currentTimeMillis(),
        )
    }
}
