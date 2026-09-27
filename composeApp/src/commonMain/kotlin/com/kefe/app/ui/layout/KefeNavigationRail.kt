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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kefe.app.data.sync.CloudMode
import com.kefe.app.ui.components.KefeCloudMark
import com.kefe.app.ui.components.shortLabel
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
 * HESAP modu ve DIKEY avatar yigini.
 *
 * Alttaki durum [CloudMode]'dan gelir, fiyat tazeliginden DEGIL: once ray fiyat
 * ucunun durumunu "Bekliyor / Çevrimdışı" diye gosteriyordu ve ayni an cipte
 * baska bir sey yaziyordu. Etiket de renk de Banners.kt'deki tek kaynaktan.
 * Dokununca hesap bolumu acilir ([onStatusClick]).
 *
 * Orta aksiyon sekme DEGILDIR: [selectedIndex] degerini degistirmez, secili
 * duruma girmez. Tasarimda Varliklar ile Hedefler arasinda durur -
 * [addAfterIndex] bunu tasir.
 *
 * Ayarlar tablette SEKME DEGILDIR: alt kumenin basindaki dislidir
 * ([onOpenSettings]) ve itilen bir ekran acar; acikken disli secili cizilir
 * ([settingsSelected]), sekmelerin hicbiri secili olmaz. NEDEN: dort sekmenin
 * sonuncusu artik Plan.
 *
 * Pencere icerikten kisaysa ray KAYAR - alt kume dahil. NEDEN: Medium her
 * 600..1239dp genisligi kapsar, yani her YATAY TELEFON (360-430dp boy) rayi
 * alir. Disliyle ray ~590dp ister; kaymasaydi disli ve hesap durumu ekranin
 * altinda kalir, Ayarlar'a baska yoldan ulasilamazdi (tablet Ozet'inde cip ve
 * disli yok). Yer varken alt kume yine en altta durur.
 */
@Composable
fun KefeNavigationRail(
    items: List<KefeNavItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    onAdd: () -> Unit,
    members: List<Pair<String, Int>>,
    cloudMode: CloudMode?,
    modifier: Modifier = Modifier,
    addAfterIndex: Int = 1,
    onStatusClick: () -> Unit = {},
    onOpenSettings: (() -> Unit)? = null,
    settingsSelected: Boolean = false,
) {
    val c = KefeTheme.colors

    // Zemin ve sag kenar cizgisi DIS kutuda: ray kaysa da hep tam boyu doldururlar.
    BoxWithConstraints(
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
            },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                // Yer varken alt kume YINE en altta durur: kolon en az ekran boyu,
                // SpaceBetween ust ve alt grubu iki uca iter. Yer yoksa (yatay telefon)
                // ray kayar ve disli/durum/avatarlar kaydirilarak her zaman erisilir.
                // Kayan kolonda agirlik (Spacer.weight) olamaz; bu yuzden SpaceBetween.
                .heightIn(min = maxHeight)
                .padding(top = Space.x20, bottom = Space.x16),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
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
            }

            Column(
                // Iki grup ust uste geldiginde (yatay telefon) de arada bosluk kalsin.
                modifier = Modifier.fillMaxWidth().padding(top = Space.x16),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Space.x10),
            ) {
                // Ayarlar'in tabletteki kapisi: sekmeyle ayni 60dp gorunum, bulut
                // durumunun ustunde. Ekran okuyucuya SEKME degil dugme: sekmelerin
                // secimini degistirmez, geri oklu bir ekran iter.
                if (onOpenSettings != null) {
                    Box(Modifier.fillMaxWidth().padding(horizontal = Space.x10)) {
                        RailTab(
                            item = KefeNavItem("Ayarlar", KefeIcons.Settings),
                            selected = settingsSelected,
                            onClick = onOpenSettings,
                            role = Role.Button,
                        )
                    }
                }

                // Oturum henuz okunmadiysa bos: "Bu cihazda" deyip bir kare sonra
                // "Eşitlendi"ye atlamasin.
                if (cloudMode != null) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(RailStatusRadius))
                            .clickable(
                                indication = null,
                                interactionSource = null,
                                role = Role.Button,
                                onClick = onStatusClick,
                            )
                            .padding(horizontal = Space.x4, vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        KefeCloudMark(cloudMode)
                        Text(
                            text = cloudMode.shortLabel(),
                            style = KefeTheme.type.nano.copy(fontWeight = FontWeight.SemiBold),
                            color = c.onSurfaceMuted,
                            maxLines = 1,
                        )
                    }
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
}

// --- Sekme -----------------------------------------------------------------

@Composable
private fun RailTab(
    item: KefeNavItem,
    selected: Boolean,
    onClick: () -> Unit,
    /** Sekmeler Tab; ayni gorunumdeki Ayarlar dislisi Button (bkz. alt kume). */
    role: Role = Role.Tab,
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
                role = role,
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
private val RailStatusRadius = 8.dp
