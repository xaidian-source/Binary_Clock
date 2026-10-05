package app.binaryclock

import android.app.Activity
import android.app.AlertDialog
import android.app.WallpaperManager
import android.appwidget.AppWidgetManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.LayerDrawable
import android.os.Bundle
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

/**
 * Full-screen clock with its settings underneath. Tap the clock to hide the
 * settings and the system bars; tap again to bring them back.
 */
class ClockActivity : Activity() {

    private var surface = Surface.APP
    private lateinit var settings: ClockSettings
    private lateinit var clock: BinaryClockView
    private lateinit var controls: View
    private lateinit var list: LinearLayout
    private var controlsVisible = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setDecorFitsSystemWindows(false)
        setContentView(R.layout.activity_clock)

        surface = Surface.entries.firstOrNull {
            it.id == getSharedPreferences("binclock", MODE_PRIVATE).getString("editing", null)
        } ?: Surface.APP
        settings = ClockSettings.load(this, surface)
        clock = findViewById(R.id.clock)
        controls = findViewById(R.id.controls)
        list = findViewById(R.id.control_list)

        findViewById<View>(R.id.root).setOnApplyWindowInsetsListener { v, insets ->
            val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        showPreview()
        clock.setOnClickListener { setControlsVisible(!controlsVisible) }

        window.insetsController?.let { c ->
            val mask = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
            c.setSystemBarsAppearance(if (ClockRenderer.isNight(this)) 0 else mask, mask)
            c.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        buildControls()
        applyKeepScreenOn()
        setControlsVisible(savedInstanceState?.getBoolean(KEY_CONTROLS, true) ?: true)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_CONTROLS, controlsVisible)
    }

    private fun setControlsVisible(visible: Boolean) {
        controlsVisible = visible
        controls.visibility = if (visible) View.VISIBLE else View.GONE
        window.insetsController?.let {
            if (visible) it.show(WindowInsets.Type.systemBars()) else it.hide(WindowInsets.Type.systemBars())
        }
    }

    private fun update(next: ClockSettings) {
        val relabel = next.vertical != settings.vertical
        settings = next
        settings.save(this, surface)
        showPreview()
        applyKeepScreenOn()
        BinaryClockWidget.updateAll(this)
        // The "Hours on ..." label depends on the layout; rebuild after the toggle finishes.
        if (relabel) list.post { buildControls() }
    }

    /** Shows the clock the way the selected place will draw it. */
    private fun showPreview() {
        clock.settings = settings
        clock.showSeconds = surface.showsSeconds(settings)
        val dark = surface.forcesDark(settings)
        clock.forceDark = dark
        val black = (surface == Surface.DREAM && settings.dreamBlack) || (surface.isWallpaper && settings.wallpaperBlack)
        clock.setBackgroundColor(
            when {
                black -> Color.BLACK
                dark -> ClockRenderer.DARK.panel
                else -> Color.TRANSPARENT
            },
        )
    }

    private fun selectSurface(next: Surface) {
        if (next == surface) return
        surface = next
        getSharedPreferences("binclock", MODE_PRIVATE).edit().putString("editing", next.id).apply()
        settings = ClockSettings.load(this, surface)
        showPreview()
        buildControls()
    }

    // Keep-screen-on only applies to this app screen, whichever place is being edited.
    private fun applyKeepScreenOn() {
        if (ClockSettings.load(this, Surface.APP).keepScreenOn) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // ---- Settings list ------------------------------------------------------

    private fun buildControls() {
        list.removeAllViews()
        note(getString(R.string.hint_tap))

        header("Editing settings for")
        surfacePicker()
        note(getString(surfaceNote(surface)))
        button("Copy settings from another place\u2026") { copyFromDialog() }

        header("Display")
        toggle("Show values", settings.showValues) { settings.copy(showValues = it) }
        toggle("Show digits", settings.showDigits) { settings.copy(showDigits = it) }
        toggle("Shapes", settings.shapes) { settings.copy(shapes = it) }
        toggle("Dim smaller bits", settings.fade) { settings.copy(fade = it) }
        toggle("12-hour time", settings.h12) { settings.copy(h12 = it) }
        toggle("Vertical columns", settings.vertical) { settings.copy(vertical = it) }
        toggle("Flip bit order", settings.flip) { settings.copy(flip = it) }
        toggle(
            if (settings.vertical) "Hours on right" else "Hours on bottom", settings.reverse,
        ) { settings.copy(reverse = it) }

        header("Colors")
        swatchRow("hour", { it.colorHour }) { s, id -> s.copy(colorHour = id) }
        swatchRow("min", { it.colorMin }) { s, id -> s.copy(colorMin = id) }
        if (!surface.isWidget) swatchRow("sec", { it.colorSec }) { s, id -> s.copy(colorSec = id) }

        when {
            surface == Surface.APP -> {
                header("Screen")
                toggle("Keep screen on", settings.keepScreenOn) { settings.copy(keepScreenOn = it) }
            }
            surface.isWidget -> {
                header("Widget")
                toggle("Widget background", settings.widgetBackground) { settings.copy(widgetBackground = it) }
                button("Add widget to home screen") { pinWidget() }
            }
            surface.isWallpaper -> {
                header("Wallpaper")
                toggle("Show seconds", settings.wallpaperSeconds) { settings.copy(wallpaperSeconds = it) }
                toggle("Black background", settings.wallpaperBlack) { settings.copy(wallpaperBlack = it) }
                button("Set as live wallpaper") { openWallpaperPicker() }
            }
            surface == Surface.DREAM -> {
                header("Screen saver")
                toggle("Black background", settings.dreamBlack) { settings.copy(dreamBlack = it) }
                button("Open screen saver settings") { openDreamSettings() }
            }
        }
    }

    private fun surfaceNote(s: Surface) = when (s) {
        Surface.APP -> R.string.note_app
        Surface.WIDGET -> R.string.note_widget
        Surface.WALLPAPER_HOME -> R.string.note_wallpaper_home
        Surface.WALLPAPER_LOCK -> R.string.note_wallpaper_lock
        Surface.DREAM -> R.string.note_dream
    }

    private fun surfacePicker() {
        val spinner = Spinner(this)
        spinner.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item, Surface.entries.map { it.label },
        )
        spinner.setSelection(surface.ordinal, false)
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, v: View?, position: Int, id: Long) {
                val next = Surface.entries[position]
                if (next != surface) list.post { selectSurface(next) }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        list.addView(spinner, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48f)))
    }

    private fun copyFromDialog() {
        val others = Surface.entries.filter { it != surface }
        AlertDialog.Builder(this)
            .setTitle("Copy to ${surface.label} from\u2026")
            .setItems(others.map { it.label }.toTypedArray()) { _, i ->
                ClockSettings.copy(this, others[i], surface)
                settings = ClockSettings.load(this, surface)
                showPreview()
                BinaryClockWidget.updateAll(this)
                buildControls()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun dp(v: Float) =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics).toInt()

    private fun header(title: String) {
        list.addView(TextView(this).apply {
            text = title
            setTextColor(getColor(R.color.accent))
            textSize = 14f
            letterSpacing = 0.04f
            setPadding(0, dp(20f), 0, dp(4f))
        })
    }

    private fun note(message: String) {
        list.addView(TextView(this).apply {
            text = message
            setTextColor(getColor(R.color.muted))
            textSize = 14f
            setPadding(0, dp(8f), 0, dp(4f))
        })
    }

    private fun toggle(title: String, checked: Boolean, change: (Boolean) -> ClockSettings) {
        list.addView(Switch(this).apply {
            text = title
            textSize = 16f
            setTextColor(getColor(R.color.ink))
            isChecked = checked
            minHeight = dp(48f)
            setOnCheckedChangeListener { _, value -> update(change(value)) }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    private fun button(title: String, action: () -> Unit) {
        list.addView(Button(this).apply {
            text = title
            isAllCaps = false
            setOnClickListener { action() }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(4f)
        })
    }

    private fun swatchRow(
        label: String,
        current: (ClockSettings) -> String,
        change: (ClockSettings, String) -> ClockSettings,
    ) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(4f), 0, dp(4f))
        }
        row.addView(TextView(this).apply {
            text = label
            textSize = 15f
            setTextColor(getColor(R.color.muted))
        }, LinearLayout.LayoutParams(dp(44f), ViewGroup.LayoutParams.WRAP_CONTENT))

        val palette = ClockRenderer.palette(this)
        val chips = ClockRenderer.SWATCHES.map { sw ->
            View(this).apply { contentDescription = "$label color: ${sw.name}" }
        }
        fun refresh() {
            chips.forEachIndexed { i, chip ->
                val sw = ClockRenderer.SWATCHES[i]
                val selected = sw.id == current(settings)
                chip.background = swatchDrawable(sw.color ?: palette.on, selected)
                chip.isSelected = selected
                chip.stateDescription = if (selected) "selected" else null
            }
        }
        chips.forEachIndexed { i, chip ->
            chip.setOnClickListener {
                update(change(settings, ClockRenderer.SWATCHES[i].id))
                refresh()
            }
            row.addView(chip, LinearLayout.LayoutParams(dp(36f), dp(36f)).apply { marginEnd = dp(1f) })
        }
        refresh()
        list.addView(row)
    }

    private fun swatchDrawable(color: Int, selected: Boolean): Drawable {
        val dot = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
        }
        if (!selected) return InsetDrawable(dot, dp(5f))
        val ring = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.TRANSPARENT)
            setStroke(dp(2f), getColor(R.color.ink))
        }
        return LayerDrawable(arrayOf(ring, InsetDrawable(dot, dp(5f))))
    }

    // ---- Shortcuts into the system ---------------------------------------------

    private fun pinWidget() {
        val manager = getSystemService(AppWidgetManager::class.java)
        if (manager != null && manager.isRequestPinAppWidgetSupported) {
            manager.requestPinAppWidget(ComponentName(this, BinaryClockWidget::class.java), null, null)
        } else {
            Toast.makeText(this, R.string.widget_manual, Toast.LENGTH_LONG).show()
        }
    }

    private fun openWallpaperPicker() {
        try {
            startActivity(
                Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).putExtra(
                    WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                    ComponentName(this, BinaryClockWallpaper::class.java),
                ),
            )
        } catch (e: ActivityNotFoundException) {
            startActivity(Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER))
        }
    }

    private fun openDreamSettings() {
        try {
            startActivity(Intent(Settings.ACTION_DREAM_SETTINGS))
        } catch (e: ActivityNotFoundException) {
            startActivity(Intent(Settings.ACTION_DISPLAY_SETTINGS))
        }
    }

    private companion object {
        const val KEY_CONTROLS = "controls"
    }
}
