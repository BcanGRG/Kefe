package com.kefe.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kefe.app.ui.theme.KefeTheme
import com.kefe.app.ui.theme.Sizes

/**
 * Yukleme yer tutucusu. Shimmer veya nabiz animasyonu YOKTUR -
 * hareket yalnizca anlamli gecislere ayrilir.
 */
@Composable
fun KefeSkeletonBlock(
    width: Dp? = null,
    height: Dp,
    modifier: Modifier = Modifier,
    radius: Dp = 8.dp,
    inCard: Boolean = false,
    bordered: Boolean = false,
) {
    val colors = KefeTheme.colors
    val fill = if (inCard) colors.surfaceSunken else colors.surfaceElevated
    val shape = RoundedCornerShape(radius)

    Box(
        modifier = modifier
            .then(if (width != null) Modifier.width(width) else Modifier.fillMaxWidth())
            .height(height)
            .clip(shape)
            .background(fill)
            .then(
                if (bordered) Modifier.border(Sizes.hairline, colors.outline, shape) else Modifier,
            )
    )
}

@Composable
fun KefeSkeletonCircle(
    size: Dp,
    inCard: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val colors = KefeTheme.colors
    val fill = if (inCard) colors.surfaceSunken else colors.surfaceElevated

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(fill)
    )
}
