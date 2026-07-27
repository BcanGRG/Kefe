package com.kefe.app.ui.layout

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kefe.app.ui.icons.KefeIcon
import com.kefe.app.ui.icons.KefeIcons
import com.kefe.app.ui.theme.KefeShapes
import com.kefe.app.ui.theme.KefeTheme
import com.kefe.app.ui.theme.Sizes
import com.kefe.app.ui.theme.Space
import com.kefe.app.ui.theme.tabular

/**
 * Masaustu ust cubugu. Telefon/tablet baslik satirindan iki noktada ayrilir:
 * baslik altina baglam satiri girer ve saga 220dp'lik kalici bir arama alani
 * yerlesir - masaustunde arama bir ekran degil, cubuk ogesidir.
 */
@Composable
fun KefeTopBar(
    title: String,
    subtitle: String,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    masked: Boolean,
    onToggleMask: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    searchPlaceholder: String = "Varlık veya hedef ara",
) {
    val c = KefeTheme.colors
    val t = KefeTheme.type

    Row(
        modifier = modifier
            .fillMaxWidth()
            .drawBehind {
                val stroke = Sizes.hairline.toPx()
                drawRect(
                    color = c.outline,
                    topLeft = Offset(0f, size.height - stroke),
                    size = Size(size.width, stroke),
                )
            }
            .padding(horizontal = Space.x28, vertical = TopBarPaddingV),
        horizontalArrangement = Arrangement.spacedBy(Space.x16),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = t.h2,
                color = c.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = t.micro.tabular(),
                color = c.onSurfaceMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        TopBarSearchField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            placeholder = searchPlaceholder,
        )

        TopBarIconButton(
            icon = if (masked) KefeIcons.EyeOff else KefeIcons.Eye,
            contentDescription = if (masked) "Tutarları göster" else "Tutarları gizle",
            onClick = onToggleMask,
        )
        TopBarIconButton(
            icon = KefeIcons.Refresh,
            contentDescription = "Fiyatları yenile",
            onClick = onRefresh,
        )
    }
}

// --- Arama -----------------------------------------------------------------

@Composable
private fun TopBarSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
) {
    val c = KefeTheme.colors
    val t = KefeTheme.type
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()

    Row(
        modifier = Modifier
            .width(SearchWidth)
            .height(TopBarControl)
            .clip(KefeShapes.button)
            .background(c.surfaceElevated)
            .border(
                width = Sizes.hairline,
                color = if (focused) c.accent else c.outline,
                shape = KefeShapes.button,
            )
            .padding(horizontal = Space.x14),
        horizontalArrangement = Arrangement.spacedBy(Space.x8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KefeIcon(
            icon = KefeIcons.Search,
            contentDescription = null,
            size = SearchIcon,
            tint = c.onSurfaceMuted,
        )
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f),
            textStyle = t.caption.copy(color = c.onSurface),
            singleLine = true,
            cursorBrush = SolidColor(c.accent),
            interactionSource = interaction,
            decorationBox = { innerTextField ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            style = t.caption,
                            color = c.onSurfaceMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    innerTextField()
                }
            },
        )
    }
}

// --- Ikon butonu -----------------------------------------------------------

/**
 * Masaustu cubugundaki 40dp kenarlikli buton. Ortak [com.kefe.app.ui.components.KefeIconButton]
 * kenarliksiz ve 44dp'dir; bu cubukta arama alaniyla ayni yuksekligi tutmasi gerekir.
 */
@Composable
private fun TopBarIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    val c = KefeTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()

    Box(
        modifier = Modifier
            .size(TopBarControl)
            .clip(KefeShapes.button)
            .background(Color.Transparent)
            .border(
                width = Sizes.hairline,
                color = if (hovered) c.onSurfaceMuted else c.outline,
                shape = KefeShapes.button,
            )
            .hoverable(interaction)
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        KefeIcon(
            icon = icon,
            contentDescription = contentDescription,
            size = TopBarIcon,
            tint = if (hovered) c.onSurface else c.onSurfaceMuted,
        )
    }
}

// --- Olculer ---------------------------------------------------------------

private val TopBarPaddingV = 18.dp
private val TopBarControl = 40.dp
private val TopBarIcon = 20.dp
private val SearchWidth = 220.dp
private val SearchIcon = 17.dp
