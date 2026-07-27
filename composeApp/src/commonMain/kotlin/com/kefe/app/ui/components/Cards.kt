package com.kefe.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.kefe.app.ui.theme.KefeShapes
import com.kefe.app.ui.theme.KefeTheme
import com.kefe.app.ui.theme.Radius
import com.kefe.app.ui.theme.Sizes
import com.kefe.app.ui.theme.Space

/**
 * Kart yuzeyleri. Yukselti golge ile degil, yuzey tonu ve kenarlik ile
 * anlatilir - bu yuzden hicbir kartta shadow/elevation yoktur.
 */

@Composable
fun KefeCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(Space.x16),
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = KefeTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()

    val borderColor = if (onClick != null && hovered) colors.accentMuted else colors.outline

    Column(
        modifier = modifier
            .clip(KefeShapes.card)
            .background(colors.surfaceElevated)
            .border(Sizes.hairline, borderColor, KefeShapes.card)
            .clickableCard(onClick, interaction)
            .padding(contentPadding),
        content = content,
    )
}

/**
 * Vurgulu kart: accent kenarlik ve icerde 3dp accentMuted halka.
 * Halka, ic tarafta ikinci bir kenarlik katmani olarak cizilir.
 */
@Composable
fun KefeAccentCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(Space.x16),
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = KefeTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val innerShape = RoundedCornerShape(Radius.card - Sizes.hairline)

    Column(
        modifier = modifier
            .clip(KefeShapes.card)
            .background(colors.surfaceElevated)
            .border(Sizes.hairline, colors.accent, KefeShapes.card)
            .padding(Sizes.hairline)
            .border(AccentRingWidth, colors.accentMuted, innerShape)
            .clickableCard(onClick, interaction)
            .padding(contentPadding),
        content = content,
    )
}

@Composable
fun KefeSunkenBox(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(Space.x12),
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .clip(KefeShapes.boxSmall)
            .background(KefeTheme.colors.surfaceSunken)
            .padding(contentPadding),
        content = content,
    )
}

/** Kesikli kenarlikli kart - "ekle" gibi henuz dolmamis alanlar icin. */
@Composable
fun KefeDashedCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(Space.x16),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = KefeTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val borderColor = if (onClick != null && hovered) colors.accentMuted else colors.outline

    Column(
        modifier = modifier
            .clip(KefeShapes.card)
            .drawBehind {
                val stroke = Sizes.hairline.toPx()
                val inset = stroke / 2f
                drawRoundRect(
                    color = borderColor,
                    topLeft = Offset(inset, inset),
                    size = Size(size.width - stroke, size.height - stroke),
                    cornerRadius = CornerRadius(Radius.card.toPx()),
                    style = Stroke(
                        width = stroke,
                        pathEffect = PathEffect.dashPathEffect(
                            floatArrayOf(DashOn.toPx(), DashOff.toPx()),
                        ),
                    ),
                )
            }
            .clickableCard(onClick, interaction)
            .padding(contentPadding),
        content = content,
    )
}

/** 1dp yatay ayirici. */
@Composable
fun KefeHairline(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(Sizes.hairline)
            .background(KefeTheme.colors.outline),
    )
}

private fun Modifier.clickableCard(
    onClick: (() -> Unit)?,
    interaction: MutableInteractionSource,
): Modifier = if (onClick == null) {
    this
} else {
    this
        .hoverable(interaction)
        .clickable(
            interactionSource = interaction,
            indication = null,
            role = Role.Button,
            onClick = onClick,
        )
}

private val AccentRingWidth = 3.dp
private val DashOn = 6.dp
private val DashOff = 5.dp
