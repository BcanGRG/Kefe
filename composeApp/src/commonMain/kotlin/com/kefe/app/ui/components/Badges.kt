package com.kefe.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kefe.app.ui.format.Money
import com.kefe.app.ui.format.trUpper
import com.kefe.app.ui.theme.KefeShapes
import com.kefe.app.ui.theme.KefeTheme
import com.kefe.app.ui.theme.Sizes
import com.kefe.app.ui.theme.Space
import com.kefe.app.ui.theme.tabular

/**
 * Rozetler ve degisim metni. Durum hicbir zaman yalniz renge birakilmaz:
 * her rozetin metni, degisim metninin de isareti (ve varsa oku) bulunur.
 */

private val BadgePaddingH = 6.dp
private val BadgePaddingV = 2.dp
private val BadgeIconSize = 12.dp
private val BadgeDotSize = 5.dp
private val DeltaIconSize = 14.dp
private val DashOn = 4.dp
private val DashOff = 3.dp

@Composable
private fun badgeTextStyle(): TextStyle =
    KefeTheme.type.label(10, 0.08, FontWeight.Bold)

/** Buyutulmeyen rozetler (Bekliyor / Elle fiyat): 10px, 600, harf araligi yok. */
@Composable
private fun plainBadgeTextStyle(): TextStyle =
    KefeTheme.type.nano.copy(fontWeight = FontWeight.SemiBold)

@Composable
fun KefeBadge(
    text: String,
    modifier: Modifier = Modifier,
    background: Color,
    contentColor: Color,
    borderColor: Color? = null,
    dashed: Boolean = false,
    leadingIcon: ImageVector? = null,
    dotColor: Color? = null,
    uppercase: Boolean = true,
    textStyle: TextStyle? = null,
    contentPadding: PaddingValues = PaddingValues(
        horizontal = BadgePaddingH,
        vertical = BadgePaddingV,
    ),
    iconSize: Dp = BadgeIconSize,
) {
    val strokeColor = borderColor ?: contentColor
    val base = modifier
        .clip(KefeShapes.pill)
        .background(background)

    val bordered = when {
        dashed -> base.drawBehind {
            val stroke = Sizes.hairline.toPx()
            val inset = stroke / 2f
            drawRoundRect(
                color = strokeColor,
                topLeft = Offset(inset, inset),
                size = Size(size.width - stroke, size.height - stroke),
                cornerRadius = CornerRadius(size.height / 2f),
                style = Stroke(
                    width = stroke,
                    pathEffect = PathEffect.dashPathEffect(
                        floatArrayOf(DashOn.toPx(), DashOff.toPx()),
                    ),
                ),
            )
        }

        borderColor != null -> base.border(Sizes.hairline, borderColor, KefeShapes.pill)
        else -> base
    }

    Row(
        modifier = bordered.padding(contentPadding),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (dotColor != null) {
            Box(
                modifier = Modifier
                    .size(BadgeDotSize)
                    .clip(CircleShape)
                    .background(dotColor),
            )
        }
        if (leadingIcon != null) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(iconSize),
            )
        }
        Text(
            text = if (uppercase) text.trUpper() else text,
            style = textStyle ?: if (uppercase) badgeTextStyle() else plainBadgeTextStyle(),
            color = contentColor,
            maxLines = 1,
        )
    }
}

@Composable
fun KefeManualBadge(
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    text: String = "Elle",
) {
    val colors = KefeTheme.colors
    KefeBadge(
        text = text,
        modifier = modifier,
        background = colors.surfaceSunken,
        contentColor = colors.onSurfaceMuted,
        borderColor = colors.outline,
        leadingIcon = icon,
        uppercase = false,
        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
        iconSize = 10.dp,
    )
}

@Composable
fun KefePendingBadge(modifier: Modifier = Modifier) {
    val colors = KefeTheme.colors
    KefeBadge(
        text = "Bekliyor",
        modifier = modifier,
        background = colors.pendingBadgeBg,
        contentColor = colors.syncPending,
        dotColor = colors.syncPending,
        uppercase = false,
        contentPadding = PaddingValues(horizontal = 7.dp, vertical = 2.dp),
    )
}

@Composable
fun KefeMainGoalBadge(modifier: Modifier = Modifier) {
    val colors = KefeTheme.colors
    KefeBadge(
        text = "Ana Hedef",
        modifier = modifier,
        background = colors.accentMuted,
        contentColor = colors.accent,
        contentPadding = PaddingValues(horizontal = Space.x8, vertical = 3.dp),
    )
}

@Composable
fun KefeBuyBadge(modifier: Modifier = Modifier) {
    val colors = KefeTheme.colors
    KefeBadge(
        text = "Alış",
        modifier = modifier,
        background = colors.buyBadgeBg,
        contentColor = colors.positive,
    )
}

@Composable
fun KefeSellBadge(modifier: Modifier = Modifier) {
    val colors = KefeTheme.colors
    KefeBadge(
        text = "Satış",
        modifier = modifier,
        background = colors.sellBadgeBg,
        contentColor = colors.negative,
    )
}

@Composable
fun KefeOwnerBadge(modifier: Modifier = Modifier) {
    val colors = KefeTheme.colors
    KefeBadge(
        text = "Sahip",
        modifier = modifier,
        background = colors.accentMuted,
        contentColor = colors.accent,
    )
}

@Composable
fun KefeMemberBadge(modifier: Modifier = Modifier) {
    val colors = KefeTheme.colors
    KefeBadge(
        text = "Üye",
        modifier = modifier,
        background = colors.surfaceSunken,
        contentColor = colors.onSurfaceMuted,
    )
}

/**
 * Yuzdesel degisim. Renk tek sinyal degildir: metin her zaman +/- isareti
 * tasir, ikon verildiginde ayrica yon oku gosterilir.
 */
@Composable
fun KefeDeltaText(
    percent: Double,
    modifier: Modifier = Modifier,
    style: TextStyle = KefeTheme.type.caption.tabular(),
    showArrow: Boolean = true,
    upIcon: ImageVector? = null,
    downIcon: ImageVector? = null,
    decimals: Int = 2,
) {
    val color = KefeTheme.colors.delta(percent)
    val arrow = when {
        !showArrow || percent == 0.0 -> null
        percent > 0.0 -> upIcon
        else -> downIcon
    }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (arrow != null) {
            Icon(
                imageVector = arrow,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(DeltaIconSize),
            )
        }
        Text(
            text = Money.delta(percent, decimals),
            style = style.tabular(),
            color = color,
        )
    }
}
