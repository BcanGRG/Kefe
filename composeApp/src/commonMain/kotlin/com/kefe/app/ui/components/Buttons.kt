package com.kefe.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import com.kefe.app.ui.theme.IconSize
import com.kefe.app.ui.theme.KefeShapes
import com.kefe.app.ui.theme.KefeTheme
import com.kefe.app.ui.theme.Radius
import com.kefe.app.ui.theme.Sizes
import com.kefe.app.ui.theme.Space
import androidx.compose.foundation.shape.RoundedCornerShape

/**
 * Kefe buton ailesi. Golge yoktur; hover/basili geri bildirimi yalniz yuzey
 * tonu farkiyla verilir.
 */

/** Hover: yuzeye beyaz %8 karistirma (parlaklik 1.08 karsiligi). */
private fun Color.hoverLift(): Color = lerp(this, Color.White, 0.08f)

/** Basili: yuzeye siyah %8 karistirma (parlaklik 0.92 karsiligi). */
private fun Color.pressSink(): Color = lerp(this, Color.Black, 0.08f)

@Composable
private fun ButtonLabel(
    text: String,
    style: TextStyle,
    contentColor: Color,
    leadingIcon: ImageVector?,
    iconSize: Dp,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(Space.x8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingIcon != null) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(iconSize),
            )
        }
        Text(text = text, style = style, color = contentColor)
    }
}

@Composable
fun KefePrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
) {
    val colors = KefeTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val pressed by interaction.collectIsPressedAsState()

    val container = when {
        !enabled -> Color.Transparent
        pressed -> colors.accent.pressSink()
        hovered -> colors.accent.hoverLift()
        else -> colors.accent
    }
    val contentColor = if (enabled) colors.onAccent else colors.onSurfaceMuted

    Box(
        modifier = modifier
            .height(Sizes.buttonPrimary)
            .clip(KefeShapes.button)
            .background(container)
            .then(
                if (enabled) Modifier
                else Modifier.border(Sizes.hairline, colors.outline, KefeShapes.button)
            )
            .hoverable(interaction, enabled = enabled)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = Space.x20),
        contentAlignment = Alignment.Center,
    ) {
        ButtonLabel(text, KefeTheme.type.bodyStrong, contentColor, leadingIcon, IconSize.medium)
    }
}

@Composable
fun KefeSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
) {
    val colors = KefeTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val pressed by interaction.collectIsPressedAsState()

    val container = when {
        !enabled -> Color.Transparent
        pressed -> colors.surfaceSunken.pressSink()
        hovered -> colors.surfaceSunken
        else -> Color.Transparent
    }
    val contentColor = if (enabled) colors.onSurface else colors.onSurfaceMuted

    Box(
        modifier = modifier
            .height(Sizes.buttonPrimary)
            .clip(KefeShapes.button)
            .background(container)
            .border(Sizes.hairline, colors.outline, KefeShapes.button)
            .hoverable(interaction, enabled = enabled)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = Space.x20),
        contentAlignment = Alignment.Center,
    ) {
        ButtonLabel(text, KefeTheme.type.bodyStrong, contentColor, leadingIcon, IconSize.medium)
    }
}

@Composable
fun KefeTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
) {
    TextActionButton(
        text = text,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        leadingIcon = leadingIcon,
        activeColor = KefeTheme.colors.accent,
    )
}

@Composable
fun KefeDestructiveTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
) {
    TextActionButton(
        text = text,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        leadingIcon = leadingIcon,
        activeColor = KefeTheme.colors.negative,
    )
}

@Composable
private fun TextActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    leadingIcon: ImageVector?,
    activeColor: Color,
) {
    val colors = KefeTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val pressed by interaction.collectIsPressedAsState()

    val contentColor = when {
        !enabled -> colors.onSurfaceMuted
        pressed -> activeColor.pressSink()
        else -> activeColor
    }
    val container = if (enabled && hovered) colors.surfaceElevated else Color.Transparent

    Box(
        modifier = modifier
            .defaultMinSize(minHeight = Sizes.touchTarget)
            .clip(RoundedCornerShape(Radius.boxSmall))
            .background(container)
            .hoverable(interaction, enabled = enabled)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = Space.x12),
        contentAlignment = Alignment.Center,
    ) {
        ButtonLabel(text, KefeTheme.type.bodyStrong, contentColor, leadingIcon, IconSize.small)
    }
}

@Composable
fun KefeIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = Sizes.touchTarget,
    iconSize: Dp = IconSize.default,
    tint: Color = KefeTheme.colors.onSurfaceMuted,
    enabled: Boolean = true,
    shape: Shape = KefeShapes.button,
) {
    val colors = KefeTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val pressed by interaction.collectIsPressedAsState()

    val container = when {
        !enabled -> Color.Transparent
        pressed -> colors.surfaceElevated.pressSink()
        hovered -> colors.surfaceElevated
        else -> Color.Transparent
    }
    val iconColor = when {
        !enabled -> colors.onSurfaceMuted
        hovered || pressed -> colors.onSurface
        else -> tint
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(container)
            .hoverable(interaction, enabled = enabled)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = iconColor,
            modifier = Modifier.size(iconSize),
        )
    }
}
