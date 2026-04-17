package com.smartnoti.app.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Test

class InsightDrillDownRangeParsingTest {

    @Test
    fun from_route_value_maps_supported_ranges() {
        assertEquals(InsightDrillDownRange.RECENT_3_HOURS, InsightDrillDownRange.fromRouteValue("recent_3_hours"))
        assertEquals(InsightDrillDownRange.RECENT_24_HOURS, InsightDrillDownRange.fromRouteValue("recent_24_hours"))
        assertEquals(InsightDrillDownRange.ALL, InsightDrillDownRange.fromRouteValue("all"))
    }

    @Test
    fun from_route_value_defaults_to_recent_24_hours_for_unknown_value() {
        assertEquals(InsightDrillDownRange.RECENT_24_HOURS, InsightDrillDownRange.fromRouteValue("unknown"))
        assertEquals(InsightDrillDownRange.RECENT_24_HOURS, InsightDrillDownRange.fromRouteValue(null))
    }
}
