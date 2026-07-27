package com.kefe.app.ui.theme

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier

val LocalKefeColors: ProvidableCompositionLocal<KefeColors> =
    staticCompositionLocalOf { KefeDarkColors }

val LocalKefeTypography: ProvidableCompositionLocal<KefeTypography> =
    staticCompositionLocalOf { KefeDefaultTypography }

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
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) KefeDarkColors else KefeLightColors

    val materialScheme = if (darkTheme) {
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

    CompositionLocalProvider(
        LocalKefeColors provides colors,
        LocalKefeTypography provides KefeDefaultTypography,
        LocalContentColor provides colors.onSurface,
        LocalIndication provides ripple(color = colors.accent),
    ) {
        MaterialTheme(
            colorScheme = materialScheme,
            content = {
                Box(Modifier.fillMaxSize().background(colors.surface)) {
                    content()
                }
            },
        )
    }
}
