package com.smartnoti.app.ui.screens.settings

import com.smartnoti.app.data.settings.SmartNotiSettings

/**
 * Plan `2026-04-26-settings-quiet-hours-window-editor.md` Task 4.
 *
 * Pure render-spec for the Quiet Hours start/end hour pickers shown inside
 * `OperationalSummaryCard`. Extracting the visibility / option list /
 * same-value warning decision keeps the Compose code a thin renderer and
 * lets us unit-test the contract without an instrumentation harness.
 *
 * The 1차 안 chosen by the plan:
 *   - `visible = quietHoursEnabled` — pickers disappear when the master
 *     switch is OFF so the user doesn't fiddle with a no-op control.
 *   - `start == end` is allowed (the underlying `QuietHoursPolicy` has
 *     ambiguous semantics for that case) but a warning copy is exposed so
 *     the UI can render an inline hint.
 *   - Hour labels reuse `formatHour` (`%02d:00`) so the picker entries match
 *     the surrounding summary line.
 */
data class QuietHoursWindowPickerSpec(
    val visible: Boolean,
    val startHour: Int,
    val endHour: Int,
    val hourOptions: List<HourOption>,
    val sameValueWarning: String?,
) {
    data class HourOption(
        val hour: Int,
        val label: String,
    )
}

class QuietHoursWindowPickerSpecBuilder {

    fun build(settings: SmartNotiSettings): QuietHoursWindowPickerSpec {
        val visible = settings.quietHoursEnabled
        val collides = settings.quietHoursStartHour == settings.quietHoursEndHour
        return QuietHoursWindowPickerSpec(
            visible = visible,
            startHour = settings.quietHoursStartHour,
            endHour = settings.quietHoursEndHour,
            hourOptions = HOUR_OPTIONS,
            sameValueWarning = if (visible && collides) SAME_VALUE_WARNING else null,
        )
    }

    companion object {
        const val SAME_VALUE_WARNING: String = "시작·종료가 같으면 적용 시각이 모호해요."

        private val HOUR_OPTIONS: List<QuietHoursWindowPickerSpec.HourOption> =
            (0..23).map { hour ->
                QuietHoursWindowPickerSpec.HourOption(
                    hour = hour,
                    label = "%02d:00".format(hour),
                )
            }
    }
}
