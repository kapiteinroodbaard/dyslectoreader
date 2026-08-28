package org.dyslectoreader.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import org.dyslectoreader.data.RulerSettings
import kotlin.math.roundToInt

/**
 * Owns all overlay windows that make up the ruler:
 *  - N blur "band" windows per zone (stepped approximation of a gradient blur),
 *    or one gradient scrim window per zone when cross-window blur is unavailable
 *  - optional visible line windows at the inner edge of each zone
 *  - two touchable handle windows (move / resize); everything else passes touches through.
 */
class RulerWindowController(
    private val context: Context,
    private val onPersistCenter: (Float) -> Unit,
    private val onPersistGap: (Float) -> Unit,
) {
    private val wm = context.getSystemService(WindowManager::class.java)

    private var settings = RulerSettings()
    private var blurEnabled = true
    private var attached = false

    // Live-drag state (may run ahead of the persisted settings while a finger is down)
    private var centerFraction = 0.5f
    private var gapDp = RulerSettings.DEFAULT_GAP_DP

    // Structural inputs the current window set was built for
    private var builtTopEnabled = true
    private var builtBlurEnabled = true
    private var builtLinesVisible = false

    private data class Win(val view: View, val lp: WindowManager.LayoutParams)

    private val bottomBands = mutableListOf<Win>()
    private val topBands = mutableListOf<Win>()
    private var bottomScrim: Win? = null
    private var topScrim: Win? = null
    private var bottomLine: Win? = null
    private var topLine: Win? = null
    private var moveHandle: Win? = null
    private var resizeHandle: Win? = null

    fun show(initial: RulerSettings, crossWindowBlurEnabled: Boolean) {
        settings = initial
        blurEnabled = crossWindowBlurEnabled
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
        if (s.topEnabled != builtTopEnabled || s.linesVisible != builtLinesVisible) rebuild()
        else applyGeometryAndStyle()
    }

    fun onBlurAvailabilityChanged(enabled: Boolean) {
        if (blurEnabled == enabled) return
        blurEnabled = enabled
        if (attached) rebuild()
    }

    fun onDisplayChanged() {
        if (attached) applyGeometryAndStyle()
    }

    // ---------- window construction ----------

    private fun rebuild() {
        removeAll()
        builtTopEnabled = settings.topEnabled
        builtBlurEnabled = blurEnabled
        builtLinesVisible = settings.linesVisible

        if (blurEnabled) {
            repeat(BAND_COUNT) { bottomBands += addBand() }
            if (settings.topEnabled) repeat(BAND_COUNT) { topBands += addBand() }
        } else {
            bottomScrim = addScrim(fadeDownward = true)
            if (settings.topEnabled) topScrim = addScrim(fadeDownward = false)
        }
        if (settings.linesVisible) {
            bottomLine = addLine()
            if (settings.topEnabled) topLine = addLine()
        }
        moveHandle = addHandle(HandleView.Kind.MOVE)
        resizeHandle = addHandle(HandleView.Kind.RESIZE)
        applyGeometryAndStyle()
    }

    private fun passThroughParams() = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT, 0,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        fitInsetsTypes = 0
    }

    private fun addBand(): Win {
        val lp = passThroughParams().apply {
            flags = flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
        }
        val v = BlurBandView(context)
        wm.addView(v, lp)
        return Win(v, lp)
    }

    private fun addScrim(fadeDownward: Boolean): Win {
        val lp = passThroughParams()
        val v = ScrimView(context, fadeDownward)
        wm.addView(v, lp)
        return Win(v, lp)
    }

    private fun addLine(): Win {
        val lp = passThroughParams()
        val v = LineView(context)
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
                }
                applyGeometryAndStyle()
            },
            onDragEnd = {
                when (kind) {
                    HandleView.Kind.MOVE -> onPersistCenter(centerFraction)
                    HandleView.Kind.RESIZE -> onPersistGap(gapDp)
                }
            },
        )
        wm.addView(v, lp)
        return Win(v, lp)
    }

    private fun removeAll() {
        (bottomBands + topBands + listOfNotNull(
            bottomScrim, topScrim, bottomLine, topLine, moveHandle, resizeHandle
        )).forEach { runCatching { wm.removeView(it.view) } }
        bottomBands.clear(); topBands.clear()
        bottomScrim = null; topScrim = null
        bottomLine = null; topLine = null
        moveHandle = null; resizeHandle = null
    }

    // ---------- geometry ----------

    private fun applyGeometryAndStyle() {
        val screenH = screenHeight()
        val screenW = screenWidth()
        val centerY = (centerFraction * screenH).roundToInt()
        val gapPx = dp(gapDp)
        val zoneHPx = dp(settings.blurHeightDp)
        val bottomEdge = centerY + gapPx / 2   // top of the bottom blur zone
        val topEdge = centerY - gapPx / 2      // bottom of the top blur zone
        val intensity = settings.intensity

        if (blurEnabled) {
            layoutBands(bottomBands, startY = bottomEdge, zoneH = zoneHPx, downward = true, intensity)
            if (settings.topEnabled)
                layoutBands(topBands, startY = topEdge - zoneHPx, zoneH = zoneHPx, downward = false, intensity)
        } else {
            bottomScrim?.let { layoutZone(it, bottomEdge, zoneHPx, intensity) }
            if (settings.topEnabled) topScrim?.let { layoutZone(it, topEdge - zoneHPx, zoneHPx, intensity) }
        }

        val linePx = dp(settings.lineThicknessDp).coerceAtLeast(1)
        bottomLine?.let {
            it.view.setBackgroundColor(settings.lineColor)
            it.lp.height = linePx
            it.lp.y = bottomEdge
            update(it)
        }
        topLine?.let {
            it.view.setBackgroundColor(settings.lineColor)
            it.lp.height = linePx
            it.lp.y = topEdge - linePx
            update(it)
        }

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
    }

    private fun layoutBands(bands: List<Win>, startY: Int, zoneH: Int, downward: Boolean, intensity: Float) {
        if (bands.isEmpty()) return
        val bandH = (zoneH / bands.size).coerceAtLeast(1)
        val maxBlur = (MIN_BLUR_PX + (MAX_BLUR_PX - MIN_BLUR_PX) * intensity).roundToInt()
        bands.forEachIndexed { i, win ->
            // Band 0 sits at startY. When the zone fades downward, band 0 is nearest the line
            // (strongest blur); when it fades upward, the last band is nearest the line.
            val strengthIndex = if (downward) i else bands.size - 1 - i
            val fraction = (bands.size - strengthIndex).toFloat() / bands.size
            win.lp.height = bandH
            win.lp.y = startY + i * bandH
            win.lp.blurBehindRadius = (maxBlur * fraction).roundToInt().coerceAtLeast(1)
            win.view.setBackgroundColor(
                Color.argb((BAND_TINT_ALPHA * intensity * fraction).roundToInt(), 30, 30, 30)
            )
            update(win)
        }
    }

    private fun layoutZone(win: Win, y: Int, zoneH: Int, intensity: Float) {
        win.lp.height = zoneH
        win.lp.y = y
        (win.view as ScrimView).maxAlpha = (SCRIM_MAX_ALPHA * intensity).roundToInt()
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
        const val BAND_COUNT = 5
        const val HANDLE_SIZE_DP = 44f
        const val MIN_BLUR_PX = 6f
        const val MAX_BLUR_PX = 90f
        const val BAND_TINT_ALPHA = 40f
        const val SCRIM_MAX_ALPHA = 200f
    }
}
