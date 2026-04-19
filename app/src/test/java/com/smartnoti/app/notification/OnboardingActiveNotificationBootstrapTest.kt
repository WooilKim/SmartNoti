package com.smartnoti.app.notification

import android.app.Notification
import android.content.Context
import android.os.Bundle
import android.os.Process
import android.os.UserHandle
import android.service.notification.StatusBarNotification
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

@RunWith(RobolectricTestRunner::class)
class OnboardingActiveNotificationBootstrapCoordinatorTest {

    private lateinit var context: Context
    private lateinit var settingsRepository: SettingsRepository
    private var triggerCount = 0
    private lateinit var coordinator: OnboardingActiveNotificationBootstrapCoordinator

    @Before
    fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        SettingsRepository.clearInstanceForTest()
        settingsRepository = SettingsRepository.getInstance(context)
        settingsRepository.clearAllForTest()
        coordinator = OnboardingActiveNotificationBootstrapCoordinator(
            settingsRepository = settingsRepository,
            signalConnectedListener = { triggerCount += 1 },
        )
    }

    @After
    fun tearDown() {
        SettingsRepository.clearInstanceForTest()
    }

    @Test
    fun bootstrap_runs_only_when_explicitly_requested_for_first_onboarding_completion() = runBlocking {
        assertFalse(coordinator.consumePendingBootstrapRequest())
        assertEquals(0, triggerCount)

        assertTrue(coordinator.requestBootstrapForFirstOnboardingCompletion())
        assertEquals(1, triggerCount)
        assertTrue(coordinator.consumePendingBootstrapRequest())
        assertFalse(coordinator.consumePendingBootstrapRequest())
    }

    @Test
    fun duplicate_processing_is_prevented_if_requested_again_after_completion() = runBlocking {
        assertTrue(coordinator.requestBootstrapForFirstOnboardingCompletion())
        assertTrue(coordinator.consumePendingBootstrapRequest())

        assertFalse(coordinator.requestBootstrapForFirstOnboardingCompletion())
        assertFalse(coordinator.consumePendingBootstrapRequest())
        assertEquals(1, triggerCount)
    }
}

@RunWith(RobolectricTestRunner::class)
class OnboardingActiveNotificationBootstrapperTest {

    @Test
    fun active_notifications_from_other_packages_are_processed_through_bootstrap_path() = runBlocking {
        val processedNotifications = mutableListOf<TestNotification>()
        val bootstrapper = OnboardingActiveNotificationBootstrapper<TestNotification>(
            appPackageName = "com.smartnoti.app",
            packageNameOf = { it.packageName },
            titleOf = { it.title },
            bodyOf = { it.body },
            notificationFlagsOf = { it.flags },
            processNotification = { processedNotifications += it },
        )

        bootstrapper.bootstrap(
            activeNotifications = listOf(
                TestNotification(
                    packageName = "com.example.chat",
                    title = "새 메시지",
                    body = "안녕하세요",
                ),
                TestNotification(
                    packageName = "com.example.mail",
                    title = "메일 도착",
                    body = "청구서가 왔어요",
                ),
            )
        )

        assertEquals(
            listOf(
                TestNotification(packageName = "com.example.chat", title = "새 메시지", body = "안녕하세요"),
                TestNotification(packageName = "com.example.mail", title = "메일 도착", body = "청구서가 왔어요"),
            ),
            processedNotifications,
        )
    }

    @Test
    fun self_notifications_and_ignored_blank_group_summaries_are_skipped() = runBlocking {
        val processedNotifications = mutableListOf<TestNotification>()
        val bootstrapper = OnboardingActiveNotificationBootstrapper<TestNotification>(
            appPackageName = "com.smartnoti.app",
            packageNameOf = { it.packageName },
            titleOf = { it.title },
            bodyOf = { it.body },
            notificationFlagsOf = { it.flags },
            processNotification = { processedNotifications += it },
        )

        bootstrapper.bootstrap(
            activeNotifications = listOf(
                TestNotification(
                    packageName = "com.smartnoti.app",
                    title = "SmartNoti",
                    body = "replacement",
                ),
                TestNotification(
                    packageName = "com.example.chat",
                    title = "",
                    body = "",
                    flags = Notification.FLAG_GROUP_SUMMARY,
                ),
                TestNotification(
                    packageName = "com.example.chat",
                    title = "새 메시지",
                    body = "본문",
                ),
            )
        )

        assertEquals(
            listOf(TestNotification(packageName = "com.example.chat", title = "새 메시지", body = "본문")),
            processedNotifications,
        )
    }

    private data class TestNotification(
        val packageName: String,
        val title: String,
        val body: String,
        val flags: Int = 0,
    )
}

@RunWith(RobolectricTestRunner::class)
class ActiveStatusBarNotificationBootstrapperTest {

    @Test
    fun status_bar_notifications_reuse_bootstrap_filtering_rules() = runBlocking {
        val processedEntries = mutableListOf<String>()
        val bootstrapper = ActiveStatusBarNotificationBootstrapper(
            appPackageName = "com.smartnoti.app",
            processNotification = { processedEntries += "${it.packageName}#${it.id}" },
        )

        bootstrapper.bootstrap(
            activeNotifications = arrayOf(
                statusBarNotification(
                    packageName = "com.smartnoti.app",
                    key = "self#1",
                    title = "SmartNoti",
                    body = "replacement",
                ),
                statusBarNotification(
                    packageName = "com.example.chat",
                    key = "summary#1",
                    title = "",
                    body = "",
                    flags = Notification.FLAG_GROUP_SUMMARY,
                ),
                statusBarNotification(
                    packageName = "com.example.chat",
                    key = "chat#1",
                    title = "새 메시지",
                    body = "본문",
                ),
            )
        )

        assertEquals(
            listOf("com.example.chat#${"chat#1".hashCode()}"),
            processedEntries,
        )
    }

    private fun statusBarNotification(
        packageName: String,
        key: String,
        title: String,
        body: String,
        flags: Int = 0,
    ): StatusBarNotification {
        val notification = Notification().apply {
            extras = Bundle().apply {
                putString(Notification.EXTRA_TITLE, title)
                putString(Notification.EXTRA_TEXT, body)
            }
            this.flags = flags
        }

        val userHandle: UserHandle = Process.myUserHandle()
        return StatusBarNotification(
            packageName,
            packageName,
            key.hashCode(),
            key,
            0,
            0,
            0,
            notification,
            userHandle,
            System.currentTimeMillis(),
        )
    }
}
