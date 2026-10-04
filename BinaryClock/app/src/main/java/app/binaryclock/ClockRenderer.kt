package app.binaryclock

import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Typeface
import java.time.LocalTime
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

class Palette(
    val panel: Int,
    val ink: Int,
    val muted: Int,
    val off: Int,
    val offRim: Int,
    val on: Int,
)

class Swatch(val id: String, val name: String, val color: Int?)

/**
 * Draws the binary clock onto any Canvas. The app screen, the screen saver and
 * the widget bitmap all go through here, so they always look the same.
 * Callers paint their own background; this only draws labels, lamps and text.
 */
object ClockRenderer {

    val LIGHT = Palette(
        panel = 0xFFCDD5D8.toInt(), ink = 0xFF1F2B31.toInt(), muted = 0xFF5D6C73.toInt(),
        off = 0xFFB3BEC3.toInt(), offRim = 0xFF98A6AC.toInt(), on = 0xFFE4570F.toInt(),
    )
    val DARK = Palette(
        panel = 0xFF16232A.toInt(), ink = 0xFFD9E2E5.toInt(), muted = 0xFF7D8F97.toInt(),
        off = 0xFF22333B.toInt(), offRim = 0xFF2D4049.toInt(), on = 0xFFFFAD33.toInt(),
    )

    val SWATCHES = listOf(
        Swatch("default", "Default", null),
        Swatch("red", "Red", 0xFFF0433A.toInt()),
        Swatch("yellow", "Yellow", 0xFFF2B705.toInt()),
        Swatch("green", "Green", 0xFF22C55E.toInt()),
        Swatch("cyan", "Cyan", 0xFF06B6D4.toInt()),
        Swatch("blue", "Blue", 0xFF3B82F6.toInt()),
        Swatch("violet", "Violet", 0xFF8B5CF6.toInt()),
        Swatch("pink", "Pink", 0xFFEC4899.toInt()),
    )

    fun isNight(context: Context): Boolean =
        (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES

    fun palette(context: Context, forceDark: Boolean = false): Palette =
        if (forceDark || isNight(context)) DARK else LIGHT

    fun swatchColor(id: String, p: Palette): Int =
        SWATCHES.firstOrNull { it.id == id }?.color ?: p.on

    // ---- Text helpers -------------------------------------------------------

    private fun hour12(h24: Int) = (h24 % 12).let { if (it == 0) 12 else it }
    private fun two(n: Int) = n.toString().padStart(2, '0')

    fun digits(t: LocalTime, s: ClockSettings, seconds: Boolean): String {
        val sec = if (seconds) ":" + two(t.second) else ""
        return if (s.h12) {
            "${hour12(t.hour)}:${two(t.minute)}$sec ${if (t.hour < 12) "am" else "pm"}"
        } else {
            "${two(t.hour)}:${two(t.minute)}$sec"
        }
    }

    fun spoken(t: LocalTime, s: ClockSettings) = "Binary clock showing " + digits(t, s, false)

    // ---- Lamp shapes (shape mode: 1 circle, 2 triangle ... 32 octagon) -------

    private class LampShape(val pts: FloatArray?, val ox: Float, val oy: Float) {
        /** Distance from the shape's center to the farthest box corner, in lamp units. */
        val reach: Float = listOf(
            hypot(ox, oy), hypot(1 - ox, oy), hypot(ox, 1 - oy), hypot(1 - ox, 1 - oy),
        ).max()
    }

    /** Regular polygon fitted into a unit box, plus where its center lands. */
    private fun poly(n: Int, rotDeg: Double): LampShape {
        val xs = DoubleArray(n) { cos(Math.toRadians(-90.0 + rotDeg + it * 360.0 / n)) }
        val ys = DoubleArray(n) { sin(Math.toRadians(-90.0 + rotDeg + it * 360.0 / n)) }
        val w = xs.max() - xs.min()
        val h = ys.max() - ys.min()
        val sc = 1.0 / max(w, h)
        val ox = (1 - w * sc) / 2 - xs.min() * sc
        val oy = (1 - h * sc) / 2 - ys.min() * sc
        val pts = FloatArray(n * 2) { i ->
            if (i % 2 == 0) (xs[i / 2] * sc + ox).toFloat() else (ys[i / 2] * sc + oy).toFloat()
        }
        return LampShape(pts, ox.toFloat(), oy.toFloat())
    }

    private val SHAPES = arrayOf(
        LampShape(null, 0.5f, 0.5f),
        poly(3, 0.0), poly(4, 45.0), poly(5, 0.0), poly(6, 0.0), poly(8, 22.5),
    )

    // ---- Layout, all in multiples of one lamp's diameter ----------------------

    private const val GAP_X = 0.22f
    private const val GAP_Y = 0.34f
    private const val LABEL_FONT = 0.30f
    private const val VALUE_FONT = 0.20f
    private const val VALUE_GAP = 0.08f
    private const val DIGITS_FONT = 0.70f
    private const val DIGITS_GAP = 0.40f
    private const val RING = 0.06f
    private const val INNER = 0.82f
    /** Converts a CSS blur length into Android's shadow-layer radius. */
    private const val BLUR = 0.87f

    private val condensed: Typeface =
        Typeface.create(Typeface.create("sans-serif-condensed", Typeface.NORMAL), 500, false)
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = condensed
        fontFeatureSettings = "tnum"
    }
    private val path = Path()

    /** Widest row label relative to font size, so am/pm switching never shifts the grid. */
    private val labelWidthFactor: Float by lazy {
        text.textSize = 100f
        listOf("hour", "min", "sec", "am", "pm").maxOf { text.measureText(it) } / 100f
    }

    private class Row(val label: String, val value: Int, val bits: Int, val colorId: String)

    // Vertical mode only
    private const val COL_GAP = 0.45f
    private const val AXIS_GAP = 0.22f
    private const val LABEL_GAP = 0.25f

    /** Width of "32" relative to font size, for the shared value scale in vertical mode. */
    private val valueWidthFactor: Float by lazy {
        text.textSize = 100f
        text.measureText("32") / 100f
    }

    fun draw(
        canvas: Canvas,
        width: Float,
        height: Float,
        s: ClockSettings,
        p: Palette,
        t: LocalTime,
        seconds: Boolean,
        maxLamp: Float = Float.MAX_VALUE,
        padding: Float = 0f,
    ) {
        val rows = ArrayList<Row>(3)
        rows += if (s.h12) {
            Row(if (t.hour < 12) "am" else "pm", hour12(t.hour), 4, s.colorHour)
        } else {
            Row("hour", t.hour, 5, s.colorHour)
        }
        rows += Row("min", t.minute, 6, s.colorMin)
        if (seconds) rows += Row("sec", t.second, 6, s.colorSec)
        // Hours last: bottom row in row mode, right column in vertical mode.
        if (s.reverse) rows.reverse()

        if (s.vertical) drawVertical(canvas, width, height, s, p, t, seconds, rows, maxLamp, padding)
        else drawHorizontal(canvas, width, height, s, p, t, seconds, rows, maxLamp, padding)
    }

    /** Rows side by side: hour, min and sec each on their own line. */
    private fun drawHorizontal(
        canvas: Canvas, width: Float, height: Float, s: ClockSettings, p: Palette,
        t: LocalTime, seconds: Boolean, rows: List<Row>, maxLamp: Float, padding: Float,
    ) {
        val labelU = LABEL_FONT * (labelWidthFactor + 0.35f)
        val valuesU = if (s.showValues) VALUE_GAP + VALUE_FONT else 0f
        val digitsU = if (s.showDigits) DIGITS_GAP + DIGITS_FONT else 0f
        val widthU = labelU + 6 * (1 + GAP_X)
        val n = rows.size
        val gridU = n * (1 + valuesU) + (n - 1) * GAP_Y
        val heightU = gridU + digitsU

        val lamp = minOf((width - 2 * padding) / widthU, (height - 2 * padding) / heightU, maxLamp)
        if (lamp <= 1f) return
        val x0 = (width - lamp * widthU) / 2
        val y0 = (height - lamp * heightU) / 2

        rows.forEachIndexed { ri, row ->
            val top = y0 + ri * lamp * (1 + valuesU + GAP_Y)

            text.textSize = LABEL_FONT * lamp
            text.color = p.muted
            text.textAlign = Paint.Align.RIGHT
            canvas.drawText(
                row.label,
                x0 + labelU * lamp - 0.35f * text.textSize,
                top + lamp / 2 - (text.descent() + text.ascent()) / 2,
                text,
            )

            for (col in 0 until 6) {
                // Default: least significant bit on the right. Flipped: on the left.
                val bit = if (s.flip) col else 5 - col
                if (bit >= row.bits) continue
                val left = x0 + lamp * (labelU + GAP_X + col * (1 + GAP_X))
                drawBit(canvas, left, top, lamp, bit, row, s, p)

                if (s.showValues) {
                    text.textSize = VALUE_FONT * lamp
                    text.color = p.muted
                    text.textAlign = Paint.Align.CENTER
                    canvas.drawText(
                        (1 shl bit).toString(),
                        left + lamp / 2,
                        top + lamp + VALUE_GAP * lamp - text.ascent(),
                        text,
                    )
                }
            }
        }

        if (s.showDigits) drawDigits(canvas, width, y0 + gridU * lamp, lamp, s, p, t, seconds)
    }

    /**
     * Rows turned into columns: hour, min and sec stand next to each other,
     * with labels underneath. Values rise upward (or downward when flipped),
     * so one shared scale on the left labels every column.
     */
    private fun drawVertical(
        canvas: Canvas, width: Float, height: Float, s: ClockSettings, p: Palette,
        t: LocalTime, seconds: Boolean, rows: List<Row>, maxLamp: Float, padding: Float,
    ) {
        val n = rows.size
        val axisU = if (s.showValues) VALUE_FONT * valueWidthFactor + AXIS_GAP else 0f
        val labelsU = LABEL_GAP + LABEL_FONT
        val digitsU = if (s.showDigits) DIGITS_GAP + DIGITS_FONT else 0f
        val widthU = axisU + n + (n - 1) * COL_GAP
        val gridU = 6 + 5 * GAP_X
        val heightU = gridU + labelsU + digitsU

        val lamp = minOf((width - 2 * padding) / widthU, (height - 2 * padding) / heightU, maxLamp)
        if (lamp <= 1f) return
        val x0 = (width - lamp * widthU) / 2
        val y0 = (height - lamp * heightU) / 2
        fun columnLeft(ci: Int) = x0 + lamp * (axisU + ci * (1 + COL_GAP))

        for (slot in 0 until 6) {
            // Default: least significant bit at the bottom. Flipped: at the top.
            val bit = if (s.flip) slot else 5 - slot
            val top = y0 + slot * lamp * (1 + GAP_X)

            if (s.showValues) {
                text.textSize = VALUE_FONT * lamp
                text.color = p.muted
                text.textAlign = Paint.Align.RIGHT
                canvas.drawText(
                    (1 shl bit).toString(),
                    x0 + (axisU - AXIS_GAP) * lamp,
                    top + lamp / 2 - (text.descent() + text.ascent()) / 2,
                    text,
                )
            }
            rows.forEachIndexed { ci, row ->
                if (bit < row.bits) drawBit(canvas, columnLeft(ci), top, lamp, bit, row, s, p)
            }
        }

        text.textSize = LABEL_FONT * lamp
        text.color = p.muted
        text.textAlign = Paint.Align.CENTER
        rows.forEachIndexed { ci, row ->
            canvas.drawText(
                row.label,
                columnLeft(ci) + lamp / 2,
                y0 + (gridU + LABEL_GAP) * lamp - text.ascent(),
                text,
            )
        }

        if (s.showDigits) drawDigits(canvas, width, y0 + (gridU + labelsU) * lamp, lamp, s, p, t, seconds)
    }

    private fun drawBit(
        c: Canvas, left: Float, top: Float, lamp: Float, bit: Int, row: Row, s: ClockSettings, p: Palette,
    ) {
        // "Dim smaller bits": 40% for the lowest bit up to 100% for the highest.
        val k = if (s.fade && row.bits > 1) 0.4f + 0.6f * bit / (row.bits - 1) else 1f
        val cc = mix(p.off, swatchColor(row.colorId, p), k)
        val on = (row.value shr bit) and 1 == 1
        if (s.shapes) drawShapeLamp(c, left, top, lamp, bit, on, cc, k, p)
        else drawRoundLamp(c, left, top, lamp, on, cc, k, p)
    }

    private fun drawDigits(
        c: Canvas, width: Float, gridBottom: Float, lamp: Float,
        s: ClockSettings, p: Palette, t: LocalTime, seconds: Boolean,
    ) {
        text.textSize = DIGITS_FONT * lamp
        text.color = p.ink
        text.textAlign = Paint.Align.CENTER
        c.drawText(digits(t, s, seconds), width / 2, gridBottom + DIGITS_GAP * lamp - text.ascent(), text)
    }

    private fun drawRoundLamp(
        c: Canvas, left: Float, top: Float, size: Float,
        on: Boolean, cc: Int, k: Float, p: Palette,
    ) {
        val cx = left + size / 2
        val cy = top + size / 2
        val r = size / 2
        val ring = size * RING
        stroke.strokeWidth = ring
        fill.shader = null

        if (!on) {
            fill.color = p.off
            c.drawCircle(cx, cy, r, fill)
            stroke.color = p.offRim
            c.drawCircle(cx, cy, r - ring / 2, stroke)
            return
        }
        // Glow
        fill.color = cc
        fill.setShadowLayer(size * 0.45f * k * BLUR, 0f, 0f, withAlpha(cc, 0.45f))
        c.drawCircle(cx, cy, r, fill)
        fill.clearShadowLayer()
        // Lit face with a bright core slightly above center
        val hi = mix(cc, Color.WHITE, 0.55f * k)
        fill.shader = RadialGradient(
            cx, top + size * 0.44f, size * 0.75f,
            intArrayOf(hi, hi, cc, cc), floatArrayOf(0f, 0.16f, 0.66f, 1f),
            Shader.TileMode.CLAMP,
        )
        c.drawCircle(cx, cy, r, fill)
        fill.shader = null
        stroke.color = cc
        c.drawCircle(cx, cy, r - ring / 2, stroke)
    }

    private fun drawShapeLamp(
        c: Canvas, left: Float, top: Float, size: Float, bit: Int,
        on: Boolean, cc: Int, k: Float, p: Palette,
    ) {
        val shape = SHAPES[bit]
        fill.shader = null

        // Outer outline (the rim)
        fill.color = if (on) cc else p.offRim
        if (on) fill.setShadowLayer(size * 0.18f * k * BLUR, 0f, 0f, withAlpha(cc, 0.55f))
        c.drawPath(shapePath(shape, left, top, size, 1f), fill)
        fill.clearShadowLayer()

        // Inner face, shrunk toward the shape's center
        if (on) {
            val hi = mix(cc, Color.WHITE, 0.55f * k)
            fill.shader = RadialGradient(
                left + size * shape.ox, top + size * shape.oy, size * shape.reach * INNER,
                intArrayOf(hi, hi, cc, cc), floatArrayOf(0f, 0.16f, 0.66f, 1f),
                Shader.TileMode.CLAMP,
            )
        } else {
            fill.color = p.off
        }
        c.drawPath(shapePath(shape, left, top, size, INNER), fill)
        fill.shader = null
    }

    private fun shapePath(shape: LampShape, left: Float, top: Float, size: Float, scale: Float): Path {
        path.reset()
        val pts = shape.pts
        if (pts == null) {
            path.addCircle(left + size * shape.ox, top + size * shape.oy, size / 2 * scale, Path.Direction.CW)
            return path
        }
        for (i in 0 until pts.size / 2) {
            val x = left + size * (shape.ox + (pts[2 * i] - shape.ox) * scale)
            val y = top + size * (shape.oy + (pts[2 * i + 1] - shape.oy) * scale)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        return path
    }

    // ---- Color helpers --------------------------------------------------------

    /** Blend from a toward b by t (0..1). */
    fun mix(a: Int, b: Int, t: Float): Int {
        val u = t.coerceIn(0f, 1f)
        fun ch(x: Int, y: Int) = (x + (y - x) * u).roundToInt()
        return Color.rgb(
            ch(Color.red(a), Color.red(b)),
            ch(Color.green(a), Color.green(b)),
            ch(Color.blue(a), Color.blue(b)),
        )
    }

    private fun withAlpha(c: Int, a: Float): Int = (c and 0x00FFFFFF) or ((a * 255).roundToInt() shl 24)
}
