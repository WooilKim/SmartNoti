package com.smartnoti.app.notification

import java.util.Collections

/**
 * Dedup key for the reconnect sweep pipeline. Matches on the triple we can
 * observe both from an in-flight `StatusBarNotification` and from an already
 * persisted row, so a sweep running after a reconnect never reprocesses what
 * the onboarding bootstrap or a previous sweep already handled.
 *
 * Plan: docs/plans/2026-04-21-listener-reconnect-active-notification-sweep.md
 */
data class SweepDedupKey(
    val packageName: String,
    val contentSignature: String,
    val postTimeMillis: Long,
)

/**
 * Mirror of [OnboardingActiveNotificationBootstrapper] for the reconnect sweep
 * path. Reuses the same shouldProcess filtering (self-package + capture policy)
 * and layers in two dedup mechanisms on top:
 *
 *   1. Per-process Set of [SweepDedupKey]. Survives as long as the service
 *      instance lives, so rapid permission toggles within a single process
 *      never double-process the same notification.
 *   2. Injected [existsInStore] membership check. Typically backed by the
 *      Room notifications table so restarts of the process still skip rows
 *      that the previous life already persisted.
 *
 * Coordination with the onboarding bootstrap is handled via
 * [onboardingBootstrapPending]: while pending, [sweep] is a no-op so the
 * bootstrap owns the first pass. The bootstrap can call
 * [recordProcessedByBootstrap] for every item it consumed so the next sweep
 * call (after the flag clears) skips those keys without touching the store.
 */
class ListenerReconnectActiveNotificationSweepCoordinator<T> internal constructor(
    private val appPackageName: String,
    private val packageNameOf: (T) -> String,
    private val titleOf: (T) -> String,
    private val bodyOf: (T) -> String,
    private val notificationFlagsOf: (T) -> Int,
    private val dedupKeyOf: (T) -> SweepDedupKey,
    private val existsInStore: suspend (SweepDedupKey) -> Boolean,
    private val onboardingBootstrapPending: suspend () -> Boolean = { false },
    private val processNotification: suspend (T) -> Unit,
) {
    private val processedKeys: MutableSet<SweepDedupKey> =
        Collections.synchronizedSet(LinkedHashSet())

    /**
     * Run the sweep over [activeNotifications]. No-op when the onboarding
     * bootstrap is still pending — the bootstrap path owns the first pass and
     * will (or has already) called [recordProcessedByBootstrap] for its items.
     */
    suspend fun sweep(activeNotifications: Iterable<T>) {
        if (onboardingBootstrapPending()) return

        activeNotifications.forEach { notification ->
            if (!shouldProcess(notification)) return@forEach

            val key = dedupKeyOf(notification)
            if (processedKeys.contains(key)) return@forEach
            if (existsInStore(key)) {
                processedKeys.add(key)
                return@forEach
            }

            processNotification(notification)
            processedKeys.add(key)
        }
    }

    /**
     * Record a key the onboarding bootstrap just processed so the next
     * [sweep] call treats it as already-handled even if it is still in the
     * system tray.
     */
    fun recordProcessedByBootstrap(key: SweepDedupKey) {
        processedKeys.add(key)
    }

    /** Snapshot of the per-process dedup Set. Exposed for diagnostics/tests. */
    fun processedKeySnapshot(): Set<SweepDedupKey> {
        synchronized(processedKeys) {
            return LinkedHashSet(processedKeys)
        }
    }

    private fun shouldProcess(notification: T): Boolean {
        if (packageNameOf(notification) == appPackageName) return false

        return !NotificationCapturePolicy.shouldIgnoreCapture(
            title = titleOf(notification),
            body = bodyOf(notification),
            notificationFlags = notificationFlagsOf(notification),
        )
    }
}
