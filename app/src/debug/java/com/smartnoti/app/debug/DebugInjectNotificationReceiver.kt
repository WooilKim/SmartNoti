package com.smartnoti.app.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import com.smartnoti.app.data.categories.CategoriesRepository
import com.smartnoti.app.data.local.NotificationRepository
import com.smartnoti.app.data.rules.RulesRepository
import com.smartnoti.app.data.settings.SettingsRepository
import com.smartnoti.app.domain.model.CapturedNotificationInput
import com.smartnoti.app.domain.model.SourceNotificationSuppressionState
import com.smartnoti.app.domain.model.toDecision
import com.smartnoti.app.domain.model.withContext
import com.smartnoti.app.domain.usecase.DeliveryProfilePolicy
import com.smartnoti.app.domain.usecase.NotificationCaptureProcessor
import com.smartnoti.app.domain.usecase.NotificationClassifier
import com.smartnoti.app.notification.DebugClassificationOverride
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Debug-APK-only receiver that helps journey-tester verification recipes
 * pin a classification by short-circuiting the full `cmd notification
 * post` → listener pipeline (which can not carry arbitrary extras
 * through the shell tool, and which would in any case bounce
 * SmartNoti-self-package posts off
 * [com.smartnoti.app.notification.OnboardingActiveNotificationBootstrapper.shouldProcess]).
 *
 * Invocation from host shell — priority-inbox recipe (force a status):
 *
 * ```bash
 * adb -s emulator-5554 shell am broadcast \
 *   -a com.smartnoti.debug.INJECT_NOTIFICATION \
 *   -p com.smartnoti.app \
 *   --es title "PriDbg12345" \
 *   --es body "검토대기시드" \
 *   --es force_status PRIORITY
 * ```
 *
 * Invocation from host shell — quiet-hours recipe (let real classifier
 * fire on a package-gated branch):
 *
 * ```bash
 * adb -s emulator-5554 shell am broadcast \
 *   -a com.smartnoti.debug.INJECT_NOTIFICATION \
 *   -p com.smartnoti.app \
 *   --es title "QuietHrsPositive12345" \
 *   --es body "쇼핑 알림" \
 *   --es package_name com.coupang.mobile \
 *   --es app_name "Coupang"
 * ```
 *
 * Extras (all optional):
 *
 * - `title`, `body` — notification text. Defaults applied when blank.
 * - `force_status` — debug-only classifier override (`PRIORITY`,
 *   `DIGEST`, `SILENT`, `IGNORE`). Bypasses the real classifier; use
 *   sparingly and only when verifying status-driven UI/routing.
 * - `package_name` — substitute the saved row's `packageName` (and
 *   `sourceEntryKey` prefix) with the supplied package. Lets recipes
 *   exercise package-gated classifier branches (e.g. quiet-hours
 *   `shoppingPackages = setOf("com.coupang.mobile")`). Empty / absent
 *   → falls back to the synthetic `com.smartnoti.debug.tester` package.
 * - `app_name` — substitute the saved row's `appName` and `sender`
 *   with the supplied human label. Empty / absent → falls back to
 *   `SmartNotiDebugTester`. Independent of `package_name` but typically
 *   passed alongside it so the inbox row label matches the real app.
 *
 * The receiver assembles a [CapturedNotificationInput], runs it through
 * the same [NotificationCaptureProcessor] the listener uses, applies the
 * [DebugClassificationOverride] resolver to a [Bundle] containing the
 * `force_status` sentinel marker (when present), and persists the
 * resulting row through [NotificationRepository.save] — exactly
 * mirroring the production save step the listener performs after
 * `processor.process`. UI surfaces (`PriorityScreen`, Home
 * `HomePassthroughReviewCard`) observe the row via the same
 * `NotificationRepository.observeAll()` flow they always use, so the
 * recipe still exercises the full UI contract end-to-end.
 *
 * This class lives in `app/src/debug/`, so it is never merged into the
 * release APK. The override resolver itself is `BuildConfig.DEBUG`-
 * gated at the listener call site. Plans:
 * `docs/plans/2026-04-22-priority-recipe-debug-inject-hook.md`,
 * `docs/plans/2026-04-26-debug-inject-package-name-extra.md`.
 */
class DebugInjectNotificationReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        Log.i(TAG, "onReceive action=${intent.action} extras=${intent.extras?.keySet()}")
        if (intent.action != null && intent.action != ACTION) {
            Log.w(TAG, "ignoring action=${intent.action}")
            return
        }
        val title = intent.getStringExtra("title").orEmpty().ifBlank { "SmartNotiDebugInject" }
        val body = intent.getStringExtra("body").orEmpty().ifBlank { "debug-injected notification" }
        val forceStatus = intent.getStringExtra("force_status").orEmpty()
        val packageNameExtra = intent.getStringExtra("package_name").orEmpty()
        val appNameExtra = intent.getStringExtra("app_name").orEmpty()

        // `goAsync()` returns null when the receiver is invoked outside a
        // real broadcast dispatch (e.g. unit tests calling `onReceive`
        // directly). Production dispatch always supplies a non-null
        // PendingResult; the null branch is a test-only safety net that
        // does not change production semantics.
        val pendingResult: PendingResult? = goAsync()
        scope.launch {
            try {
                inject(
                    appContext = context.applicationContext,
                    title = title,
                    body = body,
                    forceStatus = forceStatus,
                    packageNameExtra = packageNameExtra,
                    appNameExtra = appNameExtra,
                )
                Log.i(TAG, "injected title=$title status=$forceStatus pkg=$packageNameExtra")
            } catch (t: Throwable) {
                Log.e(TAG, "inject failed", t)
            } finally {
                pendingResult?.finish()
            }
        }
    }

    private suspend fun inject(
        appContext: Context,
        title: String,
        body: String,
        forceStatus: String,
        packageNameExtra: String,
        appNameExtra: String,
    ) {
        val notificationRepository = NotificationRepository.getInstance(appContext)
        val rulesRepository = RulesRepository.getInstance(appContext)
        val categoriesRepository = CategoriesRepository.getInstance(appContext)
        val settingsRepository = SettingsRepository.getInstance(appContext)

        val rules = rulesRepository.currentRules()
        val categories = categoriesRepository.currentCategories()
        val settings = settingsRepository.observeSettings().first()

        val effectivePackage = packageNameExtra.ifBlank { SYNTHETIC_PACKAGE }
        val effectiveAppName = appNameExtra.ifBlank { SYNTHETIC_APP_NAME }

        val now = System.currentTimeMillis()
        val captureInput = CapturedNotificationInput(
            packageName = effectivePackage,
            appName = effectiveAppName,
            sender = effectiveAppName,
            title = title,
            body = body,
            postedAtMillis = now,
            quietHours = false,
            duplicateCountInWindow = 1,
            isPersistent = false,
            sourceEntryKey = "$effectivePackage|debug-inject|$now",
        ).withContext(settingsRepository.currentNotificationContext(1))

        val classified = processor.process(
            input = captureInput,
            rules = rules,
            settings = settings,
            categories = categories,
        )

        val extras = Bundle().apply {
            if (forceStatus.isNotBlank()) {
                putString(DebugClassificationOverride.MARKER_KEY, forceStatus)
            }
        }
        val overridden = DebugClassificationOverride.resolve(extras, classified).copy(
            sourceSuppressionState = SourceNotificationSuppressionState.NOT_CONFIGURED,
            replacementNotificationIssued = false,
        )

        // Mirror the listener's contentSignature derivation so dedup
        // semantics match a real capture.
        val contentSignature = "$title|$body|debug-inject|$now"
        notificationRepository.save(overridden, now, contentSignature)

        // Touch [decision] so any future reader can rely on the same
        // status → decision mapping the listener uses downstream.
        @Suppress("UNUSED_VARIABLE")
        val decision = overridden.status.toDecision()
    }

    companion object {
        private const val TAG = "DebugInject"
        private const val ACTION = "com.smartnoti.debug.INJECT_NOTIFICATION"
        private const val SYNTHETIC_PACKAGE = "com.smartnoti.debug.tester"
        private const val SYNTHETIC_APP_NAME = "SmartNotiDebugTester"

        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private val processor = NotificationCaptureProcessor(
            classifier = NotificationClassifier(
                vipSenders = setOf("엄마", "팀장"),
                priorityKeywords = setOf("인증번호", "OTP", "결제"),
                shoppingPackages = setOf("com.coupang.mobile"),
            ),
            deliveryProfilePolicy = DeliveryProfilePolicy(),
        )
    }
}
