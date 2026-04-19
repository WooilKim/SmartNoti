package com.smartnoti.app.ui.notificationaccess

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ApplicationProvider
import com.smartnoti.app.onboarding.OnboardingStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class NotificationAccessUiBehaviorTest {

    private val grantedStatus = OnboardingStatus(
        notificationListenerGranted = true,
        postNotificationsGranted = true,
        postNotificationsRequired = false,
    )

    @Test
    fun lifecycle_observer_refreshes_status_only_on_resume() {
        var refreshedStatus: OnboardingStatus? = null
        val observer = notificationAccessLifecycleObserver(
            statusProvider = { grantedStatus },
            onStatusChanged = { refreshedStatus = it },
        )

        observer.onStateChanged(FakeLifecycleOwner, Lifecycle.Event.ON_START)

        assertNull(refreshedStatus)

        observer.onStateChanged(FakeLifecycleOwner, Lifecycle.Event.ON_RESUME)

        assertEquals(grantedStatus, refreshedStatus)
    }

    @Test
    fun open_notification_access_settings_starts_intent_when_available() {
        val baseContext = ApplicationProvider.getApplicationContext<Context>()
        val recordingContext = RecordingContext(baseContext)
        var fallbackMessage: String? = null
        val intent = Intent("com.smartnoti.test.OPEN_SETTINGS")

        val opened = openNotificationAccessSettings(
            context = recordingContext,
            intentProvider = { intent },
            onActivityNotFound = { fallbackMessage = it },
        )

        assertTrue(opened)
        assertSame(intent, recordingContext.startedIntent)
        assertNull(fallbackMessage)
    }

    @Test
    fun open_notification_access_settings_reports_fallback_when_activity_is_missing() {
        val baseContext = ApplicationProvider.getApplicationContext<Context>()
        val recordingContext = ThrowingContext(baseContext)
        var fallbackMessage: String? = null

        val opened = openNotificationAccessSettings(
            context = recordingContext,
            intentProvider = { Intent("com.smartnoti.test.MISSING") },
            onActivityNotFound = { fallbackMessage = it },
        )

        assertFalse(opened)
        assertEquals("알림 접근 설정을 찾을 수 없어요.", fallbackMessage)
    }
}

private object FakeLifecycleOwner : androidx.lifecycle.LifecycleOwner {
    override val lifecycle: Lifecycle
        get() = throw UnsupportedOperationException("Lifecycle not used by observer test")
}

private class RecordingContext(base: Context) : ContextWrapper(base) {
    var startedIntent: Intent? = null
        private set

    override fun startActivity(intent: Intent?) {
        startedIntent = intent
    }
}

private class ThrowingContext(base: Context) : ContextWrapper(base) {
    override fun startActivity(intent: Intent?) {
        throw ActivityNotFoundException("Missing activity")
    }
}
