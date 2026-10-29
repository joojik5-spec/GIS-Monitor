package tk.glucodata.ui.stats

/**
 * The statistics screen has two reading depths. The first answers the questions most people
 * open it for; the second keeps the existing clinical detail and layout controls available
 * without making them compete with the headline.
 *
 * This is deliberately a projection of [StatsLayoutState], not another persisted layout. The
 * user's saved order, visibility and dashboard pins remain the single source of truth.
 */
internal data class StatsDisclosurePlan(
    val primaryCards: List<StatsCard>,
    val detailCards: List<StatsCard>,
    val headlineMetrics: List<StatsMetric>,
    val detailMetrics: List<StatsMetric>,
)

private val HeadlineMetrics = setOf(
    StatsMetric.AVERAGE,
    StatsMetric.GMI,
    StatsMetric.CV,
)

private val PrimaryCards = setOf(
    StatsCard.OVERVIEW,
    StatsCard.METRICS,
    StatsCard.EPISODES,
    StatsCard.PATTERNS,
    StatsCard.DAY_BY_DAY,
    StatsCard.INSIGHTS,
)

/** Clinical indices called out by the simplified presentation contract. */
internal val ClinicalDetailMetrics = setOf(
    StatsMetric.GVI,
    StatsMetric.PSG,
    StatsMetric.LBGI,
    StatsMetric.HBGI,
    StatsMetric.MAGE,
    StatsMetric.MODD,
    StatsMetric.GRI,
)

internal fun buildStatsDisclosurePlan(
    visibleCards: List<StatsCard>,
    visibleMetrics: List<StatsMetric>,
): StatsDisclosurePlan = StatsDisclosurePlan(
    primaryCards = visibleCards.filter { it in PrimaryCards },
    detailCards = visibleCards.filterNot { it in PrimaryCards || it == StatsCard.METRICS },
    headlineMetrics = visibleMetrics.filter { it in HeadlineMetrics },
    detailMetrics = visibleMetrics.filterNot { it in HeadlineMetrics },
)
