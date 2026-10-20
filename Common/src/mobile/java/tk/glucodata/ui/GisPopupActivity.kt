package tk.glucodata.ui

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

/**
 * Оконная версия GIS Monitor: то же приложение в плавающем окне на 94% × 82% экрана поверх GIS.
 * Закрывается крестиком, касанием вне окна или кнопкой «Назад». Запускается из GIS командой com.gismonitor.POPUP.
 */
class GisPopupActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        window.setDimAmount(0.55f)
        val m = resources.displayMetrics
        window.setLayout((m.widthPixels * 0.94).toInt(), (m.heightPixels * 0.82).toInt())
        setFinishOnTouchOutside(true)
        window.decorView.optOutOfForceDark()
        setContent {
            val prefs = getSharedPreferences(packageName + "_preferences", Context.MODE_PRIVATE)
            var themeMode by remember { mutableStateOf(runCatching { ThemeMode.valueOf(prefs.getString("theme_mode", "SYSTEM") ?: "SYSTEM") }.getOrDefault(ThemeMode.SYSTEM)) }
            JugglucoTheme(themeMode = themeMode) {
                Box(Modifier.fillMaxSize().clip(RoundedCornerShape(26.dp))) {
                    MainApp(themeMode = themeMode, onThemeChanged = { themeMode = it; prefs.edit().putString("theme_mode", it.name).apply() })
                    FilledTonalIconButton(onClick = { finish() }, modifier = Modifier.align(Alignment.TopStart).padding(8.dp)) {
                        Icon(Icons.Filled.Close, contentDescription = "Закрыть")
                    }
                }
            }
        }
    }
}
