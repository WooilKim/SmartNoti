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
        val feedbackAction = intent.action.toFeedbackAction() ?: return

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
                val updated = feedbackPolicy.applyAction(notification, feedbackAction.ruleAction)
                repository.updateNotification(updated)
                RulesRepository.getInstance(appContext)
                    .upsertRule(feedbackPolicy.toRule(notification, feedbackAction.ruleAction))
                if (replacementNotificationId != Int.MIN_VALUE) {
                    NotificationManagerCompat.from(appContext).cancel(replacementNotificationId)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}

private enum class NotificationFeedbackAction(
    val intentAction: String,
    val ruleAction: RuleActionUi,
) {
    PROMOTE_TO_PRIORITY(
        intentAction = SmartNotiNotifier.ACTION_PROMOTE_TO_PRIORITY,
        ruleAction = RuleActionUi.ALWAYS_PRIORITY,
    ),
    KEEP_DIGEST(
        intentAction = SmartNotiNotifier.ACTION_KEEP_DIGEST,
        ruleAction = RuleActionUi.DIGEST,
    ),
    KEEP_SILENT(
        intentAction = SmartNotiNotifier.ACTION_KEEP_SILENT,
        ruleAction = RuleActionUi.SILENT,
    ),
}

private fun String?.toFeedbackAction(): NotificationFeedbackAction? {
    return NotificationFeedbackAction.entries.firstOrNull { it.intentAction == this }
}
