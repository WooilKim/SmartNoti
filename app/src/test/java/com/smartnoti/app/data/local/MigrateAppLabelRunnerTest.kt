package com.smartnoti.app.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.smartnoti.app.data.settings.SettingsRepository
import com.smartnoti.app.notification.AppLabelResolver
import com.smartnoti.app.notification.FakePackageLabelSource
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Failing tests for plan
 * `docs/plans/2026-04-27-fix-issue-503-app-label-resolver-fallback-chain.md`
 * Task 1 (Issue #503) — migration-runner contract.
 *
 * Pins the cold-start one-shot migration that re-resolves rows whose
 * `appName == packageName` (the 16-package regression observed in the wild).
 * The runner mirrors [com.smartnoti.app.data.categories.MigratePromoCategoryActionRunner]:
 *
 *   1. Gate on a DataStore flag (`isAppLabelResolutionMigrationV1Applied`).
 *      If true, return [Result.AlreadyMigrated] immediately.
 *   2. Otherwise, ask the DAO for the DISTINCT packageNames where
 *      `appName == packageName`. Resolve each via [AppLabelResolver]; if the
 *      resolver returns a label that differs from the packageName, batch-
 *      `UPDATE notifications SET appName=:label WHERE packageName=:pkg AND
 *      appName=packageName`.
 *   3. Flip the flag at the end so cold starts after the first short-circuit.
 *
 * RED signals (compile errors expected on `main`):
 *  - [MigrateAppLabelRunner]                                    — Task 4 introduces the runner
 *  - [AppLabelResolver]                                         — Task 2 introduces the resolver
 *  - [com.smartnoti.app.notification.PackageLabelSource]        — Task 2 introduces the port
 *  - [NotificationDao.selectPackagesNeedingLabelResolution]     — Task 4 introduces the query
 *  - [NotificationDao.updateAppLabel]                           — Task 4 introduces the update
 *  - [SettingsRepository.isAppLabelResolutionMigrationV1Applied]/setter — Task 4 introduces the flag
 *
 * Once Task 2 + Task 4 land, all four cases below must turn GREEN.
 */
@RunWith(RobolectricTestRunner::class)
class MigrateAppLabelRunnerTest {

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
    fun fresh_install_with_three_packageName_appName_rows_re_resolves_all_three_and_flips_flag() = runBlocking {
        seedRow(id = "row-gmail",   packageName = GMAIL_PACKAGE,   appName = GMAIL_PACKAGE)
        seedRow(id = "row-coupang", packageName = COUPANG_PACKAGE, appName = COUPANG_PACKAGE)
        seedRow(id = "row-naver",   packageName = NAVER_PACKAGE,   appName = NAVER_PACKAGE)

        val resolver = AppLabelResolver(
            FakePackageLabelSource(
                loadLabelReturns = mapOf(
                    GMAIL_PACKAGE to "Gmail",
                    COUPANG_PACKAGE to "쿠팡",
                    NAVER_PACKAGE to "네이버",
                ),
            ),
        )
        val runner = MigrateAppLabelRunner(
            notificationDao = dao,
            appLabelResolver = resolver,
            settingsRepository = settingsRepository,
        )

        val result = runner.run()

        assertTrue("first run must report Migrated", result is MigrateAppLabelRunner.Result.Migrated)
        val rows = dao.observeAll().first().associateBy { it.id }
        assertEquals("Gmail",  rows.getValue("row-gmail").appName)
        assertEquals("쿠팡",   rows.getValue("row-coupang").appName)
        assertEquals("네이버", rows.getValue("row-naver").appName)
        assertTrue(settingsRepository.isAppLabelResolutionMigrationV1Applied())
    }

    @Test
    fun second_run_is_already_migrated_and_does_not_touch_rows() = runBlocking {
        seedRow(id = "row-gmail", packageName = GMAIL_PACKAGE, appName = GMAIL_PACKAGE)
        val resolver = AppLabelResolver(
            FakePackageLabelSource(loadLabelReturns = mapOf(GMAIL_PACKAGE to "Gmail")),
        )
        val runner = MigrateAppLabelRunner(
            notificationDao = dao,
            appLabelResolver = resolver,
            settingsRepository = settingsRepository,
        )
        runner.run()

        // Simulate the user manually editing the row post-migration. The runner
        // must NOT overwrite that on the second cold start.
        val firstRun = dao.observeAll().first().single()
        dao.upsert(firstRun.copy(appName = "내가 직접 바꾼 이름"))

        val result = runner.run()

        assertTrue(
            "second run must report AlreadyMigrated",
            result is MigrateAppLabelRunner.Result.AlreadyMigrated,
        )
        assertEquals(
            "Post-migration manual edit must be preserved (flag-gated short-circuit)",
            "내가 직접 바꾼 이름",
            dao.observeAll().first().single().appName,
        )
    }

    @Test
    fun row_whose_resolver_still_returns_packageName_is_left_alone_for_next_launch() = runBlocking {
        // Some OEM background-service packages have no displayable label even
        // after the full fallback chain — resolver returns packageName as the
        // last-resort. The runner must NOT treat that as a "successful update";
        // the row stays as-is so a future launch (after an OS label refresh)
        // can try again.
        seedRow(id = "row-system", packageName = OEM_BG_PACKAGE, appName = OEM_BG_PACKAGE)
        seedRow(id = "row-gmail",  packageName = GMAIL_PACKAGE,  appName = GMAIL_PACKAGE)

        val resolver = AppLabelResolver(
            FakePackageLabelSource(
                loadLabelReturns = mapOf(
                    GMAIL_PACKAGE to "Gmail",
                    OEM_BG_PACKAGE to OEM_BG_PACKAGE, // resolver echoes package
                ),
                applicationLabelReturns = mapOf(OEM_BG_PACKAGE to OEM_BG_PACKAGE),
                // nameForUid intentionally omits OEM_BG_PACKAGE → null
            ),
        )
        val runner = MigrateAppLabelRunner(
            notificationDao = dao,
            appLabelResolver = resolver,
            settingsRepository = settingsRepository,
        )

        runner.run()

        val rows = dao.observeAll().first().associateBy { it.id }
        assertEquals("Gmail", rows.getValue("row-gmail").appName)
        assertEquals(
            "Resolver-still-equals-packageName means leave the row alone for retry",
            OEM_BG_PACKAGE,
            rows.getValue("row-system").appName,
        )
        // Flag still flips so we don't re-scan every launch — the row will be
        // picked up by future capture re-resolution if the OS label recovers.
        assertTrue(settingsRepository.isAppLabelResolutionMigrationV1Applied())
    }

    @Test
    fun rows_where_appName_already_differs_from_packageName_are_not_touched() = runBlocking {
        // Pre-existing healthy rows must not be re-resolved. The selection
        // query is `WHERE appName = packageName` exactly to avoid re-writing
        // user-friendly labels back to whatever the resolver returns now.
        seedRow(id = "row-already-good", packageName = GMAIL_PACKAGE, appName = "Gmail")

        val resolver = AppLabelResolver(
            FakePackageLabelSource(loadLabelReturns = mapOf(GMAIL_PACKAGE to "Different Label")),
        )
        val runner = MigrateAppLabelRunner(
            notificationDao = dao,
            appLabelResolver = resolver,
            settingsRepository = settingsRepository,
        )

        runner.run()

        assertEquals(
            "Healthy rows (appName != packageName) must be left untouched",
            "Gmail",
            dao.observeAll().first().single().appName,
        )
    }

    private suspend fun seedRow(id: String, packageName: String, appName: String) {
        dao.upsert(
            NotificationEntity(
                id = id,
                appName = appName,
                packageName = packageName,
                sender = null,
                title = "title-$id",
                body = "body-$id",
                postedAtMillis = 1_700_000_000_000,
                status = "DIGEST",
                reasonTags = "",
                score = null,
                isBundled = false,
                isPersistent = false,
                contentSignature = "sig-$id",
            ),
        )
    }

    private companion object {
        const val GMAIL_PACKAGE = "com.google.android.gm"
        const val COUPANG_PACKAGE = "com.coupang.mobile"
        const val NAVER_PACKAGE = "com.nhn.android.search"
        const val OEM_BG_PACKAGE = "com.samsung.android.bg.unknown"
    }
}
