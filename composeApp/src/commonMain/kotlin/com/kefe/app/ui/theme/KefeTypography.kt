package com.kefe.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * Tek aile, sistem font yigini. FontFamily.Default her platformda sistem fontuna
 * cozulur (Android: Roboto, iOS: SF, Windows: Segoe UI) - handoff'taki
 * `system-ui, -apple-system, "Segoe UI", sans-serif` yiginin karsiligi.
 *
 * ZORUNLU: tum para ve yuzde rakamlari tabular (sabit genislik) olmali.
 * Bunun icin `fontFeatureSettings = "tnum"` tasiyan `.tabular()` kullanilir -
 * listelerde tutarlar sutun gibi hizalanir.
 */
@Immutable
data class KefeTypography(
    val display: TextStyle,
    val h1: TextStyle,
    val h2: TextStyle,
    val body: TextStyle,
    val bodyStrong: TextStyle,
    val caption: TextStyle,
    /** 12px - tablo alt satiri, yogun masaustu icerigi. */
    val captionSmall: TextStyle,
    val micro: TextStyle,
    /** 10px - rozet metni, tablo basligi, en yogun bilgi. Tasarimda en cok kullanilan ikinci boy. */
    val nano: TextStyle,
    val microLabel: TextStyle,
) {
    /**
     * Ust basliklarin harf araligi kullanima gore degisir (.06 - .11em).
     * Tasarimdaki degeri birebir vermek icin bu yardimci kullanilir.
     */
    fun label(sizeSp: Int = 11, spacingEm: Double = 0.06, weight: FontWeight = FontWeight.SemiBold): TextStyle =
        (if (sizeSp <= 10) nano else microLabel).copy(
            fontSize = sizeSp.sp,
            fontWeight = weight,
            letterSpacing = spacingEm.em,
        )
}

private const val TABULAR = "tnum"

/** Para/yuzde rakamlari icin tabular rakam varyanti. */
fun TextStyle.tabular(): TextStyle = copy(fontFeatureSettings = TABULAR)

val KefeDefaultTypography = KefeTypography(
    display = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 40.sp,
        lineHeight = 48.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.015).em,
    ),
    h1 = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.01).em,
    ),
    h2 = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    body = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 15.sp,
        lineHeight = 21.sp,
        fontWeight = FontWeight.Normal,
    ),
    bodyStrong = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 15.sp,
        lineHeight = 21.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    caption = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.Normal,
    ),
    captionSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Normal,
    ),
    micro = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 11.sp,
        lineHeight = 15.sp,
        fontWeight = FontWeight.Normal,
    ),
    nano = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 10.sp,
        lineHeight = 13.sp,
        fontWeight = FontWeight.Normal,
    ),
    // Ust basliklar: uppercase uygulanan her yerde Turkce locale kullanilmali,
    // aksi halde "I" yerine "I" cikar - bkz. TurkishCase.upper()
    microLabel = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.09.em,
    ),
)

/** Marka kelime isareti - yalniz logo ve acilis ekraninda. Humanist govde harf. */
val KefeWordmarkFamily: FontFamily = FontFamily.Default
