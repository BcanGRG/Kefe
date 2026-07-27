package com.kefe.app.ui.layout

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kefe.app.ui.components.SyncStatus
import com.kefe.app.ui.icons.KefeIcon
import com.kefe.app.ui.icons.KefeIcons
import com.kefe.app.ui.theme.KefeShapes
import com.kefe.app.ui.theme.KefeTheme
import com.kefe.app.ui.theme.Sizes
import com.kefe.app.ui.theme.Space
import com.kefe.app.ui.theme.tabular

/**
 * Masaustu yan navigasyonu - 240dp.
 *
 * Rayin genisletilmis hali degildir; farkli bir yerlesimdir: marka kutusu ad ve
 * portfoy adiyla birlikte gelir, "Ekle" ortadan cikip en uste TAM GENISLIKTE bir
 * butona donusur, sekmeler ikon+etiket satirlarina iner ve ALTI ust duzey hedef
 * birden gorunur (rayda dort tane vardi). En altta uyeler ve esitleme kutusu.
 */
@Composable
fun KefeSideNavigation(
    brandTitle: String,
    brandSubtitle: String,
    onBrandClick: () -> Unit,
    items: List<KefeNavItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    onAdd: () -> Unit,
    members: List<Pair<String, Int>>,
    memberNames: String,
    syncStatus: SyncStatus,
    syncLine: String,
    modifier: Modifier = Modifier,
    addLabel: String = "İşlem Ekle",
) {
    val c = KefeTheme.colors
    val t = KefeTheme.type

    Column(
        modifier = modifier
            .width(Sizes.navWidth)
            .fillMaxHeight()
            .background(c.surfaceElevated)
            .drawBehind {
                val stroke = Sizes.hairline.toPx()
                drawRect(
                    color = c.outline,
                    topLeft = Offset(size.width - stroke, 0f),
                    size = Size(stroke, size.height),
                )
            }
            .padding(start = Space.x16, end = Space.x16, top = Space.x20, bottom = Space.x16),
    ) {
        // --- Marka / portfoy secici ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(KefeShapes.button)
                .clickable(
                    indication = null,
                    interactionSource = null,
                    role = Role.Button,
                    onClick = onBrandClick,
                )
                .padding(horizontal = Space.x4),
            horizontalArrangement = Arrangement.spacedBy(Space.x12),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(BrandBox)
                    .clip(RoundedCornerShape(BrandRadius))
                    .background(c.accentMuted),
                contentAlignment = Alignment.Center,
            ) {
                KefeIcon(
                    icon = KefeIcons.Balance,
                    contentDescription = null,
                    size = BrandIcon,
                    tint = c.accent,
                )
            }
            Column(Modifier.weight(1f)) {
                Text(brandTitle, style = t.bodyStrong, color = c.onSurface, maxLines = 1)
                Text(
                    text = brandSubtitle,
                    style = t.micro,
                    color = c.onSurfaceMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            KefeIcon(
                icon = KefeIcons.ChevronDown,
                contentDescription = null,
                size = ChevronSize,
                tint = c.onSurfaceMuted,
            )
        }

        // --- Birincil aksiyon ---
        Spacer(Modifier.height(Space.x24))
        SideAddButton(text = addLabel, onClick = onAdd)

        // --- Hedefler ---
        Spacer(Modifier.height(Space.x20))
        Column(verticalArrangement = Arrangement.spacedBy(Space.x4)) {
            items.forEachIndexed { index, item ->
                SideNavRow(
                    item = item,
                    selected = index == selectedIndex,
                    onClick = { onSelect(index) },
                )
            }
        }

        Spacer(Modifier.weight(1f))

        // --- Uyeler + esitleme ---
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(KefeShapes.button)
                .background(c.surfaceSunken)
                .border(Sizes.hairline, c.outline, KefeShapes.button)
                .padding(Space.x12),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Space.x8),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(-MemberAvatarOverlap)) {
                    members.forEach { (initials, index) ->
                        RailAvatar(
                            initials = initials,
                            index = index,
                            size = MemberAvatarSize,
                            fontSize = 10,
                            ringColor = c.surfaceSunken,
                        )
                    }
                }
                Text(
                    text = memberNames,
                    style = t.micro,
                    color = c.onSurfaceMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(Modifier.height(Space.x10))
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Box(
                    Modifier
                        .padding(top = 5.dp)
                        .size(SyncDot)
                        .clip(CircleShape)
                        .background(syncStatus.dotColor()),
                )
                Text(
                    text = syncLine,
                    style = t.micro.tabular(),
                    color = c.onSurfaceMuted,
                )
            }
        }
    }
}

// --- Satir -----------------------------------------------------------------

@Composable
private fun SideNavRow(
    item: KefeNavItem,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val c = KefeTheme.colors
    val t = KefeTheme.type
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()

    val background = when {
        selected -> c.accentMuted
        hovered -> c.surfaceSunken
        else -> Color.Transparent
    }
    val content = if (selected) c.accent else c.onSurfaceMuted

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(NavRowHeight)
            .clip(KefeShapes.button)
            .background(background)
            .hoverable(interaction)
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Tab,
                onClick = onClick,
            )
            .padding(horizontal = Space.x12),
        horizontalArrangement = Arrangement.spacedBy(Space.x12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KefeIcon(
            icon = item.icon,
            contentDescription = null,
            size = NavRowIcon,
            tint = content,
        )
        Text(
            text = item.label,
            style = t.body.copy(
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            ),
            color = content,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (item.badgeCount != null) {
            Text(
                text = item.badgeCount.toString(),
                style = t.micro.tabular(),
                color = c.onSurfaceMuted,
                maxLines = 1,
            )
        }
    }
}

// --- Birincil aksiyon ------------------------------------------------------

@Composable
private fun SideAddButton(text: String, onClick: () -> Unit) {
    val c = KefeTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val pressed by interaction.collectIsPressedAsState()

    val fill = when {
        pressed -> lerp(c.accent, Color.Black, 0.08f)
        hovered -> lerp(c.accent, Color.White, 0.08f)
        else -> c.accent
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(Sizes.fieldDefault)
            .clip(KefeShapes.button)
            .background(fill)
            .hoverable(interaction)
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            ),
        horizontalArrangement = Arrangement.spacedBy(Space.x8, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KefeIcon(
            icon = KefeIcons.Plus,
            contentDescription = null,
            size = AddIcon,
            tint = c.onAccent,
        )
        Text(text = text, style = KefeTheme.type.bodyStrong, color = c.onAccent, maxLines = 1)
    }
}

// --- Olculer ---------------------------------------------------------------

private val BrandBox = 40.dp
private val BrandRadius = 13.dp
private val BrandIcon = 24.dp
private val ChevronSize = 16.dp
private val AddIcon = 20.dp
private val NavRowHeight = 44.dp
private val NavRowIcon = 22.dp
private val MemberAvatarSize = 26.dp
private val MemberAvatarOverlap = 8.dp
