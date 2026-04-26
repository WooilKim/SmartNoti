package com.smartnoti.app.ui.screens.settings

import com.smartnoti.app.data.settings.SmartNotiSettings

/**
 * Plan `2026-04-26-duplicate-threshold-window-settings.md` Task 5.
 *
 * Pure render-spec for the "중복 알림 묶기" editor row inside
 * `OperationalSummaryCard`. Two side-by-side dropdowns (threshold + window)
 * paralleling the Quiet Hours start/end pickers. Extracting the option list /
 * label format into a builder keeps the Compose code a thin renderer and lets
 * us unit-test the contract without an instrumentation harness.
 *
 * Decisions made for the 1차 안:
 *   - Threshold options `2 / 3 / 4 / 5 / 7 / 10` — covers "더 자주 묶기" through
 *     "거의 묶지 말기" without padding the dropdown. We deliberately omit `1`
 *     because it would route every same-content notification straight to
 *     DIGEST, which is rarely what users actually want and the repository
 *     setter's `coerceAtLeast(1)` already protects against accidental zeros.
 *   - Window options `5 / 10 / 15 / 30 / 60` minutes — bookends the typical
 *     "burst" cadence and keeps the dropdown short.
 *   - Defaults = 3 / 10 to mirror `SmartNotiSettings` defaults so the UI
 *     reflects the historical hard-coded behavior on first launch.
 *   - Labels follow the copy direction in the plan: `반복 N회` and `최근 N분`.
 */
data class DuplicateThresholdEditorSpec(
    val selectedThreshold: Int,
    val selectedWindowMinutes: Int,
    val thresholdOptions: List<ThresholdOption>,
    val windowOptions: List<WindowOption>,
) {
    data class ThresholdOption(
        val value: Int,
        val label: String,
    )

    data class WindowOption(
        val minutes: Int,
        val label: String,
    )
}

class DuplicateThresholdEditorSpecBuilder {

    fun build(settings: SmartNotiSettings): DuplicateThresholdEditorSpec {
        return DuplicateThresholdEditorSpec(
            selectedThreshold = settings.duplicateDigestThreshold,
            selectedWindowMinutes = settings.duplicateWindowMinutes,
            thresholdOptions = THRESHOLD_OPTIONS,
            windowOptions = WINDOW_OPTIONS,
        )
    }

    companion object {
        // Plan-fixed candidates. If product judgment ever shifts these, change
        // them here in one place — the dropdown renderer reads only this list.
        val THRESHOLD_VALUES: List<Int> = listOf(2, 3, 4, 5, 7, 10)
        val WINDOW_MINUTE_VALUES: List<Int> = listOf(5, 10, 15, 30, 60)

        val THRESHOLD_OPTIONS: List<DuplicateThresholdEditorSpec.ThresholdOption> =
            THRESHOLD_VALUES.map { value ->
                DuplicateThresholdEditorSpec.ThresholdOption(
                    value = value,
                    label = "반복 ${value}회",
                )
            }

        val WINDOW_OPTIONS: List<DuplicateThresholdEditorSpec.WindowOption> =
            WINDOW_MINUTE_VALUES.map { minutes ->
                DuplicateThresholdEditorSpec.WindowOption(
                    minutes = minutes,
                    label = "최근 ${minutes}분",
                )
            }
    }
}
