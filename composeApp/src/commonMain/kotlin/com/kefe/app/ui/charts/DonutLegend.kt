package com.kefe.app.ui.charts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kefe.app.ui.format.Money
import com.kefe.app.ui.theme.KefeTheme
import com.kefe.app.ui.theme.Sizes
import com.kefe.app.ui.theme.Space
import com.kefe.app.ui.theme.tabular

/**
 * Halka grafiginin okuma anahtari. Renk tek basina sinyal olmadigi icin her
 * satirda sinif adi ve yuzde metni de bulunur.
 */
@Composable
fun KefeDonutLegend(
    slices: List<DonutSlice>,
    total: Double,
    modifier: Modifier = Modifier,
    horizontal: Boolean = false,
    masked: Boolean = false,
    /**
     * Telefon ozetinde satirlar bosluk yerine 1dp cizgiyle ayrilir ve her satir
     * 10dp dikey dolgu alir (handoff: `padding:10px 0` + `border-top`).
     */
    divided: Boolean = false,
) {
    val colors = KefeTheme.colors
    val rows = collapseDonutSlices(slices, colors.onSurfaceMuted)
    if (rows.isEmpty()) return

    val percentWidth = if (horizontal) 42.dp else 46.dp
    val rowGap = if (horizontal) Space.x8 else Space.x10

    Column(
        modifier = if (horizontal) modifier.widthIn(max = 260.dp) else modifier,
        verticalArrangement = if (divided) Arrangement.Top else Arrangement.spacedBy(rowGap),
    ) {
        rows.forEachIndexed { index, slice ->
            val percent = if (total > 0.0) slice.value / total * 100.0 else 0.0
            if (divided && index > 0) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(Sizes.hairline)
                        .background(colors.outline),
                )
            }
            LegendRow(
                color = slice.color,
                label = slice.label,
                amount = if (masked) Money.masked(digits = 4) else Money.tl(slice.value),
                percent = Money.ratio(percent, 1),
                percentWidth = percentWidth,
                modifier = if (divided) Modifier.padding(vertical = Space.x10) else Modifier,
            )
        }
    }
}

/** Nokta olcusu - tasarimda 9px kare, 3px yuvarlatilmis (daire degil). */
private val DotSize = 9.dp
private val DotShape = RoundedCornerShape(3.dp)

/** Sutunlar arasi bosluk - tasarimda gap:10px. */
private val ColumnGap = Space.x10

/**
 * Sinif adina her kosulda ayrilan asgari genislik. Tutar metni intrinsic
 * genisligiyle olculdugu icin dar kolonlarda adi 0'a sikistirabilir; bu esik
 * adin kaybolmasini engeller, gerekirse ad ellipsis ile kirpilir.
 */
private val LabelMinWidth = 44.dp

/**
 * Tek legend satiri: [nokta] [ad] [esnek bosluk] [tutar saga hizali] [yuzde sutunu].
 *
 * Row + weight yerine olcum sirasi elle kurulur: Row'da agirliksiz cocuklar
 * (tutar, yuzde) once ve intrinsic genislikleriyle olculur, geriye kalan pay
 * agirlikli cocuga (ad) verilir - dar kolonda bu pay 0'a dusup ad tamamen
 * kaybolur. Burada once ada asgari pay ayrilir, tutar kalan alanla sinirlanir.
 */
@Composable
private fun LegendRow(
    color: Color,
    label: String,
    amount: String,
    percent: String,
    percentWidth: Dp,
    modifier: Modifier = Modifier,
) {
    val colors = KefeTheme.colors
    Layout(
        modifier = modifier.fillMaxWidth(),
        content = {
            Box(
                Modifier
                    .size(DotSize)
                    .background(color, DotShape),
            )
            Text(
                text = label,
                style = KefeTheme.type.body,
                color = colors.onSurface,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = amount,
                style = KefeTheme.type.body.tabular(),
                color = colors.onSurface,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.End,
            )
            Text(
                text = percent,
                style = KefeTheme.type.caption.tabular(),
                color = colors.onSurfaceMuted,
                maxLines = 1,
                softWrap = false,
                textAlign = TextAlign.End,
            )
        },
    ) { measurables, constraints ->
        val gapPx = ColumnGap.roundToPx()
        val percentPx = percentWidth.roundToPx()
        val labelMinPx = LabelMinWidth.roundToPx()

        val free = Constraints()
        val dotPlaceable = measurables[0].measure(free)
        val percentPlaceable = measurables[3].measure(
            Constraints(minWidth = percentPx, maxWidth = percentPx),
        )

        val labelPlaceable: Placeable
        val amountPlaceable: Placeable
        val rowWidth: Int

        if (constraints.hasBoundedWidth) {
            rowWidth = constraints.maxWidth
            // Ad + tutar icin kalan alan (nokta, yuzde sutunu ve uc bosluk disi).
            val textSpace =
                (rowWidth - dotPlaceable.width - percentPlaceable.width - gapPx * 3)
                    .coerceAtLeast(0)
            // Tutar en fazla adin asgari payini birakacak kadar genisleyebilir.
            val amountMax = (textSpace - labelMinPx.coerceAtMost(textSpace)).coerceAtLeast(0)
            amountPlaceable = measurables[2].measure(Constraints(maxWidth = amountMax))
            val labelWidth = (textSpace - amountPlaceable.width).coerceAtLeast(0)
            labelPlaceable = measurables[1].measure(
                Constraints(minWidth = labelWidth, maxWidth = labelWidth),
            )
        } else {
            labelPlaceable = measurables[1].measure(free)
            amountPlaceable = measurables[2].measure(free)
            rowWidth = dotPlaceable.width + labelPlaceable.width + amountPlaceable.width +
                percentPlaceable.width + gapPx * 3
        }

        val height = maxOf(
            dotPlaceable.height,
            labelPlaceable.height,
            amountPlaceable.height,
            percentPlaceable.height,
        ).coerceIn(constraints.minHeight, constraints.maxHeight)

        val percentX = rowWidth - percentPlaceable.width
        val amountX = percentX - gapPx - amountPlaceable.width

        layout(rowWidth, height) {
            dotPlaceable.placeRelative(0, (height - dotPlaceable.height) / 2)
            labelPlaceable.placeRelative(
                dotPlaceable.width + gapPx,
                (height - labelPlaceable.height) / 2,
            )
            amountPlaceable.placeRelative(amountX, (height - amountPlaceable.height) / 2)
            percentPlaceable.placeRelative(percentX, (height - percentPlaceable.height) / 2)
        }
    }
}
