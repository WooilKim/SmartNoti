package com.smartnoti.app.ui.screens.settings

import com.smartnoti.app.data.local.CapturedAppSelectionItem
import com.smartnoti.app.data.settings.SmartNotiSettings

/**
 * Plan `2026-04-26-quiet-hours-shopping-packages-user-extensible.md` Task 6.
 *
 * Pure render-spec for the new "조용한 시간 대상 앱" sub-row + picker shown
 * inside `OperationalSummaryCard`. Mirrors `QuietHoursWindowPickerSpec` so
 * the Compose code stays a thin renderer and the visibility / count copy /
 * empty-set warning live in unit-testable territory.
 *
 * Decisions mirrored from the plan:
 *   - `visible = quietHoursEnabled` — the sub-row disappears when the
 *     master Switch is OFF (consistent with the start/end hour picker row).
 *   - `summary` always renders the live count (`N개`) so the user can
 *     confirm the picker matches their intent without opening it.
 *   - When the picker is open and the set is empty, an inline warning copy
 *     surfaces — the plan calls this out explicitly so users know quiet
 *     hours is silently no-op'ing even though the master switch is ON.
 *   - `items` joins the persisted set with the captured-app catalog so each
 *     row shows a human label + observed notification count when available
 *     (and a packageName-only row otherwise — covers freshly-added packages
 *     that have not posted yet during this session).
 */
data class QuietHoursPackagesPickerSpec(
    val visible: Boolean,
    val selectedCount: Int,
    val summary: String,
    val emptyWarning: String?,
    val items: List<Item>,
    val candidates: List<CapturedAppSelectionItem>,
) {
    data class Item(
        val packageName: String,
        val displayName: String,
        val supporting: String?,
    )
}

class QuietHoursPackagesPickerSpecBuilder {

    fun build(
        settings: SmartNotiSettings,
        capturedApps: List<CapturedAppSelectionItem>,
    ): QuietHoursPackagesPickerSpec {
        val selected = settings.quietHoursPackages
        val visible = settings.quietHoursEnabled
        val labelByPackage = capturedApps.associateBy(CapturedAppSelectionItem::packageName)
        // Sort selected items so the picker order is deterministic across
        // recompositions (avoids the "rows reshuffle when set changes" feel).
        // Items with a captured-app match show their human label first, then
        // unknown packages fall back to packageName-only rows.
        val items = selected.sortedBy { it }.map { pkg ->
            val captured = labelByPackage[pkg]
            QuietHoursPackagesPickerSpec.Item(
                packageName = pkg,
                displayName = captured?.appName ?: pkg,
                supporting = captured?.let { "${it.notificationCount}건 · ${it.lastSeenLabel}" },
            )
        }
        // Candidate list = captured apps not yet in the selected set. Mirrors
        // the suppressed-apps picker which only offers packages SmartNoti has
        // actually observed; "every installed app" surface is intentionally
        // out of scope per the plan (별도 picker 컴포넌트 신규 구현 = Out).
        val candidates = capturedApps
            .filterNot { it.packageName in selected }
            .sortedByDescending { it.notificationCount }
        return QuietHoursPackagesPickerSpec(
            visible = visible,
            selectedCount = selected.size,
            summary = if (selected.isEmpty()) {
                NO_TARGETS_SUMMARY
            } else {
                "${selected.size}개 앱이 조용한 시간 동안 자동으로 모아져요."
            },
            emptyWarning = if (visible && selected.isEmpty()) EMPTY_WARNING else null,
            items = items,
            candidates = candidates,
        )
    }

    companion object {
        const val ROW_LABEL: String = "조용한 시간 대상 앱"
        const val PICKER_HEADER_TITLE: String = "조용한 시간 대상 앱"
        const val PICKER_HEADER_SUBTITLE: String =
            "조용한 시간 동안 자동으로 모아둘 앱을 골라주세요."
        const val ADD_BUTTON_LABEL: String = "앱 추가"
        const val EMPTY_WARNING: String =
            "대상 앱이 없어 조용한 시간이 어떤 알림도 모으지 않아요."
        const val NO_TARGETS_SUMMARY: String = "선택된 앱이 없어요."
        const val NO_CANDIDATES_HINT: String =
            "추가할 후보가 없어요. 다른 앱이 알림을 보내면 여기서 고를 수 있어요."

        fun rowSummary(count: Int): String = if (count == 0) {
            "0개"
        } else {
            "${count}개"
        }
    }
}
