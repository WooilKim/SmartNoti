package com.smartnoti.app.debug

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.smartnoti.app.data.categories.CategoriesRepository
import com.smartnoti.app.data.local.NotificationRepository
import com.smartnoti.app.data.rules.RulesRepository
import com.smartnoti.app.data.settings.SettingsRepository
import com.smartnoti.app.domain.model.NotificationUiModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * Locks the contract for plan
 * `docs/plans/2026-04-26-debug-inject-package-name-extra.md`:
 *
 * - Default path (no `package_name` extra) keeps the synthetic
 *   `com.smartnoti.debug.tester` packageName so existing journey-tester
 *   priority-inbox recipe is not regressed.
 * - Override path (`--es package_name <pkg>`) substitutes both the saved
 *   row's packageName and the sourceEntryKey prefix.
 * - Empty-string is treated as absent (defensive).
 * - Optional `--es app_name <label>` substitutes appName / sender.
 */
@RunWith(RobolectricTestRunner::class)
class DebugInjectNotificationReceiverTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        // Each test starts from a clean repository singleton trio so
        // `getInstance(context)` rebinds against this Robolectric process.
        NotificationRepository.clearInstanceForTest()
        RulesRepository.clearInstanceForTest()
        CategoriesRepository.clearInstanceForTest()
        SettingsRepository.clearInstanceForTest()
        context.deleteDatabase("smartnoti.db")
    }

    @After
    fun tearDown() {
        NotificationRepository.clearInstanceForTest()
        RulesRepository.clearInstanceForTest()
        CategoriesRepository.clearInstanceForTest()
        SettingsRepository.clearInstanceForTest()
        context.deleteDatabase("smartnoti.db")
    }

    @Test
    fun default_path_keeps_synthetic_package_when_extra_absent() = runBlocking {
        val title = "DefaultPath_${System.nanoTime()}"
        dispatch(intent(title = title, body = "본문"))

        val row = waitForRow(title)
        assertEquals("com.smartnoti.debug.tester", row.packageName)
        assertEquals("SmartNotiDebugTester", row.appName)
    }

    @Test
    fun package_name_extra_overrides_saved_package_and_source_entry_key() = runBlocking {
        val title = "OverridePath_${System.nanoTime()}"
        dispatch(
            intent(title = title, body = "쇼핑 알림").apply {
                putExtra("package_name", "com.coupang.mobile")
            }
        )

        val row = waitForRow(title)
        assertEquals("com.coupang.mobile", row.packageName)
        // sourceEntryKey is not exposed on NotificationUiModel directly, but
        // the repository persists it; assert via NotificationRepository's
        // helper to avoid coupling to Room schema columns here.
        val key = NotificationRepository.getInstance(context).sourceEntryKeyForId(row.id)
        assertNotNull("sourceEntryKey should be persisted for debug-injected row", key)
        assertTrue(
            "sourceEntryKey ($key) must start with the overridden package",
            key!!.startsWith("com.coupang.mobile|debug-inject|"),
        )
    }

    @Test
    fun empty_package_name_extra_falls_back_to_synthetic_package() = runBlocking {
        val title = "EmptyOverride_${System.nanoTime()}"
        dispatch(
            intent(title = title, body = "본문").apply {
                putExtra("package_name", "")
            }
        )

        val row = waitForRow(title)
        assertEquals("com.smartnoti.debug.tester", row.packageName)
    }

    @Test
    fun app_name_extra_overrides_saved_app_name_and_sender() = runBlocking {
        val title = "AppNameOverride_${System.nanoTime()}"
        dispatch(
            intent(title = title, body = "쇼핑 알림").apply {
                putExtra("package_name", "com.coupang.mobile")
                putExtra("app_name", "Coupang")
            }
        )

        val row = waitForRow(title)
        assertEquals("Coupang", row.appName)
    }

    @Test
    fun absent_app_name_extra_keeps_synthetic_app_name() = runBlocking {
        val title = "AppNameDefault_${System.nanoTime()}"
        dispatch(intent(title = title, body = "본문"))

        val row = waitForRow(title)
        assertEquals("SmartNotiDebugTester", row.appName)
    }

    private fun intent(title: String, body: String): Intent {
        return Intent("com.smartnoti.debug.INJECT_NOTIFICATION").apply {
            setPackage(context.packageName)
            putExtra("title", title)
            putExtra("body", body)
        }
    }

    /**
     * Drives the receiver synchronously by instantiating it and calling
     * `onReceive` directly — Robolectric's broadcast dispatch does not
     * always invoke manifest-registered receivers under the unit-test
     * source set. The receiver internally launches a coroutine on
     * [kotlinx.coroutines.Dispatchers.IO]; we wait on the DB side via
     * [waitForRow].
     */
    private fun dispatch(intent: Intent) {
        val receiver = DebugInjectNotificationReceiver()
        receiver.onReceive(context, intent)
        // Drain main looper so any posted Handler callbacks (e.g. Room's
        // background invalidation) have a chance to settle before polling.
        shadowOf(android.os.Looper.getMainLooper()).idle()
    }

    /**
     * Polls [NotificationRepository.observeAll] until a row whose title
     * exactly matches [title] appears, or the timeout elapses. The
     * receiver runs `inject` on Dispatchers.IO so we cannot synchronously
     * await its completion — polling is the simplest robust signal.
     */
    private suspend fun waitForRow(title: String): NotificationUiModel {
        val repository = NotificationRepository.getInstance(context)
        return withTimeout(10_000L) {
            var attempt = 0
            while (true) {
                val match = repository.observeAll().first().firstOrNull { it.title == title }
                if (match != null) return@withTimeout match
                attempt += 1
                kotlinx.coroutines.delay(50L)
                if (attempt > 200) error("Row '$title' did not appear within budget")
            }
            @Suppress("UNREACHABLE_CODE")
            error("unreachable")
        }
    }
}
