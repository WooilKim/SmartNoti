package com.smartnoti.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import com.smartnoti.app.navigation.AppNavHost
import com.smartnoti.app.navigation.ReplacementNotificationEntry
import com.smartnoti.app.navigation.ReplacementNotificationEntryRoutes
import com.smartnoti.app.notification.SmartNotiNotifier
import com.smartnoti.app.ui.theme.SmartNotiTheme

class MainActivity : ComponentActivity() {
    private val pendingNotificationEntry = mutableStateOf<ReplacementNotificationEntry?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingNotificationEntry.value = intent?.extractReplacementNotificationEntry()
        setContent {
            SmartNotiTheme {
                AppNavHost(
                    pendingNotificationEntry = pendingNotificationEntry.value,
                    onPendingNotificationConsumed = { pendingNotificationEntry.value = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingNotificationEntry.value = intent.extractReplacementNotificationEntry()
    }

    private fun Intent.extractReplacementNotificationEntry(): ReplacementNotificationEntry? {
        val notificationId = getStringExtra(SmartNotiNotifier.EXTRA_NOTIFICATION_ID)
            ?.takeIf { it.isNotBlank() }
            ?: return null
        val parentRoute = ReplacementNotificationEntryRoutes.sanitize(
            getStringExtra(SmartNotiNotifier.EXTRA_PARENT_ROUTE)
        )
        return ReplacementNotificationEntry(
            notificationId = notificationId,
            parentRoute = parentRoute,
        )
    }
}
