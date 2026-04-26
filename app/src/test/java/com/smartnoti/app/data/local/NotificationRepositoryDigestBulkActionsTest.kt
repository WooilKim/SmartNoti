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
 * Plan `docs/plans/2026-04-26-inbox-digest-group-bulk-actions.md` Task 1 —
 * fixes the contract for the two new repository methods that back the Inbox
 * Digest sub-tab's per-group bulk actions:
 *
 * - `restoreDigestToPriorityByPackage(packageName)` — flips every DIGEST row
 *   for that package to PRIORITY, dedup-appending the `사용자 분류` reason
 *   tag (mirrors `ApplyCategoryActionToNotificationUseCase`).
 * - `deleteDigestByPackage(packageName)` — hard-deletes every DIGEST row for
 *   that package (mirrors `deleteSilentByPackage`).
 *
 * Both methods scope to status=DIGEST only — SILENT / PRIORITY / IGNORE rows
 * are left untouched, and other packages stay unaffected.
 */
@RunWith(RobolectricTestRunner::class)
class NotificationRepositoryDigestBulkActionsTest {

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
    fun restoreDigestToPriorityByPackage_flips_only_digest_rows_for_target_package() = runBlocking {
        seed("digest-1", status = NotificationStatusUi.DIGEST)
        seed("digest-2", status = NotificationStatusUi.DIGEST)
        seed("digest-3", status = NotificationStatusUi.DIGEST)
        seed(
            "silent-1",
            status = NotificationStatusUi.SILENT,
            silentMode = "ARCHIVED",
        )

        val affected = repository.restoreDigestToPriorityByPackage("com.smartnoti.testnotifier")

        assertEquals(3, affected)
        val rows = repository.observeAll().first().associateBy { it.id }
        assertEquals(NotificationStatusUi.PRIORITY, rows.getValue("digest-1").status)
        assertEquals(NotificationStatusUi.PRIORITY, rows.getValue("digest-2").status)
        assertEquals(NotificationStatusUi.PRIORITY, rows.getValue("digest-3").status)
        assertTrue(rows.getValue("digest-1").reasonTags.contains("사용자 분류"))
        assertTrue(rows.getValue("digest-2").reasonTags.contains("사용자 분류"))
        assertTrue(rows.getValue("digest-3").reasonTags.contains("사용자 분류"))
        // SILENT row is preserved untouched.
        assertEquals(NotificationStatusUi.SILENT, rows.getValue("silent-1").status)
        assertEquals(com.smartnoti.app.domain.model.SilentMode.ARCHIVED, rows.getValue("silent-1").silentMode)
    }

    @Test
    fun deleteDigestByPackage_hard_deletes_only_digest_rows_for_target_package() = runBlocking {
        seed("digest-1", status = NotificationStatusUi.DIGEST)
        seed("digest-2", status = NotificationStatusUi.DIGEST)
        seed("digest-3", status = NotificationStatusUi.DIGEST)
        seed(
            "silent-1",
            status = NotificationStatusUi.SILENT,
            silentMode = "ARCHIVED",
        )

        val affected = repository.deleteDigestByPackage("com.smartnoti.testnotifier")

        assertEquals(3, affected)
        val rows = repository.observeAll().first().associateBy { it.id }
        assertNull(rows["digest-1"])
        assertNull(rows["digest-2"])
        assertNull(rows["digest-3"])
        // SILENT row is preserved.
        assertEquals(NotificationStatusUi.SILENT, rows.getValue("silent-1").status)
    }

    @Test
    fun restoreDigestToPriorityByPackage_isolates_other_packages() = runBlocking {
        seed("digest-target-1", packageName = "com.target.app", status = NotificationStatusUi.DIGEST)
        seed("digest-target-2", packageName = "com.target.app", status = NotificationStatusUi.DIGEST)
        seed("digest-other-1", packageName = "com.other.app", status = NotificationStatusUi.DIGEST)
        seed("digest-other-2", packageName = "com.other.app", status = NotificationStatusUi.DIGEST)

        val affected = repository.restoreDigestToPriorityByPackage("com.target.app")

        assertEquals(2, affected)
        val rows = repository.observeAll().first().associateBy { it.id }
        assertEquals(NotificationStatusUi.PRIORITY, rows.getValue("digest-target-1").status)
        assertEquals(NotificationStatusUi.PRIORITY, rows.getValue("digest-target-2").status)
        // Other package rows untouched.
        assertEquals(NotificationStatusUi.DIGEST, rows.getValue("digest-other-1").status)
        assertEquals(NotificationStatusUi.DIGEST, rows.getValue("digest-other-2").status)
    }

    @Test
    fun deleteDigestByPackage_isolates_other_packages() = runBlocking {
        seed("digest-target-1", packageName = "com.target.app", status = NotificationStatusUi.DIGEST)
        seed("digest-target-2", packageName = "com.target.app", status = NotificationStatusUi.DIGEST)
        seed("digest-other-1", packageName = "com.other.app", status = NotificationStatusUi.DIGEST)

        val affected = repository.deleteDigestByPackage("com.target.app")

        assertEquals(2, affected)
        val rows = repository.observeAll().first().associateBy { it.id }
        assertNull(rows["digest-target-1"])
        assertNull(rows["digest-target-2"])
        // Other package row untouched.
        assertEquals(NotificationStatusUi.DIGEST, rows.getValue("digest-other-1").status)
    }

    @Test
    fun restoreDigestToPriorityByPackage_returns_zero_for_empty_group() = runBlocking {
        seed(
            "silent-1",
            status = NotificationStatusUi.SILENT,
            silentMode = "ARCHIVED",
        )

        val affected = repository.restoreDigestToPriorityByPackage("com.smartnoti.testnotifier")

        assertEquals(0, affected)
        val rows = repository.observeAll().first().associateBy { it.id }
        // SILENT row remains unchanged.
        assertEquals(NotificationStatusUi.SILENT, rows.getValue("silent-1").status)
    }

    @Test
    fun deleteDigestByPackage_returns_zero_for_empty_group() = runBlocking {
        seed(
            "silent-1",
            status = NotificationStatusUi.SILENT,
            silentMode = "ARCHIVED",
        )

        val affected = repository.deleteDigestByPackage("com.smartnoti.testnotifier")

        assertEquals(0, affected)
        val rows = repository.observeAll().first().associateBy { it.id }
        // SILENT row remains.
        assertEquals(NotificationStatusUi.SILENT, rows.getValue("silent-1").status)
    }

    @Test
    fun restoreDigestToPriorityByPackage_dedupes_existing_user_classification_tag() = runBlocking {
        seed("digest-with-tag", status = NotificationStatusUi.DIGEST, reasonTags = "사용자 분류")
        seed(
            "digest-with-other-tag",
            status = NotificationStatusUi.DIGEST,
            reasonTags = "키워드 일치|사용자 분류",
        )
        seed("digest-without-tag", status = NotificationStatusUi.DIGEST, reasonTags = "키워드 일치")

        val affected = repository.restoreDigestToPriorityByPackage("com.smartnoti.testnotifier")

        assertEquals(3, affected)
        val rows = repository.observeAll().first().associateBy { it.id }
        // Already-tagged row keeps single occurrence.
        assertEquals(listOf("사용자 분류"), rows.getValue("digest-with-tag").reasonTags)
        // Already-tagged-with-other-tags row stays unchanged.
        assertEquals(
            listOf("키워드 일치", "사용자 분류"),
            rows.getValue("digest-with-other-tag").reasonTags,
        )
        // Untagged row gets the tag appended.
        assertEquals(
            listOf("키워드 일치", "사용자 분류"),
            rows.getValue("digest-without-tag").reasonTags,
        )
        // All flipped to PRIORITY.
        assertEquals(NotificationStatusUi.PRIORITY, rows.getValue("digest-with-tag").status)
        assertEquals(NotificationStatusUi.PRIORITY, rows.getValue("digest-with-other-tag").status)
        assertEquals(NotificationStatusUi.PRIORITY, rows.getValue("digest-without-tag").status)
    }

    private suspend fun seed(
        id: String,
        title: String = "제목 $id",
        body: String = "본문 $id",
        packageName: String = "com.smartnoti.testnotifier",
        appName: String = "SmartNoti Test Notifier",
        postedAtMillis: Long = 1_700_000_000_000,
        status: NotificationStatusUi = NotificationStatusUi.DIGEST,
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
