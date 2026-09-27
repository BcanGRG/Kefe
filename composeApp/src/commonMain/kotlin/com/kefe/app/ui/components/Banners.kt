package com.kefe.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import com.kefe.app.data.sync.CloudMode
import com.kefe.app.data.sync.CloudStatus
import com.kefe.app.ui.icons.KefeIcons
import com.kefe.app.ui.theme.IconSize
import com.kefe.app.ui.theme.KefeShapes
import com.kefe.app.ui.theme.KefeTheme
import com.kefe.app.ui.theme.Sizes
import com.kefe.app.ui.theme.Space

/**
 * Fiyatlarin bayatladigini bildiren ince serit. Rakamlarin ustunu ORTMEZ -
 * kullanici degerleri gormeye devam eder, yalnizca guven sinyali verilir.
 */
@Composable
fun KefeStaleBanner(
    text: String,
    actionText: String,
    onAction: () -> Unit,
    clockIcon: ImageVector,
    modifier: Modifier = Modifier,
    strip: Boolean = false,
) {
    val colors = KefeTheme.colors
    val type = KefeTheme.type

    val surface = if (strip) {
        modifier
            .fillMaxWidth()
            .background(colors.staleBannerBg)
            .horizontalRules(colors.staleBannerBorder)
            .padding(horizontal = Space.x16, vertical = StripPaddingV)
    } else {
        modifier
            .fillMaxWidth()
            .clip(KefeShapes.boxSmall)
            .background(colors.staleBannerBg)
            .border(Sizes.hairline, colors.staleBannerBorder, KefeShapes.boxSmall)
            .heightIn(min = Sizes.touchTarget)
            .padding(horizontal = Space.x12, vertical = Space.x8)
    }

    Row(modifier = surface, verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = clockIcon,
            contentDescription = null,
            tint = colors.warning,
            modifier = Modifier.size(IconSize.tiny),
        )
        Spacer(Modifier.width(Space.x8))
        Text(
            text = text,
            style = type.caption,
            color = if (strip) colors.warning else colors.onSurface,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(Space.x8))
        Text(
            text = actionText,
            style = type.caption.copy(
                fontWeight = if (strip) FontWeight.SemiBold else FontWeight.Normal,
                textDecoration = TextDecoration.Underline,
            ),
            color = colors.warning,
            modifier = Modifier
                .clip(KefeShapes.boxSmall)
                .clickable(onClick = onAction)
                .then(
                    if (strip) Modifier else Modifier.padding(Space.x8),
                ),
        )
    }
}

/**
 * Iki satirli bilgi seridi, notr yuzey; istege bagli bir ya da iki eylem.
 *
 * Once adi KefeOfflineBanner idi ve fiyat ucu tokezleyince "Çevrimdışı" yazan
 * seridi ciziyordu - ag gayet calisirken. Artik anlami cagirandan gelir: fiyat
 * alinamadi seridi (saat ikonu) ve hesap bagi seritleri (bkz. [accountBannerCopy])
 * ayni yuzeyi kullanir.
 *
 * Tek eylem satirin SAGINDA durur (fiyat seridi "Yenile"); iki eylem metnin
 * ALTINA iner - dar telefonda yan yana sigmaz.
 */
@Composable
fun KefeTwoLineBanner(
    line1: String,
    line2: String?,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    strip: Boolean = false,
    iconTint: Color? = null,
    actionText: String? = null,
    onAction: () -> Unit = {},
    secondaryActionText: String? = null,
    onSecondaryAction: () -> Unit = {},
) {
    val colors = KefeTheme.colors
    val type = KefeTheme.type

    val surface = if (strip) {
        modifier
            .fillMaxWidth()
            .background(colors.surfaceSunken)
            .horizontalRules(colors.outline)
            .padding(horizontal = Space.x16, vertical = StripPaddingV)
    } else {
        modifier
            .fillMaxWidth()
            .clip(KefeShapes.boxMedium)
            .background(colors.surfaceSunken)
            .padding(horizontal = Space.x12, vertical = Space.x10)
    }
    val twoActions = actionText != null && secondaryActionText != null
    val tint = iconTint ?: if (strip) colors.syncOffline else colors.onSurfaceMuted

    Row(
        modifier = surface,
        verticalAlignment = if (strip || twoActions) Alignment.Top else Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier
                .padding(top = if (strip || twoActions) 1.dp else 0.dp)
                .size(if (strip) IconSize.tiny else IconSize.medium),
        )
        Spacer(Modifier.width(if (strip) Space.x8 else Space.x10))
        Column(Modifier.weight(1f)) {
            Text(
                text = line1,
                style = type.caption,
                color = if (strip) colors.onSurfaceMuted else colors.onSurface,
            )
            if (line2 != null) {
                if (!strip) Spacer(Modifier.height(2.dp))
                Text(
                    text = line2,
                    style = if (strip) type.caption else type.micro,
                    color = colors.onSurfaceMuted,
                )
            }
            if (actionText != null && secondaryActionText != null) {
                Spacer(Modifier.height(Space.x4))
                Row(horizontalArrangement = Arrangement.spacedBy(Space.x16)) {
                    BannerAction(actionText, onAction, strong = true)
                    BannerAction(secondaryActionText, onSecondaryAction, strong = false)
                }
            }
        }
        if (actionText != null && !twoActions) {
            Spacer(Modifier.width(Space.x8))
            BannerAction(actionText, onAction, strong = strip)
        }
    }
}

/** Serit eylemi: alti cizili metin, dokunma payi dikeyde genisletilmis. */
@Composable
private fun BannerAction(text: String, onClick: () -> Unit, strong: Boolean) {
    Text(
        text = text,
        style = KefeTheme.type.caption.copy(
            fontWeight = if (strong) FontWeight.SemiBold else FontWeight.Normal,
            textDecoration = TextDecoration.Underline,
        ),
        color = KefeTheme.colors.accent,
        modifier = Modifier
            .clip(KefeShapes.boxSmall)
            .clickable(onClick = onClick)
            .padding(vertical = Space.x4),
    )
}

/** Serit yuksekligi handoff'ta `padding:9px 16px`. */
private val StripPaddingV = 9.dp

/** Tam genislik seritlerin ust ve alt 1dp cizgileri. */
private fun Modifier.horizontalRules(color: Color): Modifier = this.drawBehind {
    val stroke = 1.dp.toPx()
    drawRect(color = color, topLeft = Offset.Zero, size = Size(size.width, stroke))
    drawRect(
        color = color,
        topLeft = Offset(0f, size.height - stroke),
        size = Size(size.width, stroke),
    )
}

/** Genel amacli bilgi seridi. */
@Composable
fun KefeInfoBanner(
    text: String,
    icon: ImageVector? = null,
    modifier: Modifier = Modifier,
) {
    val colors = KefeTheme.colors
    val type = KefeTheme.type

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(KefeShapes.boxSmall)
            .background(colors.surfaceSunken)
            .padding(horizontal = Space.x12, vertical = Space.x10),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = colors.onSurfaceMuted,
                modifier = Modifier.size(IconSize.tiny),
            )
            Spacer(Modifier.width(Space.x8))
        }
        Text(
            text = text,
            style = type.caption,
            color = colors.onSurfaceMuted,
            modifier = Modifier.weight(1f),
        )
    }
}

// --- Hesap modu: etiketlerin ve renklerin TEK kaynagi --------------------
//
// NEYDI. Ayni durum uc yerde uc adla yaziyordu: rayda "Bekliyor", cipte
// "Eşitleniyor", yan navigasyonda fiyat satiri; "Çevrimdışı" ise fiyat ucu
// tokezleyince bile cikiyordu. Ray ve yan nav FIYAT tazeligini, cip bulutu
// gosteriyordu. Artik her yuzey [CloudMode]'u okur ve metni buradan alir.
// "Çevrimdışı" sozlukte YOK: hesapsiz kullanim bir ariza degil, "Bu cihazda".

/** Modun renk tonu. Renk tek sinyal degil - etiket her zaman yazilir. */
enum class CloudTone { Neutral, Pending, Ok, Offline }

/** Kisa etiket: ozet cipi ve tablet rayi. */
fun CloudMode.shortLabel(): String = when (this) {
    CloudMode.Local -> "Bu cihazda"
    is CloudMode.LinkPending -> "Bağlantı yarım"
    is CloudMode.SessionLost -> "Oturum kapandı"
    is CloudMode.Cloud -> when (status) {
        CloudStatus.Syncing -> "Eşitleniyor"
        CloudStatus.Synced -> "Eşitlendi"
        CloudStatus.Unreachable -> "Eşitlenemiyor"
    }
}

/**
 * Uzun bicim: yan navigasyon ve Ayarlar. [syncedAgo] ("az önce", "5 dk önce")
 * yalniz Eşitlendi'de eklenir; digerlerinde anlamsiz.
 */
fun CloudMode.longLabel(syncedAgo: String? = null): String = when (this) {
    CloudMode.Local -> "Yalnız bu cihazda"
    is CloudMode.LinkPending -> "Hesap bağlantısı tamamlanmadı"
    is CloudMode.SessionLost -> "Oturum kapandı · yeniden giriş yapın"
    is CloudMode.Cloud -> when (status) {
        CloudStatus.Syncing -> "Hesapla eşitleniyor…"
        CloudStatus.Synced ->
            if (syncedAgo.isNullOrBlank()) "Hesapla eşitlendi" else "Hesapla eşitlendi · $syncedAgo"
        CloudStatus.Unreachable -> "Hesaba ulaşılamıyor · kayıtlar bu cihazda bekliyor"
    }
}

fun CloudMode.tone(): CloudTone = when (this) {
    // Hesapsiz kullanim NOTR: yesil "iyi" ya da gri "kopuk" demek yanlis olurdu.
    CloudMode.Local -> CloudTone.Neutral
    is CloudMode.LinkPending -> CloudTone.Pending
    is CloudMode.SessionLost -> CloudTone.Offline
    is CloudMode.Cloud -> when (status) {
        CloudStatus.Syncing -> CloudTone.Pending
        CloudStatus.Synced -> CloudTone.Ok
        CloudStatus.Unreachable -> CloudTone.Offline
    }
}

/**
 * Rozette nokta yerine cizilen ikon. Yalniz iki mod ikon alir: hesapsiz
 * kullanim (tek telefon) ve ulasilamayan hesap (ustu cizili bulut). Ustu cizili
 * bulut BASKA HICBIR YERDE kullanilmaz - fiyat seridi saat ikonunu alir.
 */
fun CloudMode.badgeIcon(): ImageVector? = when {
    this == CloudMode.Local -> KefeIcons.Device
    this is CloudMode.Cloud && status == CloudStatus.Unreachable -> KefeIcons.CloudOff
    else -> null
}

@Composable
fun CloudTone.color(): Color = when (this) {
    CloudTone.Neutral -> KefeTheme.colors.onSurfaceMuted
    CloudTone.Pending -> KefeTheme.colors.syncPending
    CloudTone.Ok -> KefeTheme.colors.syncOk
    CloudTone.Offline -> KefeTheme.colors.syncOffline
}

/**
 * Hesap bagi yarim kaldiginda ya da oturum dustugunde Ozet'in ustundeki
 * seridin metni; diger modlarda null (serit yok). [primary] ve [secondary]
 * eylemlerini cagiran moda gore baglar.
 */
data class AccountBannerCopy(
    val line1: String,
    val line2: String,
    val primary: String,
    val secondary: String,
)

fun accountBannerCopy(mode: CloudMode?): AccountBannerCopy? = when (mode) {
    is CloudMode.LinkPending -> AccountBannerCopy(
        line1 = "Hesap bağlantısı tamamlanmadı",
        line2 = "Kayıtlar henüz hesaba gönderilmiyor.",
        primary = "Tamamla",
        secondary = "Vazgeç",
    )
    is CloudMode.SessionLost -> AccountBannerCopy(
        line1 = "Oturumunuz kapandı",
        line2 = "Eşitleme durdu, kayıtlarınız bu cihazda.",
        primary = "Yeniden giriş yap",
        secondary = "Hesapsız devam et",
    )
    else -> null
}

/**
 * Rozetin isareti: ikonu varsa ikon, yoksa renkli nokta. Cip, ray ve yan nav
 * ortak. Kutu her modda AYNI genislikte: mod degisince yanindaki metin
 * kaymasin, yan navigasyonda alt satir ayni hizada kalsin.
 */
@Composable
fun KefeCloudMark(mode: CloudMode, modifier: Modifier = Modifier) {
    val tint = mode.tone().color()
    val icon = mode.badgeIcon()
    Box(modifier.size(CloudMarkIconSize), contentAlignment = Alignment.Center) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(CloudMarkIconSize),
            )
        } else {
            Box(
                modifier = Modifier
                    .size(SyncDotSize)
                    .clip(CircleShape)
                    .background(tint)
            )
        }
    }
}

/**
 * Ozet'in basligindaki hesap cipi. Dokununca hesap bolumune gidilir
 * ([onClick]); Ayarlar'in ilk bolumu hesap - her platformda tek hedef.
 */
@Composable
fun KefeSyncChip(
    mode: CloudMode,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val colors = KefeTheme.colors

    Row(
        modifier = modifier
            .height(Sizes.chipSmall)
            .clip(KefeShapes.pill)
            .background(colors.surfaceElevated)
            .border(Sizes.hairline, colors.outline, KefeShapes.pill)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = Space.x10),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KefeCloudMark(mode)
        Spacer(Modifier.width(SyncChipGap))
        Text(
            text = mode.shortLabel(),
            style = KefeTheme.type.micro.copy(
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
            ),
            color = colors.onSurfaceMuted,
            maxLines = 1,
        )
    }
}

/** Esitleme cipi olculeri - handoff: 7px nokta, 5px bosluk. */
private val SyncDotSize = 7.dp
private val SyncChipGap = 5.dp

/** Nokta yerine cizilen ikon: noktadan biraz buyuk ki cizgisi okunabilsin. */
private val CloudMarkIconSize = 11.dp

/** Serit ekranda kaldigi sure. Okunacak kadar uzun, engel olmayacak kadar kisa. */
private const val BannerVisibleMillis = 4_000L

/**
 * Ekranin altinda beliren, kapatilabilir serit.
 *
 * Kendiliginden KAYBOLMAZ: yazma hatasi gibi kacirilmamasi gereken bilgiler
 * icin. Kendiliginden kapanmasi gereken bilgiler [AutoDismissBanner] kullanir.
 *
 * [tone] PARAMETRE: her serit hata degil. Kisitlanan yenileme bir bilgidir ve
 * kirmizi cizilirse kullanici yanlis bir sey yaptigini sanir.
 */
@Composable
fun BoxScope.KefeBottomBanner(
    message: String,
    onDismiss: () -> Unit,
    tone: Color? = null,
) {
    val c = KefeTheme.colors
    val accentTone = tone ?: c.negative
    Row(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(Space.x16)
            .widthIn(max = Sizes.formMaxWidth)
            .clip(KefeShapes.card)
            .background(c.surfaceElevated)
            .border(Sizes.hairline, accentTone, KefeShapes.card)
            .padding(horizontal = Space.x16, vertical = Space.x12),
        horizontalArrangement = Arrangement.spacedBy(Space.x12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = message,
            style = KefeTheme.type.caption,
            color = c.onSurface,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "Kapat",
            style = KefeTheme.type.caption.copy(fontWeight = FontWeight.SemiBold),
            color = accentTone,
            modifier = Modifier
                .clip(KefeShapes.button)
                .clickable(onClick = onDismiss)
                .padding(horizontal = Space.x8, vertical = Space.x4),
        )
    }
}

/**
 * Kendiliginden kapanan bilgi seridi.
 *
 * [message] her degistiginde sayac bastan baslar; art arda gelen iki bildirim
 * birbirinin suresini yemez.
 */
@Composable
fun BoxScope.KefeAutoDismissBanner(
    message: String,
    onDismiss: () -> Unit,
    tone: Color? = null,
) {
    // rememberUpdatedState olmadan, kapatma islevi her bestelemede degisirse
    // bekleme yeniden baslar ve serit hic kapanmayabilir.
    val dismiss by rememberUpdatedState(onDismiss)
    LaunchedEffect(message) {
        delay(BannerVisibleMillis)
        dismiss()
    }
    KefeBottomBanner(message = message, onDismiss = onDismiss, tone = tone)
}
