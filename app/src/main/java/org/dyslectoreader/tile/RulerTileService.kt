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
        // Reflect the intent immediately: starting/stopping the service is asynchronous, so
        // reading isRunning right after toggle() still returns the *old* value and the tile
        // would keep its previous look until the shade is reopened.
        val willBeRunning = !RulerOverlayService.isRunning.value
        RulerOverlayService.toggle(this)
        setTileState(willBeRunning)
    }

    private fun updateTile() = setTileState(RulerOverlayService.isRunning.value)

    private fun setTileState(active: Boolean) {
        qsTile?.apply {
            state = if (active) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            updateTile()
        }
    }
}
