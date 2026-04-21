package com.smartnoti.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import com.smartnoti.app.navigation.AppNavHost
import com.smartnoti.app.navigation.ReplacementNotificationEntry
import com.smartnoti.app.navigation.ReplacementNotificationEntryRoutes
import com.smartnoti.app.navigation.Routes
import com.smartnoti.app.notification.SilentHiddenSummaryNotifier
import com.smartnoti.app.notification.SmartNotiNotifier
import com.smartnoti.app.ui.theme.SmartNotiTheme

class MainActivity : ComponentActivity() {
    private val pendingNotificationEntry = mutableStateOf<ReplacementNotificationEntry?>(null)
    private val pendingDeepLinkRoute = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingNotificationEntry.value = intent?.extractReplacementNotificationEntry()
        pendingDeepLinkRoute.value = intent?.extractDeepLinkRoute()
        setContent {
            SmartNotiTheme {
                AppNavHost(
                    pendingNotificationEntry = pendingNotificationEntry.value,
                    onPendingNotificationConsumed = { pendingNotificationEntry.value = null },
                    pendingDeepLinkRoute = pendingDeepLinkRoute.value,
                    onPendingDeepLinkRouteConsumed = { pendingDeepLinkRoute.value = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingNotificationEntry.value = intent.extractReplacementNotificationEntry()
        pendingDeepLinkRoute.value = intent.extractDeepLinkRoute()
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

    private fun Intent.extractDeepLinkRoute(): String? {
        val raw = getStringExtra(SmartNotiNotifier.EXTRA_DEEP_LINK_ROUTE)?.takeIf { it.isNotBlank() }
            ?: return null
        // `raw` is a short token (only "hidden" today) — we hydrate it into the real
        // `Routes.Hidden.create(...)` URL so the query-param-aware NavHost composable
        // registration picks up sender/packageName from the tray group-summary extras.
        return when (raw) {
            SilentHiddenSummaryNotifier.ROUTE_HIDDEN -> Routes.Hidden.create(
                sender = getStringExtra(SilentHiddenSummaryNotifier.EXTRA_DEEP_LINK_SENDER),
                packageName = getStringExtra(SilentHiddenSummaryNotifier.EXTRA_DEEP_LINK_PACKAGE_NAME),
            )
            else -> null
        }
    }
}
