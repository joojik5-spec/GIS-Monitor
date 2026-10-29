package tk.glucodata.ui.stats

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StatsDisclosureTest {
    @Test
    fun defaultPlanKeepsHeadlineSimpleAndMovesClinicalMetricsToDetails() {
        val layout = StatsLayoutState()

        val plan = buildStatsDisclosurePlan(layout.visibleCards, layout.visibleMetrics)

        assertEquals(
            listOf(StatsMetric.AVERAGE, StatsMetric.GMI, StatsMetric.CV),
            plan.headlineMetrics,
        )
        assertTrue(plan.detailMetrics.containsAll(listOf(StatsMetric.GVI, StatsMetric.PSG)))
        assertFalse(plan.headlineMetrics.any { it in ClinicalDetailMetrics })
        assertFalse(plan.detailMetrics.any { it in plan.headlineMetrics })
    }

    @Test
    fun planPreservesSavedOrderAndVisibilityWithinEachDepth() {
        val visibleCards = listOf(
            StatsCard.TEMPERATURE,
            StatsCard.INSIGHTS,
            StatsCard.OVERVIEW,
            StatsCard.RISK_INDEX,
            StatsCard.EPISODES,
        )
        val visibleMetrics = listOf(
            StatsMetric.GRI,
            StatsMetric.CV,
            StatsMetric.AVERAGE,
            StatsMetric.MAGE,
        )

        val plan = buildStatsDisclosurePlan(visibleCards, visibleMetrics)

        assertEquals(
            listOf(StatsCard.INSIGHTS, StatsCard.OVERVIEW, StatsCard.EPISODES),
            plan.primaryCards,
        )
        assertEquals(
            listOf(StatsCard.TEMPERATURE, StatsCard.RISK_INDEX),
            plan.detailCards,
        )
        assertEquals(listOf(StatsMetric.CV, StatsMetric.AVERAGE), plan.headlineMetrics)
        assertEquals(listOf(StatsMetric.GRI, StatsMetric.MAGE), plan.detailMetrics)
    }

    @Test
    fun everyRequestedClinicalMetricIsClassifiedAsDetail() {
        val requested = setOf(
            StatsMetric.GVI,
            StatsMetric.PSG,
            StatsMetric.LBGI,
            StatsMetric.HBGI,
            StatsMetric.MAGE,
            StatsMetric.MODD,
            StatsMetric.GRI,
        )

        assertEquals(requested, ClinicalDetailMetrics)
        val plan = buildStatsDisclosurePlan(emptyList(), requested.toList())
        assertTrue(plan.headlineMetrics.isEmpty())
        assertEquals(requested, plan.detailMetrics.toSet())
    }
}
