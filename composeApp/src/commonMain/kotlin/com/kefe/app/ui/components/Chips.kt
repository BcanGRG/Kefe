package com.kefe.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kefe.app.ui.theme.IconSize
import com.kefe.app.ui.theme.KefeShapes
import com.kefe.app.ui.theme.KefeTheme
import com.kefe.app.ui.theme.Sizes
import com.kefe.app.ui.theme.Space

/**
 * Cip ve segment secicileri. Secim durumu renk disinda kenarlik ve metin
 * agirligiyla da ayirt edilir.
 */

/**
 * Cip. Handoff'ta secilmemis cip ZEMINSIZDIR (`background:transparent`) - yalniz
 * kenarlik tasir; secili cip `accent-muted` zemin, `accent` kenarlik ve
 * `on-surface` metin alir. Metin boyu her iki durumda da 13px'tir; ayrimi
 * kalinlik yapar.
 */
@Composable
fun KefeChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = Sizes.chipMedium,
    horizontalPadding: Dp = Space.x12,
    textStyle: TextStyle = KefeTheme.type.caption,
    selectedTextStyle: TextStyle = KefeTheme.type.caption.copy(fontWeight = FontWeight.SemiBold),
    leadingIcon: ImageVector? = null,
    enabled: Boolean = true,
) {
    val colors = KefeTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val pressed by interaction.collectIsPressedAsState()

    val baseBackground = if (selected) colors.accentMuted else Color.Transparent
    val hoverBackground = if (selected) {
        lerp(baseBackground, Color.White, 0.08f)
    } else {
        colors.surfaceElevated
    }
    val background = when {
        !enabled -> baseBackground
        pressed -> lerp(hoverBackground, Color.Black, 0.08f)
        hovered -> hoverBackground
        else -> baseBackground
    }
    val borderColor = when {
        selected -> colors.accent
        hovered && enabled -> colors.onSurfaceMuted
        else -> colors.outline
    }
    val contentColor = when {
        !enabled -> colors.onSurfaceMuted
        selected -> colors.onSurface
        hovered -> colors.onSurface
        else -> colors.onSurfaceMuted
    }

    Box(
        modifier = modifier
            .height(height)
            .clip(KefeShapes.pill)
            .background(background)
            .border(Sizes.hairline, borderColor, KefeShapes.pill)
            .hoverable(interaction, enabled = enabled)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = horizontalPadding),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(Space.x4),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leadingIcon != null) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(IconSize.tiny),
                )
            }
            Text(
                text = text,
                style = if (selected) selectedTextStyle else textStyle,
                color = contentColor,
                maxLines = 1,
            )
        }
    }
}

/**
 * Kapali segment secici. Secili sekme yuzeyi kayarak yer degistirir; boylece
 * hangi sekmeden hangisine gecildigi gorulur.
 */
@Composable
fun KefeSegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (options.isEmpty()) return
    val colors = KefeTheme.colors
    val count = options.size
    val safeIndex = selectedIndex.coerceIn(0, count - 1)
    val position by animateFloatAsState(
        targetValue = safeIndex.toFloat(),
        label = "segmentPosition",
    )

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(Sizes.segment)
            .clip(KefeShapes.pill)
            .background(colors.surfaceSunken)
            .padding(SegmentInset),
    ) {
        // Genislik sinirsizsa (ornegin yatay kaydirma icinde) makul bir taban kullanilir.
        val available = if (maxWidth.value.isFinite()) maxWidth else Sizes.navWidth
        val segmentWidth = available / count

        Box(
            modifier = Modifier
                .offset(x = segmentWidth * position)
                .width(segmentWidth)
                .fillMaxHeight()
                .clip(KefeShapes.pill)
                .background(colors.surfaceElevated),
        )

        Row(modifier = Modifier.fillMaxSize()) {
            options.forEachIndexed { index, label ->
                val active = index == safeIndex
                val segmentInteraction = remember { MutableInteractionSource() }
                Box(
                    modifier = Modifier
                        .width(segmentWidth)
                        .fillMaxHeight()
                        .clip(KefeShapes.pill)
                        .hoverable(segmentInteraction)
                        .clickable(
                            interactionSource = segmentInteraction,
                            indication = null,
                            role = Role.Tab,
                            onClick = { onSelect(index) },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label,
                        style = if (active) KefeTheme.type.bodyStrong else KefeTheme.type.caption,
                        color = if (active) colors.onSurface else colors.onSurfaceMuted,
                    )
                }
            }
        }
    }
}

private val SegmentInset: Dp = 3.dp

/** Grafiklerde kullanilan varsayilan donem etiketleri. */
val KefePeriodOptions: List<String> = listOf("1A", "3A", "6A", "1Y", "Tümü")

/**
 * Donem secici - KefeChip uzerine ince sarmalayici.
 *
 * Cipler satiri doldurur: her biri esit paya sahiptir, son secenek ("Tümü")
 * daha uzun oldugu icin 1.3 paylik yer alir (handoff: `flex:1` / `flex:1.3`).
 *
 * [lastWeight] o istisnayi kapatabilmek icindir: Gün/Hafta/Ay gibi son
 * secenegin en KISA oldugu bir kumede 1.3 pay vermek cipleri egri gosterir.
 */
@Composable
fun KefePeriodChips(
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    options: List<String> = KefePeriodOptions,
    lastWeight: Float = 1.3f,
    /**
     * Cipler satiri DOLDURSUN mu.
     *
     * false ise her cip kendi metni kadar yer kaplar ve satir gerekirse yatay
     * kayar. Yedi secenek (Gün/Hafta/1A/3A/6A/1Y/Tümü) esit paya bolununce
     * "Tümü" dar telefonda kirpiliyordu.
     */
    fill: Boolean = true,
) {
    if (!fill) {
        Row(
            modifier = modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(PeriodChipGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            options.forEachIndexed { index, label ->
                KefeChip(
                    text = label,
                    selected = index == selectedIndex,
                    onClick = { onSelect(index) },
                    height = Sizes.chipMedium,
                    horizontalPadding = Space.x10,
                )
            }
        }
        return
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(PeriodChipGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        options.forEachIndexed { index, label ->
            KefeChip(
                text = label,
                selected = index == selectedIndex,
                onClick = { onSelect(index) },
                modifier = Modifier.weight(if (index == options.lastIndex) lastWeight else 1f),
                height = Sizes.chipMedium,
                horizontalPadding = Space.x4,
            )
        }
    }
}

/** Donem cipleri arasi bosluk - handoff `gap:6px`. */
private val PeriodChipGap: Dp = 6.dp
