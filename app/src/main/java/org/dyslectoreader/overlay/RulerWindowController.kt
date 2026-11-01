package org.dyslectoreader.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.hardware.input.InputManager
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import org.dyslectoreader.data.RulerSettings
import kotlin.math.roundToInt

/**
 * Owns all overlay windows that make up the ruler:
 *  - one gradient-scrim zone window below the reading gap (and one above when enabled),
 *    with the optional ruler line drawn inside the same window
 *  - three touchable handle windows (move / resize / close); everything else passes
 *    touches through.
 *
 * Real blur is deliberately not used: FLAG_BLUR_BEHIND blurs the whole screen behind the
 * window (gap included), and bounds-limited background blur isn't available to overlay
 * windows added via WindowManager. A gradient scrim gives the intended effect instead.
 *
 * Pass-through windows must keep their *window* alpha at or below the system's
 * maximum obscuring opacity (untrusted-touch rules, Android 12+); otherwise every
 * touch aimed at the app underneath is silently dropped.
 */
class RulerWindowController(
    private val context: Context,
    private val onPersistCenter: (Float) -> Unit,
    private val onPersistGap: (Float) -> Unit,
    private val onRequestClose: () -> Unit,
) {
    private val wm = context.getSystemService(WindowManager::class.java)

    /** Highest window alpha that still lets touches through to the app underneath. */
    private val maxPassThroughAlpha: Float =
        context.getSystemService(InputManager::class.java)
            .maximumObscuringOpacityForTouch
            .coerceIn(0.1f, 1f)

    private var settings = RulerSettings()
    private var attached = false

    // Live-drag state (may run ahead of the persisted settings while a finger is down)
    private var centerFraction = 0.5f
    private var gapDp = RulerSettings.DEFAULT_GAP_DP

    // Structural input the current window set was built for
    private var builtTopEnabled = true

    private data class Win(val view: View, val lp: WindowManager.LayoutParams)

    private var bottomZone: Win? = null
    private var topZone: Win? = null
    private var moveHandle: Win? = null
    private var resizeHandle: Win? = null
    private var closeHandle: Win? = null

    fun show(initial: RulerSettings) {
        settings = initial
        centerFraction = initial.centerYFraction
        gapDp = initial.gapHeightDp
        rebuild()
        attached = true
    }

    fun hide() {
        removeAll()
        attached = false
    }

    fun onSettingsChanged(s: RulerSettings) {
        settings = s
        centerFraction = s.centerYFraction
        gapDp = s.gapHeightDp
        if (!attached) return
        if (s.topEnabled != builtTopEnabled) rebuild()
        else applyGeometryAndStyle()
    }

    fun onDisplayChanged() {
        if (attached) applyGeometryAndStyle()
    }

    // ---------- window construction ----------

    private fun rebuild() {
        removeAll()
        builtTopEnabled = settings.topEnabled

        bottomZone = addZone(fadeDownward = true)
        if (settings.topEnabled) topZone = addZone(fadeDownward = false)
        moveHandle = addHandle(HandleView.Kind.MOVE)
        resizeHandle = addHandle(HandleView.Kind.RESIZE)
        closeHandle = addHandle(HandleView.Kind.CLOSE)
        applyGeometryAndStyle()
    }

    private fun addZone(fadeDownward: Boolean): Win {
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, 0,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            fitInsetsTypes = 0
            alpha = maxPassThroughAlpha
        }
        val v = ZoneView(context, fadeDownward)
        wm.addView(v, lp)
        return Win(v, lp)
    }

    private fun addHandle(kind: HandleView.Kind): Win {
        val sizePx = dp(HANDLE_SIZE_DP)
        val lp = WindowManager.LayoutParams(
            sizePx, sizePx,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            fitInsetsTypes = 0
        }
        val v = HandleView(
            context, kind,
            onDragDelta = { delta ->
                val screenH = screenHeight().toFloat()
                when (kind) {
                    HandleView.Kind.MOVE ->
                        centerFraction = (centerFraction + delta / screenH).coerceIn(0.05f, 0.95f)
                    HandleView.Kind.RESIZE ->
                        gapDp = (gapDp + 2 * pxToDp(delta))
                            .coerceIn(RulerSettings.MIN_GAP_DP, RulerSettings.MAX_GAP_DP)
                    HandleView.Kind.CLOSE -> Unit // tap-only, never drags
                }
                applyGeometryAndStyle()
            },
            onDragEnd = {
                when (kind) {
                    HandleView.Kind.MOVE -> onPersistCenter(centerFraction)
                    HandleView.Kind.RESIZE -> onPersistGap(gapDp)
                    HandleView.Kind.CLOSE -> Unit
                }
            },
            onTap = { if (kind == HandleView.Kind.CLOSE) onRequestClose() },
        )
        wm.addView(v, lp)
        return Win(v, lp)
    }

    private fun removeAll() {
        listOfNotNull(bottomZone, topZone, moveHandle, resizeHandle, closeHandle)
            .forEach { runCatching { wm.removeView(it.view) } }
        bottomZone = null; topZone = null
        moveHandle = null; resizeHandle = null; closeHandle = null
    }

    // ---------- geometry ----------

    private fun applyGeometryAndStyle() {
        val screenH = screenHeight()
        val screenW = screenWidth()
        val centerY = (centerFraction * screenH).roundToInt()
        val gapPx = dp(gapDp)
        val zoneHPx = dp(settings.blurHeightDp)
        val bottomEdge = centerY + gapPx / 2   // top of the bottom zone
        val topEdge = centerY - gapPx / 2      // bottom of the top zone

        bottomZone?.let { layoutZone(it, y = bottomEdge, zoneH = zoneHPx) }
        if (settings.topEnabled) topZone?.let { layoutZone(it, y = topEdge - zoneHPx, zoneH = zoneHPx) }

        val handleSize = dp(HANDLE_SIZE_DP)
        moveHandle?.let {
            it.lp.x = dp(12f)
            it.lp.y = bottomEdge + dp(10f)
            update(it)
        }
        resizeHandle?.let {
            it.lp.x = screenW - handleSize - dp(12f)
            it.lp.y = bottomEdge + dp(10f)
            update(it)
        }
        closeHandle?.let {
            it.lp.x = screenW - handleSize - dp(12f)
            it.lp.y = (topEdge - handleSize - dp(10f)).coerceAtLeast(0)
            update(it)
        }
    }

    private fun layoutZone(win: Win, y: Int, zoneH: Int) {
        win.lp.height = zoneH
        win.lp.y = y
        (win.view as ZoneView).apply {
            // The window itself is capped at maxPassThroughAlpha, so push the content
            // alpha up to compensate and keep the intended visual density.
            // Up to 100 % the content alpha ramps to full; beyond that (the window alpha cap
            // forbids going darker) the fully dark band near the gap grows instead.
            maxAlpha = (255f * settings.intensity).roundToInt().coerceIn(0, 255)
            solidFraction = (settings.intensity - 1f).coerceAtLeast(0f)
            lineVisible = settings.linesVisible
            lineColor = settings.lineColor
            lineThicknessPx = dp(settings.lineThicknessDp).coerceAtLeast(1).toFloat()
        }
        update(win)
    }

    private fun update(win: Win) {
        if (win.view.isAttachedToWindow) wm.updateViewLayout(win.view, win.lp)
    }

    // ---------- helpers ----------

    private fun screenHeight() = wm.currentWindowMetrics.bounds.height()
    private fun screenWidth() = wm.currentWindowMetrics.bounds.width()

    private fun dp(v: Float): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, v, context.resources.displayMetrics
    ).roundToInt()

    private fun pxToDp(px: Float): Float = px / context.resources.displayMetrics.density

    private companion object {
        const val HANDLE_SIZE_DP = 44f
    }
}
