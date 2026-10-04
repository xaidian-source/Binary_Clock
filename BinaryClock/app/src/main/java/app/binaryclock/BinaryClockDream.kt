package app.binaryclock

import android.graphics.Color
import android.service.dreams.DreamService
import android.view.ViewGroup
import android.widget.FrameLayout
import kotlin.math.min
import kotlin.random.Random

/**
 * Screen saver: the full clock with seconds, shown while charging or docked.
 * Always dark and dimmed, and it drifts a few pixels each minute so a long
 * night on the charger doesn't burn the lamp pattern into the screen.
 */
class BinaryClockDream : DreamService() {

    private var clock: BinaryClockView? = null

    private val drift = object : Runnable {
        override fun run() {
            val v = clock ?: return
            val range = min(v.width, v.height) * 0.04f
            v.translationX = (Random.nextFloat() * 2 - 1) * range
            v.translationY = (Random.nextFloat() * 2 - 1) * range
            v.postDelayed(this, 60_000)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = false   // any touch wakes the phone
        isFullscreen = true
        isScreenBright = false  // dimmed, for the nightstand

        val settings = ClockSettings.load(this)
        val view = BinaryClockView(this).apply {
            this.settings = settings
            forceDark = true
            showSeconds = true
            maxLamp = 140f * resources.displayMetrics.density
        }
        val frame = FrameLayout(this).apply {
            setBackgroundColor(if (settings.dreamBlack) Color.BLACK else ClockRenderer.DARK.panel)
            addView(
                view,
                FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
            )
        }
        clock = view
        setContentView(frame)
    }

    override fun onDreamingStarted() {
        super.onDreamingStarted()
        clock?.postDelayed(drift, 60_000)
    }

    override fun onDreamingStopped() {
        clock?.removeCallbacks(drift)
        super.onDreamingStopped()
    }

    override fun onDetachedFromWindow() {
        clock = null
        super.onDetachedFromWindow()
    }
}
