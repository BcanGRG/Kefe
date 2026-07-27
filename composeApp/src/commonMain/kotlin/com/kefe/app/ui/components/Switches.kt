package com.kefe.app.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.kefe.app.ui.theme.IconSize
import com.kefe.app.ui.theme.KefeShapes
import com.kefe.app.ui.theme.KefeTheme
import com.kefe.app.ui.theme.Sizes
import com.kefe.app.ui.theme.Space

/**
 * Secim bilesenleri. Material3 Switch/RadioButton/Checkbox kendi renk ve
 * olcu sistemini dayattigi icin hepsi elle cizildi.
 */

private val TrackWidth = 52.dp
private val TrackHeight = 32.dp
private val KnobSize = 26.dp
private val KnobInset = 3.dp
private val SelectorSize = 20.dp
private val SelectorDotSize = 10.dp
private val CheckBoxSize = 22.dp
private val CheckBoxRadius = 7.dp
private const val DisabledAlpha = 0.4f
private const val SwitchAnimationMs = 180

@Composable
fun KefeSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = KefeTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val knobOffset by animateDpAsState(
        targetValue = if (checked) TrackWidth - KnobSize - KnobInset else KnobInset,
        animationSpec = tween(durationMillis = SwitchAnimationMs),
        label = "switchKnob",
    )

    Box(
        modifier = modifier
            .width(TrackWidth)
            .height(Sizes.touchTarget)
            .toggleable(
                value = checked,
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            )
            .alpha(if (enabled) 1f else DisabledAlpha),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .width(TrackWidth)
                .height(TrackHeight)
                .clip(KefeShapes.pill)
                .background(if (checked) colors.accent else colors.surfaceSunken)
                .then(
                    if (checked) Modifier
                    else Modifier.border(Sizes.hairline, colors.outline, KefeShapes.pill),
                ),
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(
                modifier = Modifier
                    .offset(x = knobOffset)
                    .size(KnobSize)
                    .clip(CircleShape)
                    .background(if (checked) colors.onAccent else colors.onSurfaceMuted),
            )
        }
    }
}

@Composable
fun KefeSwitchRow(
    title: String,
    subtitle: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .toggleable(
                value = checked,
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            )
            .padding(vertical = Space.x8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RowTexts(
            title = title,
            subtitle = subtitle,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(Space.x16))
        KefeSwitch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
        )
    }
}

@Composable
fun KefeRadioRow(
    title: String,
    subtitle: String?,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = KefeTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                role = Role.RadioButton,
                onClick = onClick,
            )
            .padding(vertical = Space.x8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(Sizes.touchTarget),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(SelectorSize)
                    .clip(CircleShape)
                    .border(
                        width = if (selected) 2.dp else Sizes.hairline,
                        color = if (selected) colors.accent else colors.outline,
                        shape = CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (selected) {
                    Box(
                        modifier = Modifier
                            .size(SelectorDotSize)
                            .clip(CircleShape)
                            .background(colors.accent),
                    )
                }
            }
        }
        Spacer(Modifier.width(Space.x12))
        RowTexts(
            title = title,
            subtitle = subtitle,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
fun KefeCheckRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    checkIcon: ImageVector,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = KefeTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(CheckBoxRadius)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .toggleable(
                value = checked,
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                role = Role.Checkbox,
                onValueChange = onCheckedChange,
            )
            .padding(vertical = Space.x8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(Sizes.touchTarget),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(CheckBoxSize)
                    .clip(shape)
                    .background(if (checked) colors.accent else colors.surfaceSunken)
                    .then(
                        if (checked) Modifier
                        else Modifier.border(Sizes.hairline, colors.outline, shape),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (checked) {
                    Icon(
                        imageVector = checkIcon,
                        contentDescription = null,
                        tint = colors.onAccent,
                        modifier = Modifier.size(IconSize.tiny),
                    )
                }
            }
        }
        Spacer(Modifier.width(Space.x12))
        RowTexts(
            title = title,
            subtitle = null,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun RowTexts(
    title: String,
    subtitle: String?,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Text(
            text = title,
            style = KefeTheme.type.body,
            color = KefeTheme.colors.onSurface,
        )
        if (subtitle != null) {
            Spacer(Modifier.height(Space.x4))
            Text(
                text = subtitle,
                style = KefeTheme.type.caption,
                color = KefeTheme.colors.onSurfaceMuted,
            )
        }
    }
}
