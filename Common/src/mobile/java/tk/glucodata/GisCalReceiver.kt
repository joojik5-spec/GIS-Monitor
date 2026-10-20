package tk.glucodata

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import tk.glucodata.data.calibration.CalibrationManager

/**
 * Приём калибровки от приложения GIS (com.gismonitor.CALIBRATE: ts, meter_mgdl). Точка попадает в тот же
 * расчёт, что и ручная калибровка в самом Monitor, поэтому калибровки из двух приложений не расходятся.
 */
class GisCalReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val meter = i.getDoubleExtra("meter_mgdl", 0.0)
        val ts = i.getLongExtra("ts", System.currentTimeMillis())
        if (meter < 20.0 || meter > 600.0 || Math.abs(ts - System.currentTimeMillis()) > 30 * 60_000L) return
        val sp = c.getSharedPreferences("gis_bridge", Context.MODE_PRIVATE)
        // Приём включается вручную на экране «Калибровка»: без этого посторонние приложения не меняют калибровку.
        if (!sp.getBoolean("gis_cal_enabled", false)) return
        val raw = sp.getFloat("raw", 0f)
        if (raw <= 0f) return
        val cal = if (sp.contains("cal")) sp.getFloat("cal", 0f) else raw
        val k = if (Applic.unit == 1) 18.0182f else 1f
        val dup = CalibrationManager.getCachedCalibrations().any {
            Math.abs(it.timestamp - ts) < 120_000L && Math.abs(it.userValue - (meter / k).toFloat()) < 0.05f
        }
        if (dup) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try { CalibrationManager.addCalibration(ts, cal / k, raw / k, (meter / k).toFloat(), null, false) }
            finally { pending.finish() }
        }
    }
}
