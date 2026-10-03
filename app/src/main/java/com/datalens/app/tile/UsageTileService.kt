package com.datalens.app.tile

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.datalens.app.MainActivity
import com.datalens.app.R
import com.datalens.app.notifications.UsageTrackerCache
import com.datalens.app.util.ByteFormatter

/**
 * Quick Settings tile showing today's real mobile-data total. Tapping opens
 * DataLens. The tile reads the last cached snapshot (written by the tracking
 * service and app refreshes) — it never invents a number; without a snapshot
 * it invites the user to open the app.
 */
class UsageTileService : TileService() {

    override fun onTileAdded() {
        updateTile()
    }

    override fun onStartListening() {
        updateTile()
    }

    private fun updateTile() {
        val tile = qsTile ?: return
        val snapshot = UsageTrackerCache.snapshot
        if (snapshot != null) {
            // QS tile subtitles are tiny — just the formatted total.
            tile.subtitle = ByteFormatter.formatCompact(snapshot.todayTotalBytes)
            tile.state = if (snapshot.trackingEnabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        } else {
            tile.subtitle = getString(R.string.tile_open_app)
            tile.state = Tile.STATE_INACTIVE
        }
        tile.icon = Icon.createWithResource(this, R.drawable.ic_stat_datalens)
        tile.updateTile()
    }

    override fun onClick() {
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pending = PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            startActivityAndCollapse(pending)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
