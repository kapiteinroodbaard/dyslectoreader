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

/** A band of the blur zone. Its only job is a faint tint; the blur comes from the window params. */
class BlurBandView(context: Context) : View(context)

/**
 * Fallback for when cross-window blur is disabled (battery saver, device setting):
 * a scrim that fades from opaque at the line edge to transparent at the far edge.
 */
class ScrimView(context: Context, private val fadeDownward: Boolean) : View(context) {

    private val paint = Paint()
    var maxAlpha: Int = 160
        set(value) {
            field = value
            shaderDirty = true
            invalidate()
        }
    private var shaderDirty = true

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        shaderDirty = true
    }

    override fun onDraw(canvas: Canvas) {
        if (shaderDirty && height > 0) {
            val solid = Color.argb(maxAlpha, 30, 30, 30)
            val (c0, c1) = if (fadeDownward) solid to Color.TRANSPARENT else Color.TRANSPARENT to solid
            paint.shader = LinearGradient(
                0f, 0f, 0f, height.toFloat(), c0, c1, Shader.TileMode.CLAMP
            )
            shaderDirty = false
        }
        canvas.drawPaint(paint)
    }
}

/** Solid ruler line (only shown when the user enables visible lines). */
class LineView(context: Context) : View(context)

/**
 * Circular drag handle. [kind] selects the glyph: MOVE (up/down arrows) or RESIZE (out/in arrows).
 * Reports vertical drag deltas in raw screen pixels.
 */
@SuppressLint("ViewConstructor")
class HandleView(
    context: Context,
    private val kind: Kind,
    private val onDragDelta: (deltaYPx: Float) -> Unit,
    private val onDragEnd: () -> Unit,
) : View(context) {

    enum class Kind { MOVE, RESIZE }

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
        }
        canvas.drawPath(path, glyphPaint)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
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
}
