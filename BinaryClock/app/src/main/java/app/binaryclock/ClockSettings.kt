package app.binaryclock

import android.content.Context
import android.content.SharedPreferences

/** Every place the clock can appear. Each one keeps its own copy of the settings. */
enum class Surface(val id: String, val label: String) {
    APP("app", "App"),
    WIDGET("widget", "Widget"),
    WALLPAPER_HOME("wphome", "Home screen wallpaper"),
    WALLPAPER_LOCK("wplock", "Lock screen wallpaper"),
    DREAM("dream", "Screen saver");

    val isWidget get() = this == WIDGET
    val isWallpaper get() = this == WALLPAPER_HOME || this == WALLPAPER_LOCK

    /** Widgets can't tick, so they never show seconds. */
    fun showsSeconds(s: ClockSettings) = when {
        isWidget -> false
        isWallpaper -> s.wallpaperSeconds
        else -> true
    }

    /** True when the surface paints its own dark background regardless of the system theme. */
    fun forcesDark(s: ClockSettings) = this == DREAM || (isWallpaper && s.wallpaperBlack)
}

/** One set of preferences per [Surface], read by the app, widgets, wallpaper and screen saver. */
data class ClockSettings(
    val showValues: Boolean = true,
    val showDigits: Boolean = false,
    val shapes: Boolean = false,
    val fade: Boolean = false,
    val h12: Boolean = false,
    val flip: Boolean = false,
    val vertical: Boolean = false,
    val reverse: Boolean = false,
    val keepScreenOn: Boolean = false,
    val widgetBackground: Boolean = true,
    val dreamBlack: Boolean = true,
    val wallpaperSeconds: Boolean = true,
    val wallpaperBlack: Boolean = true,
    val colorHour: String = "default",
    val colorMin: String = "default",
    val colorSec: String = "default",
) {
    fun save(context: Context, surface: Surface) {
        val k = prefix(surface)
        store(context).edit()
            .putBoolean("${k}values", showValues)
            .putBoolean("${k}digits", showDigits)
            .putBoolean("${k}shapes", shapes)
            .putBoolean("${k}fade", fade)
            .putBoolean("${k}h12", h12)
            .putBoolean("${k}flip", flip)
            .putBoolean("${k}vertical", vertical)
            .putBoolean("${k}reverse", reverse)
            .putBoolean("${k}wake", keepScreenOn)
            .putBoolean("${k}widget_bg", widgetBackground)
            .putBoolean("${k}dream_black", dreamBlack)
            .putBoolean("${k}wp_seconds", wallpaperSeconds)
            .putBoolean("${k}wp_black", wallpaperBlack)
            .putString("${k}color_h", colorHour)
            .putString("${k}color_m", colorMin)
            .putString("${k}color_s", colorSec)
            .apply()
    }

    companion object {
        private fun store(context: Context): SharedPreferences =
            context.getSharedPreferences("binclock", Context.MODE_PRIVATE)

        private fun prefix(surface: Surface) = surface.id + "."

        /** Replaces one surface's settings with a copy of another's. */
        fun copy(context: Context, from: Surface, to: Surface) = load(context, from).save(context, to)

        fun load(context: Context, surface: Surface): ClockSettings {
            val p = store(context)
            val d = ClockSettings()
            val k = prefix(surface)
            // A surface that has never been customised falls back to the old shared
            // setting, so settings from earlier versions carry over to every surface.
            fun bool(key: String, def: Boolean) = p.getBoolean(k + key, p.getBoolean(key, def))
            fun str(key: String, def: String) =
                p.getString(k + key, null) ?: p.getString(key, null) ?: def
            return ClockSettings(
                showValues = bool("values", d.showValues),
                showDigits = bool("digits", d.showDigits),
                shapes = bool("shapes", d.shapes),
                fade = bool("fade", d.fade),
                h12 = bool("h12", d.h12),
                flip = bool("flip", d.flip),
                vertical = bool("vertical", d.vertical),
                reverse = bool("reverse", d.reverse),
                keepScreenOn = bool("wake", d.keepScreenOn),
                widgetBackground = bool("widget_bg", d.widgetBackground),
                dreamBlack = bool("dream_black", d.dreamBlack),
                wallpaperSeconds = bool("wp_seconds", d.wallpaperSeconds),
                wallpaperBlack = bool("wp_black", d.wallpaperBlack),
                colorHour = str("color_h", d.colorHour),
                colorMin = str("color_m", d.colorMin),
                colorSec = str("color_s", d.colorSec),
            )
        }
    }
}
