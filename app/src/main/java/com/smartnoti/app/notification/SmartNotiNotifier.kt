package com.smartnoti.app.notification

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.smartnoti.app.MainActivity
import com.smartnoti.app.data.settings.SmartNotiSettings
import com.smartnoti.app.domain.model.DeliveryProfile
import com.smartnoti.app.domain.model.NotificationDecision
import com.smartnoti.app.domain.model.sanitizedForDecision
import com.smartnoti.app.domain.usecase.ReplacementNotificationTimeoutPolicy
import com.smartnoti.app.navigation.ReplacementNotificationEntryRoutes
import com.smartnoti.app.onboarding.OnboardingPermissions

class SmartNotiNotifier(
    private val context: Context,
    private val timeoutPolicy: ReplacementNotificationTimeoutPolicy = ReplacementNotificationTimeoutPolicy(),
    // Plan `2026-04-27-fix-issue-510-replacement-icon-source-action-overlay.md`
    // Task 4: source-app launcher icon resolver injected via the
    // listener service's AppContainer-equivalent. Default no-op resolver
    // (FakeAppIconSource over an empty map) keeps legacy callers /
    // existing tests that do not pass an explicit resolver compiling — the
    // resolver returns null for every package so notifications fall back
    // to "small icon only" rather than carrying a stale large icon. The
    // production listener service wiring (see
    // SmartNotiNotificationListenerService.appIconResolver) always passes
    // a real `AndroidAppIconSource`-backed resolver.
    private val appIconResolver: AppIconResolver = AppIconResolver(NoOpAppIconSource),
) {
    private val notificationManager by lazy { NotificationManagerCompat.from(context) }

    /**
     * Post a silent-suppression replacement alert.
     *
     * Plan `docs/plans/2026-04-22-categories-runtime-wiring-fix.md` Task 6
     * removes every per-action button that this alert used to carry
     * ("중요로 고정" / "Digest로 유지" / "조용히 유지" / "무시"). The alert is
     * now **tap-only**: tapping the body opens Detail, where the single
     * "분류 변경" CTA is the user's reclassification entry point.
     */
    @SuppressLint("MissingPermission")
    fun notifySuppressedNotification(
        decision: NotificationDecision,
        packageName: String,
        appName: String,
        title: String,
        body: String,
        notificationId: String,
        reasonTags: List<String>,
        settings: SmartNotiSettings,
        deliveryProfile: DeliveryProfile = DeliveryProfile.defaultsFor(decision),
    ) {
        if (decision == NotificationDecision.PRIORITY) return
        // IGNORE never posts a replacement alert — plan
        // `2026-04-21-ignore-tier-fourth-decision` Task 2.
        if (decision == NotificationDecision.IGNORE) return
        ensureChannels()
        val sanitizedProfile = deliveryProfile.sanitizedForDecision(decision)
        val channelSpec = ReplacementNotificationChannelRegistry.resolve(
            decision = decision,
            deliveryProfile = sanitizedProfile,
        )
        val label = when (decision) {
            NotificationDecision.DIGEST -> "Digest"
            NotificationDecision.SILENT -> "Silent"
            NotificationDecision.PRIORITY -> return
            NotificationDecision.IGNORE -> return
        }
        val contentTitle = title.ifBlank { body.ifBlank { "$appName 알림" } }
        val contentText = ReplacementNotificationTextFormatter.explanationText(
            decision = decision,
            reasonTags = reasonTags,
        )
        val replacementNotificationId = NotificationReplacementIds.idFor(
            packageName = packageName,
            decision = decision,
            notificationId = notificationId,
        )
        val parentRoute = ReplacementNotificationEntryRoutes.forDecision(decision)
        val contentIntent = createContentIntent(
            notificationId = notificationId,
            parentRoute = parentRoute,
            requestCode = replacementNotificationId,
        )
        // Plan `2026-04-27-fix-issue-510-replacement-icon-source-action-overlay.md`
        // Task 4: pick a SmartNoti action-specific small icon (DIGEST /
        // SILENT) so a single tray row visually identifies what SmartNoti
        // did. Replaces the legacy `android.R.drawable.ic_dialog_info`
        // which was identical for every replacement action.
        val actionIcon = when (decision) {
            NotificationDecision.DIGEST -> ReplacementActionIcon.DIGEST
            NotificationDecision.SILENT -> ReplacementActionIcon.SILENT
            // PRIORITY / IGNORE early-return above — branches kept for
            // exhaustiveness so a future routing change surfaces here.
            NotificationDecision.PRIORITY -> ReplacementActionIcon.PRIORITY
            NotificationDecision.IGNORE -> ReplacementActionIcon.SILENT
        }
        val notificationBuilder = NotificationCompat.Builder(context, channelSpec.id)
            .setSmallIcon(actionIcon.drawableRes)
            .setContentTitle(contentTitle)
            .setContentText(contentText)
            .setSubText("$appName • $label")
            .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
            .setPriority(channelSpec.compatPriority)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setVisibility(channelSpec.notificationVisibility)
            .setContentIntent(contentIntent)

        // Plan `2026-04-27-fix-issue-510-replacement-icon-source-action-overlay.md`
        // Task 4: source-app launcher icon as the large icon so the row
        // visually identifies the source. Resolver returns null when the
        // package has no displayable launcher icon (system service /
        // disabled / etc.); in that case we deliberately omit
        // setLargeIcon so the row is honest rather than mis-branded with
        // a SmartNoti default. The action small icon above still
        // identifies what SmartNoti did.
        appIconResolver.resolve(packageName)?.let { bitmap -> notificationBuilder.setLargeIcon(bitmap) }

        // Plan `2026-04-27-tray-replacement-auto-dismiss-timeout.md` Task 2:
        // when the user has the auto-dismiss toggle ON, ask Android to
        // cancel this replacement after `replacementAutoDismissMinutes`.
        // The DB row is unaffected — `setTimeoutAfter` only retracts the
        // tray entry, so the user still sees this notification under the
        // Digest / Hidden inbox via the persisted row.
        timeoutPolicy.timeoutMillisFor(settings, decision)?.let {
            notificationBuilder.setTimeoutAfter(it)
        }

        if (channelSpec.silentBuilder) {
            notificationBuilder.setSilent(true)
        }

        val notification = notificationBuilder.build()

        if (!OnboardingPermissions.isPostNotificationsGranted(context)) {
            return
        }

        notificationManager.notify(
            replacementNotificationId,
            notification,
        )
    }

    fun ensureChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        ReplacementNotificationChannelRegistry.all().forEach { spec ->
            val channel = NotificationChannel(
                spec.id,
                spec.name,
                spec.importance,
            ).apply {
                description = spec.description
                lockscreenVisibility = spec.notificationVisibility
                enableVibration(spec.vibrationEnabled)
                if (spec.vibrationEnabled) {
                    vibrationPattern = spec.vibrationPattern.toLongArray()
                }
                setShowBadge(false)
                if (spec.soundEnabled) {
                    setSound(android.provider.Settings.System.DEFAULT_NOTIFICATION_URI, audioAttributes)
                } else {
                    setSound(null, null)
                }
            }
            manager.createNotificationChannel(channel)
        }

        ensureSilentGroupChannelOn(manager)
    }


    private fun createContentIntent(
        notificationId: String,
        parentRoute: String,
        requestCode: Int,
    ): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_NOTIFICATION_ID, notificationId)
            putExtra(EXTRA_PARENT_ROUTE, parentRoute)
        }
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        const val EXTRA_NOTIFICATION_ID = "com.smartnoti.app.extra.NOTIFICATION_ID"
        const val EXTRA_PARENT_ROUTE = "com.smartnoti.app.extra.PARENT_ROUTE"
        const val EXTRA_DEEP_LINK_ROUTE = "com.smartnoti.app.extra.DEEP_LINK_ROUTE"

        /**
         * Channel that hosts Silent **group summaries** and their **children** in the system
         * tray. `IMPORTANCE_MIN` so children arriving after we've already classified them as
         * SILENT never surface a heads-up or sound — they exist only so Android's group-collapse
         * affordance (tap the summary to expand children) can show the user which senders have
         * pending 조용히 items.
         *
         * Introduced by `silent-tray-sender-grouping` Task 2.
         */
        const val CHANNEL_SILENT_GROUP = "smartnoti_silent_group"

        /**
         * Ensures the [CHANNEL_SILENT_GROUP] channel exists without requiring a full
         * [SmartNotiNotifier] instance. Reused by [SilentHiddenSummaryNotifier] so the tray
         * grouping pipeline can create the channel lazily on first post, matching Android's
         * "first create channel, then notify()" contract.
         */
        fun ensureSilentGroupChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            ensureSilentGroupChannelOn(manager)
        }

        private fun ensureSilentGroupChannelOn(manager: NotificationManager) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            if (manager.getNotificationChannel(CHANNEL_SILENT_GROUP) != null) return
            val channel = NotificationChannel(
                CHANNEL_SILENT_GROUP,
                "SmartNoti 조용히 그룹",
                NotificationManager.IMPORTANCE_MIN,
            ).apply {
                description = "조용히로 분류된 알림을 발신자·앱 단위로 묶어 보여주는 채널입니다."
                setShowBadge(false)
                enableVibration(false)
                setSound(null, null)
                lockscreenVisibility = android.app.Notification.VISIBILITY_SECRET
            }
            manager.createNotificationChannel(channel)
        }
    }
}

object NotificationReplacementIds {
    /**
     * Returns a stable replacement-notification id keyed by the source notification's
     * identity, not just (packageName, decision). Earlier the id collapsed every DIGEST
     * from the same app into a single replacement slot, so a second DIGEST arriving
     * while the first was still in the tray would silently overwrite it. Mixing
     * [notificationId] into the hash keeps each source notification's replacement
     * distinct. When [notificationId] is blank we fall back to the legacy keying so
     * the old tests and call paths stay stable.
     */
    fun idFor(
        packageName: String,
        decision: NotificationDecision,
        notificationId: String = "",
    ): Int {
        return if (notificationId.isBlank()) {
            "$packageName:${decision.name}".hashCode()
        } else {
            "$packageName:${decision.name}:$notificationId".hashCode()
        }
    }
}
