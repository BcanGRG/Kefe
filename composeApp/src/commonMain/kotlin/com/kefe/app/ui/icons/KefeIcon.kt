package com.kefe.app.ui.icons

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import com.kefe.app.ui.theme.IconSize
import com.kefe.app.ui.theme.KefeTheme

/**
 * Tum ikonlar bu sarmalayici uzerinden cizilir; boylece olcu ve tint tek yerden gelir.
 * Dekoratif kullanimda contentDescription null verilmelidir.
 */
@Composable
fun KefeIcon(
    icon: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = IconSize.default,
    tint: Color = KefeTheme.colors.onSurfaceMuted
) {
    Icon(
        imageVector = icon,
        contentDescription = contentDescription,
        modifier = modifier.size(size),
        tint = tint
    )
}
