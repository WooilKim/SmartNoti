package com.smartnoti.app.debug

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.service.notification.NotificationListenerService
import android.util.Log
import com.smartnoti.app.notification.SmartNotiNotificationListenerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Debug-APK-only receiver that lets the `onboarding-bootstrap` journey
 * verification recipe re-exercise the bootstrap pipeline without
 * `pm clear`-ing the app.
 *
 * Invocation from host shell:
 *
 * ```bash
 * adb -s emulator-5554 shell am broadcast \
 *   -a com.smartnoti.debug.REHEARSE_BOOTSTRAP \
 *   -p com.smartnoti.app
 * ```
 *
 * The listener service exposes a sibling
 * `rehearseOnboardingBootstrapIfConnected()` static helper that
 * forwards the receiver's request to the live listener instance, which
 * is the only context that can read the system's
 * `activeNotifications` array. When the listener is not currently
 * bound the receiver requests a rebind and exits — the recipe should
 * grant NotificationListener access first.
 *
 * The result is emitted to logcat under a stable tag so the
 * verification recipe can read it back with
 * `adb logcat -d -s BootstrapRehearsal`.
 *
 * The receiver lives in `app/src/debug/`, so it is never merged into
 * the release APK. Plan:
 * `docs/plans/2026-04-27-onboarding-bootstrap-non-destructive-recipe.md`.
 */
class DebugBootstrapRehearsalReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != null && intent.action != ACTION) {
            Log.w(TAG, "ignoring action=${intent.action}")
            return
        }
        val appContext = context.applicationContext
        val pendingResult: PendingResult? = goAsync()
        scope.launch {
            try {
                val handled = SmartNotiNotificationListenerService
                    .rehearseOnboardingBootstrapIfConnected { appPackageName, actives, record, process ->
                        // Sequence is intentional: launch the rehearsal in a
                        // child coroutine on this scope so the
                        // `goAsync()`-backed PendingResult can complete on
                        // the receiver thread, while the actual pipeline
                        // (Repository writes, classifier execution) runs
                        // off-thread under SupervisorJob.
                        scope.launch {
                            val result = DebugBootstrapRehearsal.rehearse(
                                appPackageName = appPackageName,
                                activeNotifications = actives,
                                recordProcessedByBootstrap = record,
                                processNotification = process,
                            )
                            Log.i(
                                TAG,
                                "processed=${result.processedCount} skipped=${result.skippedCount}",
                            )
                        }
                    }
                if (!handled) {
                    Log.w(
                        TAG,
                        "listener not bound — requesting rebind. Recipe should ensure " +
                            "NotificationListener access is granted before re-broadcasting.",
                    )
                    runCatching {
                        NotificationListenerService.requestRebind(
                            ComponentName(appContext, SmartNotiNotificationListenerService::class.java)
                        )
                    }
                }
            } catch (t: Throwable) {
                Log.e(TAG, "rehearsal failed", t)
            } finally {
                pendingResult?.finish()
            }
        }
    }

    companion object {
        const val ACTION = "com.smartnoti.debug.REHEARSE_BOOTSTRAP"
        const val TAG = "BootstrapRehearsal"

        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
