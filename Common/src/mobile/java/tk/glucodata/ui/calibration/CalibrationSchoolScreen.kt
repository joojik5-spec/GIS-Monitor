package tk.glucodata.ui.calibration

import tk.glucodata.Applic
import androidx.compose.foundation.border
import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material.icons.filled.Delete
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.navigation.NavController
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/** «Школа калибровки» GIS Monitor: простыми словами, когда и как калибровать, плюс состояние текущего датчика. */
@Composable
fun CalibrationSchoolScreen(
    navController: NavController,
    onTriggerCalibration: (tk.glucodata.ui.CalibrationSheetState) -> Unit = {},
    dashboardViewModel: tk.glucodata.ui.viewmodel.DashboardViewModel
) {
    val ctx = LocalContext.current
    val start = ctx.getSharedPreferences("gis_bridge", Context.MODE_PRIVATE).getLong("start", 0L)
    val hours = if (start > 0) (System.currentTimeMillis() - start) / 3_600_000.0 else -1.0
    val sp0 = ctx.getSharedPreferences("gis_bridge", Context.MODE_PRIVATE)
    // То же правило, что в окне калибровки: «условия оптимальные», если скорость изменения сахара меньше 1.0.
    val history by dashboardViewModel.glucoseHistory.collectAsStateWithLifecycle()
    val unitValue by dashboardViewModel.unit.collectAsStateWithLifecycle()
    val viewModeValue by dashboardViewModel.viewMode.collectAsStateWithLifecycle()
    val isMmolNow = tk.glucodata.ui.util.GlucoseFormatter.isMmol(unitValue)
    val velocity = androidx.compose.runtime.remember(history, isMmolNow, viewModeValue) {
        tk.glucodata.logic.TrendEngine.calculateTrend(history, useRaw = viewModeValue == 1 || viewModeValue == 3, isMmol = isMmolNow).velocity
    }
    val unstable = Math.abs(velocity) >= 1.0f
    val lastTs = sp0.getLong("ts", 0L)
    val stale = lastTs > 0L && System.currentTimeMillis() - lastTs > 15 * 60_000L
    val (title, text) = when {
        hours < 0 && sp0.contains("ts") && stale -> ctx.getString(tk.glucodata.R.string.cal_no_fresh_title) to ctx.getString(tk.glucodata.R.string.cal_no_fresh_text)
        hours < 0 && sp0.contains("ts") -> ctx.getString(tk.glucodata.R.string.cal_connected_title) to ctx.getString(tk.glucodata.R.string.cal_connected_text)
        hours < 0 -> ctx.getString(tk.glucodata.R.string.cal_notfound_title) to ctx.getString(tk.glucodata.R.string.cal_notfound_text)
        hours < 24 -> ctx.getString(tk.glucodata.R.string.cal_no_title) to ctx.getString(tk.glucodata.R.string.cal_no_text, Math.round(hours).toInt(), Math.ceil(24 - hours).toInt())
        hours < 48 -> ctx.getString(tk.glucodata.R.string.cal_wait_title) to ctx.getString(tk.glucodata.R.string.cal_wait_text, Math.round(hours).toInt())
        stale -> ctx.getString(tk.glucodata.R.string.cal_no_fresh_title) to ctx.getString(tk.glucodata.R.string.cal_no_fresh_text)
        unstable -> ctx.getString(tk.glucodata.R.string.cal_unstable_title) to ctx.getString(if (velocity > 0) tk.glucodata.R.string.cal_unstable_up else tk.glucodata.R.string.cal_unstable_down)
        else -> ctx.getString(tk.glucodata.R.string.cal_ok_title) to ctx.getString(tk.glucodata.R.string.cal_ok_text)
    }
    val ok = (hours >= 48 || hours < 0 && sp0.contains("ts")) && !stale && !unstable
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(tk.glucodata.ui.components.TabScreenDefaults.contentPadding()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        tk.glucodata.ui.components.TabScreenHeader(title = ctx.getString(tk.glucodata.R.string.nav_calibration))
        val sp = ctx.getSharedPreferences("gis_bridge", Context.MODE_PRIVATE)
        // Сырое и итоговое значение берём из тех же данных, что и главный экран (после обработки алгоритмом датчика)
        val lastPt = history.lastOrNull { it.value > 0f }
        val kk = if (Applic.unit == 1) 18.0182 else 1.0
        val mult = if (isMmolNow) kk else 1.0
        // raw здесь — значение датчика после его алгоритма (без калибровок пользователя); cal — после калибровки, если она есть
        val raw = if (lastPt != null) lastPt.value.toDouble() * mult else sp.getFloat("raw", 0f).toDouble()
        val cal = if (lastPt != null) tk.glucodata.ui.SealedGlucoseValue.calibratedFor(lastPt, false, tk.glucodata.data.calibration.CalibrationManager.getResolvedCurrentSensorId())
            ?.takeIf { Math.abs(it - lastPt.value) > 0.04f }?.let { it.toDouble() * mult }
            else if (sp.contains("cal")) sp.getFloat("cal", 0f).toDouble() else null
        // Ваши калибровки: только включённые, сделанные уже на этом датчике.
        val allCals by tk.glucodata.data.calibration.CalibrationManager.getCalibrationsFlow()
            .collectAsStateWithLifecycle(initialValue = tk.glucodata.data.calibration.CalibrationManager.getCachedCalibrations())
        val userCals = allCals.filter { it.isEnabled && (start <= 0L || it.timestamp >= start) }
        val scope = androidx.compose.runtime.rememberCoroutineScope()
        val hasUserCal = userCals.isNotEmpty()
        val mmol = tk.glucodata.Applic.unit == 1
        val k = if (mmol) 18.0182 else 1.0
        fun f(v: Double) = if (mmol) String.format(java.util.Locale.US, "%.1f", v / 18.0182) else Math.round(v).toString()
        val cardBorder = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        val cardShape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp)

        // 1. Текущее значение
        if (raw > 0) {
            val trendNow = sp0.getInt("trend", 0)
            val arrow = when { trendNow >= 2 -> "\u2191"; trendNow == 1 -> "\u2197"; trendNow == 0 -> "\u2192"; trendNow == -1 -> "\u2198"; else -> "\u2193" }
            Card(shape = cardShape, border = cardBorder, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            f(cal ?: raw), style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold,
                            modifier = if (cal != null) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, androidx.compose.foundation.shape.RoundedCornerShape(20.dp)).padding(horizontal = 14.dp, vertical = 2.dp) else Modifier
                        )
                        Text(arrow, style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.primary)
                    }
                    Text(ctx.getString(tk.glucodata.R.string.cal_unit_now, ctx.getString(if (mmol) tk.glucodata.R.string.cal_unit_mmol else tk.glucodata.R.string.cal_unit_mgdl)), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (cal != null) Text(
                        ctx.getString(tk.glucodata.R.string.cal_before, f(raw)),
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // 3. Калибровка: ввод прямо на экране
        Card(shape = cardShape, border = cardBorder, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(ctx.getString(tk.glucodata.R.string.nav_calibration), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(ctx.getString(tk.glucodata.R.string.cal_compare), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                MeterPhotoButton(onTriggerCalibration)
                val whenMs = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<Long?>(null) } // null = «сейчас»
                val expanded = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
                val nowMs = System.currentTimeMillis()
                val shownMs = whenMs.value ?: nowMs
                fun cal() = java.util.Calendar.getInstance().apply { timeInMillis = shownMs }
                fun setTime(ms: Long) { whenMs.value = ms.coerceIn(nowMs - 7L * 86_400_000L, nowMs) }
                Surface(
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            Modifier.fillMaxWidth().clip(androidx.compose.foundation.shape.RoundedCornerShape(14.dp)).clickable { expanded.value = !expanded.value }.padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                        ) {
                            Text(
                                ctx.getString(tk.glucodata.R.string.cal_time, java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(shownMs))),
                                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f)
                            )
                            if (whenMs.value != null) {
                                TextButton(onClick = { whenMs.value = null }) { Text(ctx.getString(tk.glucodata.R.string.cal_now)) }
                            }
                            Text(if (expanded.value) "\u25B4" else "\u25BE", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                        }
                        androidx.compose.animation.AnimatedVisibility(visible = expanded.value) {
                            TimeStepper(selected = shownMs, onChange = { ms -> if (Math.abs(ms - nowMs) < 60_000L) whenMs.value = null else setTime(ms) }, showQuick = false, showDay = false)
                        }
                    }
                }
                val step = if (mmol) 0.1f else 1f
                val entry = androidx.compose.runtime.remember {
                    androidx.compose.runtime.mutableStateOf(if (raw > 0) (Math.round(((cal ?: raw) / k) * 10.0) / 10.0).toFloat() else 0f)
                }
                fun fmt(v: Float) = String.format(java.util.Locale.US, if (mmol) "%.1f" else "%.0f", v)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    OutlinedButton(
                        onClick = { haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove); entry.value = (Math.round((entry.value - step) * 10f) / 10f).coerceAtLeast(step) },
                        modifier = Modifier.weight(1f).height(52.dp)
                    ) { Text("−", style = MaterialTheme.typography.headlineSmall) }
                    Text(fmt(entry.value), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.4f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    OutlinedButton(
                        onClick = { haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove); entry.value = Math.round((entry.value + step) * 10f) / 10f },
                        modifier = Modifier.weight(1f).height(52.dp)
                    ) { Text("+", style = MaterialTheme.typography.headlineSmall) }
                }
                val badgeColor = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary
                Surface(
                    color = badgeColor.copy(alpha = 0.14f),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(22.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, badgeColor.copy(alpha = 0.6f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        if (ok) ctx.getString(tk.glucodata.R.string.cal_advice_ok, text) else ctx.getString(tk.glucodata.R.string.cal_advice_warn, title, text),
                        modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodyLarge, color = badgeColor
                    )
                }
                Button(
                    onClick = {
                        val v = entry.value
                        val ts = whenMs.value ?: System.currentTimeMillis()
                        // Показание датчика на момент замера: ближайшая точка истории (в пределах 10 минут), иначе текущее.
                        val near = history.minByOrNull { Math.abs(it.timestamp - ts) }?.takeIf { Math.abs(it.timestamp - ts) <= 10 * 60_000L }
                        val auto = near?.value ?: ((cal ?: raw) / k).toFloat()
                        val rawDisplay = near?.rawValue?.takeIf { it > 0f } ?: (raw / k).toFloat()
                        scope.launch {
                            val added = tk.glucodata.data.calibration.CalibrationManager.addCalibration(ts, auto, rawDisplay, v, null, false)
                            android.widget.Toast.makeText(ctx, if (added) ctx.getString(tk.glucodata.R.string.cal_added, fmt(auto), fmt(v)) else ctx.getString(tk.glucodata.R.string.cal_add_failed), android.widget.Toast.LENGTH_LONG).show()
                        }
                    },
                    enabled = raw > 0 && entry.value > 0f,
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) { Text(ctx.getString(tk.glucodata.R.string.cal_save), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
            }
        }
        if (userCals.isNotEmpty()) UserCalibrationsList(userCals, mmol, scope)
        Text(ctx.getString(tk.glucodata.R.string.cal_how), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = tk.glucodata.ui.components.TabScreenDefaults.Gutter))
        listOf(
            "1" to ctx.getString(tk.glucodata.R.string.cal_step1),
            "2" to ctx.getString(tk.glucodata.R.string.cal_step2),
            "3" to ctx.getString(tk.glucodata.R.string.cal_step3),
            "4" to ctx.getString(tk.glucodata.R.string.cal_step4),
        ).forEach { (n, t) ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(n, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Text(t, style = MaterialTheme.typography.bodyMedium)
            }
        }
        Text(
            ctx.getString(tk.glucodata.R.string.cal_dont),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Card(shape = cardShape, border = cardBorder, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(ctx.getString(tk.glucodata.R.string.cal_from_gis_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    ctx.getString(tk.glucodata.R.string.cal_from_gis_text),
                    style = MaterialTheme.typography.bodyMedium
                )
                val gisOn = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(sp0.getBoolean("gis_cal_enabled", false)) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text(ctx.getString(tk.glucodata.R.string.cal_accept_gis), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                    Switch(checked = gisOn.value, onCheckedChange = { gisOn.value = it; sp0.edit().putBoolean("gis_cal_enabled", it).apply() })
                }
            }
        }
        OutlinedButton(onClick = { navController.navigate("calibrations") }, Modifier.fillMaxWidth()) { Text(ctx.getString(tk.glucodata.R.string.cal_open)) }
        Spacer(Modifier.height(80.dp))
    }
}

/** Калибровка по фото глюкометра: снимок → распознавание числа → обычное окно калибровки с уже подставленным значением (его можно поправить). */
@Composable
private fun MeterPhotoButton(onTriggerCalibration: (tk.glucodata.ui.CalibrationSheetState) -> Unit) {
    val ctx = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val busy = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val photo = androidx.compose.runtime.remember { java.io.File(ctx.cacheDir, "meter").apply { mkdirs() }.let { java.io.File(it, "meter.jpg") } }
    val uri = androidx.compose.runtime.remember { androidx.core.content.FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", photo) }

    val pending = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<Float?>(null) }

    fun openSheet(valueDisplay: Float?) {
        val sp = ctx.getSharedPreferences("gis_bridge", Context.MODE_PRIVATE)
        val mmol = tk.glucodata.Applic.unit == 1
        fun d(v: Float) = if (mmol) v / 18.0182f else v
        val raw = d(sp.getFloat("raw", 0f))
        val auto = if (sp.contains("cal")) d(sp.getFloat("cal", 0f)) else raw
        onTriggerCalibration(tk.glucodata.ui.CalibrationSheetState.New(auto, raw, System.currentTimeMillis(), "", null, valueDisplay))
    }

    val shot = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.TakePicture()) { ok ->
        if (!ok) return@rememberLauncherForActivityResult
        busy.value = true
        scope.launch {
            val value = withContext(kotlinx.coroutines.Dispatchers.Default) { readMeter(photo, tk.glucodata.Applic.unit == 1) }
            busy.value = false
            if (value == null) {
                android.widget.Toast.makeText(ctx, ctx.getString(tk.glucodata.R.string.cal_ocr_fail), android.widget.Toast.LENGTH_LONG).show()
                openSheet(null)
            } else pending.value = value
        }
    }
    val perm = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) shot.launch(uri) else android.widget.Toast.makeText(ctx, ctx.getString(tk.glucodata.R.string.cal_cam_perm), android.widget.Toast.LENGTH_LONG).show()
    }
    pending.value?.let { v ->
        val mmol = tk.glucodata.Applic.unit == 1
        val shown = String.format(java.util.Locale.US, if (mmol) "%.1f" else "%.0f", v)
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { pending.value = null },
            title = { Text(ctx.getString(tk.glucodata.R.string.cal_confirm_title)) },
            text = { Text(ctx.getString(tk.glucodata.R.string.cal_confirm_text, shown.toString(), ctx.getString(if (mmol) tk.glucodata.R.string.cal_unit_mmol else tk.glucodata.R.string.cal_unit_mgdl))) },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    pending.value = null
                    val sp = ctx.getSharedPreferences("gis_bridge", Context.MODE_PRIVATE)
                    fun d(x: Float) = if (mmol) x / 18.0182f else x
                    val raw = d(sp.getFloat("raw", 0f))
                    val auto = if (sp.contains("cal")) d(sp.getFloat("cal", 0f)) else raw
                    scope.launch {
                        val ok = tk.glucodata.data.calibration.CalibrationManager.addCalibration(System.currentTimeMillis(), auto, raw, v, null, false)
                        val fmt = { x: Float -> String.format(java.util.Locale.US, if (mmol) "%.1f" else "%.0f", x) }
                        android.widget.Toast.makeText(ctx, if (ok) ctx.getString(tk.glucodata.R.string.cal_added, fmt(auto), shown.toString()) else ctx.getString(tk.glucodata.R.string.cal_add_failed), android.widget.Toast.LENGTH_LONG).show()
                    }
                }) { Text(ctx.getString(tk.glucodata.R.string.cal_yes)) }
            },
            dismissButton = {
                Row {
                    androidx.compose.material3.TextButton(onClick = { pending.value = null; openSheet(v) }) { Text(ctx.getString(tk.glucodata.R.string.cal_fix)) }
                    androidx.compose.material3.TextButton(onClick = { pending.value = null }) { Text(ctx.getString(tk.glucodata.R.string.cal_cancel)) }
                }
            }
        )
    }
    Button(
        onClick = {
            if (androidx.core.content.ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED) shot.launch(uri)
            else perm.launch(android.Manifest.permission.CAMERA)
        },
        enabled = !busy.value,
        modifier = Modifier.fillMaxWidth()
    ) { Text(ctx.getString(if (busy.value) tk.glucodata.R.string.cal_reading else tk.glucodata.R.string.cal_photo)) }
    Text(
        ctx.getString(tk.glucodata.R.string.cal_photo_hint),
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/** Читает число с фото: берёт самое крупное число на кадре (цифры глюкометра), а не первое попавшееся (дата, время, номера). */
private suspend fun readMeter(file: java.io.File, mmol: Boolean): Float? = try {
    val opts = android.graphics.BitmapFactory.Options().apply { inSampleSize = 2 }
    val bmp = android.graphics.BitmapFactory.decodeFile(file.absolutePath, opts) ?: return null
    val result = kotlinx.coroutines.suspendCancellableCoroutine<com.google.mlkit.vision.text.Text?> { c ->
        com.google.mlkit.vision.text.TextRecognition.getClient(com.google.mlkit.vision.text.latin.TextRecognizerOptions.DEFAULT_OPTIONS)
            .process(com.google.mlkit.vision.common.InputImage.fromBitmap(bmp, 0))
            .addOnSuccessListener { c.resume(it) { } }
            .addOnFailureListener { c.resume(null) { } }
    }
    val rx = Regex("""^\d{1,3}(?:[.,]\d)?$""")
    var best: Pair<Float, Int>? = null // значение в ммоль/л или мг/дл, высота
    result?.textBlocks?.forEach { b -> b.lines.forEach { l -> l.elements.forEach { e ->
        val t = e.text.trim()
        if (!rx.matches(t)) return@forEach
        val v = t.replace(',', '.').toFloatOrNull() ?: return@forEach
        val whole = v == Math.rint(v.toDouble()).toFloat()
        val mmolVal = when { !whole && v in 1.0f..33.3f -> v; whole && v in 20f..600f -> v / 18.0182f; whole && v in 2f..33f -> v; else -> return@forEach }
        val h = e.boundingBox?.height() ?: 0
        if (best == null || h > best!!.second) best = Pair(mmolVal, h)
    } } }
    best?.let { if (mmol) it.first else it.first * 18.0182f }
} catch (t: Throwable) { null }


/** Сохранённые калибровки: крупные плашки сразу под кнопкой «Сохранить»; удаление свайпом справа налево, раз в 15 секунд еле заметная подсказка. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun UserCalibrationsList(
    cals: List<tk.glucodata.data.calibration.CalibrationEntity>,
    mmol: Boolean,
    scope: kotlinx.coroutines.CoroutineScope
) {
    val ctx = LocalContext.current
    val hint = androidx.compose.runtime.remember { androidx.compose.animation.core.Animatable(0f) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(15_000)
            hint.snapTo(0f)
            hint.animateTo(1f, androidx.compose.animation.core.tween(1400))
            hint.snapTo(0f)
        }
    }
    fun u(v: Float) = String.format(java.util.Locale.US, if (mmol) "%.1f" else "%.0f", v)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(ctx.getString(tk.glucodata.R.string.cal_yours), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = tk.glucodata.ui.components.TabScreenDefaults.Gutter))
        cals.sortedByDescending { it.timestamp }.forEachIndexed { index, c ->
            val state = androidx.compose.material3.rememberSwipeToDismissBoxState(
                confirmValueChange = { v ->
                    if (v == androidx.compose.material3.SwipeToDismissBoxValue.EndToStart) {
                        scope.launch { tk.glucodata.data.calibration.CalibrationManager.deleteCalibration(c) }
                        true
                    } else false
                }
            )
            androidx.compose.material3.SwipeToDismissBox(
                state = state,
                enableDismissFromStartToEnd = false,
                backgroundContent = {
                    Box(
                        Modifier.fillMaxSize().clip(androidx.compose.foundation.shape.RoundedCornerShape(22.dp)).background(MaterialTheme.colorScheme.errorContainer).padding(end = 22.dp),
                        contentAlignment = androidx.compose.ui.Alignment.CenterEnd
                    ) {
                        androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Default.Delete, contentDescription = ctx.getString(tk.glucodata.R.string.cal_delete), tint = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
            ) {
                Surface(
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(java.text.SimpleDateFormat("dd.MM  HH:mm", java.util.Locale.getDefault()).format(java.util.Date(c.timestamp)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(ctx.getString(tk.glucodata.R.string.cal_row, "", u(c.sensorValue), u(c.userValue)).trimStart(' ', '·'), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            }
                        }
                        if (index == 0 && hint.value > 0f) {
                            val p = hint.value
                            Row(
                                Modifier.matchParentSize().padding(end = 18.dp).graphicsLayer { alpha = 0.05f * kotlin.math.sin(Math.PI.toFloat() * p) },
                                horizontalArrangement = Arrangement.End, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                            ) {
                                Text("\u2190", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.graphicsLayer { translationX = -120.dp.toPx() * p })
                                androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Default.Delete, contentDescription = null)
                            }
                        }
                    }
                }
            }
        }
    }
}
