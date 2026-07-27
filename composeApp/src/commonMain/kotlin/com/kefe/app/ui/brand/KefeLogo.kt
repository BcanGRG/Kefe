package com.kefe.app.ui.brand

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kefe.app.ui.theme.KefeTheme

/**
 * Yatay kilit: isaret solda, kelime isareti sagda, dikeyde ortali.
 *
 * Kelime yuksekligi isaret kutusunun %60'i, aradaki bosluk kutunun %25'i. Kilit
 * genisligi [LogoLockMinWidth] altina dustugunde kelime dusurulur ve yalniz isaret
 * kalir - sikistirilmis bir kelime isareti markayi ucuzlatir.
 */
@Composable
fun KefeLogoHorizontal(
    modifier: Modifier = Modifier,
    markSize: Dp = 40.dp,
    color: Color = KefeTheme.colors.onSurface,
    markColor: Color = KefeTheme.colors.accent,
    progress: Float = KefeMarkStaticProgress,
) {
    BoxWithConstraints(modifier) {
        if (maxWidth < LogoLockMinWidth) {
            KefeMark(progress = progress, modifier = Modifier.size(markSize), color = markColor)
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                KefeMark(progress = progress, modifier = Modifier.size(markSize), color = markColor)
                Spacer(Modifier.width(markSize * WORDMARK_GAP_RATIO))
                KefeWordmark(color = color, height = markSize * WORDMARK_HEIGHT_RATIO)
            }
        }
    }
}

/**
 * Dikey kilit: isaret ustte, kelime altta, yatayda ortali. Oranlar yatay kilitle ayni.
 * Kelime isareti isaret kutusunun kabaca iki kati genislik ister; alti kalirsa dusurulur.
 */
@Composable
fun KefeLogoVertical(
    modifier: Modifier = Modifier,
    markSize: Dp = 40.dp,
    color: Color = KefeTheme.colors.onSurface,
    markColor: Color = KefeTheme.colors.accent,
    progress: Float = KefeMarkStaticProgress,
) {
    BoxWithConstraints(modifier) {
        val fits = maxWidth >= markSize * VERTICAL_LOCK_WIDTH_RATIO
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            KefeMark(progress = progress, modifier = Modifier.size(markSize), color = markColor)
            if (fits) {
                Spacer(Modifier.height(markSize * WORDMARK_GAP_RATIO))
                KefeWordmark(color = color, height = markSize * WORDMARK_HEIGHT_RATIO)
            }
        }
    }
}

/**
 * Kelime isareti. Harf bicimine mudahale yok: FontFamily.Default, ozel harf araligi
 * veya cizim yok - kilit yalniz oranlarla kuruluyor.
 *
 * [height] hedeflenen GORUNUR harf yuksekligidir (buyuk harf boyu). Punto bundan
 * turetilir ve kullanicinin yazi tipi olceginden etkilenmez; logo metin degildir.
 */
@Composable
fun KefeWordmark(
    text: String = "Kefe",
    modifier: Modifier = Modifier,
    color: Color = KefeTheme.colors.onSurface,
    height: Dp = 24.dp,
) {
    val fontSize = with(LocalDensity.current) { (height / CAP_HEIGHT_RATIO).toSp() }
    Text(
        text = text,
        modifier = modifier,
        color = color,
        maxLines = 1,
        softWrap = false,
        style = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.SemiBold,
            fontSize = fontSize,
        ),
    )
}

/** Altinda kelime isaretinin dusuruldugu kilit genisligi. */
val LogoLockMinWidth: Dp = 104.dp

private const val WORDMARK_HEIGHT_RATIO = 0.60f
private const val WORDMARK_GAP_RATIO = 0.25f
private const val VERTICAL_LOCK_WIDTH_RATIO = 2.0f

/** Varsayilan yazi tipinde buyuk harf boyu / punto orani. */
private const val CAP_HEIGHT_RATIO = 0.70f
