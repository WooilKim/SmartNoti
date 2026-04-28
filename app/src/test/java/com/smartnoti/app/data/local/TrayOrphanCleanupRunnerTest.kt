package com.smartnoti.app.data.local

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Failing tests for plan
 * `docs/plans/2026-04-28-fix-issue-524-tray-orphan-cleanup-button.md`
 * Task 1 (Issue #524) — user-triggered Settings tray-cleanup runner contract.
 *
 * Pins the four product invariants identified in the plan:
 *
 *  1. Identification: scan the active StatusBarNotification snapshot once,
 *     extract the source `packageName` set from every entry whose `groupKey`
 *     starts with `smartnoti_silent_group_app:`, and treat any non-SmartNoti
 *     entry whose `packageName` is in that set as a cleanup candidate.
 *  2. Protection: a candidate whose `flags` intersect
 *     `FLAG_FOREGROUND_SERVICE | FLAG_NO_CLEAR | FLAG_ONGOING_EVENT` is
 *     reported as `skippedProtectedCount` and never handed to the gateway —
 *     a music / call / nav / foreground-service notification cancel would
 *     break the source app contract (see
 *     `docs/journeys/protected-source-notifications.md`).
 *  3. Listener-not-bound resilience: the runner short-circuits with
 *     `notBound = true` and zero gateway calls when the inspector reports
 *     the listener is not currently connected. Mirrors
 *     [MigrateOrphanedSourceCancellationRunner.Result.DeferredListenerNotBound].
 *  4. Preview parity: `preview()` runs the same identification logic but
 *     never invokes the gateway, exposing a `PreviewResult` that the
 *     Settings card uses to render the live "원본 알림 N건 정리 가능" text.
 *
 * RED signals (compile errors expected on `main`):
 *  - [ActiveTrayInspector]                — Task 2 introduces the read-only port
 *  - [ActiveTrayEntry]                    — Task 2 introduces the data class
 *  - [TrayOrphanCleanupRunner]            — Task 2 introduces the runner
 *  - [TrayOrphanCleanupRunner.PreviewResult] / [TrayOrphanCleanupRunner.CleanupResult]
 *
 * The fake [SourceCancellationGateway] is reused from
 * [MigrateOrphanedSourceCancellationRunnerTest] (Issue #511) — same port,
 * different driver — so the cancel call shape is end-to-end identical.
 *
 * Run via Robolectric so [android.util.Log.w] (used by the runner for the
 * PERSISTENT_PROTECTED skip diagnostic) resolves to a real logger instead
 * of the JVM `Stub!` that throws "Method w in android.util.Log not mocked".
 */
@RunWith(RobolectricTestRunner::class)
class TrayOrphanCleanupRunnerTest {

    @Test
    fun fresh_cleanup_cancels_orphan_sources_and_skips_protected() = runBlocking {
        // Fixture A from the plan: 5 SmartNoti `silent_group_app:<pkg>` entries
        // (always preserved) + 5 source orphan entries from those same packages.
        // The 5th source (Spotify) carries `FLAG_NO_CLEAR | FLAG_ONGOING_EVENT`
        // so the runner must classify it as PERSISTENT_PROTECTED and skip it.
        // Flag values are inlined as literals (mirrors `Notification.FLAG_*`)
        // because the Android `Notification` class is a `Stub!` at unit-test
        // time — same trick `ProtectedSourceNotificationDetector` uses for
        // its category strings.
        val protectedFlags = NOTIFICATION_FLAG_NO_CLEAR or NOTIFICATION_FLAG_ONGOING_EVENT

        val entries = buildList {
            // 5 SmartNoti silent_group entries — these MUST be preserved
            // because the SmartNoti app posts them under
            // `smartnoti_silent_group_app:<sourcePkg>` group keys, and the
            // packageName is SMARTNOTI_PKG (not the source app).
            add(smartNotiGroupEntry("com.naver.android.search"))
            add(smartNotiGroupEntry("com.coupang.eats"))
            add(smartNotiGroupEntry("com.kakao.talk"))
            add(smartNotiGroupEntry("com.linkedin.android"))
            add(smartNotiGroupEntry("com.spotify.music"))

            // 5 source-app entries — 4 cleanable, 1 PERSISTENT_PROTECTED.
            add(sourceEntry("com.naver.android.search", flags = 0))
            add(sourceEntry("com.coupang.eats", flags = 0))
            add(sourceEntry("com.kakao.talk", flags = 0))
            add(sourceEntry("com.linkedin.android", flags = 0))
            add(sourceEntry("com.spotify.music", flags = protectedFlags))
        }

        val inspector = FakeActiveTrayInspector(listenerBound = true, entries = entries)
        val gateway = RecordingSourceCancellationGateway()
        val runner = TrayOrphanCleanupRunner(inspector = inspector, gateway = gateway)

        val result = runner.cleanup()

        assertFalse("listener bound must NOT yield notBound", result.notBound)
        assertEquals(
            "4 source orphans must be cancelled; PERSISTENT_PROTECTED Spotify skipped",
            4,
            result.cancelledCount,
        )
        assertEquals(
            "Spotify (FLAG_NO_CLEAR | FLAG_ONGOING_EVENT) must be reported as protected-skip",
            1,
            result.skippedProtectedCount,
        )
        assertEquals(
            "Gateway must receive cancel for the 4 cleanable source keys (no SmartNoti, no Spotify)",
            setOf(
                sourceKey("com.naver.android.search"),
                sourceKey("com.coupang.eats"),
                sourceKey("com.kakao.talk"),
                sourceKey("com.linkedin.android"),
            ),
            gateway.cancelledKeys.toSet(),
        )
        assertEquals(
            "Each candidate cancelled exactly once",
            4,
            gateway.cancelledKeys.size,
        )
    }

    @Test
    fun listener_not_bound_returns_zero_no_calls() = runBlocking {
        // Fixture B: even with ostensibly cancelable entries, an unbound
        // listener (user revoked notification access, OS reclaimed the
        // service, etc.) means `cancelNotification(key)` cannot run. Surface
        // the `notBound = true` marker so the UI can show the toast
        // ("알림 권한이 활성일 때만 가능해요"); zero gateway calls.
        val entries = listOf(
            smartNotiGroupEntry("com.naver.android.search"),
            sourceEntry("com.naver.android.search", flags = 0),
        )

        val inspector = FakeActiveTrayInspector(listenerBound = false, entries = entries)
        val gateway = RecordingSourceCancellationGateway()
        val runner = TrayOrphanCleanupRunner(inspector = inspector, gateway = gateway)

        val result = runner.cleanup()

        assertTrue("listener not bound → notBound must be true", result.notBound)
        assertEquals(0, result.cancelledCount)
        assertEquals(0, result.skippedProtectedCount)
        assertTrue(
            "notBound must invoke no gateway calls",
            gateway.cancelledKeys.isEmpty(),
        )
    }

    @Test
    fun empty_tray_returns_zero() = runBlocking {
        // Fixture C: the listener is bound but the active snapshot is empty
        // (post-cleanup state). Runner must return zeros without churn.
        val inspector = FakeActiveTrayInspector(listenerBound = true, entries = emptyList())
        val gateway = RecordingSourceCancellationGateway()
        val runner = TrayOrphanCleanupRunner(inspector = inspector, gateway = gateway)

        val result = runner.cleanup()

        assertFalse(result.notBound)
        assertEquals(0, result.cancelledCount)
        assertEquals(0, result.skippedProtectedCount)
        assertTrue(gateway.cancelledKeys.isEmpty())
    }

    @Test
    fun preview_does_not_cancel() = runBlocking {
        // Fixture D: preview runs the same identification algorithm but
        // never touches the gateway — the Settings card calls preview() on
        // every render to keep the "정리 가능 N건" line live.
        val entries = listOf(
            smartNotiGroupEntry("com.naver.android.search"),
            smartNotiGroupEntry("com.coupang.eats"),
            sourceEntry("com.naver.android.search", flags = 0),
            sourceEntry("com.coupang.eats", flags = 0),
            // PERSISTENT_PROTECTED — should NOT show in preview candidates
            // because the user cannot actually clean it up.
            sourceEntry("com.spotify.music", flags = NOTIFICATION_FLAG_FOREGROUND_SERVICE),
        )

        val inspector = FakeActiveTrayInspector(listenerBound = true, entries = entries)
        val gateway = RecordingSourceCancellationGateway()
        val runner = TrayOrphanCleanupRunner(inspector = inspector, gateway = gateway)

        val preview = runner.preview()

        assertEquals(
            "Preview must enumerate the 2 cancelable sources only",
            2,
            preview.candidateCount,
        )
        assertEquals(
            "Preview package list must dedupe and exclude protected sources",
            setOf("com.naver.android.search", "com.coupang.eats"),
            preview.candidatePackageNames.toSet(),
        )
        assertTrue(
            "preview() must NEVER invoke the gateway",
            gateway.cancelledKeys.isEmpty(),
        )
    }

    // ─────────────────────────────────────────────────────────────────────
    // Scoped `cleanup(targetPackages)` overload — plan
    // `docs/plans/2026-04-28-tray-orphan-cleanup-scoped-overload.md` Task 1.
    //
    // Pins the precision contract for the v1 정리함 high-volume suggestion
    // accept path: when the user taps `[예, 묶을게요]`, only the source
    // orphans for the packageName they just suppressed are cancelled. Other
    // packages' orphans (from earlier accepts or unrelated sources) are
    // preserved. The unscoped `cleanup()` overload remains the entry point
    // for Settings → "트레이 정리" (#524) — same runner, different surface.
    //
    // Open question resolved by the plan: `cleanup(emptySet())` is a noop
    // (caller's empty intent is honoured), not a fallback to unscoped
    // cleanup. The third test below pins that semantic.
    // ─────────────────────────────────────────────────────────────────────

    @Test
    fun scoped_cleanup_only_cancels_target_packages_and_preserves_others() = runBlocking {
        // 5 source orphans + 5 SmartNoti silent_group entries (one per
        // package, so each source qualifies as a candidate). Caller passes
        // a singleton set targeting only Naver — the other 4 source orphans
        // must remain in the tray untouched.
        val entries = buildList {
            add(smartNotiGroupEntry("com.nhn.android.search"))
            add(smartNotiGroupEntry("com.kakao.talk"))
            add(smartNotiGroupEntry("com.coupang.eats"))
            add(smartNotiGroupEntry("com.genesis.mygenesis"))
            add(smartNotiGroupEntry("com.samsung.android.shealth"))

            add(sourceEntry("com.nhn.android.search", flags = 0))
            add(sourceEntry("com.kakao.talk", flags = 0))
            add(sourceEntry("com.coupang.eats", flags = 0))
            add(sourceEntry("com.genesis.mygenesis", flags = 0))
            add(sourceEntry("com.samsung.android.shealth", flags = 0))
        }

        val inspector = FakeActiveTrayInspector(listenerBound = true, entries = entries)
        val gateway = RecordingSourceCancellationGateway()
        val runner = TrayOrphanCleanupRunner(inspector = inspector, gateway = gateway)

        val result = runner.cleanup(setOf("com.nhn.android.search"))

        assertFalse("listener bound must NOT yield notBound", result.notBound)
        assertEquals(
            "Only the targeted Naver source orphan must be cancelled",
            1,
            result.cancelledCount,
        )
        assertEquals(
            "No PERSISTENT_PROTECTED entries in this fixture",
            0,
            result.skippedProtectedCount,
        )
        assertEquals(
            "Gateway must receive exactly the Naver source key",
            listOf(sourceKey("com.nhn.android.search")),
            gateway.cancelledKeys,
        )
    }

    @Test
    fun scoped_cleanup_with_empty_target_set_is_noop() = runBlocking {
        // Same fixture as the precision test — but the caller passes an
        // empty set. The plan's open-question resolution: empty set means
        // noop (the caller's intent is empty), not fallback to unscoped.
        val entries = buildList {
            add(smartNotiGroupEntry("com.nhn.android.search"))
            add(smartNotiGroupEntry("com.kakao.talk"))
            add(smartNotiGroupEntry("com.coupang.eats"))
            add(smartNotiGroupEntry("com.genesis.mygenesis"))
            add(smartNotiGroupEntry("com.samsung.android.shealth"))

            add(sourceEntry("com.nhn.android.search", flags = 0))
            add(sourceEntry("com.kakao.talk", flags = 0))
            add(sourceEntry("com.coupang.eats", flags = 0))
            add(sourceEntry("com.genesis.mygenesis", flags = 0))
            add(sourceEntry("com.samsung.android.shealth", flags = 0))
        }

        val inspector = FakeActiveTrayInspector(listenerBound = true, entries = entries)
        val gateway = RecordingSourceCancellationGateway()
        val runner = TrayOrphanCleanupRunner(inspector = inspector, gateway = gateway)

        val result = runner.cleanup(emptySet())

        assertFalse(result.notBound)
        assertEquals(
            "Empty target set must yield zero cancels (noop, not unscoped fallback)",
            0,
            result.cancelledCount,
        )
        assertEquals(0, result.skippedProtectedCount)
        assertTrue(
            "Empty target set must invoke no gateway calls",
            gateway.cancelledKeys.isEmpty(),
        )
    }

    @Test
    fun scoped_cleanup_still_skips_protected_target_entries() = runBlocking {
        // Two source orphans for the SAME targeted packageName: one
        // unprotected, one PERSISTENT_PROTECTED (FLAG_FOREGROUND_SERVICE).
        // The protected entry must be reported as skippedProtectedCount and
        // never handed to the gateway, even though its packageName is in
        // the target set.
        val entries = listOf(
            smartNotiGroupEntry("com.nhn.android.search"),
            sourceEntry("com.nhn.android.search", flags = 0),
            ActiveTrayEntry(
                key = "0|com.nhn.android.search|9999|sourceTag2|10001",
                packageName = "com.nhn.android.search",
                groupKey = null,
                flags = NOTIFICATION_FLAG_FOREGROUND_SERVICE,
            ),
        )

        val inspector = FakeActiveTrayInspector(listenerBound = true, entries = entries)
        val gateway = RecordingSourceCancellationGateway()
        val runner = TrayOrphanCleanupRunner(inspector = inspector, gateway = gateway)

        val result = runner.cleanup(setOf("com.nhn.android.search"))

        assertFalse(result.notBound)
        assertEquals(
            "The unprotected Naver source orphan must be cancelled",
            1,
            result.cancelledCount,
        )
        assertEquals(
            "The FLAG_FOREGROUND_SERVICE entry must be reported as protected-skip",
            1,
            result.skippedProtectedCount,
        )
        assertEquals(
            "Gateway must receive only the unprotected source key",
            listOf(sourceKey("com.nhn.android.search")),
            gateway.cancelledKeys,
        )
    }

    @Test
    fun scoped_cleanup_short_circuits_when_listener_not_bound() = runBlocking {
        // Listener not bound: scoped overload mirrors the unscoped path —
        // notBound = true, zero cancels, zero gateway calls. The runner
        // never even reads the target set in this branch.
        val inspector = FakeActiveTrayInspector(listenerBound = false, entries = emptyList())
        val gateway = RecordingSourceCancellationGateway()
        val runner = TrayOrphanCleanupRunner(inspector = inspector, gateway = gateway)

        val result = runner.cleanup(setOf("com.nhn.android.search"))

        assertTrue("listener not bound → notBound must be true", result.notBound)
        assertEquals(0, result.cancelledCount)
        assertEquals(0, result.skippedProtectedCount)
        assertTrue(
            "notBound must invoke no gateway calls",
            gateway.cancelledKeys.isEmpty(),
        )
    }

    private fun smartNotiGroupEntry(sourcePkg: String): ActiveTrayEntry =
        ActiveTrayEntry(
            key = "0|$SMARTNOTI_PKG|${sourcePkg.hashCode()}|smartnoti|10000",
            packageName = SMARTNOTI_PKG,
            groupKey = "$SILENT_GROUP_PREFIX$sourcePkg",
            flags = 0,
        )

    private fun sourceEntry(packageName: String, flags: Int): ActiveTrayEntry =
        ActiveTrayEntry(
            key = sourceKey(packageName),
            packageName = packageName,
            groupKey = null,
            flags = flags,
        )

    private fun sourceKey(packageName: String): String =
        "0|$packageName|${packageName.hashCode()}|sourceTag|10001"

    private companion object {
        const val SMARTNOTI_PKG = "com.smartnoti.app"
        const val SILENT_GROUP_PREFIX = "smartnoti_silent_group_app:"

        // android.app.Notification flag literals — duplicated here so the
        // unit test runs without the Android framework. Values must match
        // the platform constants the runner reads via Notification.FLAG_*.
        const val NOTIFICATION_FLAG_FOREGROUND_SERVICE = 0x00000040
        const val NOTIFICATION_FLAG_NO_CLEAR = 0x00000020
        const val NOTIFICATION_FLAG_ONGOING_EVENT = 0x00000002
    }
}

/**
 * Test-only fake of the production [ActiveTrayInspector] port. The
 * production impl wraps `NotificationListenerService.activeNotifications`
 * and exposes them as immutable [ActiveTrayEntry] rows so the runner can
 * be unit-tested without the Android framework.
 */
internal class FakeActiveTrayInspector(
    private val listenerBound: Boolean,
    private val entries: List<ActiveTrayEntry>,
) : ActiveTrayInspector {
    override fun isListenerBound(): Boolean = listenerBound
    override fun listActive(): List<ActiveTrayEntry> = entries
}
