package com.smartnoti.app.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.smartnoti.app.domain.model.NotificationStatusUi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
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

    private fun notificationEntity(
        id: String,
        title: String,
        body: String,
    ) = NotificationEntity(
        id = id,
        appName = "SmartNoti Test Notifier",
        packageName = "com.smartnoti.testnotifier",
        sender = null,
        title = title,
        body = body,
        postedAtMillis = id.split(':')[1].toLong(),
        status = NotificationStatusUi.DIGEST.name,
        reasonTags = "",
        score = null,
        isBundled = false,
        isPersistent = false,
        contentSignature = listOf(title, body).joinToString(" ").trim(),
    )
}
