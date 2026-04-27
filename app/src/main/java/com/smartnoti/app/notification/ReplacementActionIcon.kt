package com.smartnoti.app.notification

import androidx.annotation.DrawableRes
import com.smartnoti.app.R

/**
 * SmartNoti's replacement notifier surfaces (DIGEST replacement / SILENT
 * group summary + child + archived inbox bell / future PRIORITY mark)
 * each carry a distinct small-icon glyph so the user can identify
 * "what action SmartNoti took" at a glance from one tray row. Maps the
 * action to a vector drawable resource introduced by Task 3 of plan
 * `docs/plans/2026-04-27-fix-issue-510-replacement-icon-source-action-overlay.md`.
 *
 * Source-app identification (the `who sent it` half) is supplied by the
 * notifier calling
 * `setLargeIcon(appIconResolver.resolve(packageName))` — see
 * [AppIconResolver].
 *
 * Adding a new replacement action: extend this enum with a fresh
 * drawable resource. The single source of truth for the action ↔
 * drawable mapping prevents the magic-int-scattered-across-builders
 * pattern that produced Issue #510 (two notifiers each picking a
 * different `android.R.drawable.*` constant for what was conceptually
 * the same SmartNoti SILENT action).
 */
enum class ReplacementActionIcon(@DrawableRes val drawableRes: Int) {
    /**
     * DIGEST replacement (`SmartNotiNotifier.notifySuppressedNotification`,
     * decision = DIGEST). Outlined inbox glyph — the user will see
     * the full content later in the Digest inbox.
     */
    DIGEST(R.drawable.ic_replacement_digest),

    /**
     * SILENT routing surfaces:
     *  - `SilentHiddenSummaryNotifier.post` (legacy archived bell)
     *  - `SilentHiddenSummaryNotifier.postGroupSummary` (per-sender / per-app)
     *  - `SilentHiddenSummaryNotifier.postGroupChild` (group children)
     *  - `SmartNotiNotifier.notifySuppressedNotification` decision = SILENT
     *    fall-through.
     * Outlined volume_off glyph — SmartNoti silenced the source.
     */
    SILENT(R.drawable.ic_replacement_silent),

    /**
     * Reserved for future PRIORITY overlay / protected-source mark.
     * Currently unused at any production builder site (PRIORITY notifications
     * do not flow through `notifySuppressedNotification` — see the early
     * `return` at SmartNotiNotifier.kt:49). Pinned here so adding the
     * priority mark is a one-file change.
     */
    PRIORITY(R.drawable.ic_replacement_priority),
}
