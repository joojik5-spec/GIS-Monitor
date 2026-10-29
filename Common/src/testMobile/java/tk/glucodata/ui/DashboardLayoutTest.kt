package tk.glucodata.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class DashboardLayoutTest {
    @Test fun savedOrderSurvivesNewOrUnknownBlocks() {
        val actual = normalizeDashboardOrder(listOf("Readings", "unknown", "Glucose", "Readings"))
        assertEquals(listOf(DashboardBlock.Readings, DashboardBlock.Glucose), actual.take(2))
        assertEquals(DashboardBlock.entries.toSet(), actual.toSet())
        assertEquals(DashboardBlock.entries.size, actual.size)
    }
    @Test fun everyMainBlockCanBeMoved() {
        val reversed = DashboardBlock.entries.reversed()
        assertEquals(reversed, normalizeDashboardOrder(reversed.map { it.name }))
    }
    @Test fun emptyPreferencesUseDefaultLayout() {
        assertEquals(DashboardBlock.entries.toList(), normalizeDashboardOrder(emptyList()))
    }
    @Test fun movingAnyBlockPreservesTheOtherBlocks() {
        val order = DashboardBlock.entries.toList()
        for (from in order.indices) for (to in order.indices) {
            val moved = moveDashboardItem(order, from, to)
            assertEquals(order[from], moved[to])
            assertEquals(order.toSet(), moved.toSet())
            assertEquals(order.filterIndexed { index, _ -> index != from }, moved.filterIndexed { index, _ -> index != to })
        }
    }
    @Test fun embeddedStatisticsCardsBelongToOneMainBlock() {
        assertEquals(DashboardBlock.Statistics, dashboardBlockForKey("stats:METRICS"))
        assertEquals(DashboardBlock.Glucose, dashboardBlockForKey("Glucose"))
    }
}
