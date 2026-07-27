package com.kefe.app.ui.layout

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Pencere genisligi sinifi. Handoff'ta uc yerlesim var:
 *   Telefon   390x844   - alt navigasyon, tek kolon
 *   Tablet    840x1180  - navigation rail 92dp, iki kolon
 *   Masaustu  1440x900  - genisletilmis nav 240dp | icerik | sag panel 320dp
 *
 * Esikler bu uc hedefin arasina konumlandirildi; cihaz turune degil GENISLIGE
 * bakilir - katlanabilir acildiginda telefon da Medium olur.
 */
enum class WindowSize {
    /** < 600dp - telefon. Alt navigasyon, tek kolon. */
    Compact,

    /** 600..1239dp - tablet, katlanabilir acik, kucuk masaustu penceresi. Rail + iki kolon. */
    Medium,

    /** >= 1240dp - masaustu. Genisletilmis nav + icerik + sag panel. */
    Expanded;

    val isCompact: Boolean get() = this == Compact
    val isMedium: Boolean get() = this == Medium
    val isExpanded: Boolean get() = this == Expanded

    /** Tablet ve masaustunde liste-detay yan yana gosterilir. */
    val supportsTwoPane: Boolean get() = this != Compact

    /** Sag bilgi paneli yalniz masaustunde kalici. */
    val supportsSidePanel: Boolean get() = this == Expanded

    companion object {
        val MediumBreakpoint: Dp = 600.dp
        val ExpandedBreakpoint: Dp = 1240.dp

        fun fromWidth(width: Dp): WindowSize = when {
            width < MediumBreakpoint -> Compact
            width < ExpandedBreakpoint -> Medium
            else -> Expanded
        }
    }
}

val LocalWindowSize = staticCompositionLocalOf { WindowSize.Compact }

/**
 * Pencere genisligini olcup asagiya saglar. Uygulama kokune bir kez konur;
 * ekranlar `LocalWindowSize.current` ile okur.
 */
@Composable
fun ProvideWindowSize(
    modifier: Modifier = Modifier,
    content: @Composable (WindowSize) -> Unit,
) {
    BoxWithConstraints(modifier) {
        val size = WindowSize.fromWidth(maxWidth)
        CompositionLocalProvider(LocalWindowSize provides size) {
            content(size)
        }
    }
}
