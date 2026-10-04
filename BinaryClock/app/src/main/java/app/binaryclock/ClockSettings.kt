package app.binaryclock

import android.content.Context
import android.content.SharedPreferences

/** One set of preferences, read by the app, the widget and the screen saver. */
data class ClockSettings(
    val showValues: Boolean = true,
    val showDigits: Boolean = false,
    val shapes: Boolean = false,
    val fade: Boolean = false,
    val h12: Boolean = false,
    val flip: Boolean = false,
    val vertical: Boolean = false,
    val keepScreenOn: Boolean = false,
    val widgetBackground: Boolean = true,
    val dreamBlack: Boolean = true,
    val colorHour: String = "default",
    val colorMin: String = "default",
    val colorSec: String = "default",
) {
    fun save(context: Context) {
        store(context).edit()
            .putBoolean("values", showValues)
            .putBoolean("digits", showDigits)
            .putBoolean("shapes", shapes)
            .putBoolean("fade", fade)
            .putBoolean("h12", h12)
            .putBoolean("flip", flip)
            .putBoolean("vertical", vertical)
            .putBoolean("wake", keepScreenOn)
            .putBoolean("widget_bg", widgetBackground)
            .putBoolean("dream_black", dreamBlack)
            .putString("color_h", colorHour)
            .putString("color_m", colorMin)
            .putString("color_s", colorSec)
            .apply()
    }

    companion object {
        private fun store(context: Context): SharedPreferences =
            context.getSharedPreferences("binclock", Context.MODE_PRIVATE)

        fun load(context: Context): ClockSettings {
            val p = store(context)
            val d = ClockSettings()
            return ClockSettings(
                showValues = p.getBoolean("values", d.showValues),
                showDigits = p.getBoolean("digits", d.showDigits),
                shapes = p.getBoolean("shapes", d.shapes),
                fade = p.getBoolean("fade", d.fade),
                h12 = p.getBoolean("h12", d.h12),
                flip = p.getBoolean("flip", d.flip),
                vertical = p.getBoolean("vertical", d.vertical),
                keepScreenOn = p.getBoolean("wake", d.keepScreenOn),
                widgetBackground = p.getBoolean("widget_bg", d.widgetBackground),
                dreamBlack = p.getBoolean("dream_black", d.dreamBlack),
                colorHour = p.getString("color_h", null) ?: d.colorHour,
                colorMin = p.getString("color_m", null) ?: d.colorMin,
                colorSec = p.getString("color_s", null) ?: d.colorSec,
            )
        }
    }
}
