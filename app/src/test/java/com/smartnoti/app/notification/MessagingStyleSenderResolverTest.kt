package com.smartnoti.app.notification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Plan `docs/plans/2026-04-27-silent-sender-messagingstyle-gate.md` Task 1.
 *
 * Headless JUnit guards the new contract for sender extraction: the listener
 * may only adopt `EXTRA_TITLE` as the row's `sender` when the source
 * notification carries a MessagingStyle hint (template marker or
 * `EXTRA_MESSAGES`). Otherwise `sender` must be null so
 * `SilentNotificationGroupingPolicy` falls back to App-level grouping for
 * non-messaging surfaces (shopping / news / promo apps that fill `EXTRA_TITLE`
 * with product names or article headlines).
 *
 * The resolver takes a wrapper input rather than `Bundle` so it stays
 * testable without Robolectric; the listener is responsible for normalising
 * `Bundle` extras into `MessagingStyleSenderInput` before calling.
 */
class MessagingStyleSenderResolverTest {

    private val messagingTemplate = "android.app.Notification\$MessagingStyle"
    private val compatMessagingTemplate = "androidx.core.app.NotificationCompat\$MessagingStyle"

    @Test
    fun `conversation title is always preferred when present`() {
        val sender = MessagingStyleSenderResolver.resolve(
            MessagingStyleSenderInput(
                conversationTitle = "엄마",
                title = null,
                template = null,
                hasMessages = false,
            )
        )

        assertEquals("엄마", sender)
    }

    @Test
    fun `title is adopted when standard MessagingStyle template is set`() {
        val sender = MessagingStyleSenderResolver.resolve(
            MessagingStyleSenderInput(
                conversationTitle = null,
                title = "엄마",
                template = messagingTemplate,
                hasMessages = false,
            )
        )

        assertEquals("엄마", sender)
    }

    @Test
    fun `title is adopted when NotificationCompat MessagingStyle template is set`() {
        val sender = MessagingStyleSenderResolver.resolve(
            MessagingStyleSenderInput(
                conversationTitle = null,
                title = "엄마",
                template = compatMessagingTemplate,
                hasMessages = false,
            )
        )

        assertEquals("엄마", sender)
    }

    @Test
    fun `title is adopted when EXTRA_MESSAGES is non-empty even without template`() {
        val sender = MessagingStyleSenderResolver.resolve(
            MessagingStyleSenderInput(
                conversationTitle = null,
                title = "엄마",
                template = null,
                hasMessages = true,
            )
        )

        assertEquals("엄마", sender)
    }

    @Test
    fun `non-messaging notification with promotional title returns null`() {
        val sender = MessagingStyleSenderResolver.resolve(
            MessagingStyleSenderInput(
                conversationTitle = null,
                title = "오늘만 30% 할인",
                template = null,
                hasMessages = false,
            )
        )

        assertNull(sender)
    }

    @Test
    fun `blank title with messaging template returns null`() {
        val sender = MessagingStyleSenderResolver.resolve(
            MessagingStyleSenderInput(
                conversationTitle = null,
                title = "",
                template = messagingTemplate,
                hasMessages = false,
            )
        )

        assertNull(sender)
    }

    @Test
    fun `conversation title wins over title even when messaging template is set`() {
        val sender = MessagingStyleSenderResolver.resolve(
            MessagingStyleSenderInput(
                conversationTitle = "팀장",
                title = "공지",
                template = messagingTemplate,
                hasMessages = true,
            )
        )

        assertEquals("팀장", sender)
    }

    @Test
    fun `everything blank returns null`() {
        val sender = MessagingStyleSenderResolver.resolve(
            MessagingStyleSenderInput(
                conversationTitle = null,
                title = null,
                template = null,
                hasMessages = false,
            )
        )

        assertNull(sender)
    }

    @Test
    fun `blank conversation title falls through to messaging-gated title`() {
        val sender = MessagingStyleSenderResolver.resolve(
            MessagingStyleSenderInput(
                conversationTitle = "   ",
                title = "엄마",
                template = messagingTemplate,
                hasMessages = false,
            )
        )

        assertEquals("엄마", sender)
    }
}
