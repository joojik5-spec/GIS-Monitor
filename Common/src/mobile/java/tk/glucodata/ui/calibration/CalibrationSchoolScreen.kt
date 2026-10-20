package tk.glucodata.ui.calibration

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
        hours < 0 && sp0.contains("ts") && stale ->
            "Нет свежих данных" to "Последнее значение пришло больше 15 минут назад. Дождитесь новых показаний."
        hours < 0 && sp0.contains("ts") ->
            "Датчик подключён" to "Возраст датчика пока не определён. Калибруйте, когда сахар ровный."
        hours < 0 -> "Датчик ещё не найден" to "Как только придут первые значения, здесь появится совет."
        hours < 24 -> "Сейчас калибровать нельзя" to "Датчику ${Math.round(hours)} ч. Первые сутки он привыкает к коже, значения плавают. Подождите ещё ${Math.ceil(24 - hours).toInt()} ч."
        hours < 48 -> "Лучше подождать" to "Датчику ${Math.round(hours)} ч. Калибровка возможна, но точнее будет после 48 часов."
        stale -> "Нет свежих данных" to "Последнее значение пришло больше 15 минут назад. Дождитесь новых показаний."
        unstable -> "Подождите ~15 минут (нестабильно)" to "Сахар ${if (velocity > 0) "растёт" else "падает"}: замер сейчас даст неточную калибровку. Дождитесь ровной линии без стрелок."
        else -> "Условия оптимальные" to "Датчик «устоялся», сахар ровный. Сделайте замер глюкометром и введите его в калибровках."
    }
    val ok = (hours >= 48 || hours < 0 && sp0.contains("ts")) && !stale && !unstable
    Column(
        Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Калибровка", fontSize = 30.sp, fontWeight = FontWeight.Bold)
        val sp = ctx.getSharedPreferences("gis_bridge", Context.MODE_PRIVATE)
        val raw = sp.getFloat("raw", 0f).toDouble(); val cal = if (sp.contains("cal")) sp.getFloat("cal", 0f).toDouble() else null
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
            Card(shape = cardShape, border = cardBorder) {
                Column(Modifier.padding(horizontal = 24.dp, vertical = 22.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(f(cal ?: raw), fontSize = 64.sp, fontWeight = FontWeight.Bold)
                        Text(arrow, fontSize = 38.sp, color = MaterialTheme.colorScheme.primary)
                    }
                    Text((if (mmol) "ммоль/л" else "мг/дл") + " · сейчас", fontSize = 17.sp, fontStyle = FontStyle.Italic, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        "Сырой сигнал: ${f(raw)} · " + (if (hasUserCal) "после калибровки" else "показание датчика") + ": ${f(cal ?: raw)}",
                        fontSize = 14.sp, fontStyle = FontStyle.Italic, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (userCals.isNotEmpty()) {
                        Text("Ваши калибровки на этом датчике", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Это замеры глюкометра, которые были сохранены (здесь или из GIS). Если вы их не вводили, удалите.",
                            fontSize = 13.sp, fontStyle = FontStyle.Italic, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        fun u(v: Float) = String.format(java.util.Locale.US, if (mmol) "%.1f" else "%.0f", v)
                        userCals.sortedByDescending { it.timestamp }.take(3).forEach { c ->
                            val t = java.text.SimpleDateFormat("dd.MM HH:mm", java.util.Locale.getDefault()).format(java.util.Date(c.timestamp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                Text("$t · датчик ${u(c.sensorValue)} → глюкометр ${u(c.userValue)}", fontSize = 14.sp, modifier = Modifier.weight(1f))
                                TextButton(onClick = { scope.launch { tk.glucodata.data.calibration.CalibrationManager.deleteCalibration(c) } }) { Text("Удалить") }
                            }
                        }
                    }
                }
            }
        }

        // 2. Датчик
        val sensorEnd = sp.getLong("end", 0L)
        Card(shape = cardShape, border = cardBorder) {
            Column(Modifier.padding(horizontal = 24.dp, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text((sp.getString("name", "Датчик") ?: "Датчик").let { if (it == "Sensor") "Датчик" else it }, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                val totalDays = if (start > 0 && sensorEnd > start) ((sensorEnd - start) / 86_400_000L).toInt() else 0
                val day = if (start > 0) (hours / 24).toInt() + 1 else 0
                Text(
                    if (day > 0 && totalDays > 0) "День ${day.coerceAtMost(totalDays)} из $totalDays" else "Возраст датчика пока не определён",
                    fontSize = 17.sp, fontStyle = FontStyle.Italic, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                run {
                    val sensorVm: tk.glucodata.ui.viewmodel.SensorViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
                    androidx.compose.runtime.DisposableEffect(Unit) { sensorVm.startPolling(); onDispose { sensorVm.stopPolling() } }
                    val list by sensorVm.sensors.collectAsStateWithLifecycle()
                    val rssi = (list.firstOrNull { it.streaming } ?: list.firstOrNull())?.rssi ?: 0
                    val lastTs = sp.getLong("ts", 0L)
                    val ageMin = if (lastTs > 0) (System.currentTimeMillis() - lastTs) / 60_000L else -1L
                    val level = when {
                        ageMin < 0 || ageMin > 15 -> 0
                        rssi in -69..-1 -> 4
                        rssi in -80..-70 -> 3
                        rssi in -90..-81 -> 2
                        rssi < -90 -> 1
                        else -> 4
                    }
                    val word = when (level) { 4 -> "Отличный"; 3 -> "Хороший"; 2 -> "Средний"; 1 -> "Слабый"; else -> "Нет связи" }
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = androidx.compose.ui.Alignment.Bottom) {
                            for (i in 1..4) Box(
                                Modifier.width(6.dp).height((6 + i * 5).dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(2.dp))
                                    .background(if (i <= level) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)
                            )
                        }
                        Text(
                            "Сигнал: $word" + (if (rssi in -120..-1 && level > 0) " ($rssi дБм)" else if (ageMin > 15) " (данных нет $ageMin мин)" else ""),
                            fontSize = 16.sp, fontStyle = FontStyle.Italic, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (start > 0 && sensorEnd > start) {
                    LinearProgressIndicator(
                        progress = { ((System.currentTimeMillis() - start).toFloat() / (sensorEnd - start).toFloat()).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(3.dp))
                    )
                }
            }
        }

        // 3. Калибровка: ввод прямо на экране
        Card(shape = cardShape, border = cardBorder) {
            Column(Modifier.padding(horizontal = 24.dp, vertical = 22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Калибровка", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text("Сравните с глюкометром и введите его число.", fontSize = 16.sp, fontStyle = FontStyle.Italic, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                                "Время: " + java.text.SimpleDateFormat("d MMM, HH:mm", java.util.Locale.getDefault()).format(java.util.Date(shownMs)),
                                fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f)
                            )
                            if (whenMs.value != null) {
                                TextButton(onClick = { whenMs.value = null }) { Text("Сейчас") }
                            }
                            Text(if (expanded.value) "\u25B4" else "\u25BE", fontSize = 18.sp, color = MaterialTheme.colorScheme.primary)
                        }
                        androidx.compose.animation.AnimatedVisibility(visible = expanded.value) {
                            TimeStepper(selected = shownMs, onChange = { ms -> if (Math.abs(ms - nowMs) < 60_000L) whenMs.value = null else setTime(ms) })
                        }
                        if (!expanded.value) {
                            Text("Нажмите, чтобы изменить время замера", fontSize = 12.sp, fontStyle = FontStyle.Italic, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                        onClick = { entry.value = (Math.round((entry.value - step) * 10f) / 10f).coerceAtLeast(step) },
                        modifier = Modifier.weight(1f).height(60.dp)
                    ) { Text("−", fontSize = 26.sp) }
                    Text(fmt(entry.value), fontSize = 46.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.4f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    OutlinedButton(
                        onClick = { entry.value = Math.round((entry.value + step) * 10f) / 10f },
                        modifier = Modifier.weight(1f).height(60.dp)
                    ) { Text("+", fontSize = 26.sp) }
                }
                val badgeColor = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary
                Surface(
                    color = badgeColor.copy(alpha = 0.14f),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(22.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, badgeColor.copy(alpha = 0.6f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        (if (ok) "\u2713 Сейчас оптимально калибровать: " else "\u26A0 $title. ") + text,
                        modifier = Modifier.padding(16.dp), fontSize = 16.sp, color = badgeColor
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
                            android.widget.Toast.makeText(ctx, if (added) "Калибровка добавлена: было ${fmt(auto)} → глюкометр ${fmt(v)}" else "Не удалось добавить калибровку", android.widget.Toast.LENGTH_LONG).show()
                        }
                    },
                    enabled = raw > 0 && entry.value > 0f,
                    modifier = Modifier.fillMaxWidth().height(58.dp)
                ) { Text("Сохранить калибровку", fontSize = 18.sp, fontWeight = FontWeight.Bold) }
            }
        }
        Text("Как калибровать", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        listOf(
            "1" to "Вымойте руки и сделайте замер глюкометром.",
            "2" to "Калибруйте, когда сахар ровный: без стрелок вверх и вниз, не после еды и не после инсулина.",
            "3" to "Нужно три замера на разных уровнях, например 5, 8 и 12. Тогда линия подстроится точнее.",
            "4" to "Сразу введите число в калибровках. Старый замер уже не подходит.",
        ).forEach { (n, t) ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(n, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Text(t, fontSize = 15.sp)
            }
        }
        Text(
            "Не калибруйте при резких скачках, если сахар меняется быстро или если сомневаетесь в замере.",
            fontSize = 13.sp, fontStyle = FontStyle.Italic, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
            shape = MaterialTheme.shapes.extraLarge
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Калибровка из приложения GIS", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    "Замер можно ввести и в приложении GIS: он придёт сюда и учтётся так же, как введённый в GIS Monitor, " +
                        "а в AAPS и Nightscout уйдёт уже откалиброванное значение. " +
                        "Для этого включите приём ниже. По умолчанию он выключен, чтобы посторонние приложения не могли менять вашу калибровку.",
                    fontSize = 14.sp
                )
                val gisOn = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(sp0.getBoolean("gis_cal_enabled", false)) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text("Принимать калибровки из GIS", fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                    Switch(checked = gisOn.value, onCheckedChange = { gisOn.value = it; sp0.edit().putBoolean("gis_cal_enabled", it).apply() })
                }
            }
        }
        OutlinedButton(onClick = { navController.navigate("calibrations") }, Modifier.fillMaxWidth()) { Text("Открыть калибровки") }
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
                android.widget.Toast.makeText(ctx, "Число не распознано. Введите его вручную.", android.widget.Toast.LENGTH_LONG).show()
                openSheet(null)
            } else pending.value = value
        }
    }
    val perm = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) shot.launch(uri) else android.widget.Toast.makeText(ctx, "Нужен доступ к камере, чтобы сфотографировать глюкометр.", android.widget.Toast.LENGTH_LONG).show()
    }
    pending.value?.let { v ->
        val mmol = tk.glucodata.Applic.unit == 1
        val shown = String.format(java.util.Locale.US, if (mmol) "%.1f" else "%.0f", v)
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { pending.value = null },
            title = { Text("Подтверждаете показание?") },
            text = { Text("Глюкометр показывает $shown ${if (mmol) "ммоль/л" else "мг/дл"}. Если верно, калибровка добавится сразу.") },
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
                        android.widget.Toast.makeText(ctx, if (ok) "Калибровка добавлена: было ${fmt(auto)} → глюкометр $shown" else "Не удалось добавить калибровку", android.widget.Toast.LENGTH_LONG).show()
                    }
                }) { Text("Да, верно") }
            },
            dismissButton = {
                Row {
                    androidx.compose.material3.TextButton(onClick = { pending.value = null; openSheet(v) }) { Text("Исправить") }
                    androidx.compose.material3.TextButton(onClick = { pending.value = null }) { Text("Отмена") }
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
    ) { Text(if (busy.value) "Читаю число…" else "Калибровка по фото глюкометра") }
    Text(
        "Сфотографируйте экран глюкометра. Число подставится само, вы проверите его и подтвердите.",
        fontSize = 13.sp, fontStyle = FontStyle.Italic, color = MaterialTheme.colorScheme.onSurfaceVariant
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
