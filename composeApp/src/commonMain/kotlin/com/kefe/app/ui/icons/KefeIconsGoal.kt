package com.kefe.app.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import com.kefe.app.ui.format.trLower

/**
 * Hedef ikonlari ve anahtar -> ikon eslemesi.
 * 24x24 viewport, 2f cizgi kalinligi, yuvarlak uc/birlesim.
 */
internal object KefeIconsGoal {

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

    private var _home: ImageVector? = null

    /** Ev. */
    val Home: ImageVector
        get() = _home ?: vector("KefeGoalHome") {
            // cati
            moveTo(3f, 11f)
            lineTo(12f, 3.5f)
            lineTo(21f, 11f)
            // govde
            moveTo(5f, 9.3f)
            lineTo(5f, 20f)
            lineTo(19f, 20f)
            lineTo(19f, 9.3f)
            // kapi
            moveTo(9.5f, 20f)
            lineTo(9.5f, 14f)
            lineTo(14.5f, 14f)
            lineTo(14.5f, 20f)
        }.also { _home = it }

    private var _car: ImageVector? = null

    /** Araba. */
    val Car: ImageVector
        get() = _car ?: vector("KefeGoalCar") {
            // govde ve kabin
            moveTo(2.5f, 17f)
            lineTo(2.5f, 12.5f)
            lineTo(6f, 12.5f)
            lineTo(8.5f, 7.5f)
            lineTo(15.5f, 7.5f)
            lineTo(18f, 12.5f)
            lineTo(21.5f, 12.5f)
            lineTo(21.5f, 17f)
            close()
            // sol tekerlek
            moveTo(5.1f, 17f)
            arcTo(1.9f, 1.9f, 0f, true, true, 8.9f, 17f)
            arcTo(1.9f, 1.9f, 0f, true, true, 5.1f, 17f)
            close()
            // sag tekerlek
            moveTo(15.1f, 17f)
            arcTo(1.9f, 1.9f, 0f, true, true, 18.9f, 17f)
            arcTo(1.9f, 1.9f, 0f, true, true, 15.1f, 17f)
            close()
        }.also { _car = it }

    private var _plane: ImageVector? = null

    /** Ucak (ustten gorunum). */
    val Plane: ImageVector
        get() = _plane ?: vector("KefeGoalPlane") {
            moveTo(12f, 2.5f)
            curveTo(13.2f, 2.5f, 13.8f, 4.1f, 13.8f, 5.9f)
            lineTo(13.8f, 9.6f)
            lineTo(21.5f, 14.2f)
            lineTo(21.5f, 16.4f)
            lineTo(13.8f, 14.2f)
            lineTo(13.8f, 18.6f)
            lineTo(16.2f, 20.2f)
            lineTo(16.2f, 21.4f)
            lineTo(12f, 20f)
            lineTo(7.8f, 21.4f)
            lineTo(7.8f, 20.2f)
            lineTo(10.2f, 18.6f)
            lineTo(10.2f, 14.2f)
            lineTo(2.5f, 16.4f)
            lineTo(2.5f, 14.2f)
            lineTo(10.2f, 9.6f)
            lineTo(10.2f, 5.9f)
            curveTo(10.2f, 4.1f, 10.8f, 2.5f, 12f, 2.5f)
            close()
        }.also { _plane = it }

    private var _education: ImageVector? = null

    /** Egitim: mezuniyet kepi. */
    val Education: ImageVector
        get() = _education ?: vector("KefeGoalEducation") {
            // kep
            moveTo(12f, 4f)
            lineTo(22f, 9f)
            lineTo(12f, 14f)
            lineTo(2f, 9f)
            close()
            // alt bant
            moveTo(6f, 11f)
            lineTo(6f, 16f)
            curveTo(7.6f, 17.8f, 9.7f, 18.6f, 12f, 18.6f)
            curveTo(14.3f, 18.6f, 16.4f, 17.8f, 18f, 16f)
            lineTo(18f, 11f)
            // puskul
            moveTo(21.4f, 9.3f)
            lineTo(21.4f, 14.5f)
        }.also { _education = it }

    private var _shield: ImageVector? = null

    /** Kalkan. */
    val Shield: ImageVector
        get() = _shield ?: vector("KefeGoalShield") {
            moveTo(12f, 2.5f)
            lineTo(20f, 5.5f)
            lineTo(20f, 11.5f)
            curveTo(20f, 16.5f, 16.6f, 19.8f, 12f, 21.5f)
            curveTo(7.4f, 19.8f, 4f, 16.5f, 4f, 11.5f)
            lineTo(4f, 5.5f)
            close()
        }.also { _shield = it }

    private var _heart: ImageVector? = null

    /** Kalp. */
    val Heart: ImageVector
        get() = _heart ?: vector("KefeGoalHeart") {
            moveTo(19f, 14f)
            curveTo(20.5f, 12.5f, 22f, 10.8f, 22f, 8.5f)
            arcTo(5.5f, 5.5f, 0f, false, false, 16.5f, 3f)
            curveTo(14.7f, 3f, 13.5f, 3.5f, 12f, 5f)
            curveTo(10.5f, 3.5f, 9.3f, 3f, 7.5f, 3f)
            arcTo(5.5f, 5.5f, 0f, false, false, 2f, 8.5f)
            curveTo(2f, 10.8f, 3.5f, 12.5f, 5f, 14f)
            lineTo(12f, 21f)
            close()
        }.also { _heart = it }

    private var _gift: ImageVector? = null

    /** Hediye. */
    val Gift: ImageVector
        get() = _gift ?: vector("KefeGoalGift") {
            // kapak
            moveTo(2.5f, 8f)
            lineTo(21.5f, 8f)
            lineTo(21.5f, 12f)
            lineTo(2.5f, 12f)
            close()
            // kutu
            moveTo(4f, 12f)
            lineTo(4f, 21f)
            lineTo(20f, 21f)
            lineTo(20f, 12f)
            // dikey kurdele
            moveTo(12f, 8f)
            lineTo(12f, 21f)
            // sol fiyonk
            moveTo(12f, 8f)
            curveTo(10.5f, 8f, 7f, 8f, 7f, 5.5f)
            curveTo(7f, 4.1f, 8.1f, 3f, 9.5f, 3f)
            curveTo(11.5f, 3f, 12f, 6f, 12f, 8f)
            // sag fiyonk
            moveTo(12f, 8f)
            curveTo(13.5f, 8f, 17f, 8f, 17f, 5.5f)
            curveTo(17f, 4.1f, 15.9f, 3f, 14.5f, 3f)
            curveTo(12.5f, 3f, 12f, 6f, 12f, 8f)
        }.also { _gift = it }

    private var _star: ImageVector? = null

    /** Yildiz. */
    val Star: ImageVector
        get() = _star ?: vector("KefeGoalStar") {
            moveTo(12f, 2.2f)
            lineTo(15.1f, 8.5f)
            lineTo(22f, 9.5f)
            lineTo(17f, 14.3f)
            lineTo(18.2f, 21.2f)
            lineTo(12f, 17.9f)
            lineTo(5.8f, 21.2f)
            lineTo(7f, 14.3f)
            lineTo(2f, 9.5f)
            lineTo(8.9f, 8.5f)
            close()
        }.also { _star = it }

    private var _child: ImageVector? = null

    /** Cocuk. */
    val Child: ImageVector
        get() = _child ?: vector("KefeGoalChild") {
            // bas
            moveTo(9f, 5.5f)
            arcTo(3f, 3f, 0f, true, true, 15f, 5.5f)
            arcTo(3f, 3f, 0f, true, true, 9f, 5.5f)
            close()
            // govde
            moveTo(12f, 8.5f)
            lineTo(12f, 15.5f)
            // kollar
            moveTo(7f, 11.2f)
            lineTo(12f, 13f)
            lineTo(17f, 11.2f)
            // bacaklar
            moveTo(12f, 15.5f)
            lineTo(8.4f, 21f)
            moveTo(12f, 15.5f)
            lineTo(15.6f, 21f)
        }.also { _child = it }

    private var _laptop: ImageVector? = null

    /** Dizustu bilgisayar. */
    val Laptop: ImageVector
        get() = _laptop ?: vector("KefeGoalLaptop") {
            // ekran
            moveTo(5.5f, 4.5f)
            lineTo(18.5f, 4.5f)
            arcTo(1.5f, 1.5f, 0f, false, true, 20f, 6f)
            lineTo(20f, 15f)
            lineTo(4f, 15f)
            lineTo(4f, 6f)
            arcTo(1.5f, 1.5f, 0f, false, true, 5.5f, 4.5f)
            close()
            // taban
            moveTo(4f, 15f)
            lineTo(2.5f, 18.5f)
            lineTo(21.5f, 18.5f)
            lineTo(20f, 15f)
        }.also { _laptop = it }

    private var _palm: ImageVector? = null

    /** Palmiye. */
    val Palm: ImageVector
        get() = _palm ?: vector("KefeGoalPalm") {
            // govde
            moveTo(12f, 10.5f)
            curveTo(12.4f, 14f, 12.9f, 17.5f, 14.2f, 21f)
            // sol yatay yaprak
            moveTo(12f, 10.5f)
            curveTo(9.4f, 8.4f, 5.6f, 8.4f, 3f, 10.6f)
            // sag yatay yaprak
            moveTo(12f, 10.5f)
            curveTo(14.6f, 8.4f, 18.4f, 8.4f, 21f, 10.6f)
            // sol ust yaprak
            moveTo(12f, 10.5f)
            curveTo(10.6f, 6.6f, 8f, 4.4f, 5.4f, 3.8f)
            // sag ust yaprak
            moveTo(12f, 10.5f)
            curveTo(13.4f, 6.6f, 16f, 4.4f, 18.6f, 3.8f)
        }.also { _palm = it }

    private var _ring: ImageVector? = null

    /** Yuzuk. */
    val Ring: ImageVector
        get() = _ring ?: vector("KefeGoalRing") {
            // halka
            moveTo(6.2f, 15.2f)
            arcTo(5.8f, 5.8f, 0f, true, true, 17.8f, 15.2f)
            arcTo(5.8f, 5.8f, 0f, true, true, 6.2f, 15.2f)
            close()
            // tas
            moveTo(9.3f, 6.6f)
            lineTo(12f, 3.2f)
            lineTo(14.7f, 6.6f)
            lineTo(12f, 9.3f)
            close()
            // tas faceti
            moveTo(9.3f, 6.6f)
            lineTo(14.7f, 6.6f)
        }.also { _ring = it }

    /**
     * Hedef anahtarini ikona cevirir. Bilinmeyen anahtar Star dondurur;
     * boylece cagiran taraf null kontrolu yapmak zorunda kalmaz.
     */
    fun goalIcon(key: String): ImageVector = when (key.trLower()) {
        "ev" -> Home
        "araba" -> Car
        "ucak" -> Plane
        "egitim" -> Education
        "kalkan" -> Shield
        "kalp" -> Heart
        "hediye" -> Gift
        "yildiz" -> Star
        "cocuk" -> Child
        "laptop" -> Laptop
        "palmiye" -> Palm
        "yuzuk" -> Ring
        else -> Star
    }
}
