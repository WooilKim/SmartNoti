package com.smartnoti.app.notification

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.smartnoti.app.data.local.NotificationEntity
import com.smartnoti.app.data.local.NotificationRepository
import com.smartnoti.app.data.local.SmartNotiDatabase
import com.smartnoti.app.data.settings.SettingsRepository
import com.smartnoti.app.domain.model.NotificationStatusUi
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Integration-style test for Task 3 of
 * docs/plans/2026-04-21-listener-reconnect-active-notification-sweep.md.
 *
 * Covers the wiring that [SmartNotiNotificationListenerService.onListenerConnected]
 * performs when it enqueues the reconnect sweep after the onboarding bootstrap:
 *
 *   - Sweep consults [NotificationRepository.existsByContentSignature] to skip
 *     rows that survive from a previous process life.
 *   - Sweep defers while [SettingsRepository.isOnboardingBootstrapPending] returns
 *     true, so the bootstrap path owns the first pass.
 *   - [ListenerReconnectActiveNotificationSweepCoordinator.recordProcessedByBootstrap]
 *     is called for every item the bootstrap hands off, so the next sweep after
 *     the pending flag clears treats those items as already-handled.
 */
@RunWith(RobolectricTestRunner::class)
class ListenerReconnectSweepRepositoryIntegrationTest {

    private lateinit var context: Context
    private lateinit var database: SmartNotiDatabase
    private lateinit var repository: NotificationRepository
    private lateinit var settingsRepository: SettingsRepository

    @Before
    fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, SmartNotiDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = NotificationRepository(database.notificationDao())
        SettingsRepository.clearInstanceForTest()
        settingsRepository = SettingsRepository.getInstance(context)
        settingsRepository.clearAllForTest()
    }

    @After
    fun tearDown() {
        database.close()
        NotificationRepository.clearInstanceForTest()
        SettingsRepository.clearInstanceForTest()
        context.deleteDatabase("smartnoti.db")
    }

    @Test
    fun sweep_skips_rows_already_persisted_in_repository() = runBlocking {
        // Seed the DB as if a previous process life already captured and stored
        // one chat notification.
        database.notificationDao().upsert(
            NotificationEntity(
                id = "prev-chat-1",
                appName = "채팅",
                packageName = "com.example.chat",
                sender = null,
                title = "이미 저장",
                body = "본문",
                postedAtMillis = 1_000L,
                status = NotificationStatusUi.DIGEST.name,
                reasonTags = "",
                score = null,
                isBundled = false,
                isPersistent = false,
                contentSignature = "chat-sig-1",
            )
        )

        val processed = mutableListOf<TestActiveNotification>()
        val sweep = ListenerReconnectActiveNotificationSweepCoordinator<TestActiveNotification>(
            appPackageName = "com.smartnoti.app",
            packageNameOf = { it.packageName },
            titleOf = { it.title },
            bodyOf = { it.body },
            notificationFlagsOf = { it.flags },
            dedupKeyOf = {
                SweepDedupKey(
                    packageName = it.packageName,
                    contentSignature = it.contentSignature,
                    postTimeMillis = it.postTimeMillis,
                )
            },
            existsInStore = { key ->
                repository.existsByContentSignature(
                    packageName = key.packageName,
                    contentSignature = key.contentSignature,
                    postedAtMillis = key.postTimeMillis,
                )
            },
            processNotification = { processed += it },
        )

        sweep.sweep(
            listOf(
                TestActiveNotification(
                    packageName = "com.example.chat",
                    title = "이미 저장",
                    body = "본문",
                    postTimeMillis = 1_000L,
                    contentSignature = "chat-sig-1",
                ),
                TestActiveNotification(
                    packageName = "com.example.mail",
                    title = "메일",
                    body = "새 메일",
                    postTimeMillis = 2_000L,
                    contentSignature = "mail-sig-1",
                ),
            )
        )

        assertEquals(
            listOf("com.example.mail|mail-sig-1|2000"),
            processed.map { "${it.packageName}|${it.contentSignature}|${it.postTimeMillis}" },
        )
    }

    @Test
    fun sweep_defers_while_settings_repository_reports_bootstrap_pending() = runBlocking {
        // Raise the pending flag via the real SettingsRepository — the same
        // mechanism the onboarding coordinator uses.
        val requested = settingsRepository.requestOnboardingActiveNotificationBootstrap()
        assertEquals(true, requested)
        assertEquals(true, settingsRepository.isOnboardingBootstrapPending())

        val processed = mutableListOf<String>()
        val sweep = ListenerReconnectActiveNotificationSweepCoordinator<TestActiveNotification>(
            appPackageName = "com.smartnoti.app",
            packageNameOf = { it.packageName },
            titleOf = { it.title },
            bodyOf = { it.body },
            notificationFlagsOf = { it.flags },
            dedupKeyOf = {
                SweepDedupKey(
                    packageName = it.packageName,
                    contentSignature = it.contentSignature,
                    postTimeMillis = it.postTimeMillis,
                )
            },
            existsInStore = { false },
            onboardingBootstrapPending = {
                settingsRepository.isOnboardingBootstrapPending()
            },
            processNotification = { processed += it.contentSignature },
        )

        val actives = listOf(
            TestActiveNotification(
                packageName = "com.example.mail",
                title = "메일",
                body = "본문",
                postTimeMillis = 100L,
                contentSignature = "mail-1",
            ),
        )

        // First reconnect — bootstrap still pending, sweep defers.
        sweep.sweep(actives)
        assertEquals(emptyList<String>(), processed)

        // Simulate the bootstrap running: it consumes the pending request, and
        // for each notification it just processed, records the dedup key so the
        // follow-up sweep skips it without hitting existsInStore.
        val consumed = settingsRepository.consumeOnboardingActiveNotificationBootstrapRequest()
        assertEquals(true, consumed)
        sweep.recordProcessedByBootstrap(
            SweepDedupKey(
                packageName = "com.example.mail",
                contentSignature = "mail-1",
                postTimeMillis = 100L,
            )
        )

        // Second reconnect — bootstrap pending flag now clear, but the item
        // the bootstrap already handled is still in the tray. Sweep must skip
        // it thanks to the bootstrap-recorded key.
        sweep.sweep(actives)
        assertEquals(emptyList<String>(), processed)
    }

    private data class TestActiveNotification(
        val packageName: String,
        val title: String,
        val body: String,
        val flags: Int = 0,
        val postTimeMillis: Long,
        val contentSignature: String,
    )
}
