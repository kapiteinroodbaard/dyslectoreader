package org.dyslectoreader.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class RulerSettings(
    val blurHeightDp: Float = DEFAULT_BLUR_HEIGHT_DP,
    val intensity: Float = DEFAULT_INTENSITY,
    val gapHeightDp: Float = DEFAULT_GAP_DP,
    val topEnabled: Boolean = true,
    val linesVisible: Boolean = false,
    val lineThicknessDp: Float = DEFAULT_LINE_THICKNESS_DP,
    val lineColor: Int = DEFAULT_LINE_COLOR,
    val centerYFraction: Float = 0.5f,
) {
    companion object {
        const val DEFAULT_BLUR_HEIGHT_DP = 140f
        const val DEFAULT_INTENSITY = 0.7f
        const val DEFAULT_GAP_DP = 72f
        const val DEFAULT_LINE_THICKNESS_DP = 2f
        const val DEFAULT_LINE_COLOR = 0xFF1E88E5.toInt()
        const val MIN_GAP_DP = 32f
        const val MAX_GAP_DP = 400f
    }
}

private val Context.dataStore by preferencesDataStore(name = "ruler_settings")

class SettingsRepository(private val context: Context) {

    private object Keys {
        val BLUR_HEIGHT = floatPreferencesKey("blur_height_dp")
        val INTENSITY = floatPreferencesKey("intensity")
        val GAP_HEIGHT = floatPreferencesKey("gap_height_dp")
        val TOP_ENABLED = booleanPreferencesKey("top_enabled")
        val LINES_VISIBLE = booleanPreferencesKey("lines_visible")
        val LINE_THICKNESS = floatPreferencesKey("line_thickness_dp")
        val LINE_COLOR = intPreferencesKey("line_color")
        val CENTER_Y = floatPreferencesKey("center_y_fraction")
    }

    val settings: Flow<RulerSettings> = context.dataStore.data.map { p ->
        RulerSettings(
            blurHeightDp = p[Keys.BLUR_HEIGHT] ?: RulerSettings.DEFAULT_BLUR_HEIGHT_DP,
            intensity = p[Keys.INTENSITY] ?: RulerSettings.DEFAULT_INTENSITY,
            gapHeightDp = p[Keys.GAP_HEIGHT] ?: RulerSettings.DEFAULT_GAP_DP,
            topEnabled = p[Keys.TOP_ENABLED] ?: true,
            linesVisible = p[Keys.LINES_VISIBLE] ?: false,
            lineThicknessDp = p[Keys.LINE_THICKNESS] ?: RulerSettings.DEFAULT_LINE_THICKNESS_DP,
            lineColor = p[Keys.LINE_COLOR] ?: RulerSettings.DEFAULT_LINE_COLOR,
            centerYFraction = p[Keys.CENTER_Y] ?: 0.5f,
        )
    }

    suspend fun setBlurHeight(dp: Float) = context.dataStore.edit { it[Keys.BLUR_HEIGHT] = dp }
    suspend fun setIntensity(v: Float) = context.dataStore.edit { it[Keys.INTENSITY] = v }
    suspend fun setGapHeight(dp: Float) = context.dataStore.edit {
        it[Keys.GAP_HEIGHT] = dp.coerceIn(RulerSettings.MIN_GAP_DP, RulerSettings.MAX_GAP_DP)
    }
    suspend fun setTopEnabled(v: Boolean) = context.dataStore.edit { it[Keys.TOP_ENABLED] = v }
    suspend fun setLinesVisible(v: Boolean) = context.dataStore.edit { it[Keys.LINES_VISIBLE] = v }
    suspend fun setLineThickness(dp: Float) = context.dataStore.edit { it[Keys.LINE_THICKNESS] = dp }
    suspend fun setLineColor(color: Int) = context.dataStore.edit { it[Keys.LINE_COLOR] = color }
    suspend fun setCenterYFraction(v: Float) = context.dataStore.edit {
        it[Keys.CENTER_Y] = v.coerceIn(0.05f, 0.95f)
    }

    companion object {
        @Volatile private var instance: SettingsRepository? = null
        fun get(context: Context): SettingsRepository =
            instance ?: synchronized(this) {
                instance ?: SettingsRepository(context.applicationContext).also { instance = it }
            }
    }
}
