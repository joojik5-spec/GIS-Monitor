package tk.glucodata.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class DashboardExpandedPanelTest {
    @Test
    fun activePanelClosesWhenRequestedAgain() {
        assertEquals(
            DashboardExpandedPanel.None,
            DashboardExpandedPanel.Chart.toggle(DashboardExpandedPanel.Chart)
        )
    }

    @Test
    fun requestingChartOpensItWithoutIntermediateState() {
        assertEquals(
            DashboardExpandedPanel.Chart,
            DashboardExpandedPanel.None.toggle(DashboardExpandedPanel.Chart)
        )
    }

    @Test
    fun statisticsBehavesLikeAnInlineToggle() {
        assertEquals(
            DashboardExpandedPanel.Statistics,
            DashboardExpandedPanel.None.toggle(DashboardExpandedPanel.Statistics)
        )
        assertEquals(
            DashboardExpandedPanel.None,
            DashboardExpandedPanel.Statistics.toggle(DashboardExpandedPanel.Statistics)
        )
    }

    @Test
    fun openingStatisticsClosesTheChartPanel() {
        assertEquals(
            DashboardExpandedPanel.Statistics,
            DashboardExpandedPanel.Chart.toggle(DashboardExpandedPanel.Statistics)
        )
    }
}
