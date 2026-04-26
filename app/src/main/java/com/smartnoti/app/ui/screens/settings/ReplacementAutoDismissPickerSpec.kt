package com.smartnoti.app.ui.screens.settings

import com.smartnoti.app.data.settings.SmartNotiSettings

/**
 * Plan `2026-04-27-tray-replacement-auto-dismiss-timeout.md` Task 3.
 *
 * Pure render-spec for the "자동 정리" duration picker that lives inside the
 * `SuppressionManagementCard` — sibling to the persistent-notification toggles
 * already grouped under "고급 숨김 옵션". Mirrors the
 * `DuplicateThresholdEditorSpec` pattern so the Compose layer stays a thin
 * renderer and the option list / label format are JUnit-testable.
 *
 * Decisions made for the 1차 안 (driven by the plan's product intent block):
 *   - Duration options `5 / 15 / 30 / 60 / 180` minutes — five-option preset
 *     keeps the dropdown short. 5 분 is the "훑어보고 사라지면 충분" edge,
 *     180 분 is the "회의/운동 끝나고 한 번에 본다" edge. 30 is the default.
 *   - Labels use a hour suffix once the value crosses an hour boundary so the
 *     dropdown reads `5분 / 15분 / 30분 / 1시간 / 3시간` without a unit toggle.
 *   - The picker stays visible regardless of `enabled`; the renderer wires the
 *     `enabled` state to dim the chip when the master toggle is OFF (mirrors
 *     the QuietHours start/end picker visibility contract).
 */
data class ReplacementAutoDismissPickerSpec(
    val enabled: Boolean,
    val selectedMinutes: Int,
    val options: List<DurationOption>,
) {
    data class DurationOption(
        val minutes: Int,
        val label: String,
    )
}

class ReplacementAutoDismissPickerSpecBuilder {

    fun build(settings: SmartNotiSettings): ReplacementAutoDismissPickerSpec {
        return ReplacementAutoDismissPickerSpec(
            enabled = settings.replacementAutoDismissEnabled,
            selectedMinutes = settings.replacementAutoDismissMinutes,
            options = OPTIONS,
        )
    }

    companion object {
        // Plan-fixed candidate set. Adjust here in one place if product
        // judgment ever shifts these — the renderer reads only this list.
        val MINUTE_VALUES: List<Int> = listOf(5, 15, 30, 60, 180)

        val OPTIONS: List<ReplacementAutoDismissPickerSpec.DurationOption> =
            MINUTE_VALUES.map { minutes ->
                ReplacementAutoDismissPickerSpec.DurationOption(
                    minutes = minutes,
                    label = labelFor(minutes),
                )
            }

        /**
         * Format helper exposed for unit tests + the picker's "selected label
         * fallback" branch (when the persisted value is not in the preset set,
         * e.g. set programmatically before this UI shipped).
         */
        fun labelFor(minutes: Int): String {
            return if (minutes >= 60 && minutes % 60 == 0) {
                "${minutes / 60}시간"
            } else {
                "${minutes}분"
            }
        }
    }
}
