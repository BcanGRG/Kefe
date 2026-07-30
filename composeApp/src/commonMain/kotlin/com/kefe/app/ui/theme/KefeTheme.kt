package com.kefe.app.ui.theme

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier

val LocalKefeColors: ProvidableCompositionLocal<KefeColors> =
    staticCompositionLocalOf { KefeDarkColors }

val LocalKefeTypography: ProvidableCompositionLocal<KefeTypography> =
    staticCompositionLocalOf { KefeDefaultTypography }

/**
 * Kurus gosterimi tercihi. Varsayilan KAPALI - tam liraya yuvarlanir.
 *
 * Ayar 116'dan fazla yerde kullanilan Money.tl'ye tek tek parametre gecmek
 * yerine bir CompositionLocal ile tasinir; yalniz satir ici tutarlar
 * ([com.kefe.app.ui.format.moneyTl]) buna bakar, hero toplamlar 0 ondalikta
 * kalir.
 */
val LocalShowCents: ProvidableCompositionLocal<Boolean> =
    staticCompositionLocalOf { false }

/**
 * Kefe tasarim sistemi. Renk/tipografi jetonlarina `KefeTheme.colors` ve
 * `KefeTheme.type` uzerinden erisilir.
 *
 * MaterialTheme yalnizca altta yatan material3 bilesenlerinin (ripple, text
 * selection, sheet davranisi) makul renkler almasi icin eslenir - ekranlar
 * dogrudan Kefe jetonlarini kullanir, MaterialTheme.colorScheme'i degil.
 */
object KefeTheme {
    val colors: KefeColors
        @Composable get() = LocalKefeColors.current

    val type: KefeTypography
        @Composable get() = LocalKefeTypography.current
}

@Composable
fun KefeTheme(
    darkTheme: Boolean = true,
    showCents: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) KefeDarkColors else KefeLightColors

    // Tema koku CompositionLocal saglar: asagidaki degerlerin KIMLIGI degisirse
    // agactaki her okuyucu gecersizlesir. Bu iki nesne her bestelemede yeniden
    // yaratiliyordu ve KefeTheme, Ayarlar durumunu okuyan App() icinde yasiyor -
    // yani her ayar emisyonunda butun ekran yeniden besteleniyordu. Ciplerin ve
    // bos durum kartlarinin titremesinin sebebi buydu.
    val materialScheme = remember(darkTheme) {
        if (darkTheme) {
            darkColorScheme(
                primary = colors.accent,
                onPrimary = colors.onAccent,
                background = colors.surface,
                onBackground = colors.onSurface,
                surface = colors.surfaceElevated,
                onSurface = colors.onSurface,
                surfaceVariant = colors.surfaceSunken,
                onSurfaceVariant = colors.onSurfaceMuted,
                outline = colors.outline,
                error = colors.negative,
                scrim = colors.scrim,
            )
        } else {
            lightColorScheme(
                primary = colors.accent,
                onPrimary = colors.onAccent,
                background = colors.surface,
                onBackground = colors.onSurface,
                surface = colors.surfaceElevated,
                onSurface = colors.onSurface,
                surfaceVariant = colors.surfaceSunken,
                onSurfaceVariant = colors.onSurfaceMuted,
                outline = colors.outline,
                error = colors.negative,
                scrim = colors.scrim,
            )
        }
    }

    val indication = remember(darkTheme) { ripple(color = colors.accent) }

    CompositionLocalProvider(
        LocalKefeColors provides colors,
        LocalKefeTypography provides KefeDefaultTypography,
        LocalShowCents provides showCents,
        LocalContentColor provides colors.onSurface,
        LocalIndication provides indication,
    ) {
        MaterialTheme(
            colorScheme = materialScheme,
            content = {
                // Zemin sistem cubuklarinin ALTINA da uzanir (kenardan kenara
                // gorunum), ama ICERIK onlarin altina girmez: Android'de ust
                // cubuktaki portfoy adi sistem saatiyle cakisiyordu.
                Box(Modifier.fillMaxSize().background(colors.surface)) {
                    Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                        content()
                    }
                }
            },
        )
    }
}
