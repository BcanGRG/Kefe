package com.kefe.app.ui.screens.account

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.kefe.app.domain.model.MemberPermission
import com.kefe.app.ui.components.KefeBottomSheet
import com.kefe.app.ui.components.KefeDashedCard
import com.kefe.app.ui.components.KefeHairline
import com.kefe.app.ui.format.trLower
import com.kefe.app.ui.format.trUpper
import com.kefe.app.ui.icons.KefeIcon
import com.kefe.app.ui.icons.KefeIcons
import com.kefe.app.ui.theme.IconSize
import com.kefe.app.ui.theme.KefeShapes
import com.kefe.app.ui.theme.KefeTheme
import com.kefe.app.ui.theme.Sizes
import com.kefe.app.ui.theme.Space
import com.kefe.app.ui.theme.tabular

/**
 * Paylasim. Iki uye de ayni portfoyu gorur; kimin ne ekledigi Aktivite'de
 * kalir. Tek uye kaldiginda ekran davet cagrisina doner.
 */
@Composable
fun ShareScreen(
    state: ShareUiState,
    onIntent: (ShareIntent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            AccountTopBar(
                title = "Paylaşım",
                onBack = onBack,
                action = { InviteHeaderButton(onClick = { onIntent(ShareIntent.OpenInvite) }) },
            )

            Column(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(start = Space.x16, end = Space.x16, bottom = Space.x24),
            ) {
                if (state.isAlone) {
                    ShareEmpty(onInvite = { onIntent(ShareIntent.OpenInvite) })
                } else {
                    ShareMembers(state, onIntent)
                }
            }
        }

        InviteSheet(state, onIntent)
    }
}

// --- Baslik eylemi ---------------------------------------------------------

@Composable
private fun InviteHeaderButton(onClick: () -> Unit) {
    val c = KefeTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()

    Row(
        modifier = Modifier
            .height(Sizes.chipLarge)
            .clip(KefeShapes.pill)
            .background(if (hovered) c.accent.copy(alpha = 0.24f) else c.accentMuted)
            .border(Sizes.hairline, c.accent, KefeShapes.pill)
            .hoverable(interaction)
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = Space.x14),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KefeIcon(KefeIcons.Plus, null, size = IconSize.tiny, tint = c.accent)
        Text(
            "Davet et",
            style = KefeTheme.type.caption.copy(fontWeight = FontWeight.SemiBold),
            color = c.onSurface,
            maxLines = 1,
        )
    }
}

// --- Uye listesi -----------------------------------------------------------

@Composable
private fun ShareMembers(state: ShareUiState, onIntent: (ShareIntent) -> Unit) {
    val c = KefeTheme.colors
    val t = KefeTheme.type

    Text(
        "${state.portfolioName} · ${state.members.size} üye",
        style = t.caption,
        color = c.onSurfaceMuted,
    )
    Spacer(Modifier.height(Space.x12))

    AccountGroupCard {
        state.members.forEachIndexed { index, member ->
            if (index > 0) KefeHairline()
            MemberRow(
                member = member,
                menuOpen = state.permissionTargetId == member.id,
                onMenu = { onIntent(ShareIntent.ToggleMemberMenu(member.id)) },
            )
        }

        state.permissionTarget?.let { target ->
            KefeHairline()
            PermissionBlock(
                member = target,
                onSelect = { onIntent(ShareIntent.SetPermission(target.id, it)) },
                onRemove = { onIntent(ShareIntent.RemoveMember(target.id)) },
            )
        }
    }

    Spacer(Modifier.height(Space.x14))
    ShareNote("İki üye de aynı portföyü görür. Kim ne ekledi Aktivite'de kayıtlı kalır.")
}

@Composable
private fun MemberRow(member: ShareMemberRow, menuOpen: Boolean, onMenu: () -> Unit) {
    val c = KefeTheme.colors
    val t = KefeTheme.type

    Row(
        modifier = Modifier.fillMaxWidth().padding(Space.x14),
        horizontalArrangement = Arrangement.spacedBy(Space.x12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AccountAvatar(
            initials = member.initials,
            index = member.index,
            size = Sizes.avatarLarge,
            fontSize = 13.sp,
        )

        Column(Modifier.weight(1f)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Space.x8),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(member.name, style = t.bodyStrong, color = c.onSurface)
                RoleTag(isOwner = member.isOwner)
            }
            Spacer(Modifier.height(3.dp))
            Text(
                "${member.permission.label()} · ${member.lastSeen}",
                style = t.micro,
                color = c.onSurfaceMuted,
            )
        }

        if (member.isCurrentUser) {
            Text("Siz", style = t.micro, color = c.onSurfaceMuted)
        } else {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(KefeShapes.boxSmall)
                    .background(if (menuOpen) c.surfaceSunken else Color.Transparent)
                    .clickable(
                        indication = null,
                        interactionSource = null,
                        role = Role.Button,
                        onClick = onMenu,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                // Tasarimda uye satirindaki menu noktalari DIKEY dizilir.
                KefeIcon(
                    icon = KefeIcons.MoreVertical,
                    contentDescription = "${member.name} için seçenekler",
                    size = 20.dp,
                    tint = if (menuOpen) c.onSurface else c.onSurfaceMuted,
                )
            }
        }
    }
}

/** Rol rozeti - ortak rozetten farkli olarak 7dp yan dolgu ve .06em aralik. */
@Composable
private fun RoleTag(isOwner: Boolean) {
    val c = KefeTheme.colors
    Box(
        modifier = Modifier
            .clip(KefeShapes.pill)
            .background(if (isOwner) c.accentMuted else c.surfaceSunken)
            .then(
                if (isOwner) Modifier
                else Modifier.border(Sizes.hairline, c.outline, KefeShapes.pill)
            )
            .padding(horizontal = 7.dp, vertical = 2.dp),
    ) {
        Text(
            text = (if (isOwner) "Sahip" else "Üye").trUpper(),
            style = KefeTheme.type.nano.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.06.em,
            ),
            color = if (isOwner) c.accent else c.onSurfaceMuted,
        )
    }
}

@Composable
private fun PermissionBlock(
    member: ShareMemberRow,
    onSelect: (MemberPermission) -> Unit,
    onRemove: () -> Unit,
) {
    val c = KefeTheme.colors
    val options = listOf(MemberPermission.CanEdit, MemberPermission.ViewOnly)

    Column(
        Modifier
            .fillMaxWidth()
            .background(c.surfaceSunken)
            .padding(horizontal = Space.x14, vertical = Space.x12),
    ) {
        Text(
            text = "${member.name.possessive()} yetkisi".trUpper(),
            style = KefeTheme.type.label(11, 0.06),
            color = c.onSurfaceMuted,
        )
        Spacer(Modifier.height(Space.x8))
        AccountPillSegment(
            options = options.map { it.label() },
            selectedIndex = options.indexOf(member.permission).coerceAtLeast(0),
            onSelect = { onSelect(options[it]) },
        )
        Spacer(Modifier.height(Space.x8))
        AccountFlatButton(
            text = "Portföyden çıkar",
            onClick = onRemove,
            contentColor = c.negative,
            modifier = Modifier.fillMaxWidth(),
            hoverBackground = c.negative.copy(alpha = 0.12f),
        )
    }
}

/**
 * Turkce iyelik eki: son unluye gore ın/in/un/ün, ad unluyle bitiyorsa
 * kaynastirma n'si eklenir. "Ayşe" -> "Ayşe'nin", "Volkan" -> "Volkan'ın".
 */
private fun String.possessive(): String {
    val lower = trLower()
    val lastVowel = lower.lastOrNull { it in "aeıioöuü" } ?: 'i'
    val suffix = when (lastVowel) {
        'a', 'ı' -> "ın"
        'o', 'u' -> "un"
        'ö', 'ü' -> "ün"
        else -> "in"
    }
    val endsWithVowel = lower.lastOrNull()?.let { it in "aeıioöuü" } == true
    return "$this'${if (endsWithVowel) "n" else ""}$suffix"
}

// --- Bos uye durumu --------------------------------------------------------

@Composable
private fun ShareEmpty(onInvite: () -> Unit) {
    val c = KefeTheme.colors
    val t = KefeTheme.type

    Column(
        Modifier
            .fillMaxWidth()
            .clip(KefeShapes.card)
            .background(c.surfaceElevated)
            .border(Sizes.hairline, c.outline, KefeShapes.card)
            .padding(horizontal = 18.dp, vertical = Space.x24),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Ikinci halka 10dp bindirilir; offset yerine negatif aralik kullanilir
        // ki grubun toplam genisligi de tasarimdaki gibi 10dp kisalsin.
        Row(
            horizontalArrangement = Arrangement.spacedBy((-10).dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AccountAvatar(initials = "VK", index = 0, size = Sizes.touchTarget, fontSize = 15.sp)
            Box(
                modifier = Modifier
                    .size(Sizes.touchTarget)
                    .drawBehind {
                        drawCircle(
                            color = c.outline,
                            radius = size.minDimension / 2f - 0.75.dp.toPx(),
                            style = Stroke(
                                width = 1.5.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(
                                    floatArrayOf(4.dp.toPx(), 3.dp.toPx()),
                                ),
                            ),
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                KefeIcon(KefeIcons.Plus, null, size = 20.dp, tint = c.onSurfaceMuted)
            }
        }

        Spacer(Modifier.height(Space.x16))
        Text("Portföyü paylaşın", style = t.h2, color = c.onSurface)
        Spacer(Modifier.height(Space.x8))
        Text(
            "Eşiniz aynı birikimi kendi telefonundan görür, kayıt da ekleyebilir. " +
                "Tek başınıza da her şey çalışmaya devam eder.",
            style = t.caption.copy(lineHeight = 19.sp),
            color = c.onSurfaceMuted,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(18.dp))
        AccountFilledButton(
            text = "Davet et",
            onClick = onInvite,
            modifier = Modifier.fillMaxWidth(),
            height = Sizes.fieldDefault,
        )
    }

    Spacer(Modifier.height(Space.x12))
    KefeDashedCard(contentPadding = PaddingValues(Space.x14)) {
        Text(
            "Davet bağlantısı 7 gün geçerlidir; dilediğiniz zaman iptal edebilirsiniz.",
            style = KefeTheme.type.caption.copy(lineHeight = 19.sp),
            color = KefeTheme.colors.onSurfaceMuted,
        )
    }
}

@Composable
private fun ShareNote(text: String) {
    val c = KefeTheme.colors
    Row(horizontalArrangement = Arrangement.spacedBy(Space.x8)) {
        KefeIcon(
            icon = KefeIcons.Info,
            contentDescription = null,
            modifier = Modifier.padding(top = 2.dp),
            size = 15.dp,
            tint = c.onSurfaceMuted,
        )
        Text(
            text,
            style = KefeTheme.type.micro.copy(lineHeight = 17.sp),
            color = c.onSurfaceMuted,
        )
    }
}

// --- Davet sayfasi ---------------------------------------------------------

@Composable
private fun InviteSheet(state: ShareUiState, onIntent: (ShareIntent) -> Unit) {
    val c = KefeTheme.colors
    val t = KefeTheme.type

    KefeBottomSheet(
        visible = state.inviteVisible,
        onDismiss = { onIntent(ShareIntent.DismissInvite) },
        title = "Davet et",
        closeIcon = KefeIcons.Close,
    ) {
        Text(
            "Bağlantıyı paylaşın ya da kodu okutun. Davet ${state.inviteValidDays} gün geçerli.",
            style = t.caption.copy(lineHeight = 19.sp),
            color = c.onSurfaceMuted,
        )

        Spacer(Modifier.height(Space.x16))
        Column(
            Modifier
                .fillMaxWidth()
                .clip(KefeShapes.card)
                .background(c.surfaceElevated)
                .border(Sizes.hairline, c.outline, KefeShapes.card)
                .padding(Space.x20),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(180.dp)
                    .clip(KefeShapes.button)
                    .background(QrPaper)
                    .padding(Space.x12),
            ) {
                InviteQrCode(Modifier.size(156.dp))
            }

            Spacer(Modifier.height(Space.x20))
            Text(
                "davet kodu".trUpper(),
                style = t.label(11, 0.06),
                color = c.onSurfaceMuted,
            )
            Spacer(Modifier.height(Space.x10))
            Row(horizontalArrangement = Arrangement.spacedBy(Space.x8)) {
                state.inviteCode.forEach { digit ->
                    Box(
                        modifier = Modifier
                            .width(36.dp)
                            .height(Sizes.fieldDefault)
                            .clip(KefeShapes.boxSmall)
                            .background(c.surface)
                            .border(Sizes.hairline, c.outline, KefeShapes.boxSmall),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(digit.toString(), style = t.h2.tabular(), color = c.onSurface)
                    }
                }
            }
        }

        Spacer(Modifier.height(Space.x12))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(KefeShapes.button)
                .background(c.surfaceElevated)
                .border(Sizes.hairline, c.outline, KefeShapes.button)
                .padding(horizontal = Space.x14, vertical = Space.x12),
            horizontalArrangement = Arrangement.spacedBy(Space.x10),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = state.inviteLink,
                style = t.caption,
                color = c.onSurfaceMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            CopyButton(
                text = if (state.linkCopied) "Kopyalandı" else "Kopyala",
                onClick = { onIntent(ShareIntent.CopyLink) },
            )
        }

        Spacer(Modifier.height(Space.x12))
        AccountFilledButton(
            text = "Bağlantıyı paylaş",
            onClick = { onIntent(ShareIntent.ShareLink) },
            modifier = Modifier.fillMaxWidth(),
            leadingIcon = AccountIcons.Upload,
        )
        Spacer(Modifier.height(Space.x4))
        AccountFlatButton(
            text = "Daveti iptal et",
            onClick = { onIntent(ShareIntent.CancelInvite) },
            contentColor = c.onSurfaceMuted,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun CopyButton(text: String, onClick: () -> Unit) {
    val c = KefeTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()

    Box(
        modifier = Modifier
            .height(36.dp)
            .clip(KefeShapes.pill)
            .background(if (hovered) c.surfaceSunken else Color.Transparent)
            .border(Sizes.hairline, c.outline, KefeShapes.pill)
            .hoverable(interaction)
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = Space.x12),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = KefeTheme.type.captionSmall.copy(fontWeight = FontWeight.SemiBold),
            color = c.accent,
            maxLines = 1,
        )
    }
}

/**
 * Davet karekodu. 21x21 modul, kose bulucu desenleriyle birlikte tasarimdaki
 * desenin aynisi. Renkler temaya bagli DEGILDIR: okunabilmesi icin kontrastin
 * her iki temada da siyah/beyaz kalmasi gerekir.
 */
@Composable
private fun InviteQrCode(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val module = size.minDimension / QrModules
        for (row in 0 until QrModules.toInt()) {
            val bits = QrRows[row]
            for (column in 0 until QrModules.toInt()) {
                if ((bits shr column) and 1 == 1) {
                    drawRect(
                        color = QrInk,
                        topLeft = Offset(column * module, row * module),
                        size = Size(module, module),
                    )
                }
            }
        }
    }
}

private const val QrModules = 21f
private val QrPaper = Color(0xFFFFFFFF)
private val QrInk = Color(0xFF111111)

/** Her deger bir satir; bit 0 en soldaki modul. */
private val QrRows = intArrayOf(
    0x1FC57F, 0x105141, 0x175A5D, 0x17455D, 0x17545D, 0x104941, 0x1FD37F,
    0x00000,
    0x15AB2D, 0x0934D2, 0x16592B, 0x00492B4, 0x196B59,
    0x00000,
    0x16AD7F, 0x095641, 0x12695D, 0x00CB25D, 0x134B5D, 0x059241, 0x192D7F,
)
