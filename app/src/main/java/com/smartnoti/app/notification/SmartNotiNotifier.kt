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
        val contentTitle = title.ifBlank { "$appName 알림" }
        val contentText = body.ifBlank { "$appName 알림이 SmartNoti에서 정리되었어요." }
        val notification = NotificationCompat.Builder(context, channelId)
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
            .setContentIntent(
                createContentIntent(
                    notificationId = notificationId,
                    requestCode = NotificationReplacementIds.idFor(
                        packageName = packageName,
                        decision = decision,
                    ),
                )
            )
            .build()

        if (!OnboardingPermissions.isPostNotificationsGranted(context)) {
            return
        }

        notificationManager.notify(
            NotificationReplacementIds.idFor(packageName = packageName, decision = decision),
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
        requestCode: Int,
    ): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_NOTIFICATION_ID, notificationId)
        }
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        const val DIGEST_CHANNEL_ID = "smartnoti_digest"
        const val SILENT_CHANNEL_ID = "smartnoti_silent"
        const val EXTRA_NOTIFICATION_ID = "com.smartnoti.app.extra.NOTIFICATION_ID"
    }
}

object NotificationReplacementIds {
    fun idFor(packageName: String, decision: NotificationDecision): Int {
        return "$packageName:${decision.name}".hashCode()
    }
}
