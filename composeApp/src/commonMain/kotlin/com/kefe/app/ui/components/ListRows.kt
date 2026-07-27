package com.kefe.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.kefe.app.ui.format.Money
import com.kefe.app.ui.theme.IconSize
import com.kefe.app.ui.theme.KefeShapes
import com.kefe.app.ui.theme.KefeTheme
import com.kefe.app.ui.theme.Sizes
import com.kefe.app.ui.theme.Space
import com.kefe.app.ui.theme.tabular
import kotlin.math.roundToInt

/**
 * Liste satiri: sol ikon kutusu, baslik/alt satir, sagda deger ve degisim.
 *
 * Degisim degeri asla yalniz renkle anlatilmaz - Money.delta her zaman
 * +/- isaretiyle birlikte yazar.
 */
@Composable
fun KefeListRow(
    title: String,
    subtitle: String? = null,
    value: String? = null,
    delta: Double? = null,
    leadingIcon: ImageVector? = null,
    leadingTint: Color = KefeTheme.colors.onSurfaceMuted,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    badges: (@Composable RowScope.() -> Unit)? = null,
    muted: Boolean = false,
) {
    val colors = KefeTheme.colors
    val type = KefeTheme.type

    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()

    val background = if (muted || hovered) colors.surfaceSunken else Color.Transparent
    // Satir zemini cokuk oldugunda ikon kutusu ayni tonda kalirsa kaybolur
    val iconBoxColor = if (muted || hovered) colors.surfaceElevated else colors.surfaceSunken

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = Sizes.touchTarget)
            .clip(KefeShapes.boxMedium)
            .background(background)
            .hoverable(interactionSource)
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = onClick,
                    )
                } else {
                    Modifier
                }
            )
            .padding(
                start = Space.x12,
                end = Space.x12,
                top = Space.x10,
                bottom = Space.x10,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingIcon != null) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(KefeShapes.boxSmall)
                    .background(iconBoxColor),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    tint = if (muted) leadingTint.copy(alpha = 0.65f) else leadingTint,
                    modifier = Modifier.size(IconSize.small),
                )
            }
            Spacer(Modifier.width(Space.x12))
        }

        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Space.x8),
            ) {
                Text(
                    text = title,
                    style = type.body,
                    color = if (muted) colors.onSurfaceMuted else colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                badges?.invoke(this)
            }
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = type.micro,
                    color = colors.onSurfaceMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        if (value != null || delta != null) {
            Spacer(Modifier.width(Space.x12))
            Column(horizontalAlignment = Alignment.End) {
                if (value != null) {
                    Text(
                        text = value,
                        style = type.body.tabular(),
                        color = if (muted) colors.onSurfaceMuted else colors.onSurface,
                        maxLines = 1,
                    )
                }
                if (delta != null) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = Money.delta(delta, 2),
                        style = type.micro.tabular(),
                        color = colors.delta(delta),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/**
 * Katlanabilir bolum basligi: renk noktasi, baslik, toplam, yuzde ve chevron.
 */
@Composable
fun KefeSectionHeader(
    dotColor: Color,
    title: String,
    total: String,
    percent: String?,
    expanded: Boolean,
    onToggle: () -> Unit,
    chevronIcon: ImageVector,
    modifier: Modifier = Modifier,
) {
    val colors = KefeTheme.colors
    val type = KefeTheme.type
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 90f else 0f,
        animationSpec = tween(durationMillis = 180),
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = Sizes.touchTarget)
            .clip(KefeShapes.boxMedium)
            .clickable(onClick = onToggle)
            .padding(horizontal = Space.x12, vertical = Space.x8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(dotColor)
        )
        Spacer(Modifier.width(Space.x10))
        Text(
            text = title,
            style = type.bodyStrong,
            color = colors.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = total,
            style = type.body.tabular(),
            color = colors.onSurface,
            maxLines = 1,
        )
        if (percent != null) {
            Spacer(Modifier.width(Space.x8))
            Text(
                text = percent,
                style = type.micro.tabular(),
                color = colors.onSurfaceMuted,
                maxLines = 1,
            )
        }
        Spacer(Modifier.width(Space.x8))
        Icon(
            imageVector = chevronIcon,
            contentDescription = null,
            tint = colors.onSurfaceMuted,
            modifier = Modifier.size(IconSize.small).rotate(rotation),
        )
    }
}

private val SwipeActionWidth = 76.dp

/**
 * Sola kaydirinca "Düzenle" ve "Sil" aksiyonlarini acan satir sarmalayici.
 * Masaustunde ayni aksiyonlar icin sag tik kancasi (onRightClick) sunulur.
 */
@Composable
fun KefeSwipeableRow(
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    editIcon: ImageVector,
    deleteIcon: ImageVector,
    modifier: Modifier = Modifier,
    onRightClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val colors = KefeTheme.colors
    val type = KefeTheme.type
    val density = LocalDensity.current
    val openPx = with(density) { (SwipeActionWidth * 2).toPx() }

    var target by remember { mutableStateOf(0f) }
    val offsetPx by animateFloatAsState(
        targetValue = target,
        animationSpec = tween(durationMillis = 180),
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
    ) {
        // Arka katman: kaydirma acildikca ortaya cikan aksiyonlar
        Row(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
        ) {
            SwipeAction(
                icon = editIcon,
                label = "Düzenle",
                background = colors.surfaceSunken,
                contentColor = colors.onSurface,
                labelStyle = type.micro,
                onClick = {
                    target = 0f
                    onEdit()
                },
            )
            SwipeAction(
                icon = deleteIcon,
                label = "Sil",
                background = colors.negative,
                contentColor = Color.White,
                labelStyle = type.micro,
                onClick = {
                    target = 0f
                    onDelete()
                },
            )
        }

        Box(
            modifier = Modifier
                .offset { IntOffset(offsetPx.roundToInt(), 0) }
                .fillMaxWidth()
                .background(colors.surface)
                .pointerInput(openPx) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            target = if (target < -openPx / 2f) -openPx else 0f
                        },
                        onDragCancel = { target = 0f },
                    ) { change, dragAmount ->
                        change.consume()
                        target = (target + dragAmount).coerceIn(-openPx, 0f)
                    }
                }
                .then(
                    if (onRightClick != null) {
                        Modifier.pointerInput(onRightClick) {
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    if (event.type == PointerEventType.Press &&
                                        event.buttons.isSecondaryPressed
                                    ) {
                                        onRightClick()
                                    }
                                }
                            }
                        }
                    } else {
                        Modifier
                    }
                )
        ) {
            content()
        }
    }
}

@Composable
private fun SwipeAction(
    icon: ImageVector,
    label: String,
    background: Color,
    contentColor: Color,
    labelStyle: androidx.compose.ui.text.TextStyle,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(SwipeActionWidth)
            .fillMaxHeight()
            .background(background)
            .clickable(onClick = onClick)
            .padding(Space.x8),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(IconSize.medium),
        )
        Spacer(Modifier.height(Space.x4))
        Text(text = label, style = labelStyle, color = contentColor, maxLines = 1)
    }
}
