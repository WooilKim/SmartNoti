package com.smartnoti.app.notification

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.smartnoti.app.MainActivity
import com.smartnoti.app.data.local.NotificationEntity
import com.smartnoti.app.data.settings.SmartNotiSettings
import com.smartnoti.app.domain.model.NotificationDecision
import com.smartnoti.app.domain.usecase.ReplacementNotificationTimeoutPolicy
import com.smartnoti.app.domain.usecase.SilentGroupKey
import com.smartnoti.app.onboarding.OnboardingPermissions

/**
 * Posts (or cancels) a single persistent SmartNoti notification that acts as the user's
 * inbox for anything classified as Silent and still in the **보관 중** (archived) bucket
 * of the Hidden inbox. Items the user has already moved to **처리됨** (processed) are
 * excluded — they have been acknowledged and should not re-clutter the tray.
 *
 * Silent notifications are hidden from the system tray by default (see
 * [SourceNotificationRoutingPolicy]); this summary is the user's affordance to know
 * how many 보관 중 items remain and jump into the list view.
 *
 * Starting with `silent-tray-sender-grouping` Task 2, this notifier also owns the
 * **group-level** Silent tray surface: per-sender / per-app group summaries and their
 * IMPORTANCE_MIN children. Those ride on [SmartNotiNotifier.CHANNEL_SILENT_GROUP],
 * distinct from the archived-summary channel above, because they target a different
 * product contract (sender granularity inside the tray, not the 보관 중 header count).
 */
class SilentHiddenSummaryNotifier(
    private val context: Context,
    private val timeoutPolicy: ReplacementNotificationTimeoutPolicy = ReplacementNotificationTimeoutPolicy(),
    // Plan `2026-04-27-fix-issue-510-replacement-icon-source-action-overlay.md`
    // Task 4: source-app launcher icon resolver. Default no-op resolver
    // keeps legacy callers / existing tests compiling — see the same
    // pattern documented on SmartNotiNotifier's constructor. The
    // production listener service wiring always passes a real resolver.
    private val appIconResolver: AppIconResolver = AppIconResolver(NoOpAppIconSource),
) {
    private val notificationManager by lazy { NotificationManagerCompat.from(context) }

    @SuppressLint("MissingPermission")
    fun post(count: Int, settings: SmartNotiSettings) {
        if (count <= 0) {
            cancel()
            return
        }
        ensureChannel()
        if (!OnboardingPermissions.isPostNotificationsGranted(context)) return

        val contentIntent = createContentIntent()
        // Plan `2026-04-27-fix-issue-510-replacement-icon-source-action-overlay.md`
        // Task 4: SILENT small icon (volume_off glyph) so the archived
        // bell visually identifies the SILENT action. Large icon is
        // intentionally omitted — this summary aggregates across every
        // SILENT item in the inbox so no single source can fairly
        // represent the row.
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(ReplacementActionIcon.SILENT.drawableRes)
            .setContentTitle("보관 중인 조용한 알림 ${count}건")
            .setContentText("탭: 보관함 열기 · 스와이프: 확인으로 처리")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(
                        "조용히로 분류되어 보관 중인 알림이 ${count}건 있어요. " +
                            "탭하면 '보관 중' 목록을 열고, 옆으로 밀어 없애면 확인한 것으로 처리돼요 " +
                            "(다음 보관 알림이 들어오면 다시 알려드려요).",
                    ),
            )
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setOngoing(false)
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .setContentIntent(contentIntent)
            .addAction(
                android.R.drawable.ic_menu_view,
                "숨겨진 알림 보기",
                contentIntent,
            )
        // Plan `2026-04-27-tray-replacement-auto-dismiss-timeout.md` Task 2:
        // archived-summary follows the same auto-dismiss policy as other
        // SmartNoti-posted SILENT tray entries. The summary is keyed off
        // the SILENT decision because it is the user's SILENT inbox bell.
        timeoutPolicy.timeoutMillisFor(settings, NotificationDecision.SILENT)?.let {
            builder.setTimeoutAfter(it)
        }

        notificationManager.notify(NOTIFICATION_ID, builder.build())
    }

    fun cancel() {
        notificationManager.cancel(NOTIFICATION_ID)
    }

    fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "SmartNoti 보관 중 알림 요약",
                NotificationManager.IMPORTANCE_MIN,
            ).apply {
                description = "조용히로 분류되어 보관 중인 알림의 개수를 보여줍니다."
                setShowBadge(false)
                enableVibration(false)
                setSound(null, null)
            }
            manager.createNotificationChannel(channel)
        }
        SmartNotiNotifier.ensureSilentGroupChannel(context)
    }

    /**
     * Posts a per-group summary notification for [key]. Android collapses children with the
     * same [groupTagFor] value under this summary in the tray, so tapping the summary expands
     * them inline without SmartNoti having to synthesise its own inbox UI.
     *
     * Passing `count == 0` (no children remain for the key) is treated as a cancel so the
     * listener pipeline can call [postGroupSummary] unconditionally with the current count.
     */
    @SuppressLint("MissingPermission")
    fun postGroupSummary(
        key: SilentGroupKey,
        count: Int,
        preview: List<NotificationEntity>,
        rootDeepLink: String,
        settings: SmartNotiSettings,
    ) {
        if (count <= 0) {
            cancelGroupSummary(key)
            return
        }
        SmartNotiNotifier.ensureSilentGroupChannel(context)
        if (!OnboardingPermissions.isPostNotificationsGranted(context)) return

        val title = "${key.displayLabel()} · 조용히 ${count}건"
        val previewLines = preview
            .take(MAX_PREVIEW_LINES)
            .map { it.previewLine() }
            .filter { it.isNotBlank() }
        val inboxStyle = NotificationCompat.InboxStyle()
            .setBigContentTitle(title)
            .setSummaryText("SmartNoti · 조용히")
        previewLines.forEach { inboxStyle.addLine(it) }
        val contentText = when {
            previewLines.isEmpty() -> "탭해서 보관함에서 확인하세요"
            else -> previewLines.first()
        }
        val contentIntent = createGroupContentIntent(rootDeepLink, key)
        // Plan `2026-04-27-fix-issue-510-replacement-icon-source-action-overlay.md`
        // Task 4: SILENT small icon for the per-group summary. Large icon
        // is set ONLY when every child shares the same source package —
        // mixed-source groups omit large to avoid picking an unfair
        // "winner" representative (Product intent decision in the plan).
        val builder = NotificationCompat.Builder(context, SmartNotiNotifier.CHANNEL_SILENT_GROUP)
            .setSmallIcon(ReplacementActionIcon.SILENT.drawableRes)
            .setContentTitle(title)
            .setContentText(contentText)
            .setStyle(inboxStyle)
            .setGroup(groupTagFor(key))
            .setGroupSummary(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .setContentIntent(contentIntent)
        // Plan `2026-04-27-tray-replacement-auto-dismiss-timeout.md` Task 2:
        // group summaries respect the same auto-dismiss policy. Children
        // (postGroupChild) carry the same timeout, so the summary and its
        // children retract roughly in lock-step.
        timeoutPolicy.timeoutMillisFor(settings, NotificationDecision.SILENT)?.let {
            builder.setTimeoutAfter(it)
        }

        // Plan `2026-04-27-fix-issue-510-replacement-icon-source-action-overlay.md`
        // Task 4: large icon for homogeneous groups only. When every
        // child shares the same source packageName, the source is
        // unambiguous and the large icon adds value; mixed-source groups
        // omit it (Product intent — no fair single-source representative).
        val previewPackages = preview.mapTo(HashSet()) { it.packageName }
        if (previewPackages.size == 1) {
            appIconResolver.resolve(previewPackages.single())?.let { bitmap ->
                builder.setLargeIcon(bitmap)
            }
        }

        notificationManager.notify(groupSummaryNotificationIdFor(key), builder.build())
    }

    /** Cancels the group summary notification for [key] if one is currently posted. */
    fun cancelGroupSummary(key: SilentGroupKey) {
        notificationManager.cancel(groupSummaryNotificationIdFor(key))
    }

    /**
     * Cancels a previously-posted group child notification keyed on the same `notificationId`
     * that was passed to [postGroupChild]. Paired with [cancelGroupSummary] so the listener's
     * tray-grouping pipeline (plan Task 3) can retract an entire group when it drops to a
     * singleton or disappears.
     */
    fun cancelGroupChild(notificationId: Long) {
        notificationManager.cancel(groupChildNotificationId(notificationId))
    }

    /**
     * Posts a per-child Silent notification belonging to [key]'s group. The child shares the
     * same `setGroup(groupTagFor(key))` as the summary, so the Android UI (SystemUI / shade)
     * stacks them under a single header that the user must explicitly expand.
     */
    @SuppressLint("MissingPermission")
    fun postGroupChild(
        notificationId: Long,
        entity: NotificationEntity,
        key: SilentGroupKey,
        settings: SmartNotiSettings,
    ) {
        SmartNotiNotifier.ensureSilentGroupChannel(context)
        if (!OnboardingPermissions.isPostNotificationsGranted(context)) return

        val childTitle = entity.title.ifBlank {
            entity.sender?.takeIf { it.isNotBlank() } ?: entity.appName
        }
        val childText = entity.body.ifBlank { "조용히로 분류됨" }
        val contentIntent = createGroupContentIntent(ROUTE_HIDDEN, key)
        // Plan `2026-04-27-fix-issue-510-replacement-icon-source-action-overlay.md`
        // Task 4: SILENT small icon + source-app launcher large icon so
        // the expanded child row visually identifies both the action and
        // the source app at a glance.
        val builder = NotificationCompat.Builder(context, SmartNotiNotifier.CHANNEL_SILENT_GROUP)
            .setSmallIcon(ReplacementActionIcon.SILENT.drawableRes)
            .setContentTitle(childTitle)
            .setContentText(childText)
            .setSubText("${entity.appName} · 조용히")
            .setStyle(NotificationCompat.BigTextStyle().bigText(childText))
            .setGroup(groupTagFor(key))
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .setWhen(entity.postedAtMillis)
            .setContentIntent(contentIntent)
        // Plan `2026-04-27-tray-replacement-auto-dismiss-timeout.md` Task 2:
        // children retract on the same SILENT timeout as their summary.
        timeoutPolicy.timeoutMillisFor(settings, NotificationDecision.SILENT)?.let {
            builder.setTimeoutAfter(it)
        }
        // Plan `2026-04-27-fix-issue-510-replacement-icon-source-action-overlay.md`
        // Task 4: source-app launcher icon for the expanded child row.
        // Resolver returns null for system services with no displayable
        // launcher icon; in that case we omit setLargeIcon so the row is
        // honest rather than mis-branded.
        appIconResolver.resolve(entity.packageName)?.let { bitmap -> builder.setLargeIcon(bitmap) }

        notificationManager.notify(groupChildNotificationId(notificationId), builder.build())
    }

    private fun NotificationEntity.previewLine(): String {
        val label = sender?.takeIf { it.isNotBlank() } ?: title.ifBlank { appName }
        val bodyPreview = body.ifBlank { "" }
        return if (bodyPreview.isBlank()) label else "$label · $bodyPreview"
    }

    private fun SilentGroupKey.displayLabel(): String = when (this) {
        is SilentGroupKey.Sender -> normalizedName
        is SilentGroupKey.App -> packageName
    }

    private fun createContentIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(SmartNotiNotifier.EXTRA_DEEP_LINK_ROUTE, ROUTE_HIDDEN)
        }
        return PendingIntent.getActivity(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun createGroupContentIntent(
        rootDeepLink: String,
        key: SilentGroupKey,
    ): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(SmartNotiNotifier.EXTRA_DEEP_LINK_ROUTE, rootDeepLink)
            when (key) {
                is SilentGroupKey.Sender ->
                    putExtra(EXTRA_DEEP_LINK_SENDER, key.normalizedName)
                is SilentGroupKey.App ->
                    putExtra(EXTRA_DEEP_LINK_PACKAGE_NAME, key.packageName)
            }
        }
        return PendingIntent.getActivity(
            context,
            groupRequestCodeFor(key),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun groupRequestCodeFor(key: SilentGroupKey): Int {
        return REQUEST_CODE xor groupTagFor(key).hashCode()
    }

    companion object {
        const val CHANNEL_ID = "smartnoti_silent_summary"
        const val NOTIFICATION_ID = 0x5A11
        const val ROUTE_HIDDEN = "hidden"
        const val EXTRA_DEEP_LINK_SENDER = "com.smartnoti.app.extra.DEEP_LINK_SENDER"
        const val EXTRA_DEEP_LINK_PACKAGE_NAME = "com.smartnoti.app.extra.DEEP_LINK_PACKAGE_NAME"

        private const val REQUEST_CODE = 0x5A12
        private const val GROUP_TAG_PREFIX = "smartnoti_silent_group_"
        private const val GROUP_SUMMARY_ID_BASE = 0x5A20
        private const val GROUP_CHILD_ID_BASE = 0x5A30_0000
        private const val MAX_PREVIEW_LINES = 3

        /**
         * Stable per-key group tag the tray uses to collapse children under a summary. Sender
         * and App variants have disjoint hash spaces (different prefix suffixes) so the same
         * literal string cannot accidentally re-key a Sender group as an App group.
         */
        fun groupTagFor(key: SilentGroupKey): String {
            val suffix = when (key) {
                is SilentGroupKey.Sender -> "sender:${key.normalizedName}"
                is SilentGroupKey.App -> "app:${key.packageName}"
            }
            return GROUP_TAG_PREFIX + suffix
        }

        /**
         * Unique notification id for a group **summary**. Keyed off the group tag's hash so
         * repeated `postGroupSummary(key, ...)` calls update rather than duplicate.
         */
        internal fun groupSummaryNotificationIdFor(key: SilentGroupKey): Int {
            return GROUP_SUMMARY_ID_BASE xor groupTagFor(key).hashCode()
        }

        /**
         * Notification id for a group child. Uses a high base so it can never collide with the
         * legacy archived-summary `NOTIFICATION_ID` (0x5A11) or the group-summary id space.
         */
        internal fun groupChildNotificationId(rowId: Long): Int {
            return GROUP_CHILD_ID_BASE xor rowId.toInt()
        }
    }
}
