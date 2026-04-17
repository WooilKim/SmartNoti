package com.smartnoti.app.domain.usecase

data class QuietHoursPolicy(
    val startHour: Int,
    val endHour: Int,
) {
    fun isQuietAt(hourOfDay: Int): Boolean {
        return if (startHour == endHour) {
            true
        } else if (startHour < endHour) {
            hourOfDay in startHour until endHour
        } else {
            hourOfDay >= startHour || hourOfDay < endHour
        }
    }
}
