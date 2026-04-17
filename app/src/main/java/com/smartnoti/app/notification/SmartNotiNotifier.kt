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
import com.smartnoti.app.domain.model.NotificationDecision
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
    ) {
        ensureChannels()
        val channelId = when (decision) {
            NotificationDecision.DIGEST -> DIGEST_CHANNEL_ID
            NotificationDecision.SILENT -> SILENT_CHANNEL_ID
            NotificationDecision.PRIORITY -> return
        }
        val label = when (decision) {
            NotificationDecision.DIGEST -> "Digest"
            NotificationDecision.SILENT -> "Silent"
            NotificationDecision.PRIORITY -> return
        }
        val contentTitle = title.ifBlank { body.ifBlank { "$appName 알림" } }
        val contentText = ReplacementNotificationTextFormatter.explanationText(
            decision = decision,
            reasonTags = reasonTags,
        )
        val replacementNotificationId = NotificationReplacementIds.idFor(
            packageName = packageName,
            decision = decision,
        )
        val parentRoute = ReplacementNotificationEntryRoutes.forDecision(decision)
        val contentIntent = createContentIntent(
            notificationId = notificationId,
            parentRoute = parentRoute,
            requestCode = replacementNotificationId,
        )
        val keepAction = keepActionFor(decision)
        val notificationBuilder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(contentTitle)
            .setContentText(contentText)
            .setSubText("$appName • $label")
            .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
            .setPriority(
                when (decision) {
                    NotificationDecision.DIGEST -> NotificationCompat.PRIORITY_LOW
                    NotificationDecision.SILENT -> NotificationCompat.PRIORITY_MIN
                    NotificationDecision.PRIORITY -> NotificationCompat.PRIORITY_DEFAULT
                }
            )
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
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
        val digestChannel = NotificationChannel(
            DIGEST_CHANNEL_ID,
            "SmartNoti Digest",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Suppressed digest notifications surfaced by SmartNoti"
            setSound(null, null)
            enableVibration(false)
            setShowBadge(false)
        }
        val silentChannel = NotificationChannel(
            SILENT_CHANNEL_ID,
            "SmartNoti Silent",
            NotificationManager.IMPORTANCE_MIN,
        ).apply {
            description = "Suppressed silent notifications surfaced by SmartNoti"
            setSound(null, null)
            enableVibration(false)
            setShowBadge(false)
        }
        manager.createNotificationChannel(digestChannel)
        manager.createNotificationChannel(silentChannel)
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
    }

    private fun feedbackRequestCode(notificationId: String, action: RuleAction): Int {
        return feedbackRequestCodeForTest(notificationId, action.intentAction)
    }

    companion object {
        const val DIGEST_CHANNEL_ID = "smartnoti_digest"
        const val SILENT_CHANNEL_ID = "smartnoti_silent"
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
    fun idFor(packageName: String, decision: NotificationDecision): Int {
        return "$packageName:${decision.name}".hashCode()
    }
}
