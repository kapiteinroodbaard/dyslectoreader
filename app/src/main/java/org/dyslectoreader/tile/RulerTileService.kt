package org.dyslectoreader.tile

import android.app.PendingIntent
import android.content.Intent
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import org.dyslectoreader.MainActivity
import org.dyslectoreader.overlay.RulerOverlayService

class RulerTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onClick() {
        super.onClick()
        if (!Settings.canDrawOverlays(this)) {
            // No overlay permission yet — open the app so the user can grant it.
            val pi = PendingIntent.getActivity(
                this, 0,
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE
            )
            startActivityAndCollapse(pi)
            return
        }
        RulerOverlayService.toggle(this)
        updateTile()
    }

    private fun updateTile() {
        qsTile?.apply {
            state = if (RulerOverlayService.isRunning.value) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            updateTile()
        }
    }
}
