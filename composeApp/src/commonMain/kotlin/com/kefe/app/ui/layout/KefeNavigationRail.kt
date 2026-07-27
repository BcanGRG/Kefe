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
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kefe.app.ui.components.SyncStatus
import com.kefe.app.ui.icons.KefeIcon
import com.kefe.app.ui.icons.KefeIcons
import com.kefe.app.ui.theme.IconSize
import com.kefe.app.ui.theme.KefeTheme
import com.kefe.app.ui.theme.Sizes
import com.kefe.app.ui.theme.Space

/**
 * Tablet ve masaustu navigasyonunun ortak ogesi. Sayac rozeti yalniz
 * masaustu seridinde gorunur; raylada yer yoktur.
 */
@Immutable
data class KefeNavItem(
    val label: String,
    val icon: ImageVector,
    val badgeCount: Int? = null,
)

/**
 * Tablet navigasyon rayi - 92dp.
 *
 * Yerlesim tasarimdan birebir: 44dp marka kutusu, 28dp bosluk, 60dp sekmeler
 * (6dp araliklarla), sekmelerin ARASINDA duran 56dp "Ekle" aksiyonu, en altta
 * esitleme durumu ve DIKEY avatar yigini.
 *
 * Orta aksiyon sekme DEGILDIR: [selectedIndex] degerini degistirmez, secili
 * duruma girmez. Tasarimda Varliklar ile Hedefler arasinda durur -
 * [addAfterIndex] bunu tasir.
 */
@Composable
fun KefeNavigationRail(
    items: List<KefeNavItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    onAdd: () -> Unit,
    members: List<Pair<String, Int>>,
    syncStatus: SyncStatus,
    modifier: Modifier = Modifier,
    addAfterIndex: Int = 1,
) {
    val c = KefeTheme.colors

    Column(
        modifier = modifier
            .width(Sizes.railWidth)
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
            .padding(top = Space.x20, bottom = Space.x16),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(RailBrandBox)
                .clip(RoundedCornerShape(RailBrandRadius))
                .background(c.accentMuted),
            contentAlignment = Alignment.Center,
        ) {
            KefeIcon(
                icon = KefeIcons.Balance,
                contentDescription = null,
                size = RailBrandIcon,
                tint = c.accent,
            )
        }

        Column(
            modifier = Modifier
                .padding(top = Space.x28)
                .fillMaxWidth()
                .padding(horizontal = Space.x10),
            verticalArrangement = Arrangement.spacedBy(RailTabGap),
        ) {
            items.forEachIndexed { index, item ->
                RailTab(
                    item = item,
                    selected = index == selectedIndex,
                    onClick = { onSelect(index) },
                )
                if (index == addAfterIndex) {
                    // Tasarimda butonun kendi 6px dis boslugu var; kolonun 6px
                    // araligiyla toplanip 12px'e cikar.
                    Box(Modifier.padding(vertical = RailAddMargin)) {
                        RailAddAction(onClick = onAdd)
                    }
                }
            }
        }

        Spacer(Modifier.weight(1f))

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Space.x10),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(SyncDot)
                        .clip(CircleShape)
                        .background(syncStatus.dotColor()),
                )
                Text(
                    text = syncStatus.label(),
                    style = KefeTheme.type.nano.copy(fontWeight = FontWeight.SemiBold),
                    color = c.onSurfaceMuted,
                    maxLines = 1,
                )
            }

            // Rayda avatarlar yan yana degil, ALT ALTA bindirilir.
            Column(verticalArrangement = Arrangement.spacedBy(-RailAvatarOverlap)) {
                members.forEach { (initials, index) ->
                    RailAvatar(
                        initials = initials,
                        index = index,
                        size = RailAvatarSize,
                        fontSize = 11,
                        ringColor = c.surfaceElevated,
                    )
                }
            }
        }
    }
}

// --- Sekme -----------------------------------------------------------------

@Composable
private fun RailTab(
    item: KefeNavItem,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val c = KefeTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()

    val background = when {
        selected -> c.accentMuted
        hovered -> c.surfaceSunken
        else -> Color.Transparent
    }
    val content = if (selected) c.accent else c.onSurfaceMuted

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(RailTabHeight)
            .clip(RoundedCornerShape(RailTabRadius))
            .background(background)
            .hoverable(interaction)
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Tab,
                onClick = onClick,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        KefeIcon(
            icon = item.icon,
            contentDescription = null,
            size = IconSize.default,
            tint = content,
        )
        Spacer(Modifier.height(Space.x4))
        Text(
            text = item.label,
            style = KefeTheme.type.nano.copy(fontWeight = FontWeight.SemiBold),
            color = content,
            maxLines = 1,
        )
    }
}

// --- Ekle aksiyonu ---------------------------------------------------------

@Composable
private fun RailAddAction(onClick: () -> Unit) {
    val c = KefeTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val pressed by interaction.collectIsPressedAsState()

    val fill = when {
        pressed -> lerp(c.accent, Color.Black, 0.08f)
        hovered -> lerp(c.accent, Color.White, 0.08f)
        else -> c.accent
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(Sizes.fabSize)
            .clip(RoundedCornerShape(RailAddRadius))
            .background(fill)
            .hoverable(interaction)
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        KefeIcon(
            icon = KefeIcons.Plus,
            contentDescription = "İşlem ekle",
            size = IconSize.default,
            tint = c.onAccent,
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = "Ekle",
            style = KefeTheme.type.nano.copy(fontWeight = FontWeight.Bold),
            color = c.onAccent,
            maxLines = 1,
        )
    }
}

// --- Avatar ----------------------------------------------------------------

/**
 * Rayda ve yan navigasyonda kullanilan avatar. Ortak [com.kefe.app.ui.components.KefeAvatar]
 * punto secimini kendi olcegine gore yapiyor (32dp -> 15sp); tasarimda bu iki
 * yerde 11/10sp isteniyor, o yuzden burada ayri yazildi.
 */
@Composable
internal fun RailAvatar(
    initials: String,
    index: Int,
    size: Dp,
    fontSize: Int,
    ringColor: Color,
    modifier: Modifier = Modifier,
) {
    val c = KefeTheme.colors
    val background = if (index % 2 == 0) c.avatarA else c.avatarB

    Box(
        modifier = modifier
            .border(1.5.dp, ringColor, CircleShape)
            .size(size)
            .clip(CircleShape)
            .background(background),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initials.trim().take(2),
            style = KefeTheme.type.micro.copy(
                fontSize = fontSize.sp,
                lineHeight = (fontSize + 2).sp,
                fontWeight = FontWeight.SemiBold,
            ),
            color = c.onSurface,
            maxLines = 1,
        )
    }
}

// --- Ortak yardimcilar -----------------------------------------------------

@Composable
internal fun SyncStatus.dotColor(): Color = when (this) {
    SyncStatus.Synced -> KefeTheme.colors.syncOk
    SyncStatus.Pending -> KefeTheme.colors.syncPending
    SyncStatus.Offline -> KefeTheme.colors.syncOffline
}

internal fun SyncStatus.label(): String = when (this) {
    SyncStatus.Synced -> "Eşit"
    SyncStatus.Pending -> "Bekliyor"
    SyncStatus.Offline -> "Çevrimdışı"
}

// --- Olculer ---------------------------------------------------------------

private val RailBrandBox = 44.dp
private val RailBrandRadius = 14.dp
private val RailBrandIcon = 26.dp
private val RailTabHeight = 60.dp
private val RailTabRadius = 14.dp
private val RailTabGap = 6.dp
private val RailAddMargin = 6.dp
private val RailAddRadius = 16.dp
private val RailAvatarSize = 32.dp
private val RailAvatarOverlap = 8.dp
internal val SyncDot = 6.dp
