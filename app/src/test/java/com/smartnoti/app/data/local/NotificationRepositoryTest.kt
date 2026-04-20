package com.smartnoti.app.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.smartnoti.app.domain.model.NotificationStatusUi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class NotificationRepositoryTest {

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
    fun cleanup_legacy_blank_group_summary_rows_removes_only_blank_ranker_group_entries() = runBlocking {
        database.notificationDao().upsert(
            notificationEntity(
                id = "com.smartnoti.testnotifier:1700000000000:2147483647:ranker_group",
                title = "",
                body = "",
            )
        )
        database.notificationDao().upsert(
            notificationEntity(
                id = "com.smartnoti.testnotifier:1700000000001:2147483647:ranker_group",
                title = "업데이트 3건",
                body = "새 메시지가 도착했어요",
            )
        )
        database.notificationDao().upsert(
            notificationEntity(
                id = "com.smartnoti.testnotifier:1700000000002",
                title = "",
                body = "",
            )
        )

        repository.cleanupLegacyBlankGroupSummaryRows()

        assertEquals(
            listOf(
                "com.smartnoti.testnotifier:1700000000002",
                "com.smartnoti.testnotifier:1700000000001:2147483647:ranker_group",
            ),
            repository.observeAll().first().map { it.id },
        )
    }

    @Test
    fun observe_captured_apps_filtered_excludes_persistent_only_apps_without_blocking_nested_lookup() = runBlocking {
        database.notificationDao().upsert(
            notificationEntity(
                id = "persistent-1",
                packageName = "android.system",
                appName = "시스템 UI",
                isPersistent = true,
                postedAtMillis = 1_700_000_000_000,
            )
        )
        database.notificationDao().upsert(
            notificationEntity(
                id = "visible-1",
                packageName = "com.chat.app",
                appName = "채팅",
                isPersistent = false,
                postedAtMillis = 1_700_000_100_000,
            )
        )
        database.notificationDao().upsert(
            notificationEntity(
                id = "visible-2",
                packageName = "com.chat.app",
                appName = "채팅",
                isPersistent = true,
                postedAtMillis = 1_700_000_200_000,
            )
        )

        val apps = repository.observeCapturedAppsFiltered(hidePersistentNotifications = true).first()

        assertEquals(1, apps.size)
        assertEquals("com.chat.app", apps.first().packageName)
        assertEquals(1, apps.first().notificationCount)
        assertTrue(apps.first().lastSeenLabel.isNotBlank())
    }

    @Test
    fun silent_mode_column_round_trips_through_dao() = runBlocking {
        database.notificationDao().upsert(
            notificationEntity(
                id = "silent-archived-1",
                status = NotificationStatusUi.SILENT,
                silentMode = "ARCHIVED",
                postedAtMillis = 1_700_000_700_000,
            )
        )
        database.notificationDao().upsert(
            notificationEntity(
                id = "silent-processed-1",
                status = NotificationStatusUi.SILENT,
                silentMode = "PROCESSED",
                postedAtMillis = 1_700_000_800_000,
            )
        )

        val rows = database.notificationDao().observeAll().first().associateBy { it.id }

        assertEquals("ARCHIVED", rows.getValue("silent-archived-1").silentMode)
        assertEquals("PROCESSED", rows.getValue("silent-processed-1").silentMode)
    }

    private fun notificationEntity(
        id: String,
        title: String = "제목 $id",
        body: String = "본문 $id",
        packageName: String = "com.smartnoti.testnotifier",
        appName: String = "SmartNoti Test Notifier",
        isPersistent: Boolean = false,
        postedAtMillis: Long = id.split(':').getOrNull(1)?.toLongOrNull() ?: 1_700_000_000_000,
        status: NotificationStatusUi = NotificationStatusUi.DIGEST,
        silentMode: String? = null,
    ) = NotificationEntity(
        id = id,
        appName = appName,
        packageName = packageName,
        sender = null,
        title = title,
        body = body,
        postedAtMillis = postedAtMillis,
        status = status.name,
        reasonTags = "",
        score = null,
        isBundled = false,
        isPersistent = isPersistent,
        contentSignature = listOf(title, body).joinToString(" ").trim(),
        silentMode = silentMode,
    )
}
