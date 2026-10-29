package tk.glucodata.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp

internal enum class DashboardExpandedPanel {
    None,
    Chart,
    Statistics;

    fun toggle(requested: DashboardExpandedPanel): DashboardExpandedPanel =
        if (this == requested) None else requested
}

internal object DashboardExpandedPanelState {
    private var prefs: android.content.SharedPreferences? = null
    private var state by mutableStateOf(DashboardExpandedPanel.None)
    var current: DashboardExpandedPanel
        get() = state
        set(value) {
            state = value
            prefs?.edit()?.putString("expanded_panel", value.name)?.apply()
        }
    fun load(context: android.content.Context) {
        if (prefs != null) return
        prefs = context.getSharedPreferences("solmi_dashboard_layout", android.content.Context.MODE_PRIVATE)
        state = DashboardExpandedPanel.entries.firstOrNull {
            it.name == prefs?.getString("expanded_panel", null)
        } ?: DashboardExpandedPanel.None
    }
    fun close() { current = DashboardExpandedPanel.None }
}

@Composable
internal fun DashboardExpandedPanelSurface(
    visible: Boolean,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(12.dp),
    content: @Composable () -> Unit
) {
    val sizeMotion = spring<IntSize>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow
    )
    val alphaMotion = spring<Float>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow
    )
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = expandVertically(expandFrom = Alignment.Top, animationSpec = sizeMotion) +
            fadeIn(animationSpec = alphaMotion),
        exit = shrinkVertically(shrinkTowards = Alignment.Top, animationSpec = sizeMotion) +
            fadeOut(animationSpec = alphaMotion)
    ) {
        Column(modifier = Modifier.padding(top = 12.dp)) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 1.dp
            ) {
                Column(modifier = Modifier.padding(contentPadding)) {
                    content()
                }
            }
        }
    }
}
