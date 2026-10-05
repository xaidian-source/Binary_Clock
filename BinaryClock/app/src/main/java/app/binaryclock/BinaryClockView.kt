package app.binaryclock

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.View
import java.time.LocalTime

/** A ticking clock view. Redraws on every second boundary while visible. */
class BinaryClockView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    var settings: ClockSettings = ClockSettings.load(context, Surface.APP)
        set(value) {
            field = value
            spokenMinute = -1
            invalidate()
        }

    /** The screen saver always uses the dark palette. */
    var forceDark = false
        set(value) {
            field = value
            invalidate()
        }

    var showSeconds = true
        set(value) {
            field = value
            invalidate()
        }

    var maxLamp = 96f * resources.displayMetrics.density
    private val pad = 16f * resources.displayMetrics.density
    private var spokenMinute = -1

    private val ticker = object : Runnable {
        override fun run() {
            invalidate()
            val now = System.currentTimeMillis()
            postDelayed(this, 1000 - now % 1000 + 8)
        }
    }

    init {
        // The lamp glow uses a shadow layer, which hardware canvases only draw for text.
        setLayerType(LAYER_TYPE_SOFTWARE, null)
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        restartTicker()
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(ticker)
        super.onDetachedFromWindow()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility == VISIBLE) restartTicker() else removeCallbacks(ticker)
    }

    private fun restartTicker() {
        removeCallbacks(ticker)
        post(ticker)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val t = LocalTime.now()
        ClockRenderer.draw(
            canvas, width.toFloat(), height.toFloat(), settings,
            ClockRenderer.palette(context, forceDark), t, showSeconds, maxLamp, pad,
        )
        // Update the spoken description once a minute, not every second,
        // so TalkBack isn't flooded with changes.
        val minuteOfDay = t.hour * 60 + t.minute
        if (minuteOfDay != spokenMinute) {
            spokenMinute = minuteOfDay
            contentDescription = ClockRenderer.spoken(t, settings)
        }
    }
}
