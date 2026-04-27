package com.smartnoti.app.notification

/**
 * Pure helper that decides whether a notification's `EXTRA_TITLE` should be
 * adopted as the row's `sender` for tray grouping.
 *
 * Plan `docs/plans/2026-04-27-silent-sender-messagingstyle-gate.md` — the
 * listener used to fall back to `EXTRA_TITLE` unconditionally, which caused
 * non-messaging apps (shopping / news / promo) to leak product names or
 * article headlines into `NotificationEntity.sender`. That broke
 * `SilentNotificationGroupingPolicy`'s App-fallback because the row always
 * looked like a "named sender" with N=1 — different titles produced different
 * single-member groups instead of one App-level cluster.
 *
 * Contract:
 * 1. `conversationTitle` (Notification.EXTRA_CONVERSATION_TITLE) wins when
 *    non-blank — that extra is set explicitly by messaging apps.
 * 2. Otherwise, fall back to `title` (Notification.EXTRA_TITLE) only when a
 *    MessagingStyle hint is present:
 *    - `template` matches `android.app.Notification$MessagingStyle` or
 *      `androidx.core.app.NotificationCompat$MessagingStyle`, **or**
 *    - `hasMessages` is true (caller saw `EXTRA_MESSAGES` non-empty).
 * 3. Otherwise return `null`. The grouping policy fallback then kicks in and
 *    the row clusters by package name.
 *
 * The input is a small wrapper rather than `Bundle` so this stays unit-test
 * friendly without Robolectric. The listener is responsible for reading
 * extras and constructing [MessagingStyleSenderInput] before calling.
 */
object MessagingStyleSenderResolver {

    private const val MESSAGING_STYLE_TEMPLATE = "android.app.Notification\$MessagingStyle"
    private const val COMPAT_MESSAGING_STYLE_TEMPLATE = "androidx.core.app.NotificationCompat\$MessagingStyle"

    fun resolve(input: MessagingStyleSenderInput): String? {
        input.conversationTitle?.takeIf { it.isNotBlank() }?.let { return it }

        val isMessagingStyle = input.template == MESSAGING_STYLE_TEMPLATE ||
            input.template == COMPAT_MESSAGING_STYLE_TEMPLATE ||
            input.hasMessages

        return if (isMessagingStyle) {
            input.title?.takeIf { it.isNotBlank() }
        } else {
            null
        }
    }
}

data class MessagingStyleSenderInput(
    val conversationTitle: String?,
    val title: String?,
    val template: String?,
    val hasMessages: Boolean,
)
