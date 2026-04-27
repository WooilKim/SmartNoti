package com.smartnoti.app.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.smartnoti.app.domain.model.NotificationStatusUi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Plan `docs/plans/2026-04-27-ignored-archive-bulk-restore-and-clear.md` Task 1 —
 * fixes the contract for the two new repository methods that back the
 * IgnoredArchive screen's header bulk actions:
 *
 * - `restoreAllIgnoredToPriority()` — flips every IGNORE row to PRIORITY,
 *   dedup-appending the `사용자 분류` reason tag (mirrors
 *   `restoreDigestToPriorityByPackage` / `ApplyCategoryActionToNotificationUseCase`).
 * - `deleteAllIgnored()` — hard-deletes every IGNORE row (mirrors
 *   `deleteAllSilent`).
 *
 * Both methods scope to status=IGNORE only — DIGEST / SILENT / PRIORITY rows
 * are left untouched.
 */
@RunWith(RobolectricTestRunner::class)
class NotificationRepositoryIgnoredBulkTest {

    private lateinit var context: Context
    private lateinit var database: SmartNotiDatabase
    private lateinit var repository: NotificationRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, SmartNotiDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = NotificationRepository(database.notificationDao())
    }

    @After
    fun tearDown() {
        database.close()
        NotificationRepository.clearInstanceForTest()
        context.deleteDatabase("smartnoti.db")
    }

    @Test
    fun restoreAllIgnoredToPriority_flips_only_ignore_rows() = runBlocking {
        seed("ignore-1", status = NotificationStatusUi.IGNORE)
        seed("ignore-2", status = NotificationStatusUi.IGNORE)
        seed("ignore-3", status = NotificationStatusUi.IGNORE)
        seed("digest-1", status = NotificationStatusUi.DIGEST)
        seed("priority-1", status = NotificationStatusUi.PRIORITY)

        val affected = repository.restoreAllIgnoredToPriority()

        assertEquals(3, affected)
        val rows = repository.observeAll().first().associateBy { it.id }
        assertEquals(NotificationStatusUi.PRIORITY, rows.getValue("ignore-1").status)
        assertEquals(NotificationStatusUi.PRIORITY, rows.getValue("ignore-2").status)
        assertEquals(NotificationStatusUi.PRIORITY, rows.getValue("ignore-3").status)
        assertTrue(rows.getValue("ignore-1").reasonTags.contains("사용자 분류"))
        assertTrue(rows.getValue("ignore-2").reasonTags.contains("사용자 분류"))
        assertTrue(rows.getValue("ignore-3").reasonTags.contains("사용자 분류"))
        // DIGEST / PRIORITY rows untouched.
        assertEquals(NotificationStatusUi.DIGEST, rows.getValue("digest-1").status)
        assertEquals(NotificationStatusUi.PRIORITY, rows.getValue("priority-1").status)
    }

    @Test
    fun deleteAllIgnored_hard_deletes_only_ignore_rows() = runBlocking {
        seed("ignore-1", status = NotificationStatusUi.IGNORE)
        seed("ignore-2", status = NotificationStatusUi.IGNORE)
        seed("ignore-3", status = NotificationStatusUi.IGNORE)
        seed("digest-1", status = NotificationStatusUi.DIGEST)
        seed("priority-1", status = NotificationStatusUi.PRIORITY)
        seed(
            "silent-1",
            status = NotificationStatusUi.SILENT,
            silentMode = "ARCHIVED",
        )

        val affected = repository.deleteAllIgnored()

        assertEquals(3, affected)
        val rows = repository.observeAll().first().associateBy { it.id }
        assertNull(rows["ignore-1"])
        assertNull(rows["ignore-2"])
        assertNull(rows["ignore-3"])
        // Other status rows preserved.
        assertEquals(NotificationStatusUi.DIGEST, rows.getValue("digest-1").status)
        assertEquals(NotificationStatusUi.PRIORITY, rows.getValue("priority-1").status)
        assertEquals(NotificationStatusUi.SILENT, rows.getValue("silent-1").status)
    }

    @Test
    fun restoreAllIgnoredToPriority_returns_zero_for_empty_set() = runBlocking {
        seed("digest-1", status = NotificationStatusUi.DIGEST)
        seed("priority-1", status = NotificationStatusUi.PRIORITY)

        val affected = repository.restoreAllIgnoredToPriority()

        assertEquals(0, affected)
        val rows = repository.observeAll().first().associateBy { it.id }
        // Other rows untouched.
        assertEquals(NotificationStatusUi.DIGEST, rows.getValue("digest-1").status)
        assertEquals(NotificationStatusUi.PRIORITY, rows.getValue("priority-1").status)
    }

    @Test
    fun deleteAllIgnored_returns_zero_for_empty_set() = runBlocking {
        seed("digest-1", status = NotificationStatusUi.DIGEST)
        seed("priority-1", status = NotificationStatusUi.PRIORITY)

        val affected = repository.deleteAllIgnored()

        assertEquals(0, affected)
        val rows = repository.observeAll().first().associateBy { it.id }
        // Other rows preserved.
        assertEquals(NotificationStatusUi.DIGEST, rows.getValue("digest-1").status)
        assertEquals(NotificationStatusUi.PRIORITY, rows.getValue("priority-1").status)
    }

    @Test
    fun restoreAllIgnoredToPriority_dedupes_existing_user_classification_tag() = runBlocking {
        seed("ignore-with-tag", status = NotificationStatusUi.IGNORE, reasonTags = "사용자 분류")
        seed(
            "ignore-with-other-tag",
            status = NotificationStatusUi.IGNORE,
            reasonTags = "키워드 일치|사용자 분류",
        )
        seed("ignore-without-tag", status = NotificationStatusUi.IGNORE, reasonTags = "키워드 일치")

        val affected = repository.restoreAllIgnoredToPriority()

        assertEquals(3, affected)
        val rows = repository.observeAll().first().associateBy { it.id }
        // Already-tagged row keeps single occurrence.
        assertEquals(listOf("사용자 분류"), rows.getValue("ignore-with-tag").reasonTags)
        // Already-tagged-with-other-tags row stays unchanged.
        assertEquals(
            listOf("키워드 일치", "사용자 분류"),
            rows.getValue("ignore-with-other-tag").reasonTags,
        )
        // Untagged row gets the tag appended.
        assertEquals(
            listOf("키워드 일치", "사용자 분류"),
            rows.getValue("ignore-without-tag").reasonTags,
        )
        // All flipped to PRIORITY.
        assertEquals(NotificationStatusUi.PRIORITY, rows.getValue("ignore-with-tag").status)
        assertEquals(NotificationStatusUi.PRIORITY, rows.getValue("ignore-with-other-tag").status)
        assertEquals(NotificationStatusUi.PRIORITY, rows.getValue("ignore-without-tag").status)
    }

    /**
     * Plan Task 6 — per-id bulk delete query that backs the multi-select
     * ActionBar's "모두 지우기" CTA. Mixed-status seed makes sure the WHERE
     * clause does not accidentally widen beyond the supplied id set.
     */
    @Test
    fun deleteIgnoredByIds_only_deletes_supplied_ignore_rows() = runBlocking {
        seed("ignore-1", status = NotificationStatusUi.IGNORE)
        seed("ignore-2", status = NotificationStatusUi.IGNORE)
        seed("ignore-3", status = NotificationStatusUi.IGNORE)
        seed("digest-1", status = NotificationStatusUi.DIGEST)
        seed("priority-1", status = NotificationStatusUi.PRIORITY)

        val affected = repository.deleteIgnoredByIds(setOf("ignore-1", "ignore-3"))

        assertEquals(2, affected)
        val rows = repository.observeAll().first().associateBy { it.id }
        assertNull(rows["ignore-1"])
        assertNull(rows["ignore-3"])
        // Untouched IGNORE row preserved.
        assertEquals(NotificationStatusUi.IGNORE, rows.getValue("ignore-2").status)
        // Other status rows preserved.
        assertEquals(NotificationStatusUi.DIGEST, rows.getValue("digest-1").status)
        assertEquals(NotificationStatusUi.PRIORITY, rows.getValue("priority-1").status)
    }

    @Test
    fun deleteIgnoredByIds_skips_non_ignore_rows_even_when_id_matches() = runBlocking {
        // Defensive: the call site only ever passes IGNORE ids (multi-select
        // is rendered inside IgnoredArchiveScreen), but the WHERE clause
        // should still scope to status='IGNORE' so a stale id reused by a
        // re-classified row never accidentally wipes it.
        seed("ignore-1", status = NotificationStatusUi.IGNORE)
        seed("digest-1", status = NotificationStatusUi.DIGEST)

        val affected = repository.deleteIgnoredByIds(setOf("ignore-1", "digest-1"))

        assertEquals(1, affected)
        val rows = repository.observeAll().first().associateBy { it.id }
        assertNull(rows["ignore-1"])
        assertEquals(NotificationStatusUi.DIGEST, rows.getValue("digest-1").status)
    }

    @Test
    fun deleteIgnoredByIds_returns_zero_for_empty_set() = runBlocking {
        seed("ignore-1", status = NotificationStatusUi.IGNORE)
        seed("digest-1", status = NotificationStatusUi.DIGEST)

        val affected = repository.deleteIgnoredByIds(emptySet())

        assertEquals(0, affected)
        val rows = repository.observeAll().first().associateBy { it.id }
        // Nothing touched.
        assertEquals(NotificationStatusUi.IGNORE, rows.getValue("ignore-1").status)
        assertEquals(NotificationStatusUi.DIGEST, rows.getValue("digest-1").status)
    }

    private suspend fun seed(
        id: String,
        title: String = "제목 $id",
        body: String = "본문 $id",
        packageName: String = "com.smartnoti.testnotifier",
        appName: String = "SmartNoti Test Notifier",
        postedAtMillis: Long = 1_700_000_000_000,
        status: NotificationStatusUi = NotificationStatusUi.IGNORE,
        silentMode: String? = null,
        reasonTags: String = "",
    ) {
        database.notificationDao().upsert(
            NotificationEntity(
                id = id,
                appName = appName,
                packageName = packageName,
                sender = null,
                title = title,
                body = body,
                postedAtMillis = postedAtMillis,
                status = status.name,
                reasonTags = reasonTags,
                score = null,
                isBundled = false,
                isPersistent = false,
                contentSignature = listOf(title, body).joinToString(" ").trim(),
                silentMode = silentMode,
                sourceEntryKey = null,
                ruleHitIds = null,
            )
        )
    }
}
