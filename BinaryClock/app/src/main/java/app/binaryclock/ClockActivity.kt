package app.binaryclock

import android.app.Activity
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
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

/**
 * Full-screen clock with its settings underneath. Tap the clock to hide the
 * settings and the system bars; tap again to bring them back.
 */
class ClockActivity : Activity() {

    private lateinit var settings: ClockSettings
    private lateinit var clock: BinaryClockView
    private lateinit var controls: View
    private lateinit var list: LinearLayout
    private var controlsVisible = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setDecorFitsSystemWindows(false)
        setContentView(R.layout.activity_clock)

        settings = ClockSettings.load(this)
        clock = findViewById(R.id.clock)
        controls = findViewById(R.id.controls)
        list = findViewById(R.id.control_list)

        findViewById<View>(R.id.root).setOnApplyWindowInsetsListener { v, insets ->
            val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        clock.settings = settings
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
        settings = next
        settings.save(this)
        clock.settings = settings
        applyKeepScreenOn()
        BinaryClockWidget.updateAll(this)
    }

    private fun applyKeepScreenOn() {
        if (settings.keepScreenOn) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    // ---- Settings list ------------------------------------------------------

    private fun buildControls() {
        list.removeAllViews()
        note(getString(R.string.hint_tap))

        header("Display")
        toggle("Show values", settings.showValues) { settings.copy(showValues = it) }
        toggle("Show digits", settings.showDigits) { settings.copy(showDigits = it) }
        toggle("Shapes", settings.shapes) { settings.copy(shapes = it) }
        toggle("Dim smaller bits", settings.fade) { settings.copy(fade = it) }
        toggle("12-hour time", settings.h12) { settings.copy(h12 = it) }
        toggle("Vertical columns", settings.vertical) { settings.copy(vertical = it) }
        toggle("Flip bit order", settings.flip) { settings.copy(flip = it) }
        toggle("Keep screen on", settings.keepScreenOn) { settings.copy(keepScreenOn = it) }

        header("Colors")
        swatchRow("hour", { it.colorHour }) { s, id -> s.copy(colorHour = id) }
        swatchRow("min", { it.colorMin }) { s, id -> s.copy(colorMin = id) }
        swatchRow("sec", { it.colorSec }) { s, id -> s.copy(colorSec = id) }

        header("Widget")
        note(getString(R.string.widget_note))
        toggle("Widget background", settings.widgetBackground) { settings.copy(widgetBackground = it) }
        button("Add widget to home screen") { pinWidget() }

        header("Screen saver")
        note(getString(R.string.dream_note))
        toggle("Black background", settings.dreamBlack) { settings.copy(dreamBlack = it) }
        button("Open screen saver settings") { openDreamSettings() }
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
