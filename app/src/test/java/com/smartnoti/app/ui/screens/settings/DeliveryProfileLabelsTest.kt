package com.smartnoti.app.ui.screens.settings

import com.smartnoti.app.domain.model.AlertLevel
import com.smartnoti.app.domain.model.LockScreenVisibilityMode
import com.smartnoti.app.domain.model.VibrationMode
import org.junit.Assert.assertEquals
import org.junit.Test

class DeliveryProfileLabelsTest {

    @Test
    fun alert_levels_map_to_expected_korean_labels() {
        assertEquals("강함", AlertLevel.LOUD.toKoreanLabel())
        assertEquals("보통", AlertLevel.SOFT.toKoreanLabel())
        assertEquals("조용함", AlertLevel.QUIET.toKoreanLabel())
        assertEquals("없음", AlertLevel.NONE.toKoreanLabel())
    }

    @Test
    fun vibration_modes_map_to_expected_korean_labels() {
        assertEquals("강하게", VibrationMode.STRONG.toKoreanLabel())
        assertEquals("가볍게", VibrationMode.LIGHT.toKoreanLabel())
        assertEquals("끔", VibrationMode.OFF.toKoreanLabel())
    }

    @Test
    fun lock_screen_visibility_modes_map_to_expected_korean_labels() {
        assertEquals("전체 공개", LockScreenVisibilityMode.PUBLIC.toKoreanLabel())
        assertEquals("내용 숨김", LockScreenVisibilityMode.PRIVATE.toKoreanLabel())
        assertEquals("숨김", LockScreenVisibilityMode.SECRET.toKoreanLabel())
    }
}
