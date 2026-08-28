package org.dyslectoreader.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.service.quicksettings.TileService
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.dyslectoreader.MainActivity
import org.dyslectoreader.R
import org.dyslectoreader.data.SettingsRepository
import org.dyslectoreader.tile.RulerTileService
import java.util.function.Consumer

class RulerOverlayService : LifecycleService() {

    private lateinit var controller: RulerWindowController
    private lateinit var repo: SettingsRepository
    private val blurListener = Consumer<Boolean> { enabled ->
        controller.onBlurAvailabilityChanged(enabled)
    }

    override fun onCreate() {
        super.onCreate()
        repo = SettingsRepository.get(this)
        controller = RulerWindowController(
            this,
            onPersistCenter = { v -> lifecycleScope.launch { repo.setCenterYFraction(v) } },
            onPersistGap = { v -> lifecycleScope.launch { repo.setGapHeight(v) } },
        )
        startForeground(NOTIF_ID, buildNotification())

        val wm = getSystemService(WindowManager::class.java)
        lifecycleScope.launch {
            val initial = repo.settings.first()
            controller.show(initial, wm.isCrossWindowBlurEnabled)
            wm.addCrossWindowBlurEnabledListener(mainExecutor, blurListener)
            isRunning.value = true
            TileService.requestListeningState(
                this@RulerOverlayService,
                android.content.ComponentName(this@RulerOverlayService, RulerTileService::class.java)
            )
            repo.settings.collect { controller.onSettingsChanged(it) }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        controller.onDisplayChanged()
    }

    override fun onDestroy() {
        getSystemService(WindowManager::class.java)
            .removeCrossWindowBlurEnabledListener(blurListener)
        controller.hide()
        isRunning.value = false
        TileService.requestListeningState(
            this, android.content.ComponentName(this, RulerTileService::class.java)
        )
        super.onDestroy()
    }

    private fun buildNotification(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID, getString(R.string.notif_channel_name),
                NotificationManager.IMPORTANCE_LOW
            )
        )
        val contentIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_tile_ruler)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(getString(R.string.notif_text))
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "ruler_overlay"
        private const val NOTIF_ID = 1

        /** Observable running state for the tile and the in-app toggle. */
        val isRunning = MutableStateFlow(false)

        fun start(context: Context) {
            context.startForegroundService(Intent(context, RulerOverlayService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, RulerOverlayService::class.java))
        }

        fun toggle(context: Context) {
            if (isRunning.value) stop(context) else start(context)
        }
    }
}
