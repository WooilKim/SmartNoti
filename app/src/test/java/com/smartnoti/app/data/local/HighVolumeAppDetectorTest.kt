package com.smartnoti.app.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.smartnoti.app.domain.model.NotificationStatusUi
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Plan `docs/plans/2026-04-28-fix-issue-525-high-volume-app-suggest-suppression.md`
 * Task 1 (RED) — pin the contract for the new `HighVolumeAppDetector` that
 * surfaces noisy packages on the Inbox `Digest` sub-tab so the user can
 * one-tap accept the auto-bundle suggestion.
 *
 * Behaviors covered (all must initially compile-fail because
 * [HighVolumeAppDetector] / [HighVolumeAppCandidate] do not exist yet):
 *  - Returns top-N candidates whose 7-day rolling count meets the threshold,
 *    sorted desc by count with packageName asc tiebreak.
 *  - Excludes packages already in `currentSuppressedSourceApps`.
 *  - Excludes packages in `currentSuggestedSuppressionDismissed` (the new
 *    sticky-dismiss set introduced by Task 5).
 *  - Excludes packages in `currentSuppressedSourceAppsExcluded` (the
 *    pre-existing sticky-exclude set — issue #525 reuses it so a single
 *    user intent ("don't bundle this app") spans both auto-expansion and
 *    suggestions).
 *  - Excludes packages where every row inside the window is PRIORITY-classified.
 *  - Includes mixed-status packages (PRIORITY + DIGEST + SILENT) and counts
 *    every row as a noise signal — issue #525 plan calls this out as an
 *    intentional v1 decision (refinement is a v2 open question).
 *  - Stale rows outside the 7-day window do not count.
 *
 * The fixture mirrors the user's R3CY2058DLJ device snapshot (네이버 24,
 * 카카오톡 20, 쿠팡이츠 11, 마이제네시스 11, 삼성헬스 10, 삼성캘린더 10) so
 * the GREEN implementation reproduces issue #525's reported state byte-for-byte.
 *
 * **Threshold semantics:** the plan's Task 2 NB explicitly flags the unit
 * choice (per-day vs total-window). This test pins
 * `detect(avgPerDayThreshold = 10, windowDays = 7)` — i.e. an average of
 * ≥ 10 notifications per day over the 7-day window. The implementer wires
 * the SQL `HAVING` to `>= avgPerDayThreshold * windowDays` (= 70 here).
 */
@RunWith(RobolectricTestRunner::class)
class HighVolumeAppDetectorTest {

    private lateinit var context: Context
    private lateinit var database: SmartNotiDatabase
    private lateinit var dao: NotificationDao
    private lateinit var detector: HighVolumeAppDetector

    /**
     * Frozen "now" pinned to a real Unix epoch millis far enough into the
     * future that the 7-day window arithmetic stays comfortably inside Long
     * range and is human-readable when a fixture row is dumped during
     * debugging. 2026-04-27 12:00 UTC.
     */
    private val nowMillis: Long = 1_777_233_600_000L
    private val sevenDaysMillis: Long = 7L * 24 * 60 * 60 * 1000

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, SmartNotiDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.notificationDao()
        detector = HighVolumeAppDetector(dao = dao, clock = { nowMillis })
    }

    @After
    fun tearDown() {
        database.close()
        context.deleteDatabase("smartnoti.db")
    }

    @Test
    fun returns_top_n_above_threshold_excludes_suppressed_dismissed_and_excluded() = runBlocking {
        // R3CY2058DLJ snapshot — six packages above threshold.
        seedRows("com.nhn.android.search", appName = "네이버", count = 24)
        seedRows("com.kakao.talk", appName = "카카오톡", count = 20)
        seedRows("com.coupang.eats", appName = "쿠팡이츠", count = 11)
        seedRows("com.genesis.oneapp", appName = "마이제네시스", count = 11)
        seedRows("net.samsung.android.health.research.kor", appName = "삼성헬스", count = 10)
        seedRows("com.samsung.android.calendar", appName = "삼성캘린더", count = 10)

        // Below-threshold noise — must be excluded by the SQL HAVING.
        seedRows("com.nondrop.minor", appName = "마이너", count = 5)

        val result = detector.detect(
            avgPerDayThreshold = 10,
            windowDays = 7,
            currentSuppressedSourceApps = setOf("com.kakao.talk"),
            currentSuggestedSuppressionDismissed = setOf("com.coupang.eats"),
            currentSuppressedSourceAppsExcluded = setOf("com.genesis.oneapp"),
        )

        // Expected survivors after the three exclusion sets:
        //  - com.nhn.android.search (24)
        //  - net.samsung.android.health.research.kor (10)
        //  - com.samsung.android.calendar (10)
        // Tiebreak between the two 10s is packageName ASC.
        assertEquals(3, result.size)
        assertEquals("com.nhn.android.search", result[0].packageName)
        assertEquals("네이버", result[0].appName)
        assertEquals(24, result[0].count)
        // Two 10-count packages sorted by packageName ASC: "com.samsung..." < "net.samsung..."
        assertEquals("com.samsung.android.calendar", result[1].packageName)
        assertEquals(10, result[1].count)
        assertEquals("net.samsung.android.health.research.kor", result[2].packageName)
        assertEquals(10, result[2].count)
    }

    @Test
    fun avg_per_day_is_count_divided_by_window_days() = runBlocking {
        seedRows("com.nhn.android.search", appName = "네이버", count = 24)

        val result = detector.detect(
            avgPerDayThreshold = 10,
            windowDays = 7,
            currentSuppressedSourceApps = emptySet(),
            currentSuggestedSuppressionDismissed = emptySet(),
            currentSuppressedSourceAppsExcluded = emptySet(),
        )

        assertEquals(1, result.size)
        // 24 / 7 ≈ 3.428... — pinned with epsilon to avoid Double-precision flake.
        val expected = 24.0 / 7.0
        assertTrue(
            "avgPerDay should equal count/windowDays, got=${result[0].avgPerDay} expected≈$expected",
            kotlin.math.abs(result[0].avgPerDay - expected) < 1e-9,
        )
    }

    @Test
    fun empty_db_returns_empty() = runBlocking {
        val result = detector.detect(
            avgPerDayThreshold = 10,
            windowDays = 7,
            currentSuppressedSourceApps = emptySet(),
            currentSuggestedSuppressionDismissed = emptySet(),
            currentSuppressedSourceAppsExcluded = emptySet(),
        )
        assertEquals(emptyList<HighVolumeAppCandidate>(), result)
    }

    @Test
    fun only_priority_packages_excluded() = runBlocking {
        // Every row for this package is PRIORITY → user already classified
        // it as a wanted signal. Detector must not propose it.
        seedRows(
            "com.friend.messenger",
            appName = "친구 메신저",
            count = 30,
            status = NotificationStatusUi.PRIORITY,
        )

        val result = detector.detect(
            avgPerDayThreshold = 10,
            windowDays = 7,
            currentSuppressedSourceApps = emptySet(),
            currentSuggestedSuppressionDismissed = emptySet(),
            currentSuppressedSourceAppsExcluded = emptySet(),
        )
        assertEquals(emptyList<HighVolumeAppCandidate>(), result)
    }

    @Test
    fun mixed_status_priority_and_digest_includes_in_count() = runBlocking {
        // 5 PRIORITY + 15 DIGEST + 4 SILENT = 24 total. Every row counts —
        // mixed-status packages are NOT priority-only, so the package
        // surfaces. (Plan Task 1 fixture D — open question for v2 refinement.)
        seedRows(
            packageName = "com.naver.shopping",
            appName = "네이버쇼핑",
            count = 5,
            status = NotificationStatusUi.PRIORITY,
            idPrefix = "shop-pri-",
        )
        seedRows(
            packageName = "com.naver.shopping",
            appName = "네이버쇼핑",
            count = 15,
            status = NotificationStatusUi.DIGEST,
            idPrefix = "shop-digest-",
        )
        seedRows(
            packageName = "com.naver.shopping",
            appName = "네이버쇼핑",
            count = 4,
            status = NotificationStatusUi.SILENT,
            idPrefix = "shop-silent-",
        )

        val result = detector.detect(
            avgPerDayThreshold = 10,
            windowDays = 7,
            currentSuppressedSourceApps = emptySet(),
            currentSuggestedSuppressionDismissed = emptySet(),
            currentSuppressedSourceAppsExcluded = emptySet(),
        )
        assertEquals(1, result.size)
        assertEquals("com.naver.shopping", result[0].packageName)
        assertEquals(24, result[0].count)
    }

    @Test
    fun stale_rows_outside_window_excluded() = runBlocking {
        // 50 stale rows (postedAtMillis = now - 8day) — must NOT be counted.
        seedRows(
            packageName = "com.naver.android.search",
            appName = "네이버",
            count = 50,
            postedAtMillis = nowMillis - sevenDaysMillis - 24L * 60 * 60 * 1000,
            idPrefix = "stale-",
        )
        // 8 in-window rows — total 8 → below avg-10/day threshold (= 70 / 7).
        seedRows(
            packageName = "com.naver.android.search",
            appName = "네이버",
            count = 8,
            postedAtMillis = nowMillis - 60L * 60 * 1000,
            idPrefix = "live-",
        )

        val result = detector.detect(
            avgPerDayThreshold = 10,
            windowDays = 7,
            currentSuppressedSourceApps = emptySet(),
            currentSuggestedSuppressionDismissed = emptySet(),
            currentSuppressedSourceAppsExcluded = emptySet(),
        )
        assertEquals(emptyList<HighVolumeAppCandidate>(), result)
    }

    /**
     * Helper: seed [count] rows for [packageName] with timestamps spread
     * across the 7-day window (now-1h, now-2h, …) so every row lands
     * comfortably inside the rolling window unless [postedAtMillis] is
     * passed explicitly.
     */
    private suspend fun seedRows(
        packageName: String,
        appName: String,
        count: Int,
        status: NotificationStatusUi = NotificationStatusUi.DIGEST,
        postedAtMillis: Long? = null,
        idPrefix: String = "row-",
    ) {
        repeat(count) { idx ->
            val ts = postedAtMillis ?: (nowMillis - (idx + 1) * 60L * 60 * 1000)
            dao.upsert(
                NotificationEntity(
                    id = "$idPrefix$packageName-$idx",
                    appName = appName,
                    packageName = packageName,
                    sender = null,
                    title = "제목 $idx",
                    body = "본문 $idx",
                    postedAtMillis = ts,
                    status = status.name,
                    reasonTags = "",
                    score = null,
                    isBundled = false,
                    isPersistent = false,
                    contentSignature = "$packageName-$idx",
                    silentMode = null,
                    sourceEntryKey = null,
                    ruleHitIds = null,
                ),
            )
        }
    }
}
