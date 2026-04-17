package com.smartnoti.app.domain.usecase

class PersistentNotificationPolicy {
    fun shouldTreatAsPersistent(
        isOngoing: Boolean,
        isClearable: Boolean,
    ): Boolean {
        return isOngoing || !isClearable
    }
}
