package tk.glucodata.ui.calibration

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.remember
import androidx.navigation.NavController
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/** «Школа калибровки» GIS Monitor: простыми словами, когда и как калибровать, плюс состояние текущего датчика. */
@Composable
fun CalibrationSchoolScreen(navController: NavController, onTriggerCalibration: (tk.glucodata.ui.CalibrationSheetState) -> Unit = {}) {
    val ctx = LocalContext.current
    val start = ctx.getSharedPreferences("gis_bridge", Context.MODE_PRIVATE).getLong("start", 0L)
    val hours = if (start > 0) (System.currentTimeMillis() - start) / 3_600_000.0 else -1.0
    val (title, text) = when {
        hours < 0 && ctx.getSharedPreferences("gis_bridge", Context.MODE_PRIVATE).contains("ts") ->
            "Датчик подключён" to "Возраст датчика пока не определён. Калибруйте, когда сахар ровный."
        hours < 0 -> "Датчик ещё не найден" to "Как только придут первые значения, здесь появится совет."
        hours < 24 -> "Сейчас калибровать нельзя" to "Датчику ${Math.round(hours)} ч. Первые сутки он привыкает к коже, значения плавают. Подождите ещё ${Math.ceil(24 - hours).toInt()} ч."
        hours < 48 -> "Лучше подождать" to "Датчику ${Math.round(hours)} ч. Калибровка возможна, но точнее будет после 48 часов."
        else -> "Можно калибровать" to "Датчик «устоялся». Сделайте замер глюкометром и введите его в калибровках."
    }
    val ok = hours >= 48 || hours < 0 && ctx.getSharedPreferences("gis_bridge", Context.MODE_PRIVATE).contains("ts")
    Column(
        Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Калибровка", fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (ok) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer
            ),
            shape = MaterialTheme.shapes.extraLarge
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(title, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                Text(text, fontSize = 15.sp)
            }
        }
        val sp = ctx.getSharedPreferences("gis_bridge", Context.MODE_PRIVATE)
        val raw = sp.getFloat("raw", 0f).toDouble(); val cal = if (sp.contains("cal")) sp.getFloat("cal", 0f).toDouble() else null
        // Ваши калибровки: только включённые, сделанные уже на этом датчике.
        val userCals = remember(start) {
            tk.glucodata.data.calibration.CalibrationManager.getCachedCalibrations().filter { it.isEnabled && (start <= 0L || it.timestamp >= start) }
        }
        val hasUserCal = userCals.isNotEmpty()
        if (raw > 0) {
            val mmol = tk.glucodata.Applic.unit == 1
            fun f(v: Double) = if (mmol) String.format(java.util.Locale.US, "%.1f", v / 18.0182) else Math.round(v).toString()
            Card(shape = MaterialTheme.shapes.extraLarge) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column { Text("Сырой сигнал", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant); Text(f(raw), fontSize = 34.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.tertiary) }
                        Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
                            Text(if (hasUserCal) "С вашей калибровкой" else "Алгоритм датчика", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(f(cal ?: raw), fontSize = 34.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    val msg = when {
                        hasUserCal -> "Ваших калибровок: ${userCals.size}. В AAPS и Nightscout уходит значение с калибровкой."
                        cal == null -> "Калибровок нет: в AAPS и Nightscout уходит значение датчика."
                        else -> "Вы не калибровали. Разница с сырым сигналом — это собственная обработка датчика, а не ваша калибровка. В AAPS и Nightscout уходит значение датчика."
                    }
                    Text(msg, fontSize = 13.sp, fontStyle = FontStyle.Italic, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val last = userCals.maxByOrNull { it.timestamp }
                    if (last != null) {
                        val t = java.text.SimpleDateFormat("dd.MM HH:mm", java.util.Locale.getDefault()).format(java.util.Date(last.timestamp))
                        fun u(v: Float) = String.format(java.util.Locale.US, if (mmol) "%.1f" else "%.0f", v)
                        Text("Последняя калибровка $t: было ${u(last.sensorValue)} → глюкометр ${u(last.userValue)}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
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
                Text("Калибровка в приложении GIS", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    "Если на этом телефоне установлено приложение GIS, замер глюкометра можно ввести и там: " +
                        "удобно, когда вы уже работаете с данными AAPS и Nightscout в GIS.",
                    fontSize = 14.sp
                )
            }
        }
        MeterPhotoButton(onTriggerCalibration)
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
