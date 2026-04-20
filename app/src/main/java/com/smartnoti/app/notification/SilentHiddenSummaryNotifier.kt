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
import com.smartnoti.app.onboarding.OnboardingPermissions

/**
 * Posts (or cancels) a single persistent SmartNoti notification that acts as the user's
 * inbox for anything classified as Silent. Silent notifications are hidden from the
 * system tray by default (see [SourceNotificationRoutingPolicy]); this summary is the
 * user's affordance to know how many are hidden and jump into a list view.
 */
class SilentHiddenSummaryNotifier(
    private val context: Context,
) {
    private val notificationManager by lazy { NotificationManagerCompat.from(context) }

    @SuppressLint("MissingPermission")
    fun post(count: Int) {
        if (count <= 0) {
            cancel()
            return
        }
        ensureChannel()
        if (!OnboardingPermissions.isPostNotificationsGranted(context)) return

        val contentIntent = createContentIntent()
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentTitle("숨겨진 알림 ${count}건")
            .setContentText("탭: 목록 보기 · 스와이프: 확인으로 처리")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(
                        "조용히로 분류된 알림 ${count}건을 알림센터에서 숨겼어요. " +
                            "탭하면 전체 목록을 보고, 옆으로 밀어 없애면 확인한 것으로 처리돼요 " +
                            "(다음 숨긴 알림이 들어오면 다시 알려드려요).",
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
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    fun cancel() {
        notificationManager.cancel(NOTIFICATION_ID)
    }

    fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "SmartNoti 숨긴 알림 요약",
            NotificationManager.IMPORTANCE_MIN,
        ).apply {
            description = "조용히로 분류되어 숨겨진 알림의 개수를 보여줍니다."
            setShowBadge(false)
            enableVibration(false)
            setSound(null, null)
        }
        manager.createNotificationChannel(channel)
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

    companion object {
        const val CHANNEL_ID = "smartnoti_silent_summary"
        const val NOTIFICATION_ID = 0x5A11
        const val ROUTE_HIDDEN = "hidden"
        private const val REQUEST_CODE = 0x5A12
    }
}
