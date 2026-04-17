package com.smartnoti.app.domain.model

import com.smartnoti.app.domain.usecase.QuietHoursPolicy

data class NotificationContext(
    val quietHoursEnabled: Boolean,
    val quietHoursPolicy: QuietHoursPolicy,
    val currentHourOfDay: Int,
    val duplicateCountInWindow: Int,
) {
    fun isQuietHoursActive(): Boolean {
        return quietHoursEnabled && quietHoursPolicy.isQuietAt(currentHourOfDay)
    }
}
