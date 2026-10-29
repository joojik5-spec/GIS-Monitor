package tk.glucodata.ui

import android.content.Context
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import tk.glucodata.R

enum class DashboardHeaderPart { All, Sensor, Glucose }

internal enum class DashboardBlock(val titleRes: Int) {
    Sensor(R.string.nav_sensors), Glucose(R.string.nav_glucose),
    Pinned(R.string.dashboard_pinned), Statistics(R.string.statistics),
    Chart(R.string.dashboard_chart), Readings(R.string.historyname),
    Calibrations(R.string.nav_calibration)
}

internal fun normalizeDashboardOrder(saved: List<String>): List<DashboardBlock> =
    (saved.mapNotNull { name -> DashboardBlock.entries.firstOrNull { it.name == name } } +
        DashboardBlock.entries).distinct()

internal object DashboardLayout {
    var order by mutableStateOf(DashboardBlock.entries.toList())
        private set
    var navigation by mutableStateOf(listOf("dashboard", "school", "sensors", "settings"))
        private set
    val anchors = mutableStateMapOf<String, Rect>()
    var returnToGlucose by mutableIntStateOf(0)
    var tutorialStep by mutableIntStateOf(-1)
    private var prefs: android.content.SharedPreferences? = null
    fun load(context: Context) {
        if (prefs != null) return
        prefs = context.getSharedPreferences("solmi_dashboard_layout", Context.MODE_PRIVATE)
        order = normalizeDashboardOrder(prefs?.getString("order", "").orEmpty().split(','))
        val defaults = listOf("dashboard", "school", "sensors", "settings")
        navigation = (prefs?.getString("navigation", "").orEmpty().split(',').filter { it in defaults } + defaults).distinct()
        if (prefs?.getBoolean("tutorial_seen_v1", false) != true) tutorialStep = 0
    }
    fun reorder(value: List<DashboardBlock>) {
        order = normalizeDashboardOrder(value.map { it.name })
        prefs?.edit()?.putString("order", order.joinToString(",") { it.name })?.apply()
    }
    fun reorderNavigation(value: List<String>) {
        navigation = value
        prefs?.edit()?.putString("navigation", value.joinToString(","))?.apply()
    }
    fun finishTutorial() {
        tutorialStep = -1
        prefs?.edit()?.putBoolean("tutorial_seen_v1", true)?.apply()
    }
}

/** A hand points at the real control, while the page remains visible through a cutout. */
@Composable
internal fun DashboardTutorial() {
    val step = DashboardLayout.tutorialStep
    if (step < 0) return
    var origin by remember { mutableStateOf(Offset.Zero) }
    val anchor = DashboardLayout.anchors[when(step) { 0 -> "glucose"; 1 -> "chart"; else -> "glucose" }]
    val target = anchor?.translate(-origin)
    val dragDemo = rememberInfiniteTransition(label = "dashboardDragTutorial")
    val dragProgress by dragDemo.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 2600
                0f at 0
                0f at 650
                1f at 1700
                1f at 2250
                0f at 2600
            },
            repeatMode = RepeatMode.Restart,
        ),
        label = "dashboardDragProgress",
    )
    fun advance() {
        when(step) {
            0 -> { DashboardExpandedPanelState.current = DashboardExpandedPanel.Statistics; DashboardLayout.tutorialStep = 1 }
            1 -> { DashboardExpandedPanelState.current = DashboardExpandedPanel.Chart; DashboardLayout.tutorialStep = 2 }
            else -> { DashboardLayout.finishTutorial(); DashboardExpandedPanelState.close() }
        }
    }
    Box(Modifier.fillMaxSize().zIndex(100f).onGloballyPositioned { origin = it.positionInRoot() }
        .clickable(onClick = ::advance)) {
        Canvas(Modifier.fillMaxSize()) {
            val dragOffset = if (step == 2) Offset(0f, 92.dp.toPx() * dragProgress) else Offset.Zero
            val shownTarget = target?.translate(dragOffset)
            val scrim = Path().apply {
                fillType = PathFillType.EvenOdd
                addRect(Rect(Offset.Zero, size))
                if (target != null) addRect(target)
            }
            drawPath(scrim, Color.Black.copy(alpha = 0.72f))
            if (shownTarget != null) {
                if (step == 2) {
                    drawLine(
                        color = Color(0xFF5DCEC5).copy(alpha = 0.65f),
                        start = shownTarget.center - dragOffset,
                        end = shownTarget.center - dragOffset + Offset(0f, 92.dp.toPx()),
                        strokeWidth = 3.dp.toPx(),
                    )
                    drawCircle(
                        color = Color(0xFF5DCEC5),
                        radius = 7.dp.toPx(),
                        center = shownTarget.center,
                    )
                }
                drawRoundRect(Color(0xFF5DCEC5), shownTarget.topLeft, shownTarget.size,
                    CornerRadius(16.dp.toPx()), style = Stroke(2.dp.toPx()))
            }
        }
        if (target != null) {
            val handShift = if (step == 2) 92.dp * dragProgress else 0.dp
            Icon(Icons.Default.TouchApp, contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.offset {
                    IntOffset(
                        target.center.x.roundToInt() - 24.dp.roundToPx(),
                        target.bottom.roundToInt() + handShift.roundToPx(),
                    )
                }.size(48.dp))
        }
        Surface(Modifier.align(Alignment.BottomCenter).padding(16.dp), shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainer) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(when(step) {
                    0 -> R.string.dashboard_tutorial_glucose
                    1 -> R.string.dashboard_tutorial_chart
                    else -> R.string.dashboard_arrange
                }), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(when(step) {
                    0 -> R.string.dashboard_tutorial_glucose_body
                    1 -> R.string.dashboard_tutorial_chart_body
                    else -> R.string.dashboard_drag_hint
                }))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = DashboardLayout::finishTutorial) { Text(stringResource(R.string.dashboard_skip)) }
                    TextButton(onClick = ::advance) { Text(stringResource(R.string.dashboard_try)) }
                }
            }
        }
    }
}
