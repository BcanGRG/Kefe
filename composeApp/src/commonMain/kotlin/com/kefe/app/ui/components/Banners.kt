package com.kefe.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kefe.app.ui.theme.IconSize
import com.kefe.app.ui.theme.KefeShapes
import com.kefe.app.ui.theme.KefeTheme
import com.kefe.app.ui.theme.Sizes
import com.kefe.app.ui.theme.Space

/**
 * Fiyatlarin bayatladigini bildiren ince serit. Rakamlarin ustunu ORTMEZ -
 * kullanici degerleri gormeye devam eder, yalnizca guven sinyali verilir.
 */
@Composable
fun KefeStaleBanner(
    text: String,
    actionText: String,
    onAction: () -> Unit,
    clockIcon: ImageVector,
    modifier: Modifier = Modifier,
    strip: Boolean = false,
) {
    val colors = KefeTheme.colors
    val type = KefeTheme.type

    val surface = if (strip) {
        modifier
            .fillMaxWidth()
            .background(colors.staleBannerBg)
            .horizontalRules(colors.staleBannerBorder)
            .padding(horizontal = Space.x16, vertical = StripPaddingV)
    } else {
        modifier
            .fillMaxWidth()
            .clip(KefeShapes.boxSmall)
            .background(colors.staleBannerBg)
            .border(Sizes.hairline, colors.staleBannerBorder, KefeShapes.boxSmall)
            .heightIn(min = Sizes.touchTarget)
            .padding(horizontal = Space.x12, vertical = Space.x8)
    }

    Row(modifier = surface, verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = clockIcon,
            contentDescription = null,
            tint = colors.warning,
            modifier = Modifier.size(IconSize.tiny),
        )
        Spacer(Modifier.width(Space.x8))
        Text(
            text = text,
            style = type.caption,
            color = if (strip) colors.warning else colors.onSurface,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(Space.x8))
        Text(
            text = actionText,
            style = type.caption.copy(
                fontWeight = if (strip) FontWeight.SemiBold else FontWeight.Normal,
                textDecoration = TextDecoration.Underline,
            ),
            color = colors.warning,
            modifier = Modifier
                .clip(KefeShapes.boxSmall)
                .clickable(onClick = onAction)
                .then(
                    if (strip) Modifier else Modifier.padding(Space.x8),
                ),
        )
    }
}

/** Cevrimdisi bilgilendirme seridi - iki satirli, notr yuzey. */
@Composable
fun KefeOfflineBanner(
    line1: String,
    line2: String,
    cloudOffIcon: ImageVector,
    modifier: Modifier = Modifier,
    strip: Boolean = false,
) {
    val colors = KefeTheme.colors
    val type = KefeTheme.type

    val surface = if (strip) {
        modifier
            .fillMaxWidth()
            .background(colors.surfaceSunken)
            .horizontalRules(colors.outline)
            .padding(horizontal = Space.x16, vertical = StripPaddingV)
    } else {
        modifier
            .fillMaxWidth()
            .clip(KefeShapes.boxMedium)
            .background(colors.surfaceSunken)
            .padding(horizontal = Space.x12, vertical = Space.x10)
    }

    Row(
        modifier = surface,
        verticalAlignment = if (strip) Alignment.Top else Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = cloudOffIcon,
            contentDescription = null,
            tint = if (strip) colors.syncOffline else colors.onSurfaceMuted,
            modifier = Modifier
                .padding(top = if (strip) 1.dp else 0.dp)
                .size(if (strip) IconSize.tiny else IconSize.medium),
        )
        Spacer(Modifier.width(if (strip) Space.x8 else Space.x10))
        Column {
            Text(
                text = line1,
                style = type.caption,
                color = if (strip) colors.onSurfaceMuted else colors.onSurface,
            )
            if (!strip) Spacer(Modifier.height(2.dp))
            Text(
                text = line2,
                style = if (strip) type.caption else type.micro,
                color = colors.onSurfaceMuted,
            )
        }
    }
}

/** Serit yuksekligi handoff'ta `padding:9px 16px`. */
private val StripPaddingV = 9.dp

/** Tam genislik seritlerin ust ve alt 1dp cizgileri. */
private fun Modifier.horizontalRules(color: Color): Modifier = this.drawBehind {
    val stroke = 1.dp.toPx()
    drawRect(color = color, topLeft = Offset.Zero, size = Size(size.width, stroke))
    drawRect(
        color = color,
        topLeft = Offset(0f, size.height - stroke),
        size = Size(size.width, stroke),
    )
}

/** Genel amacli bilgi seridi. */
@Composable
fun KefeInfoBanner(
    text: String,
    icon: ImageVector? = null,
    modifier: Modifier = Modifier,
) {
    val colors = KefeTheme.colors
    val type = KefeTheme.type

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(KefeShapes.boxSmall)
            .background(colors.surfaceSunken)
            .padding(horizontal = Space.x12, vertical = Space.x10),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = colors.onSurfaceMuted,
                modifier = Modifier.size(IconSize.tiny),
            )
            Spacer(Modifier.width(Space.x8))
        }
        Text(
            text = text,
            style = type.caption,
            color = colors.onSurfaceMuted,
            modifier = Modifier.weight(1f),
        )
    }
}

/** Esitleme durumu. Etiket metni her zaman yazilir - renk tek sinyal olamaz. */
enum class SyncStatus { Synced, Pending, Offline }

@Composable
fun KefeSyncChip(
    state: SyncStatus,
    modifier: Modifier = Modifier,
) {
    val colors = KefeTheme.colors

    val dotColor = when (state) {
        SyncStatus.Synced -> colors.syncOk
        SyncStatus.Pending -> colors.syncPending
        SyncStatus.Offline -> colors.syncOffline
    }
    val label = when (state) {
        SyncStatus.Synced -> "Eşit"
        SyncStatus.Pending -> "Eşitleniyor"
        SyncStatus.Offline -> "Çevrimdışı"
    }

    Row(
        modifier = modifier
            .height(Sizes.chipSmall)
            .clip(KefeShapes.pill)
            .background(colors.surfaceElevated)
            .border(Sizes.hairline, colors.outline, KefeShapes.pill)
            .padding(horizontal = Space.x10),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(SyncDotSize)
                .clip(CircleShape)
                .background(dotColor)
        )
        Spacer(Modifier.width(SyncChipGap))
        Text(
            text = label,
            style = KefeTheme.type.micro.copy(
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
            ),
            color = colors.onSurfaceMuted,
            maxLines = 1,
        )
    }
}

/** Esitleme cipi olculeri - handoff: 7px nokta, 5px bosluk. */
private val SyncDotSize = 7.dp
private val SyncChipGap = 5.dp
