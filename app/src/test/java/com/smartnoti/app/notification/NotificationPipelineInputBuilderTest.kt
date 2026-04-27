package com.smartnoti.app.notification

import android.app.Notification
import android.os.Bundle
import android.os.Process
import android.os.UserHandle
import android.service.notification.StatusBarNotification
import com.smartnoti.app.data.settings.SmartNotiSettings
import com.smartnoti.app.domain.model.NotificationContext
import com.smartnoti.app.domain.usecase.LiveDuplicateCountTracker
import com.smartnoti.app.domain.usecase.PersistentNotificationPolicy
import com.smartnoti.app.domain.usecase.QuietHoursPolicy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Plan `2026-04-27-refactor-listener-process-notification-extract.md` Task 3.
 *
 * Pins the four contracts the listener relies on after the input-build stage
 * was extracted:
 *   1. `appNameLookup` is invoked with the SBN's package name and its result
 *      surfaces on both [NotificationPipelineInputBuilder.PipelineInput] and
 *      the inner [com.smartnoti.app.domain.model.CapturedNotificationInput].
 *   2. `MessagingStyleSenderResolver` is consulted — the EXTRA_TITLE-only
 *      shopping/promo path leaves `sender = null` (gate from plan
 *      `2026-04-27-silent-sender-messagingstyle-gate.md`).
 *   3. `isPersistent` is forwarded to the captured input and the duplicate
 *      threshold from settings is plumbed through unchanged.
 *   4. The persistent suffix on contentSignature still fires when the SBN
 *      is treated as persistent.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class NotificationPipelineInputBuilderTest {

    @Test
    fun build_forwards_appName_lookup_and_threshold_for_non_persistent_capture() = runTest {
        val tracker = LiveDuplicateCountTracker()
        val duplicateBuilder = NotificationDuplicateContextBuilder(
            tracker = tracker,
            persistedDuplicateCount = { _, _, _ -> 0 },
        )
        var lookedUpPackage: String? = null
        var lookedUpDuplicateCount: Int = -1
        val builder = NotificationPipelineInputBuilder(
            persistentNotificationPolicy = PersistentNotificationPolicy(),
            duplicateContextBuilder = duplicateBuilder,
            appNameLookup = { pkg ->
                lookedUpPackage = pkg
                "Pretty Name"
            },
            contextLookup = { count ->
                lookedUpDuplicateCount = count
                NotificationContext(
                    quietHoursEnabled = false,
                    quietHoursPolicy = QuietHoursPolicy(0, 0),
                    currentHourOfDay = 0,
                    duplicateCountInWindow = count,
                )
            },
        )

        val sbn = sbn(
            packageName = "com.example.app",
            key = "k1",
            title = "Promo",
            body = "Sale",
            isOngoing = false,
            isClearable = true,
        )

        val result = builder.build(
            sbn = sbn,
            settings = SmartNotiSettings(
                duplicateWindowMinutes = 10,
                duplicateDigestThreshold = 4,
            ),
        )

        assertEquals("com.example.app", lookedUpPackage)
        assertEquals("Pretty Name", result.appName)
        assertEquals("Pretty Name", result.captureInput.appName)
        assertEquals("com.example.app", result.captureInput.packageName)
        assertFalse(result.isPersistent)
        // Plan `2026-04-27-silent-sender-messagingstyle-gate.md`: shopping/promo
        // notifications without MessagingStyle hint must not leak title -> sender.
        assertEquals(null, result.captureInput.sender)
        assertEquals(4, result.captureInput.duplicateThreshold)
        // Non-persistent → contentSignature has no |persistent: suffix.
        assertFalse(result.contentSignature.contains("|persistent:"))
        // contextLookup was driven by the duplicate count (1 for first
        // sighting — `LiveDuplicateCountTracker.recordAndCount` floors at 1
        // because the current notification is itself a sighting).
        assertEquals(1, lookedUpDuplicateCount)
    }

    @Test
    fun build_marks_persistent_and_appends_persistent_suffix_to_signature() = runTest {
        val tracker = LiveDuplicateCountTracker()
        val duplicateBuilder = NotificationDuplicateContextBuilder(
            tracker = tracker,
            persistedDuplicateCount = { _, _, _ -> 0 },
        )
        val builder = NotificationPipelineInputBuilder(
            persistentNotificationPolicy = PersistentNotificationPolicy(),
            duplicateContextBuilder = duplicateBuilder,
            appNameLookup = { it },
            contextLookup = { count ->
                NotificationContext(
                    quietHoursEnabled = false,
                    quietHoursPolicy = QuietHoursPolicy(0, 0),
                    currentHourOfDay = 0,
                    duplicateCountInWindow = count,
                )
            },
        )

        // Ongoing + non-clearable -> treat as persistent.
        val sbn = sbn(
            packageName = "com.example.foreground",
            key = "fg-1",
            title = "Service running",
            body = "Tap to manage",
            isOngoing = true,
            isClearable = false,
            id = 99,
        )

        val result = builder.build(
            sbn = sbn,
            settings = SmartNotiSettings(duplicateWindowMinutes = 10),
        )

        assertTrue(result.isPersistent)
        assertNotNull(result.contentSignature)
        assertTrue(
            "Persistent capture must add |persistent:<pkg>:<id> suffix",
            result.contentSignature.endsWith("|persistent:com.example.foreground:99"),
        )
        // Persistent capture forces duplicateCountInWindow to 1.
        assertEquals(1, result.captureInput.duplicateCountInWindow)
    }

    private fun sbn(
        packageName: String,
        key: String,
        title: String,
        body: String,
        isOngoing: Boolean,
        isClearable: Boolean,
        id: Int = key.hashCode(),
    ): StatusBarNotification {
        var flags = 0
        if (isOngoing) flags = flags or Notification.FLAG_ONGOING_EVENT
        if (!isClearable) flags = flags or Notification.FLAG_NO_CLEAR
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
            id,
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
