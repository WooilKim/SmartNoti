package com.smartnoti.app.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.smartnoti.app.data.local.NotificationRepository
import com.smartnoti.app.data.rules.RulesRepository
import com.smartnoti.app.domain.model.RuleActionUi
import com.smartnoti.app.domain.usecase.NotificationFeedbackPolicy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SmartNotiNotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != SmartNotiNotifier.ACTION_PROMOTE_TO_PRIORITY) return

        val notificationId = intent.getStringExtra(SmartNotiNotifier.EXTRA_NOTIFICATION_ID)
            ?.takeIf { it.isNotBlank() }
            ?: return
        val replacementNotificationId = intent.getIntExtra(
            SmartNotiNotifier.EXTRA_REPLACEMENT_NOTIFICATION_ID,
            Int.MIN_VALUE,
        )
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val appContext = context.applicationContext
                val repository = NotificationRepository.getInstance(appContext)
                val notification = repository.observeNotification(notificationId)
                    .first()
                    ?: return@launch
                val feedbackPolicy = NotificationFeedbackPolicy()
                val updated = feedbackPolicy.applyAction(notification, RuleActionUi.ALWAYS_PRIORITY)
                repository.updateNotification(updated)
                RulesRepository.getInstance(appContext)
                    .upsertRule(feedbackPolicy.toRule(notification, RuleActionUi.ALWAYS_PRIORITY))
                if (replacementNotificationId != Int.MIN_VALUE) {
                    NotificationManagerCompat.from(appContext).cancel(replacementNotificationId)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
