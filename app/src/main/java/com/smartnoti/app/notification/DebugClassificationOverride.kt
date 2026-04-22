package com.smartnoti.app.notification

import android.os.Bundle
import com.smartnoti.app.domain.model.NotificationStatusUi
import com.smartnoti.app.domain.model.NotificationUiModel

/**
 * Debug-only classification override resolver.
 *
 * Plan: `docs/plans/2026-04-22-priority-recipe-debug-inject-hook.md`.
 *
 * The `journey-tester` priority-inbox recipe kept SKIPping because
 * accumulated user rules in the emulator (`person:엄마 → DIGEST`,
 * `인증번호 → SILENT`, etc.) would shadow the test sender and force the
 * classifier down a DIGEST / SILENT path. This resolver gives the recipe
 * a rule-oblivious pin: post a notification with the sentinel extras
 * marker [MARKER_KEY] set to a [NotificationStatusUi] enum name and the
 * listener — when running a `BuildConfig.DEBUG` build — substitutes the
 * classifier's verdict with the marker's value.
 *
 * The override is intentionally minimal:
 *
 * - Only the `status` and `reasonTags` fields of the fallback
 *   [NotificationUiModel] are replaced. All other fields (id, app
 *   metadata, delivery profile, timestamps, matched rule ids, …) pass
 *   through untouched so the rest of the capture pipeline sees the same
 *   row the classifier produced, just with a pinned status.
 * - `reasonTags` is replaced with `listOf("디버그 주입")` alone — we
 *   deliberately do NOT mix user-rule / classifier signals back in so a
 *   tester inspecting the Detail screen can tell instantly that this
 *   row came from the debug injection path rather than a genuine match.
 * - Only the four [NotificationStatusUi] enum names (case-sensitive) are
 *   honored; anything else (typos, lower-case, random strings, missing
 *   marker) falls through and returns the `fallback` instance unchanged.
 *
 * Callers MUST gate invocation with `if (BuildConfig.DEBUG)` so release
 * builds fold the call site into dead code (see
 * [SmartNotiNotificationListenerService]). This resolver itself does
 * not read `BuildConfig` because it has to remain unit-testable from a
 * plain JVM Robolectric target that does not carry the build variant.
 */
internal object DebugClassificationOverride {

    /**
     * Extras key the journey-tester recipe sets via
     * `adb shell cmd notification post --es <key> <status>`.
     *
     * Namespaced under `com.smartnoti.debug.` so there is effectively
     * zero chance of collision with an unrelated framework / third-party
     * app extras bag.
     */
    const val MARKER_KEY: String = "com.smartnoti.debug.FORCE_STATUS"

    /**
     * Reason tag written onto overridden rows. Debug-only string — it
     * only ever appears in debug APKs, so there is no localization
     * concern. Prefixed wording is intentionally distinct from any
     * real classifier tag.
     */
    private const val DEBUG_REASON_TAG: String = "디버그 주입"

    private val ALLOWED: Map<String, NotificationStatusUi> = NotificationStatusUi.values()
        .associateBy { it.name }

    /**
     * If [extras] contains a [MARKER_KEY] whose value matches a
     * [NotificationStatusUi] enum name exactly, return [fallback] with
     * its status replaced and `reasonTags` rewritten to
     * `listOf("디버그 주입")`. Otherwise return [fallback] unchanged.
     */
    fun resolve(
        extras: Bundle,
        fallback: NotificationUiModel,
    ): NotificationUiModel {
        val raw = extras.getString(MARKER_KEY) ?: return fallback
        val status = ALLOWED[raw] ?: return fallback
        if (status == fallback.status && fallback.reasonTags == listOf(DEBUG_REASON_TAG)) {
            return fallback
        }
        return fallback.copy(
            status = status,
            reasonTags = listOf(DEBUG_REASON_TAG),
        )
    }
}
