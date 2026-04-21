package com.smartnoti.app.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.smartnoti.app.domain.model.NotificationStatusUi
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Tests-first for the DAO / Repository helper introduced in Task 3 of
 * docs/plans/2026-04-21-listener-reconnect-active-notification-sweep.md.
 *
 * The listener-reconnect sweep coordinator relies on a per-row existence check
 * so it can skip items that a previous process life (or the onboarding
 * bootstrap) already persisted. The lookup is scoped to
 * (packageName, contentSignature, postedAtMillis) — the same triple used to
 * build [SweepDedupKey].
 */
@RunWith(RobolectricTestRunner::class)
class NotificationRepositoryExistsByContentSignatureTest {

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
    fun exists_returns_true_only_for_exact_triple_match() = runBlocking {
        database.notificationDao().upsert(
            notificationEntity(
                id = "n-1",
                packageName = "com.example.chat",
                title = "새 메시지",
                body = "본문",
                postedAtMillis = 1_700_000_000_000,
                contentSignatureOverride = "chat-signature",
            )
        )

        assertTrue(
            repository.existsByContentSignature(
                packageName = "com.example.chat",
                contentSignature = "chat-signature",
                postedAtMillis = 1_700_000_000_000,
            )
        )

        // Different package
        assertFalse(
            repository.existsByContentSignature(
                packageName = "com.example.mail",
                contentSignature = "chat-signature",
                postedAtMillis = 1_700_000_000_000,
            )
        )

        // Different content signature
        assertFalse(
            repository.existsByContentSignature(
                packageName = "com.example.chat",
                contentSignature = "another-signature",
                postedAtMillis = 1_700_000_000_000,
            )
        )

        // Different postTime
        assertFalse(
            repository.existsByContentSignature(
                packageName = "com.example.chat",
                contentSignature = "chat-signature",
                postedAtMillis = 1_700_000_000_001,
            )
        )
    }

    @Test
    fun exists_is_false_for_empty_table() = runBlocking {
        assertFalse(
            repository.existsByContentSignature(
                packageName = "com.example.chat",
                contentSignature = "anything",
                postedAtMillis = 1L,
            )
        )
    }

    private fun notificationEntity(
        id: String,
        title: String = "제목 $id",
        body: String = "본문 $id",
        packageName: String = "com.smartnoti.testnotifier",
        appName: String = "SmartNoti Test Notifier",
        postedAtMillis: Long = 1_700_000_000_000,
        status: NotificationStatusUi = NotificationStatusUi.DIGEST,
        contentSignatureOverride: String? = null,
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
        isPersistent = false,
        contentSignature = contentSignatureOverride ?: listOf(title, body).joinToString(" ").trim(),
    )
}
