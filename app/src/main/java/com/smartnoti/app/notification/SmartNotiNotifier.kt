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
import com.smartnoti.app.domain.model.DeliveryProfile
import com.smartnoti.app.domain.model.NotificationDecision
import com.smartnoti.app.domain.model.sanitizedForDecision
import com.smartnoti.app.navigation.ReplacementNotificationEntryRoutes
import com.smartnoti.app.onboarding.OnboardingPermissions

class SmartNotiNotifier(
    private val context: Context,
) {
    private val notificationManager by lazy { NotificationManagerCompat.from(context) }

    @SuppressLint("MissingPermission")
    fun notifySuppressedNotification(
        decision: NotificationDecision,
        packageName: String,
        appName: String,
        title: String,
        body: String,
        notificationId: String,
        reasonTags: List<String>,
        deliveryProfile: DeliveryProfile = DeliveryProfile.defaultsFor(decision),
    ) {
        if (decision == NotificationDecision.PRIORITY) return
        // IGNORE early-return — plan `2026-04-21-ignore-tier-fourth-decision`
        // Task 2 seeds the guard here; Task 4 expands it into the full early
        // path in [SmartNotiNotificationListenerService] (persist + tray
        // cancel + no replacement alert). For Task 2 we simply refuse to
        // post a replacement notification to keep the build green without
        // materializing any user-visible IGNORE behaviour yet.
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
        val keepAction = keepActionFor(decision)
        val notificationBuilder = NotificationCompat.Builder(context, channelSpec.id)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
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
            .addAction(
                android.R.drawable.ic_input_add,
                ACTION_LABEL_PROMOTE_TO_PRIORITY,
                createFeedbackActionIntent(
                    notificationId = notificationId,
                    replacementNotificationId = replacementNotificationId,
                    action = RuleAction.ALWAYS_PRIORITY,
                ),
            )

        if (channelSpec.silentBuilder) {
            notificationBuilder.setSilent(true)
        }

        if (keepAction != null) {
            notificationBuilder.addAction(
                android.R.drawable.ic_menu_recent_history,
                keepAction.label,
                createFeedbackActionIntent(
                    notificationId = notificationId,
                    replacementNotificationId = replacementNotificationId,
                    action = keepAction.action,
                ),
            )
        }

        val notification = notificationBuilder
            .addAction(
                android.R.drawable.ic_menu_view,
                ACTION_LABEL_OPEN,
                contentIntent,
            )
            .build()

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

    private fun createFeedbackActionIntent(
        notificationId: String,
        replacementNotificationId: Int,
        action: RuleAction,
    ): PendingIntent {
        val intent = Intent(context, SmartNotiNotificationActionReceiver::class.java).apply {
            this.action = action.intentAction
            putExtra(EXTRA_NOTIFICATION_ID, notificationId)
            putExtra(EXTRA_REPLACEMENT_NOTIFICATION_ID, replacementNotificationId)
        }
        return PendingIntent.getBroadcast(
            context,
            feedbackRequestCode(notificationId, action),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun keepActionFor(decision: NotificationDecision): NotificationQuickAction? = when (decision) {
        NotificationDecision.PRIORITY -> null
        NotificationDecision.DIGEST -> NotificationQuickAction(
            label = ACTION_LABEL_KEEP_DIGEST,
            action = RuleAction.DIGEST,
        )
        NotificationDecision.SILENT -> NotificationQuickAction(
            label = ACTION_LABEL_KEEP_SILENT,
            action = RuleAction.SILENT,
        )
        // No replacement alert is ever posted for IGNORE, so no "keep"
        // quick-action is needed. Plan `2026-04-21-ignore-tier-fourth-decision`
        // Task 2 — the wiring is finalized alongside the Detail feedback
        // button in Task 6a.
        NotificationDecision.IGNORE -> null
    }

    private fun feedbackRequestCode(notificationId: String, action: RuleAction): Int {
        return feedbackRequestCodeForTest(notificationId, action.intentAction)
    }

    companion object {
        const val ACTION_PROMOTE_TO_PRIORITY = "com.smartnoti.app.action.PROMOTE_TO_PRIORITY"
        const val ACTION_KEEP_DIGEST = "com.smartnoti.app.action.KEEP_DIGEST"
        const val ACTION_KEEP_SILENT = "com.smartnoti.app.action.KEEP_SILENT"
        const val ACTION_LABEL_OPEN = "열기"
        const val ACTION_LABEL_PROMOTE_TO_PRIORITY = "중요로 고정"
        const val ACTION_LABEL_KEEP_DIGEST = "Digest로 유지"
        const val ACTION_LABEL_KEEP_SILENT = "조용히 유지"
        const val EXTRA_NOTIFICATION_ID = "com.smartnoti.app.extra.NOTIFICATION_ID"
        const val EXTRA_PARENT_ROUTE = "com.smartnoti.app.extra.PARENT_ROUTE"
        const val EXTRA_REPLACEMENT_NOTIFICATION_ID = "com.smartnoti.app.extra.REPLACEMENT_NOTIFICATION_ID"
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

        internal fun feedbackRequestCodeForTest(notificationId: String, action: String): Int {
            return (notificationId.hashCode() * 31) + action.hashCode()
        }
    }
}

private data class NotificationQuickAction(
    val label: String,
    val action: RuleAction,
)

private enum class RuleAction(
    val intentAction: String,
) {
    ALWAYS_PRIORITY(SmartNotiNotifier.ACTION_PROMOTE_TO_PRIORITY),
    DIGEST(SmartNotiNotifier.ACTION_KEEP_DIGEST),
    SILENT(SmartNotiNotifier.ACTION_KEEP_SILENT),
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
