package com.kefe.app.ui.layout

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kefe.app.ui.icons.KefeIcons
import com.kefe.app.ui.theme.IconSize
import com.kefe.app.ui.theme.KefeShapes
import com.kefe.app.ui.theme.KefeTheme
import com.kefe.app.ui.theme.Radius
import com.kefe.app.ui.theme.Sizes
import com.kefe.app.ui.theme.Space

/**
 * Telefon alt navigasyonu. Dort sekme ve ortada bir aksiyon vardir.
 *
 * ONEMLI: orta slot SEKME DEGILDIR - secili duruma girmez, `selected` degerini
 * degistirmez. Ust kenardan tasar ve cevresindeki `surface` halka ile
 * seritten ayrilir; boylece bir sekme gibi okunmaz.
 *
 * Sekmeler [items]'tan gelir (ray gibi): etiket ve ikon tek yerde,
 * KefeDestinations'ta tanimli. NEYDI: dort sekme burada elle yaziliydi; Plan
 * Ayarlar'in yerini alinca iki liste ayri ayri degismek zorundaydi. Rozet
 * ([KefeNavItem.badgeCount]) burada cizilmez - serit dar.
 *
 * [selected] -1 ise hicbir sekme secili cizilmez (Ayarlar acikken ya da kok
 * listede yokken). NEYDI: kabuk `coerceAtLeast(0)` ile Ozet'i yanlislikla
 * secili gosteriyordu.
 *
 * Aksiyonun yeri [addAfterIndex]'ten sonraki bosluktur - rayla ayni sozlesme.
 */
@Composable
fun KefeBottomNav(
    items: List<KefeNavItem>,
    selected: Int,
    onSelect: (Int) -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
    addAfterIndex: Int = 1,
) {
    val colors = KefeTheme.colors

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(Sizes.bottomNav)
            .background(colors.surface)
            .drawBehind {
                val stroke = Sizes.hairline.toPx()
                drawRect(
                    color = colors.outline,
                    topLeft = Offset.Zero,
                    size = Size(size.width, stroke),
                )
            },
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = Space.x8,
                    end = Space.x8,
                    top = Space.x8,
                    bottom = BottomInset,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Sekmeler 52dp'dir; kalan 8/18dp dolgu seridi 78dp'ye tamamlar.
            items.forEachIndexed { index, item ->
                NavTab(item.icon, item.label, selected == index) { onSelect(index) }
                // Orta aksiyonun yeri - sekme sayilmaz, bos birakilir
                if (index == addAfterIndex) Spacer(Modifier.weight(1f))
            }
        }

        // Aksiyonun ALT kenari sekme alaninin alt kenariyla hizalanir; 62dp'lik
        // kutu 52dp'lik sekme alanindan yukari tasar (handoff: `margin-top:-26px`).
        AddAction(
            onClick = onAdd,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(y = -BottomInset),
        )
    }
}

@Composable
private fun RowScope.NavTab(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = KefeTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()

    val contentColor = when {
        selected -> colors.accent
        hovered -> colors.onSurface
        else -> colors.onSurfaceMuted
    }

    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .clip(KefeShapes.button)
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
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(IconSize.default),
        )
        Spacer(Modifier.height(Space.x4))
        Text(
            text = label,
            style = KefeTheme.type.micro.copy(
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            ),
            color = contentColor,
            maxLines = 1,
        )
    }
}

/** Ortadaki one cikan aksiyon: 56dp accent kutu, cevresinde 3dp surface halka. */
@Composable
private fun AddAction(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = KefeTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val pressed by interaction.collectIsPressedAsState()

    val fill = when {
        pressed -> lerp(colors.accent, Color.Black, 0.08f)
        hovered -> lerp(colors.accent, Color.White, 0.08f)
        else -> colors.accent
    }

    Box(
        modifier = modifier
            .size(Sizes.fabSize + FabRing * 2)
            .clip(RoundedCornerShape(Radius.fab + FabRing))
            .background(colors.surface),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(Sizes.fabSize)
                .clip(KefeShapes.fab)
                .background(fill)
                .hoverable(interaction)
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    role = Role.Button,
                    onClick = onClick,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = KefeIcons.Plus,
                contentDescription = "İşlem ekle",
                tint = colors.onAccent,
                modifier = Modifier.size(AddIconSize),
            )
        }
    }
}

/** Serit yuksekligi 78dp; alt guvenli alan icin 18dp bosluk kalir. */
private val BottomInset = 18.dp

/** Aksiyonu seritten ayiran surface halka kalinligi. */
private val FabRing = 3.dp

private val AddIconSize = 26.dp
