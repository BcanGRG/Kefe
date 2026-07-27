package com.kefe.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kefe.app.ui.theme.IconSize
import com.kefe.app.ui.theme.KefeShapes
import com.kefe.app.ui.theme.KefeTheme
import com.kefe.app.ui.theme.Space

/**
 * Bos durum: notr bir aciklama ve istege bagli tek bir eylem.
 * Sahte veri veya sifir degerli gorsel gosterilmez.
 */
@Composable
fun KefeEmptyState(
    icon: ImageVector?,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    val colors = KefeTheme.colors
    val type = KefeTheme.type

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Space.x24, vertical = Space.x32),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (icon != null) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(KefeShapes.boxMedium)
                    .background(colors.surfaceSunken),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = colors.onSurfaceMuted,
                    modifier = Modifier.size(IconSize.default),
                )
            }
            Spacer(Modifier.height(Space.x20))
        }
        Text(
            text = title,
            style = type.h2,
            color = colors.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(Space.x8))
        Text(
            text = body,
            style = type.body,
            color = colors.onSurfaceMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 320.dp),
        )
        if (action != null) {
            Spacer(Modifier.height(Space.x20))
            action()
        }
    }
}
