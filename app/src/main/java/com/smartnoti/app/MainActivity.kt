package com.smartnoti.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import com.smartnoti.app.navigation.AppNavHost
import com.smartnoti.app.notification.SmartNotiNotifier
import com.smartnoti.app.ui.theme.SmartNotiTheme

class MainActivity : ComponentActivity() {
    private val pendingNotificationId = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingNotificationId.value = intent?.extractNotificationId()
        setContent {
            SmartNotiTheme {
                AppNavHost(
                    pendingNotificationId = pendingNotificationId.value,
                    onPendingNotificationConsumed = { pendingNotificationId.value = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingNotificationId.value = intent.extractNotificationId()
    }

    private fun Intent.extractNotificationId(): String? =
        getStringExtra(SmartNotiNotifier.EXTRA_NOTIFICATION_ID)?.takeIf { it.isNotBlank() }
}
