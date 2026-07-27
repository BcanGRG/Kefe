package com.kefe.app.ui.screens.account

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.kefe.app.ui.components.KefeHairline
import com.kefe.app.ui.components.KefeSwitch
import com.kefe.app.ui.format.trUpper
import com.kefe.app.ui.icons.KefeIcon
import com.kefe.app.ui.icons.KefeIcons
import com.kefe.app.ui.theme.KefeShapes
import com.kefe.app.ui.theme.KefeTheme
import com.kefe.app.ui.theme.Sizes
import com.kefe.app.ui.theme.Space
import com.kefe.app.ui.theme.tabular

/**
 * Ayarlar: yedi bolum, tek kaydirma. Yikici olan tek eylem en altta ve
 * negative renkte durur - yanlislikla dokunulacak yerde degil.
 */
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onIntent: (SettingsIntent) -> Unit,
    onBack: () -> Unit,
    onOpenShare: () -> Unit,
    modifier: Modifier = Modifier,
    /** Surum satirina basinca acilan bilesen katalogu - gelistirme araci. */
    onOpenGallery: () -> Unit = {},
) {
    val c = KefeTheme.colors
    val t = KefeTheme.type

    Column(modifier.fillMaxSize()) {
        AccountTopBar(title = "Ayarlar", onBack = onBack)

        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(start = Space.x16, end = Space.x16, bottom = Space.x24),
        ) {
            ShareCard(state = state, onClick = onOpenShare)

            SectionLabel("Görünüm")
            AccountGroupCard {
                Column(Modifier.fillMaxWidth().padding(Space.x14)) {
                    Text("Tema", style = t.body, color = c.onSurface)
                    Spacer(Modifier.height(Space.x10))
                    AccountPillSegment(
                        options = ThemeMode.entries.map { it.label() },
                        selectedIndex = ThemeMode.entries.indexOf(state.themeMode),
                        onSelect = { onIntent(SettingsIntent.SelectTheme(ThemeMode.entries[it])) },
                    )
                }
                KefeHairline()
                SettingsValueRow(
                    title = "Para birimi",
                    value = state.currencyLabel,
                    onClick = { onIntent(SettingsIntent.OpenCurrency) },
                )
                KefeHairline()
                SettingsSwitchRow(
                    title = "Kuruşları göster",
                    subtitle = "Ana toplamlarda yine gizli kalır",
                    checked = state.showCents,
                    onCheckedChange = { onIntent(SettingsIntent.SetShowCents(it)) },
                )
            }

            SectionLabel("Gizlilik")
            AccountGroupCard {
                SettingsSwitchRow(
                    title = "Açılışta bakiyeyi gizle",
                    subtitle = "Yüzdeler görünür kalır",
                    checked = state.hideBalanceOnStart,
                    onCheckedChange = { onIntent(SettingsIntent.SetHideBalanceOnStart(it)) },
                )
                KefeHairline()
                SettingsSwitchRow(
                    title = "Biyometrik kilit",
                    subtitle = "Parmak izi veya yüz ile aç",
                    checked = state.biometricLock,
                    onCheckedChange = { onIntent(SettingsIntent.SetBiometricLock(it)) },
                    leadingIcon = KefeIcons.Fingerprint,
                )
            }

            SectionLabel("Fiyatlar")
            AccountGroupCard {
                SettingsValueRow(
                    title = "Otomatik güncelleme",
                    value = state.priceRefreshLabel,
                    onClick = { onIntent(SettingsIntent.OpenPriceRefresh) },
                )
                KefeHairline()
                SettingsValueRow(
                    title = "Kaynak",
                    value = state.priceSourceLabel,
                    onClick = { onIntent(SettingsIntent.OpenPriceSource) },
                )
            }

            SectionLabel("Bildirimler")
            AccountGroupCard {
                SettingsSwitchRow(
                    title = "Eş kayıt ekleyince",
                    subtitle = null,
                    checked = state.notifyPartnerEntry,
                    onCheckedChange = { onIntent(SettingsIntent.SetNotifyPartnerEntry(it)) },
                )
                KefeHairline()
                SettingsSwitchRow(
                    title = "Aylık hatırlatıcı",
                    subtitle = "Ayın son günü, tek bildirim",
                    checked = state.notifyMonthlyReminder,
                    onCheckedChange = { onIntent(SettingsIntent.SetNotifyMonthlyReminder(it)) },
                )
                KefeHairline()
                SettingsSwitchRow(
                    title = "Kilometre taşı geçilince",
                    subtitle = null,
                    checked = state.notifyMilestone,
                    onCheckedChange = { onIntent(SettingsIntent.SetNotifyMilestone(it)) },
                )
            }

            SectionLabel("Veri")
            AccountGroupCard {
                SettingsRow(onClick = { onIntent(SettingsIntent.Backup) }) {
                    Text("Yedekle", style = t.body, color = c.onSurface, modifier = Modifier.weight(1f))
                    Text(
                        state.lastBackupLabel,
                        style = t.micro.tabular(),
                        color = c.onSurfaceMuted,
                    )
                }
                KefeHairline()
                SettingsValueRow(
                    title = "Geri yükle",
                    value = null,
                    onClick = { onIntent(SettingsIntent.Restore) },
                )
                KefeHairline()
                SettingsRow(onClick = { onIntent(SettingsIntent.ExportCsv) }) {
                    Text(
                        "CSV olarak indir",
                        style = t.body,
                        color = c.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    KefeIcon(KefeIcons.Download, null, size = 18.dp, tint = c.onSurfaceMuted)
                }
                KefeHairline()
                SettingsRow(
                    onClick = { onIntent(SettingsIntent.DeleteAllData) },
                    hoverBackground = c.negative.copy(alpha = 0.10f),
                ) {
                    Text(
                        "Tüm verileri sil",
                        style = t.body,
                        color = c.negative,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            SectionLabel("Hesap")
            AccountGroupCard {
                SettingsRow(onClick = null) {
                    Text("E-posta", style = t.body, color = c.onSurface, modifier = Modifier.weight(1f))
                    Text(state.email, style = t.caption, color = c.onSurfaceMuted)
                }
                KefeHairline()
                SettingsRow(onClick = { onIntent(SettingsIntent.SignOut) }) {
                    Text("Çıkış yap", style = t.body, color = c.onSurface, modifier = Modifier.weight(1f))
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = Space.x24),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Surum satiri bilesen katalogunu acar: tasarim karsilastirmasini
                // ve acik/koyu tema kontrolunu tek ekranda yapmak icin.
                Text(
                    text = "Kefe ${state.appVersion}",
                    style = t.micro,
                    color = c.onSurfaceMuted,
                    modifier = Modifier.clickable(
                        indication = null,
                        interactionSource = null,
                        onClick = onOpenGallery,
                    ),
                )
                FooterDot()
                FooterLink("Gizlilik") { onIntent(SettingsIntent.OpenPrivacy) }
                FooterDot()
                FooterLink("Koşullar") { onIntent(SettingsIntent.OpenTerms) }
            }
        }
    }
}

// --- Paylasim karti --------------------------------------------------------

@Composable
private fun ShareCard(state: SettingsUiState, onClick: () -> Unit) {
    val c = KefeTheme.colors
    val t = KefeTheme.type

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(KefeShapes.card)
            .background(c.surfaceElevated)
            .border(Sizes.hairline, c.outline, KefeShapes.card)
            .clickable(
                indication = null,
                interactionSource = null,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(Space.x14),
        horizontalArrangement = Arrangement.spacedBy(Space.x12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy((-12).dp)) {
            state.members.take(2).forEach { member ->
                AccountAvatar(
                    initials = member.initials,
                    index = member.index,
                    size = 36.dp,
                    fontSize = t.captionSmall.fontSize,
                    modifier = Modifier.border(1.5.dp, c.surfaceElevated, CircleShape),
                )
            }
        }

        Column(Modifier.weight(1f)) {
            Text("Paylaşım", style = t.bodyStrong, color = c.onSurface)
            Spacer(Modifier.height(2.dp))
            Text(state.shareSummary, style = t.micro, color = c.onSurfaceMuted)
        }

        KefeIcon(KefeIcons.ChevronRight, null, size = 20.dp, tint = c.onSurfaceMuted)
    }
}

// --- Satirlar --------------------------------------------------------------

@Composable
private fun SectionLabel(text: String) {
    Spacer(Modifier.height(Space.x24))
    Text(
        text = text.trUpper(),
        style = KefeTheme.type.label(11, 0.08, FontWeight.Bold),
        color = KefeTheme.colors.onSurfaceMuted,
    )
    Spacer(Modifier.height(Space.x8))
}

/** 14dp dolgulu taban satir. Vurgulanan zemin yalniz isaretci uzerindeyken cizilir. */
@Composable
private fun SettingsRow(
    onClick: (() -> Unit)?,
    hoverBackground: Color? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val background = when {
        !hovered || onClick == null -> Color.Transparent
        else -> hoverBackground ?: KefeTheme.colors.surfaceSunken
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(background)
            .then(
                if (onClick == null) Modifier
                else Modifier
                    .hoverable(interaction)
                    .clickable(
                        interactionSource = interaction,
                        indication = null,
                        role = Role.Button,
                        onClick = onClick,
                    )
            )
            .padding(Space.x14),
        horizontalArrangement = Arrangement.spacedBy(Space.x12),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
private fun SettingsValueRow(title: String, value: String?, onClick: () -> Unit) {
    val c = KefeTheme.colors
    val t = KefeTheme.type

    SettingsRow(onClick = onClick) {
        Text(title, style = t.body, color = c.onSurface, modifier = Modifier.weight(1f))
        if (value != null) {
            Text(value, style = t.caption, color = c.onSurfaceMuted)
        }
        KefeIcon(KefeIcons.ChevronRight, null, size = 18.dp, tint = c.onSurfaceMuted)
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    subtitle: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    leadingIcon: ImageVector? = null,
) {
    val c = KefeTheme.colors
    val t = KefeTheme.type

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(
                value = checked,
                indication = null,
                interactionSource = null,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            )
            .padding(Space.x14),
        horizontalArrangement = Arrangement.spacedBy(Space.x12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingIcon != null) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(KefeShapes.boxSmall)
                    .background(c.surfaceSunken),
                contentAlignment = Alignment.Center,
            ) {
                KefeIcon(leadingIcon, null, size = 20.dp, tint = c.accent)
            }
        }

        Column(Modifier.weight(1f)) {
            Text(title, style = t.body, color = c.onSurface)
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(subtitle, style = t.micro, color = c.onSurfaceMuted)
            }
        }

        // Anahtarin kendi dokunma alani 44dp; satir zaten dokunulabilir oldugu
        // icin gorsel yukseklik tasarimdaki 32dp'ye sabitlenir.
        Box(
            modifier = Modifier.width(52.dp).height(32.dp),
            contentAlignment = Alignment.Center,
        ) {
            KefeSwitch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

@Composable
private fun FooterDot() {
    Text(
        "·",
        style = KefeTheme.type.micro,
        color = KefeTheme.colors.onSurfaceMuted,
        modifier = Modifier.padding(horizontal = Space.x8),
    )
}

@Composable
private fun FooterLink(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        style = KefeTheme.type.micro.copy(textDecoration = TextDecoration.Underline),
        color = KefeTheme.colors.onSurfaceMuted,
        modifier = Modifier.clickable(
            indication = null,
            interactionSource = null,
            role = Role.Button,
            onClick = onClick,
        ),
    )
}
