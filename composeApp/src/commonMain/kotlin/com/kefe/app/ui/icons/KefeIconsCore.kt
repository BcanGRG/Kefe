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
 * Kefe cekirdek ikon seti: gezinme, aksiyon, durum ve durum cubugu.
 * Tumu 24x24 viewport, 2px outline, yuvarlak uc/birlesim.
 * Renk cagiran tarafta tint ile verilir; burada stroke Color.Black birakilir.
 */
internal object KefeIconsCore {

    // --- Gezinme ---

    private var _balance: ImageVector? = null
    val Balance: ImageVector
        get() = _balance ?: kefeIcon("KefeBalance") {
            // dikey govde + taban
            moveTo(12f, 3.5f); lineTo(12f, 20f)
            moveTo(7.5f, 20f); lineTo(16.5f, 20f)
            // kiris
            moveTo(5f, 7f); lineTo(19f, 7f)
            // sol kefe
            moveTo(2.5f, 12.5f); lineTo(5f, 7f); lineTo(7.5f, 12.5f)
            quadTo(5f, 16f, 2.5f, 12.5f)
            // sag kefe
            moveTo(16.5f, 12.5f); lineTo(19f, 7f); lineTo(21.5f, 12.5f)
            quadTo(19f, 16f, 16.5f, 12.5f)
        }.also { _balance = it }

    private var _wallet: ImageVector? = null
    val Wallet: ImageVector
        get() = _wallet ?: kefeIcon("KefeWallet") {
            roundedRect(2.5f, 6.5f, 21.5f, 19.5f, 3f)
            // sag taraftaki kart cebi
            moveTo(21.5f, 10.5f)
            lineTo(17.5f, 10.5f)
            arcTo(2.5f, 2.5f, 0f, false, false, 17.5f, 15.5f)
            lineTo(21.5f, 15.5f)
            dot(19.4f, 13f)
        }.also { _wallet = it }

    private var _target: ImageVector? = null
    val Target: ImageVector
        get() = _target ?: kefeIcon("KefeTarget") {
            circle(12f, 12f, 8f)
            circle(12f, 12f, 4f)
            dot(12f, 12f)
        }.also { _target = it }

    private var _listMenu: ImageVector? = null
    val ListMenu: ImageVector
        get() = _listMenu ?: kefeIcon("KefeListMenu") {
            dot(4f, 7f); moveTo(8f, 7f); lineTo(20f, 7f)
            dot(4f, 12f); moveTo(8f, 12f); lineTo(20f, 12f)
            dot(4f, 17f); moveTo(8f, 17f); lineTo(20f, 17f)
        }.also { _listMenu = it }

    private var _plus: ImageVector? = null
    val Plus: ImageVector
        get() = _plus ?: kefeIcon("KefePlus") {
            moveTo(12f, 5f); lineTo(12f, 19f)
            moveTo(5f, 12f); lineTo(19f, 12f)
        }.also { _plus = it }

    private var _chevronRight: ImageVector? = null
    val ChevronRight: ImageVector
        get() = _chevronRight ?: kefeIcon("KefeChevronRight") {
            moveTo(9f, 5f); lineTo(16f, 12f); lineTo(9f, 19f)
        }.also { _chevronRight = it }

    private var _chevronDown: ImageVector? = null
    val ChevronDown: ImageVector
        get() = _chevronDown ?: kefeIcon("KefeChevronDown") {
            moveTo(5f, 9f); lineTo(12f, 16f); lineTo(19f, 9f)
        }.also { _chevronDown = it }

    private var _chevronUp: ImageVector? = null
    val ChevronUp: ImageVector
        get() = _chevronUp ?: kefeIcon("KefeChevronUp") {
            moveTo(5f, 15f); lineTo(12f, 8f); lineTo(19f, 15f)
        }.also { _chevronUp = it }

    private var _arrowBack: ImageVector? = null
    val ArrowBack: ImageVector
        get() = _arrowBack ?: kefeIcon("KefeArrowBack") {
            moveTo(20f, 12f); lineTo(4f, 12f)
            moveTo(10f, 6f); lineTo(4f, 12f); lineTo(10f, 18f)
        }.also { _arrowBack = it }

    private var _close: ImageVector? = null
    val Close: ImageVector
        get() = _close ?: kefeIcon("KefeClose") {
            moveTo(6f, 6f); lineTo(18f, 18f)
            moveTo(18f, 6f); lineTo(6f, 18f)
        }.also { _close = it }

    // --- Aksiyon ---

    private var _check: ImageVector? = null
    val Check: ImageVector
        get() = _check ?: kefeIcon("KefeCheck") {
            moveTo(4.5f, 12.5f); lineTo(9.5f, 17.5f); lineTo(19.5f, 6.5f)
        }.also { _check = it }

    private var _pencil: ImageVector? = null
    val Pencil: ImageVector
        get() = _pencil ?: kefeIcon("KefePencil") {
            moveTo(4f, 20f)
            lineTo(5f, 16f)
            lineTo(16f, 5f)
            lineTo(19f, 8f)
            lineTo(8f, 19f)
            close()
            // bilezik
            moveTo(13.5f, 7.5f); lineTo(16.5f, 10.5f)
        }.also { _pencil = it }

    private var _trash: ImageVector? = null
    val Trash: ImageVector
        get() = _trash ?: kefeIcon("KefeTrash") {
            moveTo(3.5f, 6f); lineTo(20.5f, 6f)
            moveTo(9f, 6f); lineTo(9f, 3.5f); lineTo(15f, 3.5f); lineTo(15f, 6f)
            moveTo(5.5f, 6f); lineTo(6.5f, 20.5f); lineTo(17.5f, 20.5f); lineTo(18.5f, 6f)
            moveTo(10f, 10f); lineTo(10f, 17f)
            moveTo(14f, 10f); lineTo(14f, 17f)
        }.also { _trash = it }

    private var _eye: ImageVector? = null
    val Eye: ImageVector
        get() = _eye ?: kefeIcon("KefeEye") {
            eyeLens()
            circle(12f, 12f, 2.8f)
        }.also { _eye = it }

    private var _eyeOff: ImageVector? = null
    val EyeOff: ImageVector
        get() = _eyeOff ?: kefeIcon("KefeEyeOff") {
            eyeLens()
            circle(12f, 12f, 2.8f)
            moveTo(3.5f, 3.5f); lineTo(20.5f, 20.5f)
        }.also { _eyeOff = it }

    private var _refresh: ImageVector? = null
    val Refresh: ImageVector
        get() = _refresh ?: kefeIcon("KefeRefresh") {
            // sagdan baslayip saat yonunde neredeyse tam tur
            moveTo(19f, 12f)
            arcTo(7f, 7f, 0f, false, true, 12f, 19f)
            arcTo(7f, 7f, 0f, false, true, 5f, 12f)
            arcTo(7f, 7f, 0f, false, true, 12f, 5f)
            // ok ucu
            moveTo(9.6f, 3.4f); lineTo(12f, 5f); lineTo(9.6f, 7.4f)
        }.also { _refresh = it }

    private var _share: ImageVector? = null
    val Share: ImageVector
        get() = _share ?: kefeIcon("KefeShare") {
            circle(18f, 5f, 2.2f)
            circle(6f, 12f, 2.2f)
            circle(18f, 19f, 2.2f)
            moveTo(8f, 11f); lineTo(16f, 6.1f)
            moveTo(8f, 13f); lineTo(16f, 17.9f)
        }.also { _share = it }

    private var _download: ImageVector? = null
    val Download: ImageVector
        get() = _download ?: kefeIcon("KefeDownload") {
            moveTo(12f, 3f); lineTo(12f, 14.5f)
            moveTo(7f, 9.5f); lineTo(12f, 14.5f); lineTo(17f, 9.5f)
            moveTo(4f, 16.5f); lineTo(4f, 20f); lineTo(20f, 20f); lineTo(20f, 16.5f)
        }.also { _download = it }

    private var _copy: ImageVector? = null
    val Copy: ImageVector
        get() = _copy ?: kefeIcon("KefeCopy") {
            // arkadaki sayfa (L seklinde acik cerceve)
            moveTo(15.5f, 6.5f)
            lineTo(15.5f, 4.5f)
            arcTo(2f, 2f, 0f, false, false, 13.5f, 2.5f)
            lineTo(5.5f, 2.5f)
            arcTo(2f, 2f, 0f, false, false, 3.5f, 4.5f)
            lineTo(3.5f, 12.5f)
            arcTo(2f, 2f, 0f, false, false, 5.5f, 14.5f)
            lineTo(7.5f, 14.5f)
            // ondeki sayfa
            roundedRect(8.5f, 8.5f, 20.5f, 20.5f, 2.5f)
        }.also { _copy = it }

    private var _search: ImageVector? = null
    val Search: ImageVector
        get() = _search ?: kefeIcon("KefeSearch") {
            circle(10.5f, 10.5f, 6.5f)
            moveTo(15.3f, 15.3f); lineTo(20.5f, 20.5f)
        }.also { _search = it }

    private var _dragHandle: ImageVector? = null
    val DragHandle: ImageVector
        get() = _dragHandle ?: kefeIcon("KefeDragHandle") {
            moveTo(5f, 9.5f); lineTo(19f, 9.5f)
            moveTo(5f, 14.5f); lineTo(19f, 14.5f)
        }.also { _dragHandle = it }

    private var _moreHorizontal: ImageVector? = null
    val MoreHorizontal: ImageVector
        get() = _moreHorizontal ?: kefeIcon("KefeMoreHorizontal") {
            dot(5.5f, 12f)
            dot(12f, 12f)
            dot(18.5f, 12f)
        }.also { _moreHorizontal = it }

    private var _plusSmall: ImageVector? = null
    val PlusSmall: ImageVector
        get() = _plusSmall ?: kefeIcon("KefePlusSmall") {
            moveTo(12f, 7f); lineTo(12f, 17f)
            moveTo(7f, 12f); lineTo(17f, 12f)
        }.also { _plusSmall = it }

    private var _minusSmall: ImageVector? = null
    val MinusSmall: ImageVector
        get() = _minusSmall ?: kefeIcon("KefeMinusSmall") {
            moveTo(7f, 12f); lineTo(17f, 12f)
        }.also { _minusSmall = it }

    // --- Durum ---

    private var _cloudOff: ImageVector? = null
    val CloudOff: ImageVector
        get() = _cloudOff ?: kefeIcon("KefeCloudOff") {
            cloudBody()
            moveTo(3.5f, 3.5f); lineTo(20.5f, 20.5f)
        }.also { _cloudOff = it }

    private var _clock: ImageVector? = null
    val Clock: ImageVector
        get() = _clock ?: kefeIcon("KefeClock") {
            circle(12f, 12f, 8.5f)
            moveTo(12f, 7f); lineTo(12f, 12f); lineTo(15.5f, 14f)
        }.also { _clock = it }

    private var _info: ImageVector? = null
    val Info: ImageVector
        get() = _info ?: kefeIcon("KefeInfo") {
            circle(12f, 12f, 8.5f)
            moveTo(12f, 11.2f); lineTo(12f, 16.5f)
            dot(12f, 7.9f)
        }.also { _info = it }

    private var _calendar: ImageVector? = null
    val Calendar: ImageVector
        get() = _calendar ?: kefeIcon("KefeCalendar") {
            roundedRect(3.5f, 5.5f, 20.5f, 20.5f, 2.5f)
            moveTo(3.5f, 10f); lineTo(20.5f, 10f)
            moveTo(8f, 3f); lineTo(8f, 7.5f)
            moveTo(16f, 3f); lineTo(16f, 7.5f)
        }.also { _calendar = it }

    private var _lock: ImageVector? = null
    val Lock: ImageVector
        get() = _lock ?: kefeIcon("KefeLock") {
            roundedRect(4.5f, 10.5f, 19.5f, 20.5f, 2.5f)
            moveTo(8f, 10.5f)
            lineTo(8f, 7.5f)
            arcTo(4f, 4f, 0f, false, true, 16f, 7.5f)
            lineTo(16f, 10.5f)
            moveTo(12f, 14.3f); lineTo(12f, 16.7f)
        }.also { _lock = it }

    private var _fingerprint: ImageVector? = null
    val Fingerprint: ImageVector
        get() = _fingerprint ?: kefeIcon("KefeFingerprint") {
            moveTo(3.5f, 17f)
            lineTo(3.5f, 13.5f)
            arcTo(8.5f, 8.5f, 0f, false, true, 20.5f, 13.5f)
            lineTo(20.5f, 17f)
            moveTo(6.5f, 19f)
            lineTo(6.5f, 13.5f)
            arcTo(5.5f, 5.5f, 0f, false, true, 17.5f, 13.5f)
            lineTo(17.5f, 19f)
            moveTo(9.5f, 20f)
            lineTo(9.5f, 13.5f)
            arcTo(2.5f, 2.5f, 0f, false, true, 14.5f, 13.5f)
            lineTo(14.5f, 20f)
        }.also { _fingerprint = it }

    private var _arrowUpRight: ImageVector? = null
    val ArrowUpRight: ImageVector
        get() = _arrowUpRight ?: kefeIcon("KefeArrowUpRight") {
            moveTo(6.5f, 17.5f); lineTo(17.5f, 6.5f)
            moveTo(9f, 6.5f); lineTo(17.5f, 6.5f); lineTo(17.5f, 15f)
        }.also { _arrowUpRight = it }

    private var _arrowDownRight: ImageVector? = null
    val ArrowDownRight: ImageVector
        get() = _arrowDownRight ?: kefeIcon("KefeArrowDownRight") {
            moveTo(6.5f, 6.5f); lineTo(17.5f, 17.5f)
            moveTo(17.5f, 9f); lineTo(17.5f, 17.5f); lineTo(9f, 17.5f)
        }.also { _arrowDownRight = it }

    // Ozet ekranindaki degisim oklari DIKEYDIR (handoff: `M12 19V5 M6 11l6-6 6 6`);
    // capraz oklar liste satirlarinda kullanilir.

    private var _arrowUp: ImageVector? = null
    val ArrowUp: ImageVector
        get() = _arrowUp ?: kefeIcon("KefeArrowUp") {
            moveTo(12f, 19f); lineTo(12f, 5f)
            moveTo(6f, 11f); lineTo(12f, 5f); lineTo(18f, 11f)
        }.also { _arrowUp = it }

    private var _arrowDown: ImageVector? = null
    val ArrowDown: ImageVector
        get() = _arrowDown ?: kefeIcon("KefeArrowDown") {
            moveTo(12f, 5f); lineTo(12f, 19f)
            moveTo(6f, 13f); lineTo(12f, 19f); lineTo(18f, 13f)
        }.also { _arrowDown = it }

    private var _pin: ImageVector? = null
    val Pin: ImageVector
        get() = _pin ?: kefeIcon("KefePin") {
            moveTo(8.5f, 3f); lineTo(15.5f, 3f)
            moveTo(10f, 3f)
            lineTo(9.5f, 9f)
            lineTo(6.5f, 12.5f)
            lineTo(17.5f, 12.5f)
            lineTo(14.5f, 9f)
            lineTo(14f, 3f)
            moveTo(12f, 12.5f); lineTo(12f, 20.5f)
        }.also { _pin = it }

    private var _qrCode: ImageVector? = null
    val QrCode: ImageVector
        get() = _qrCode ?: kefeIcon("KefeQrCode") {
            roundedRect(3.5f, 3.5f, 9.5f, 9.5f, 1f)
            roundedRect(14.5f, 3.5f, 20.5f, 9.5f, 1f)
            roundedRect(3.5f, 14.5f, 9.5f, 20.5f, 1f)
            moveTo(14.5f, 14.5f); lineTo(14.5f, 17f)
            moveTo(17.5f, 14.5f); lineTo(20.5f, 14.5f)
            moveTo(20.5f, 17.5f); lineTo(20.5f, 20.5f)
            moveTo(14.5f, 20.5f); lineTo(17.5f, 20.5f)
        }.also { _qrCode = it }

    // --- Durum cubugu ---

    private var _signalBars: ImageVector? = null
    val SignalBars: ImageVector
        get() = _signalBars ?: kefeIcon("KefeSignalBars") {
            moveTo(4f, 19.5f); lineTo(4f, 16.5f)
            moveTo(9.3f, 19.5f); lineTo(9.3f, 13f)
            moveTo(14.7f, 19.5f); lineTo(14.7f, 9.5f)
            moveTo(20f, 19.5f); lineTo(20f, 5.5f)
        }.also { _signalBars = it }

    private var _wifi: ImageVector? = null
    val Wifi: ImageVector
        get() = _wifi ?: kefeIcon("KefeWifi") {
            moveTo(4.2f, 10.7f)
            arcTo(11f, 11f, 0f, false, true, 19.8f, 10.7f)
            moveTo(6.7f, 13.2f)
            arcTo(7.5f, 7.5f, 0f, false, true, 17.3f, 13.2f)
            moveTo(9.2f, 15.7f)
            arcTo(4f, 4f, 0f, false, true, 14.8f, 15.7f)
            dot(12f, 18.5f)
        }.also { _wifi = it }

    private var _battery: ImageVector? = null
    val Battery: ImageVector
        get() = _battery ?: kefeIcon("KefeBattery") {
            roundedRect(2.5f, 8f, 18.5f, 16f, 2f)
            moveTo(21f, 10.5f); lineTo(21f, 13.5f)
            moveTo(5.5f, 12f); lineTo(12f, 12f)
        }.also { _battery = it }
}

/** Tek gecisli outline ikon govdesi; tum ikonlar ayni optik agirliga sahip. */
private fun kefeIcon(name: String, pathBuilder: PathBuilder.() -> Unit): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
            pathBuilder = pathBuilder
        )
    }.build()

/** Kose yaricapli dikdortgen, saat yonunde. */
private fun PathBuilder.roundedRect(
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
    radius: Float
) {
    moveTo(left + radius, top)
    lineTo(right - radius, top)
    arcTo(radius, radius, 0f, false, true, right, top + radius)
    lineTo(right, bottom - radius)
    arcTo(radius, radius, 0f, false, true, right - radius, bottom)
    lineTo(left + radius, bottom)
    arcTo(radius, radius, 0f, false, true, left, bottom - radius)
    lineTo(left, top + radius)
    arcTo(radius, radius, 0f, false, true, left + radius, top)
    close()
}

/** Iki yarim yaydan olusan tam cember. */
private fun PathBuilder.circle(cx: Float, cy: Float, radius: Float) {
    moveTo(cx - radius, cy)
    arcTo(radius, radius, 0f, false, true, cx + radius, cy)
    arcTo(radius, radius, 0f, false, true, cx - radius, cy)
    close()
}

/** Yuvarlak uc sayesinde nokta gibi gorunen cok kisa cizgi. */
private fun PathBuilder.dot(cx: Float, cy: Float) {
    moveTo(cx - 0.1f, cy)
    lineTo(cx + 0.1f, cy)
}

/** Goz badem formu; Eye ve EyeOff ayni govdeyi paylasir. */
private fun PathBuilder.eyeLens() {
    moveTo(2.5f, 12f)
    quadTo(12f, 4f, 21.5f, 12f)
    quadTo(12f, 20f, 2.5f, 12f)
    close()
}

/** Uc kabarcikli bulut; CloudOff bunun uzerine cizgi ekler. */
private fun PathBuilder.cloudBody() {
    moveTo(6.5f, 18.5f)
    arcTo(3.5f, 3.5f, 0f, false, true, 7.2f, 11.8f)
    arcTo(4.3f, 4.3f, 0f, false, true, 15.4f, 11.5f)
    arcTo(3.8f, 3.8f, 0f, false, true, 17.5f, 18.5f)
    close()
}
