package app.binaryclock

import android.app.WallpaperManager
import android.graphics.Color
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import java.time.LocalTime
import kotlin.math.min

/**
 * Live wallpaper for the home and lock screens. Draws once a second while the
 * screen is visible and stops completely when it isn't. On the lock screen the
 * clock sits in the lower part, below the system clock.
 */
class BinaryClockWallpaper : WallpaperService() {

    override fun onCreateEngine(): Engine = ClockEngine()

    private inner class ClockEngine : Engine() {
        private val handler = Handler(Looper.getMainLooper())
        private var visible = false
        private var width = 0
        private var height = 0

        private val ticker = object : Runnable {
            override fun run() {
                drawFrame()
                if (visible) {
                    val step = if (currentSettings().wallpaperSeconds) 1000L else 60_000L
                    val now = System.currentTimeMillis()
                    handler.postDelayed(this, step - now % step + 8)
                }
            }
        }

        override fun onVisibilityChanged(visible: Boolean) {
            this.visible = visible
            handler.removeCallbacks(ticker)
            if (visible) handler.post(ticker)
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, w: Int, h: Int) {
            width = w
            height = h
            drawFrame()
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            visible = false
            handler.removeCallbacks(ticker)
        }

        override fun onDestroy() {
            visible = false
            handler.removeCallbacks(ticker)
        }

        /** Engine.getWallpaperFlags() needs Android 14; older phones treat everything as home. */
        private fun isLockOnly(): Boolean {
            if (isPreview || Build.VERSION.SDK_INT < 34) return false
            val flags = wallpaperFlags
            return (flags and WallpaperManager.FLAG_LOCK) != 0 && (flags and WallpaperManager.FLAG_SYSTEM) == 0
        }

        private fun currentSettings() = ClockSettings.load(
            this@BinaryClockWallpaper,
            if (isLockOnly()) Surface.WALLPAPER_LOCK else Surface.WALLPAPER_HOME,
        )

        private fun drawFrame() {
            if (width == 0 || height == 0) return
            val holder = surfaceHolder
            // Software canvas: the lamp glow is a shadow layer, which hardware canvases skip.
            val canvas = try { holder.lockCanvas() } catch (e: Exception) { null } ?: return
            try {
                val ctx = this@BinaryClockWallpaper
                val onLock = isLockOnly()
                val settings = currentSettings()
                val palette = ClockRenderer.palette(ctx, forceDark = settings.wallpaperBlack)
                canvas.drawColor(if (settings.wallpaperBlack) Color.BLACK else palette.panel)

                val area = if (onLock) Rect(0, (height * 0.42f).toInt(), width, (height * 0.92f).toInt())
                else Rect(0, 0, width, height)
                val density = ctx.resources.displayMetrics.density
                canvas.save()
                canvas.translate(area.left.toFloat(), area.top.toFloat())
                ClockRenderer.draw(
                    canvas, area.width().toFloat(), area.height().toFloat(), settings, palette,
                    LocalTime.now(), seconds = settings.wallpaperSeconds,
                    maxLamp = 96f * density, padding = min(width, height) * 0.08f,
                )
                canvas.restore()
            } finally {
                holder.unlockCanvasAndPost(canvas)
            }
        }
    }
}
