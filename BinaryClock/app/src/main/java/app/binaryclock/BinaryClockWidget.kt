package app.binaryclock

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import android.widget.RemoteViews
import java.time.LocalTime
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Hours + minutes widget for the home screen and the lock screen widget page.
 *
 * Widgets can't animate, so the clock is drawn into a bitmap once a minute.
 * The minute alarm is a non-waking one: while the screen is off nothing runs,
 * and the first update after the phone wakes refreshes the time.
 */
class BinaryClockWidget : AppWidgetProvider() {

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_TICK,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> updateAll(context)
        }
    }

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { render(context, manager, it) }
        scheduleNextTick(context)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context, manager: AppWidgetManager, appWidgetId: Int, newOptions: Bundle,
    ) {
        render(context, manager, appWidgetId)
    }

    override fun onDisabled(context: Context) {
        cancelTicks(context)
    }

    companion object {
        private const val ACTION_TICK = "app.binaryclock.action.TICK"

        /** Redraws every placed widget; called on ticks and whenever settings change. */
        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, BinaryClockWidget::class.java))
            if (ids.isEmpty()) {
                cancelTicks(context)
                return
            }
            ids.forEach { render(context, manager, it) }
            scheduleNextTick(context)
        }

        private fun render(context: Context, manager: AppWidgetManager, id: Int) {
            // Size the bitmap to the widget's actual size (portrait width x portrait height).
            val opts = manager.getAppWidgetOptions(id)
            // The same widget gets its own settings when the lock screen is hosting it.
            val onLock = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_HOST_CATEGORY, -1) ==
                AppWidgetProviderInfo.WIDGET_CATEGORY_KEYGUARD
            val settings = ClockSettings.load(context, if (onLock) Surface.LOCK_WIDGET else Surface.WIDGET)
            val density = context.resources.displayMetrics.density
            val wDp = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH).takeIf { it > 0 } ?: 250
            val hDp = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT).takeIf { it > 0 } ?: 110
            var w = wDp * density
            var h = hDp * density
            val cap = 1200f / max(w, h)
            if (cap < 1f) {
                w *= cap
                h *= cap
            }
            val bitmap = Bitmap.createBitmap(
                max(1, w.roundToInt()), max(1, h.roundToInt()), Bitmap.Config.ARGB_8888,
            )
            val now = LocalTime.now()
            ClockRenderer.draw(
                Canvas(bitmap), bitmap.width.toFloat(), bitmap.height.toFloat(), settings,
                ClockRenderer.palette(context), now, seconds = false,
                padding = min(bitmap.width, bitmap.height) * 0.1f,
            )

            val views = RemoteViews(context.packageName, R.layout.widget_clock)
            views.setImageViewBitmap(R.id.widget_image, bitmap)
            views.setContentDescription(R.id.widget_image, ClockRenderer.spoken(now, settings))
            views.setInt(
                android.R.id.background, "setBackgroundResource",
                if (settings.widgetBackground) R.drawable.widget_bg else 0,
            )
            val open = PendingIntent.getActivity(
                context, 0,
                Intent(context, ClockActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            views.setOnClickPendingIntent(android.R.id.background, open)
            manager.updateAppWidget(id, views)
        }

        private fun tickIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
            context, 1,
            Intent(context, BinaryClockWidget::class.java).setAction(ACTION_TICK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        private fun scheduleNextTick(context: Context) {
            val alarms = context.getSystemService(AlarmManager::class.java) ?: return
            val now = System.currentTimeMillis()
            val next = now - now % 60_000 + 60_000 + 50
            // RTC (not RTC_WAKEUP): never wakes the phone just to redraw a clock.
            if (alarms.canScheduleExactAlarms()) {
                alarms.setExact(AlarmManager.RTC, next, tickIntent(context))
            } else {
                alarms.setWindow(AlarmManager.RTC, next, 5_000, tickIntent(context))
            }
        }

        private fun cancelTicks(context: Context) {
            context.getSystemService(AlarmManager::class.java)?.cancel(tickIntent(context))
        }
    }
}
