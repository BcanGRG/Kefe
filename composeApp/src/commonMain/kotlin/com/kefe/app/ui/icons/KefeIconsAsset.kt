package com.kefe.app.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Varlik sinifi ikonlari: altin, gumus, doviz, fon, nakit.
 * 24x24 viewport, 2f cizgi kalinligi, yuvarlak uc/birlesim.
 */
internal object KefeIconsAsset {

    // Tum ikonlar ayni cizgi parametrelerini paylasir; tekrar yazmamak icin tek yardimci.
    private inline fun vector(name: String, pathBuilder: PathBuilder.() -> Unit): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).path(
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
            pathBuilder = pathBuilder
        ).build()

    private var _gold: ImageVector? = null

    /** Altin: madeni para (ic ice iki daire). */
    val Gold: ImageVector
        get() = _gold ?: vector("KefeAssetGold") {
            // dis cember
            moveTo(3f, 12f)
            arcTo(9f, 9f, 0f, true, true, 21f, 12f)
            arcTo(9f, 9f, 0f, true, true, 3f, 12f)
            close()
            // ic cember
            moveTo(7.5f, 12f)
            arcTo(4.5f, 4.5f, 0f, true, true, 16.5f, 12f)
            arcTo(4.5f, 4.5f, 0f, true, true, 7.5f, 12f)
            close()
        }.also { _gold = it }

    private var _silver: ImageVector? = null

    /** Gumus: kulce (on yuz + ust yuz). */
    val Silver: ImageVector
        get() = _silver ?: vector("KefeAssetSilver") {
            // on yuz
            moveTo(3.5f, 18f)
            lineTo(6.2f, 11.5f)
            lineTo(17.8f, 11.5f)
            lineTo(20.5f, 18f)
            close()
            // ust yuz
            moveTo(6.2f, 11.5f)
            lineTo(8.6f, 6.5f)
            lineTo(15.4f, 6.5f)
            lineTo(17.8f, 11.5f)
        }.also { _silver = it }

    private var _fx: ImageVector? = null

    /** Doviz: kure (ekvator + meridyen). */
    val Fx: ImageVector
        get() = _fx ?: vector("KefeAssetFx") {
            moveTo(3f, 12f)
            arcTo(9f, 9f, 0f, true, true, 21f, 12f)
            arcTo(9f, 9f, 0f, true, true, 3f, 12f)
            close()
            // ekvator
            moveTo(3f, 12f)
            lineTo(21f, 12f)
            // meridyen
            moveTo(12f, 3f)
            arcTo(4.6f, 9f, 0f, false, true, 12f, 21f)
            arcTo(4.6f, 9f, 0f, false, true, 12f, 3f)
        }.also { _fx = it }

    private var _fund: ImageVector? = null

    /** Fon: eksen uzerinde cizgi grafik. */
    val Fund: ImageVector
        get() = _fund ?: vector("KefeAssetFund") {
            // eksen
            moveTo(4f, 4f)
            lineTo(4f, 20f)
            lineTo(20f, 20f)
            // seri
            moveTo(7.5f, 15.5f)
            lineTo(11.5f, 10.5f)
            lineTo(14.5f, 13.5f)
            lineTo(19f, 7.5f)
        }.also { _fund = it }

    private var _cash: ImageVector? = null

    /** Nakit: banknot (cerceve + merkez daire + yan cizgiler). */
    val Cash: ImageVector
        get() = _cash ?: vector("KefeAssetCash") {
            moveTo(4.5f, 6f)
            lineTo(19.5f, 6f)
            arcTo(2f, 2f, 0f, false, true, 21.5f, 8f)
            lineTo(21.5f, 16f)
            arcTo(2f, 2f, 0f, false, true, 19.5f, 18f)
            lineTo(4.5f, 18f)
            arcTo(2f, 2f, 0f, false, true, 2.5f, 16f)
            lineTo(2.5f, 8f)
            arcTo(2f, 2f, 0f, false, true, 4.5f, 6f)
            close()
            // merkez
            moveTo(9.5f, 12f)
            arcTo(2.5f, 2.5f, 0f, true, true, 14.5f, 12f)
            arcTo(2.5f, 2.5f, 0f, true, true, 9.5f, 12f)
            close()
            // yan cizgiler
            moveTo(5.5f, 10f)
            lineTo(5.5f, 14f)
            moveTo(18.5f, 10f)
            lineTo(18.5f, 14f)
        }.also { _cash = it }
}
