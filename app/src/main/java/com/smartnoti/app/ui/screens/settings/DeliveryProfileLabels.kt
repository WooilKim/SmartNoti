package com.smartnoti.app.ui.screens.settings

import com.smartnoti.app.domain.model.AlertLevel
import com.smartnoti.app.domain.model.LockScreenVisibilityMode
import com.smartnoti.app.domain.model.VibrationMode

internal fun AlertLevel.toKoreanLabel(): String = when (this) {
    AlertLevel.LOUD -> "강함"
    AlertLevel.SOFT -> "보통"
    AlertLevel.QUIET -> "조용함"
    AlertLevel.NONE -> "없음"
}

internal fun VibrationMode.toKoreanLabel(): String = when (this) {
    VibrationMode.STRONG -> "강하게"
    VibrationMode.LIGHT -> "가볍게"
    VibrationMode.OFF -> "끔"
}

internal fun LockScreenVisibilityMode.toKoreanLabel(): String = when (this) {
    LockScreenVisibilityMode.PUBLIC -> "전체 공개"
    LockScreenVisibilityMode.PRIVATE -> "내용 숨김"
    LockScreenVisibilityMode.SECRET -> "숨김"
}
