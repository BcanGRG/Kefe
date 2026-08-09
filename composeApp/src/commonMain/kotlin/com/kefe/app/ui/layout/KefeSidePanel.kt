package com.kefe.app.ui.layout

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kefe.app.ui.components.KefeHairline
import com.kefe.app.ui.format.Money
import com.kefe.app.ui.theme.AssetClassColor
import com.kefe.app.ui.theme.KefeTheme
import com.kefe.app.ui.theme.Sizes
import com.kefe.app.ui.theme.Space
import com.kefe.app.ui.theme.tabular
import com.kefe.app.ui.format.changeText

/** Sag paneldeki tek fiyat satiri. Bicimlenmis metin disaridan gelir. */
@Immutable
data class KefeMarketRow(
    val name: String,
    val priceText: String,
    /** null = bilinmiyor; satir "—" yazar. */
    val changePercent: Double?,
    val assetClass: AssetClassColor,
)

/**
 * Masaustu sag paneli - 320dp. Yalniz masaustunde kalicidir; tablette ve
 * telefonda ayni bilgi Piyasa ekraninda yasar.
 *
 * Degisim sutununda isaret her zaman yazilir ([Money.delta]) - yon bilgisi
 * renge birakilmaz.
 */
@Composable
fun KefeSidePanel(
    title: String,
    updatedAt: String,
    rows: List<KefeMarketRow>,
    note: String,
    modifier: Modifier = Modifier,
    onRowClick: ((KefeMarketRow) -> Unit)? = null,
) {
    val c = KefeTheme.colors
    val t = KefeTheme.type

    Column(
        modifier = modifier
            .width(Sizes.sidePanelWidth)
            .fillMaxHeight()
            .background(c.surfaceElevated)
            .drawBehind {
                val stroke = Sizes.hairline.toPx()
                drawRect(
                    color = c.outline,
                    topLeft = Offset.Zero,
                    size = Size(stroke, size.height),
                )
            }
            .verticalScroll(rememberScrollState()),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = PanelPad, end = PanelPad, top = PanelPad, bottom = Space.x12),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = t.bodyStrong, color = c.onSurface, modifier = Modifier.weight(1f))
            Text(updatedAt, style = t.micro.tabular(), color = c.onSurfaceMuted, maxLines = 1)
        }

        rows.forEach { row ->
            // Tasarimda ilk satirin da ustunde cizgi var: baslik ile listeyi ayirir.
            KefeHairline()
            MarketPanelRow(
                row = row,
                onClick = onRowClick?.let { handler -> { handler(row) } },
            )
        }

        Text(
            text = note,
            style = t.micro.copy(lineHeight = 16.sp),
            color = c.onSurfaceMuted,
            modifier = Modifier.padding(
                start = PanelPad,
                end = PanelPad,
                top = Space.x14,
                bottom = Space.x20,
            ),
        )
    }
}

@Composable
private fun MarketPanelRow(row: KefeMarketRow, onClick: (() -> Unit)?) {
    val c = KefeTheme.colors
    val t = KefeTheme.type
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()

    val base = Modifier
        .fillMaxWidth()
        .background(if (hovered && onClick != null) c.surfaceSunken else Color.Transparent)
    val clickable = if (onClick == null) {
        base
    } else {
        base
            .hoverable(interaction)
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
    }

    Row(
        modifier = clickable.padding(horizontal = PanelPad, vertical = Space.x10),
        horizontalArrangement = Arrangement.spacedBy(Space.x10),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(DotSize)
                .clip(RoundedCornerShape(DotRadius))
                .background(c.assetClass(row.assetClass)),
        )
        Text(
            text = row.name,
            style = t.caption,
            color = c.onSurface,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = row.priceText,
            style = t.caption.tabular(),
            color = c.onSurface,
            maxLines = 1,
            softWrap = false,
        )
        Text(
            text = changeText(row.changePercent),
            style = t.captionSmall.copy(fontWeight = FontWeight.SemiBold).tabular(),
            color = row.changePercent?.let { c.delta(it) } ?: c.onSurfaceMuted,
            textAlign = TextAlign.End,
            maxLines = 1,
            modifier = Modifier.width(ChangeWidth),
        )
    }
}

// --- Olculer ---------------------------------------------------------------

private val PanelPad = 18.dp
private val DotSize = 8.dp
private val DotRadius = 2.dp
private val ChangeWidth = 54.dp
