package com.smartnoti.app.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.smartnoti.app.data.settings.SettingsRepository
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
 * Failing tests for plan
 * `docs/plans/2026-04-28-fix-issue-511-cancel-source-on-replacement.md`
 * Task 1, item 5 (Issue #511) — migration runner contract.
 *
 * Pins the cold-start one-shot migration that recovers the **current Railway
 * cohort** — users who already have rows in the DB with
 * `replacementNotificationIssued = 1` (after the Task 3 cancel-after-post
 * fix backfills it correctly) AND the source notification still in the
 * tray. Without this runner, those trapped duplicates linger forever
 * because Task 3 only fixes NEW captures going forward.
 *
 * Mirrors [com.smartnoti.app.data.local.MigrateAppLabelRunner] from
 * `docs/plans/2026-04-27-fix-issue-503-app-label-resolver-fallback-chain.md`:
 *
 *   1. Gate on a DataStore flag
 *      (`isMigrateOrphanedSourceCancellationV1Applied`). If true, return
 *      [Result.AlreadyMigrated] immediately so cold starts after the first
 *      short-circuit cheaply.
 *   2. Otherwise, ask the DAO for `(sourceEntryKey, packageName)` pairs of
 *      rows where `replacementNotificationIssued = 1` AND `sourceEntryKey`
 *      is non-null. For each pair, ask the [ActiveSourceNotificationInspector]
 *      whether the source key is still active in the tray. If yes, invoke
 *      [SourceCancellationGateway.cancel] for that key — this is the same
 *      `NotificationListenerService.cancelNotification(key)` path the
 *      pipeline uses (plan Task 3 wires the production impl).
 *   3. Flip the flag at the end so the scan is skipped on every cold start
 *      after the first.
 *
 * Listener-not-bound resilience (per plan Risks R4): if the inspector
 * reports that the listener is not currently bound, the runner returns
 * [Result.DeferredListenerNotBound] WITHOUT flipping the flag, so a
 * subsequent cold start retries.
 *
 * RED signals on `main`:
 *  - [MigrateOrphanedSourceCancellationRunner]                 — Task 5 introduces the runner
 *  - [ActiveSourceNotificationInspector]                       — Task 5 introduces the port
 *  - [SourceCancellationGateway]                               — Task 3 introduces the port
 *  - [NotificationDao.selectOrphanedSourceCancellationRows]    — Task 5 introduces the query
 *  - [SettingsRepository.isMigrateOrphanedSourceCancellationV1Applied]/setter — Task 5 introduces the flag
 */
@RunWith(RobolectricTestRunner::class)
class MigrateOrphanedSourceCancellationRunnerTest {

    private lateinit var context: Context
    private lateinit var database: SmartNotiDatabase
    private lateinit var dao: NotificationDao
    private lateinit var settingsRepository: SettingsRepository

    @Before
    fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, SmartNotiDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.notificationDao()

        SettingsRepository.clearInstanceForTest()
        settingsRepository = SettingsRepository.getInstance(context)
        settingsRepository.clearAllForTest()
    }

    @After
    fun tearDown() {
        database.close()
        SettingsRepository.clearInstanceForTest()
        context.deleteDatabase("smartnoti.db")
    }

    @Test
    fun fresh_install_with_two_orphaned_replacement_rows_cancels_both_active_sources_and_flips_flag() = runBlocking {
        // Two Railway-style rows: replacementNotificationIssued = true on both.
        // Inspector reports both source keys still active in the tray.
        // Runner must invoke cancel exactly once per source and flip the flag.
        seedRow(id = "row-build", sourceEntryKey = SOURCE_KEY_BUILD, replacementIssued = true)
        seedRow(id = "row-deploy", sourceEntryKey = SOURCE_KEY_DEPLOY, replacementIssued = true)

        val inspector = FakeActiveSourceNotificationInspector(
            listenerBound = true,
            activeKeys = setOf(SOURCE_KEY_BUILD, SOURCE_KEY_DEPLOY),
        )
        val gateway = RecordingSourceCancellationGateway()
        val runner = MigrateOrphanedSourceCancellationRunner(
            notificationDao = dao,
            inspector = inspector,
            gateway = gateway,
            settingsRepository = settingsRepository,
        )

        val result = runner.run()

        assertTrue("first run must report Migrated", result is MigrateOrphanedSourceCancellationRunner.Result.Migrated)
        assertEquals(
            "Both orphaned source keys must be cancelled",
            setOf(SOURCE_KEY_BUILD, SOURCE_KEY_DEPLOY),
            gateway.cancelledKeys.toSet(),
        )
        assertEquals(
            "Each orphan must be cancelled exactly once",
            2,
            gateway.cancelledKeys.size,
        )
        assertTrue(settingsRepository.isMigrateOrphanedSourceCancellationV1Applied())
    }

    @Test
    fun second_run_short_circuits_with_already_migrated_and_does_not_recancel() = runBlocking {
        seedRow(id = "row-build", sourceEntryKey = SOURCE_KEY_BUILD, replacementIssued = true)
        val inspector = FakeActiveSourceNotificationInspector(
            listenerBound = true,
            activeKeys = setOf(SOURCE_KEY_BUILD),
        )
        val gateway = RecordingSourceCancellationGateway()
        val runner = MigrateOrphanedSourceCancellationRunner(
            notificationDao = dao,
            inspector = inspector,
            gateway = gateway,
            settingsRepository = settingsRepository,
        )

        runner.run()
        gateway.cancelledKeys.clear()

        val result = runner.run()

        assertTrue(
            "second run must short-circuit with AlreadyMigrated",
            result is MigrateOrphanedSourceCancellationRunner.Result.AlreadyMigrated,
        )
        assertTrue(
            "AlreadyMigrated must not invoke any further cancels",
            gateway.cancelledKeys.isEmpty(),
        )
    }

    @Test
    fun source_no_longer_active_in_tray_is_skipped_and_flag_still_flips() = runBlocking {
        // The user (or the OS) cleared the source notification before the
        // migration ran. Runner must NOT call cancel for that key (it would
        // be a no-op anyway, but we should not log a misleading "cancelled
        // 1 source" metric). The flag still flips because we successfully
        // scanned the cohort.
        seedRow(id = "row-stale", sourceEntryKey = SOURCE_KEY_BUILD, replacementIssued = true)

        val inspector = FakeActiveSourceNotificationInspector(
            listenerBound = true,
            activeKeys = emptySet(),
        )
        val gateway = RecordingSourceCancellationGateway()
        val runner = MigrateOrphanedSourceCancellationRunner(
            notificationDao = dao,
            inspector = inspector,
            gateway = gateway,
            settingsRepository = settingsRepository,
        )

        val result = runner.run()

        assertTrue(result is MigrateOrphanedSourceCancellationRunner.Result.Migrated)
        assertTrue(
            "Source already cleared from tray must NOT trigger a cancel call",
            gateway.cancelledKeys.isEmpty(),
        )
        assertTrue(
            "Successful scan (even with 0 actual cancels) flips the flag",
            settingsRepository.isMigrateOrphanedSourceCancellationV1Applied(),
        )
    }

    @Test
    fun row_without_replacement_issued_is_left_alone() = runBlocking {
        // Healthy row: source was never replaced (e.g. PRIORITY pass-through
        // recorded the sourceEntryKey but no SmartNoti replacement was
        // posted). Runner MUST NOT cancel — that would silently delete a
        // notification the user is actively expecting.
        seedRow(id = "row-priority", sourceEntryKey = SOURCE_KEY_BUILD, replacementIssued = false)

        val inspector = FakeActiveSourceNotificationInspector(
            listenerBound = true,
            activeKeys = setOf(SOURCE_KEY_BUILD),
        )
        val gateway = RecordingSourceCancellationGateway()
        val runner = MigrateOrphanedSourceCancellationRunner(
            notificationDao = dao,
            inspector = inspector,
            gateway = gateway,
            settingsRepository = settingsRepository,
        )

        val result = runner.run()

        assertTrue(result is MigrateOrphanedSourceCancellationRunner.Result.Migrated)
        assertTrue(
            "PRIORITY-style row (replacementNotificationIssued = false) must NEVER be cancelled",
            gateway.cancelledKeys.isEmpty(),
        )
    }

    @Test
    fun listener_not_bound_defers_without_flipping_flag() = runBlocking {
        // Plan Risks R4: if the listener is not currently bound, the runner
        // cannot meaningfully cancel anything — the listener-only
        // `cancelNotification(key)` API requires a live service. The runner
        // returns DeferredListenerNotBound and leaves the flag unflipped so
        // a future cold start (when the listener has bound) retries.
        seedRow(id = "row-build", sourceEntryKey = SOURCE_KEY_BUILD, replacementIssued = true)

        val inspector = FakeActiveSourceNotificationInspector(
            listenerBound = false,
            activeKeys = emptySet(),
        )
        val gateway = RecordingSourceCancellationGateway()
        val runner = MigrateOrphanedSourceCancellationRunner(
            notificationDao = dao,
            inspector = inspector,
            gateway = gateway,
            settingsRepository = settingsRepository,
        )

        val result = runner.run()

        assertTrue(
            "Listener not bound → runner must defer",
            result is MigrateOrphanedSourceCancellationRunner.Result.DeferredListenerNotBound,
        )
        assertTrue(
            "Deferred run must invoke no cancels",
            gateway.cancelledKeys.isEmpty(),
        )
        assertFalse(
            "Flag MUST stay unflipped so the next cold start retries",
            settingsRepository.isMigrateOrphanedSourceCancellationV1Applied(),
        )
    }

    private suspend fun seedRow(
        id: String,
        sourceEntryKey: String,
        replacementIssued: Boolean,
    ) {
        dao.upsert(
            NotificationEntity(
                id = id,
                appName = "Gmail",
                packageName = GMAIL_PACKAGE,
                sender = "Railway",
                title = "Railway",
                body = "Build failed",
                postedAtMillis = 1_700_000_000_000,
                status = "SILENT",
                reasonTags = "",
                score = null,
                isBundled = false,
                isPersistent = false,
                contentSignature = "sig-$id",
                sourceEntryKey = sourceEntryKey,
                replacementNotificationIssued = replacementIssued,
            ),
        )
    }

    private companion object {
        const val GMAIL_PACKAGE = "com.google.android.gm"
        const val SOURCE_KEY_BUILD = "0|com.google.android.gm|7|RailwayBuildFailed|10001"
        const val SOURCE_KEY_DEPLOY = "0|com.google.android.gm|8|RailwayDeployCrashed|10002"
    }
}

/**
 * Test-only fake of the production [ActiveSourceNotificationInspector] port
 * the runner uses to query whether a source key is still in the tray. The
 * production implementation wraps `NotificationListenerService.activeNotifications`
 * and exposes `listenerBound` based on whether the static activeService
 * singleton in `SmartNotiNotificationListenerService` is non-null.
 */
internal class FakeActiveSourceNotificationInspector(
    private val listenerBound: Boolean,
    private val activeKeys: Set<String>,
) : ActiveSourceNotificationInspector {
    override fun isListenerBound(): Boolean = listenerBound
    override fun isSourceKeyActive(sourceEntryKey: String): Boolean =
        sourceEntryKey in activeKeys
}

/**
 * Test-only fake of the production [SourceCancellationGateway] port. The
 * production implementation forwards to the active listener's
 * `cancelNotification(key)` on `Dispatchers.Main` (mirroring
 * `ListenerSourceTrayActions.cancelSource` in
 * `SmartNotiNotificationListenerService`).
 */
internal class RecordingSourceCancellationGateway : SourceCancellationGateway {
    val cancelledKeys: MutableList<String> = mutableListOf()
    override fun cancel(sourceEntryKey: String) {
        cancelledKeys += sourceEntryKey
    }
}
