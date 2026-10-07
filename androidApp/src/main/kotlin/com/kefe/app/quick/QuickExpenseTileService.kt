package com.kefe.app.quick

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/**
 * Hizli ayarlar panelindeki "Harcama ekle" kutucugu: dokununca panel kapanir,
 * hizli giris penceresi acilir. Telefon kilitliyse once kilit acilir.
 */
class QuickExpenseTileService : TileService() {

    override fun onStartListening() {
        val tile = qsTile ?: return
        tile.state = Tile.STATE_ACTIVE
        tile.label = "Harcama ekle"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) tile.subtitle = "Kefe"
        tile.updateTile()
    }

    override fun onClick() {
        if (isLocked) unlockAndRun { open() } else open()
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun open() {
        val intent = QuickExpenseActivity.intent(this, null)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(
                PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT),
            )
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
