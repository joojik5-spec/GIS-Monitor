package tk.glucodata.ui

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import tk.glucodata.Applic
import tk.glucodata.R
import tk.glucodata.accessibility.AODOverlayService

/**
 * Настройка вида AOD на чёрном фоне: галочки и ползунки внизу, результат сразу виден сверху.
 * Всё сохраняется в те же настройки, что читает служба AOD.
 */
@Composable
fun AodEditorScreen(navController: NavController) {
    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences("tk.glucodata_preferences", Context.MODE_PRIVATE) }
    var opacity by remember { mutableFloatStateOf(prefs.getFloat("aod_opacity", 1.0f)) }
    var textScale by remember { mutableFloatStateOf(prefs.getFloat("aod_text_scale", 1.5f)) }
    var chartScale by remember { mutableFloatStateOf(prefs.getFloat("aod_chart_scale", 1.5f)) }
    var arrowScale by remember { mutableFloatStateOf(prefs.getFloat("aod_arrow_scale", 1.0f)) }
    var showChart by remember { mutableStateOf(prefs.getBoolean("aod_show_chart", true)) }
    var showArrow by remember { mutableStateOf(prefs.getBoolean("aod_show_arrow", true)) }
    var showSecondary by remember { mutableStateOf(prefs.getBoolean("aod_show_secondary", false)) }

    fun save() {
        prefs.edit()
            .putFloat("aod_opacity", opacity).putFloat("aod_text_scale", textScale)
            .putFloat("aod_chart_scale", chartScale).putFloat("aod_arrow_scale", arrowScale)
            .putBoolean("aod_show_chart", showChart).putBoolean("aod_show_arrow", showArrow)
            .putBoolean("aod_show_secondary", showSecondary).apply()
        context.sendBroadcast(Intent(AODOverlayService.ACTION_IMMEDIATE_REFRESH).setPackage(context.packageName))
    }

    // сахар для предпросмотра — настоящий, если Monitor его уже получил
    val glucose = remember {
        val sp = context.getSharedPreferences("gis_bridge", Context.MODE_PRIVATE)
        val mg = if (sp.contains("cal")) sp.getFloat("cal", 0f) else sp.getFloat("raw", 0f)
        when {
            mg <= 0f -> "5.3"
            Applic.unit == 1 -> String.format(java.util.Locale.US, "%.1f", mg / 18.0182f)
            else -> mg.toInt().toString()
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Column(Modifier.fillMaxSize()) {
            IconButton(onClick = { navController.popBackStack() }, modifier = Modifier.padding(8.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = Color.White)
            }
            // предпросмотр: чёрный экран, как на AOD
            Column(
                Modifier.fillMaxWidth().weight(1f).alpha(opacity),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(glucose, color = Color.White, fontSize = (34f * textScale).sp, fontWeight = FontWeight.Medium)
                    if (showArrow) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = Color.White,
                            modifier = Modifier.padding(start = 8.dp).size((30f * arrowScale * textScale).dp)
                        )
                    }
                }
                if (showSecondary) {
                    Text("+0.1 · 5 мин", color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
                }
                if (showChart) {
                    Canvas(Modifier.fillMaxWidth().padding(top = 12.dp).height((70f * chartScale).dp)) {
                        val path = Path()
                        val n = 40
                        for (i in 0..n) {
                            val x = size.width * i / n
                            val y = size.height * (0.5f - 0.28f * Math.sin(i / 6.0).toFloat() - 0.1f * Math.sin(i / 2.3).toFloat())
                            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                        }
                        drawPath(path, Color.White, style = Stroke(width = 3.dp.toPx()))
                        drawLine(Color.White.copy(alpha = 0.25f), Offset(0f, size.height * 0.2f), Offset(size.width, size.height * 0.2f), 1.dp.toPx())
                        drawLine(Color.White.copy(alpha = 0.25f), Offset(0f, size.height * 0.8f), Offset(size.width, size.height * 0.8f), 1.dp.toPx())
                    }
                }
            }
            // управление: сверху вниз — что показывать, затем размеры
            Column(
                Modifier.fillMaxWidth().height(330.dp).verticalScroll(rememberScrollState())
                    .background(Color(0xFF111418), RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                EditorSwitch(stringResource(R.string.show_chart), showChart) { showChart = it; save() }
                EditorSwitch(stringResource(R.string.show_trend_arrow), showArrow) { showArrow = it; save() }
                EditorSwitch(stringResource(R.string.display_secondary_values), showSecondary) { showSecondary = it; save() }
                EditorSlider(stringResource(R.string.opacity_percent, (opacity * 100).toInt()), opacity, 0.1f..1.0f) { opacity = it; save() }
                EditorSlider(stringResource(R.string.text_size_percent, (textScale * 100).toInt()), textScale, 0.5f..3.0f) { textScale = it; save() }
                if (showArrow) EditorSlider(stringResource(R.string.arrow_size_percent, (arrowScale * 100).toInt()), arrowScale, 0.5f..2.0f) { arrowScale = it; save() }
                if (showChart) EditorSlider(stringResource(R.string.chart_size_percent, (chartScale * 100).toInt()), chartScale, 0.5f..2.0f) { chartScale = it; save() }
                Box(Modifier.height(90.dp))
            }
        }
    }
}

@Composable
private fun EditorSwitch(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(title, color = Color.White, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** Ползунок не на всю ширину (как в отчётах GIS), подпись с процентами над ним. */
@Composable
private fun EditorSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Column(Modifier.padding(top = 6.dp)) {
        Text(label, color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp)
        Slider(value = value, onValueChange = onChange, valueRange = range, modifier = Modifier.fillMaxWidth(0.7f))
    }
}
