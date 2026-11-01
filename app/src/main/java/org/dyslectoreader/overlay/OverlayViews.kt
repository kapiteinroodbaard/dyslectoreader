package org.dyslectoreader.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.view.MotionEvent
import android.view.View

/**
 * One masking zone of the ruler: a scrim that fades from opaque at the reading-line
 * edge to transparent at the far edge, plus (optionally) the visible ruler line drawn
 * at the inner edge. Line and gradient live in one view so the whole zone is a single
 * window — keeping the per-window touch-obscuring opacity under the system limit.
 */
class ZoneView(context: Context, private val fadeDownward: Boolean) : View(context) {

    private val gradientPaint = Paint()
    private val linePaint = Paint()

    var maxAlpha: Int = 160
        set(value) {
            if (field == value) return
            field = value
            shaderDirty = true
            invalidate()
        }
    /** Fraction of the zone (from the inner edge) kept fully dark before the fade starts. */
    var solidFraction: Float = 0f
        set(value) {
            if (field == value) return
            field = value
            shaderDirty = true
            invalidate()
        }
    var lineVisible: Boolean = false
        set(value) { field = value; invalidate() }
    var lineColor: Int = Color.WHITE
        set(value) { field = value; invalidate() }
    var lineThicknessPx: Float = 0f
        set(value) { field = value; invalidate() }

    private var shaderDirty = true

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        shaderDirty = true
    }

    override fun onDraw(canvas: Canvas) {
        if (shaderDirty && height > 0) {
            val solid = Color.argb(maxAlpha, 15, 15, 15)
            // Hold fully dark for solidFraction, then an ease-out fade (still 3/4 dark halfway)
            // so the zone stays dense longer than a plain linear ramp.
            val hold = solidFraction.coerceIn(0f, 0.9f)
            val mid = Color.argb(maxAlpha * 3 / 4, 15, 15, 15)
            val colors = intArrayOf(solid, solid, mid, Color.TRANSPARENT)
            val stops = floatArrayOf(0f, hold, hold + (1f - hold) / 2f, 1f)
            val (c, pos) = if (fadeDownward) colors to stops
            else colors.reversedArray() to stops.map { 1f - it }.reversed().toFloatArray()
            gradientPaint.shader = LinearGradient(
                0f, 0f, 0f, height.toFloat(), c, pos, Shader.TileMode.CLAMP
            )
            shaderDirty = false
        }
        canvas.drawPaint(gradientPaint)

        if (lineVisible && lineThicknessPx > 0f) {
            linePaint.color = lineColor
            // fadeDownward means this zone sits below the gap; its inner edge is the top.
            val top = if (fadeDownward) 0f else height - lineThicknessPx
            canvas.drawRect(0f, top, width.toFloat(), top + lineThicknessPx, linePaint)
        }
    }
}

/**
 * Circular overlay control. [kind] selects the glyph and the gesture: MOVE (up/down arrows)
 * and RESIZE (out/in arrows) report vertical drag deltas in raw screen pixels, CLOSE (an X)
 * is a plain tap that invokes [onTap].
 */
@SuppressLint("ViewConstructor")
class HandleView(
    context: Context,
    private val kind: Kind,
    private val onDragDelta: (deltaYPx: Float) -> Unit = {},
    private val onDragEnd: () -> Unit = {},
    private val onTap: () -> Unit = {},
) : View(context) {

    enum class Kind { MOVE, RESIZE, CLOSE }

    private val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(210, 40, 40, 46)
    }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.argb(230, 255, 255, 255)
    }
    private val glyphPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = Color.WHITE
    }
    private val path = Path()
    private var lastRawY = 0f
    private var pressed = false

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val r = width / 2f - 2f
        ringPaint.strokeWidth = width * 0.05f
        glyphPaint.strokeWidth = width * 0.07f
        circlePaint.alpha = if (pressed) 255 else 210
        canvas.drawCircle(cx, cy, r, circlePaint)
        canvas.drawCircle(cx, cy, r, ringPaint)

        val a = width * 0.16f // arrow half-width
        val inner = width * 0.10f
        val outer = width * 0.30f
        path.reset()
        when (kind) {
            Kind.MOVE -> {
                // chevron up + chevron down
                path.moveTo(cx - a, cy - inner); path.lineTo(cx, cy - outer); path.lineTo(cx + a, cy - inner)
                path.moveTo(cx - a, cy + inner); path.lineTo(cx, cy + outer); path.lineTo(cx + a, cy + inner)
            }
            Kind.RESIZE -> {
                // chevrons pointing away from each other with a bar in the middle
                path.moveTo(cx - a, cy - outer + a); path.lineTo(cx, cy - outer); path.lineTo(cx + a, cy - outer + a)
                path.moveTo(cx - a, cy + outer - a); path.lineTo(cx, cy + outer); path.lineTo(cx + a, cy + outer - a)
                path.moveTo(cx - a, cy); path.lineTo(cx + a, cy)
            }
            Kind.CLOSE -> {
                val d = width * 0.20f
                path.moveTo(cx - d, cy - d); path.lineTo(cx + d, cy + d)
                path.moveTo(cx + d, cy - d); path.lineTo(cx - d, cy + d)
            }
        }
        canvas.drawPath(path, glyphPaint)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (kind == Kind.CLOSE) return onTapEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastRawY = event.rawY
                pressed = true
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val delta = event.rawY - lastRawY
                lastRawY = event.rawY
                if (delta != 0f) onDragDelta(delta)
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                pressed = false
                invalidate()
                onDragEnd()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun onTapEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pressed = true
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP -> {
                val inside = event.x >= 0 && event.y >= 0 && event.x <= width && event.y <= height
                pressed = false
                invalidate()
                if (inside) onTap()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                pressed = false
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}
