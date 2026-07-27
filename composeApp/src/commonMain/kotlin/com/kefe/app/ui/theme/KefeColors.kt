package com.kefe.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * Kefe renk jetonlari. Degerler tasarim handoff'undaki tablodan birebir alinmistir.
 * Koyu tema birincildir. Yukselti golgeyle degil yuzey tonu farkiyla anlatilir.
 */
@Immutable
data class KefeColors(
    val isDark: Boolean,

    // Yuzeyler ve metin
    val surface: Color,
    val surfaceElevated: Color,
    val surfaceSunken: Color,
    val onSurface: Color,
    val onSurfaceMuted: Color,

    // Vurgu
    val accent: Color,
    val accentMuted: Color,
    val onAccent: Color,

    // Anlamsal
    val positive: Color,
    val negative: Color,
    val warning: Color,
    val outline: Color,

    // Esitleme durumu
    val syncOk: Color,
    val syncPending: Color,
    val syncOffline: Color,

    // Varlik sinifi paleti (dusuk doygunluk, kategorik)
    val gold: Color,
    val silver: Color,
    val fx: Color,
    val fund: Color,
    val cash: Color,

    // Avatar - kategori paletinden bagimsiz notrler
    val avatarA: Color,
    val avatarB: Color,

    // Yari saydam yardimcilar
    val staleBannerBg: Color,
    val staleBannerBorder: Color,
    val buyBadgeBg: Color,
    val sellBadgeBg: Color,
    val pendingBadgeBg: Color,
    val scrim: Color,
) {
    /** Varlik sinifi -> renk. Donut, legend, satir ikonu ve yigilmis barlarda ayni kaynak. */
    fun assetClass(assetClass: AssetClassColor): Color = when (assetClass) {
        AssetClassColor.Gold -> gold
        AssetClassColor.Silver -> silver
        AssetClassColor.Fx -> fx
        AssetClassColor.Fund -> fund
        AssetClassColor.Cash -> cash
    }

    /**
     * Artis/azalis rengi. KRITIK: renk asla tek sinyal degildir - cagiran taraf
     * her zaman ok ikonu veya +/- isareti ile birlikte kullanmak zorundadir.
     */
    fun delta(value: Double): Color = when {
        value > 0 -> positive
        value < 0 -> negative
        else -> onSurfaceMuted
    }
}

enum class AssetClassColor { Gold, Silver, Fx, Fund, Cash }

val KefeDarkColors = KefeColors(
    isDark = true,
    surface = Color(0xFF15120E),
    surfaceElevated = Color(0xFF1E1A15),
    surfaceSunken = Color(0xFF0E0C09),
    onSurface = Color(0xFFF3EEE5),
    onSurfaceMuted = Color(0xFFA39B8E),
    accent = Color(0xFFD9AE5F),
    accentMuted = Color(0xFF3B2F1B),
    onAccent = Color(0xFF1B160E),
    positive = Color(0xFF56A981),
    negative = Color(0xFFDC6A61),
    warning = Color(0xFFE2913E),
    outline = Color(0xFF302A23),
    syncOk = Color(0xFF5FA98E),
    syncPending = Color(0xFFD9A038),
    syncOffline = Color(0xFF7C746A),
    gold = Color(0xFFD9B45A),
    silver = Color(0xFFA3B4C2),
    fx = Color(0xFF6FA694),
    fund = Color(0xFF8E92CB),
    cash = Color(0xFFC9917C),
    avatarA = Color(0x3D968C7D),
    avatarB = Color(0x42788796),
    staleBannerBg = Color(0x26D9A038),
    staleBannerBorder = Color(0x52D9A038),
    buyBadgeBg = Color(0x2956A981),
    sellBadgeBg = Color(0x29DC6A61),
    pendingBadgeBg = Color(0x24D9A038),
    scrim = Color(0x8C000000),
)

val KefeLightColors = KefeColors(
    isDark = false,
    surface = Color(0xFFFAF7F1),
    surfaceElevated = Color(0xFFFFFFFF),
    surfaceSunken = Color(0xFFEFE9DE),
    onSurface = Color(0xFF1C1813),
    onSurfaceMuted = Color(0xFF6B6357),
    accent = Color(0xFF8C5E16),
    accentMuted = Color(0xFFF1E3C8),
    onAccent = Color(0xFFFFF6E8),
    positive = Color(0xFF2C7B56),
    negative = Color(0xFFB24239),
    warning = Color(0xFFA65C10),
    outline = Color(0xFFDED6C8),
    syncOk = Color(0xFF2C7B62),
    syncPending = Color(0xFF8F6512),
    syncOffline = Color(0xFF7A7266),
    gold = Color(0xFF9A7415),
    silver = Color(0xFF5C7284),
    fx = Color(0xFF2F7565),
    fund = Color(0xFF5257A6),
    cash = Color(0xFF9B5B41),
    avatarA = Color(0x3D968C7D),
    avatarB = Color(0x42788796),
    staleBannerBg = Color(0x26D9A038),
    staleBannerBorder = Color(0x52D9A038),
    buyBadgeBg = Color(0x292C7B56),
    sellBadgeBg = Color(0x29B24239),
    pendingBadgeBg = Color(0x248F6512),
    scrim = Color(0x8C000000),
)
