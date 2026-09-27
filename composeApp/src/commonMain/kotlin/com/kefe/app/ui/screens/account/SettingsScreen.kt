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
import androidx.compose.foundation.layout.widthIn
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
import com.kefe.app.data.sync.CloudMode
import com.kefe.app.ui.components.KefeCloudMark
import com.kefe.app.ui.components.KefeConfirmDialog
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
 * Ayarlar: tek kaydirma. Sira: Profiller, Hesap ve eşitleme, Görünüm, Gizlilik,
 * Fiyatlar, Veri. Yikici olan tek eylem en altta ve negative renkte durur -
 * yanlislikla dokunulacak yerde degil.
 */
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onIntent: (SettingsIntent) -> Unit,
    onOpenShare: () -> Unit,
    /** Hesap bolumundeki "Hesaba bağla" - giris ekranini baglama amaciyla acar. */
    onLink: () -> Unit,
    modifier: Modifier = Modifier,
    /** Ust cubuktaki geri oku; null ise ok cizilmez (Ayarlar kokken). */
    onBack: (() -> Unit)? = null,
    /**
     * Dusen oturumda "Yeniden giriş yap" - giris ekranini bagli hesabin
     * e-postasiyla acar. Baglamadan AYRI: basligi ve notu farkli (bkz. signInCopy).
     */
    onRelogin: () -> Unit = onLink,
    /** Yarim baglantida "Tamamla" - hesabi indirip "bu telefon kimin"i soran adima. */
    onCompleteLink: () -> Unit = {},
    /** Surum satirina basinca acilan bilesen katalogu - gelistirme araci. */
    onOpenGallery: () -> Unit = {},
) {
    val c = KefeTheme.colors
    val t = KefeTheme.type

    // Onay penceresi icerigin USTUNE cizilmeli; ekranin koku Column oldugu icin
    // burada bir Box gerekiyor - Column'un cocugu olarak eklenirse dikey akisa
    // girip sayfayi ikiye boluyor.
    Box(modifier.fillMaxSize()) {

    Column(Modifier.fillMaxSize()) {
        // Telefonda ve tablette Ayarlar ITILIR (Ozet'teki disli / raydaki disli),
        // geri oku acildigi yere doner. Masaustunde yan menu satiri kok oldugu
        // icin null: geri gidilecek bir yer yok. NEYDI: Ayarlar alt barin bir
        // sekmesiydi; yerini Plan aldi.
        AccountTopBar(title = "Ayarlar", onBack = onBack)

        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(start = Space.x16, end = Space.x16, bottom = Space.x24),
        ) {
            ShareCard(state = state, onClick = onOpenShare)

            // Hesap ve esitleme profillerin HEMEN ALTINDA. NEYDI: en altta "Bulut"
            // adiyla duruyordu; kayitlarin yalniz bu telefonda mi yoksa hesapta da
            // mi oldugu - uygulamanin en onemli sorusu - Veri'nin bile altindaydi.
            // Ozet cipi, ray ve yan navigasyon buraya getirir: tek hedef.
            accountSection(state.cloudMode, state.cloudConfigured, state.lastSyncedLabel)?.let { section ->
                SectionLabel("Hesap ve eşitleme")
                AccountSectionCard(
                    section = section,
                    mode = state.cloudMode,
                    onAction = { action ->
                        when (action) {
                            AccountAction.Link -> onLink()
                            AccountAction.Relogin -> onRelogin()
                            AccountAction.CompleteLink -> onCompleteLink()
                            AccountAction.DropLink -> onIntent(SettingsIntent.DropLink)
                            AccountAction.SyncNow -> onIntent(SettingsIntent.SyncNow)
                            AccountAction.SignOut -> onIntent(SettingsIntent.SignOut)
                        }
                    },
                )
            }

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
                // Kilidin karsiligi olmayan cihazda (masaustu, donanimsiz
                // telefon) satir cizilmez; acilamayacak anahtar bozuk gorunur.
                // Alt satir "parmak izi" ile sinirli degil: Android istemi cihaz
                // PIN'ini de kabul ediyor, iOS da parolaya dusuyor.
                if (state.lockAvailable) {
                    KefeHairline()
                    SettingsSwitchRow(
                        title = "Açılış kilidi",
                        subtitle = "Kefe açılırken parmak izi, yüz ya da ekran kilidi sorulur",
                        checked = state.biometricLock,
                        onCheckedChange = { onIntent(SettingsIntent.SetBiometricLock(it)) },
                        leadingIcon = KefeIcons.Fingerprint,
                    )
                }
            }

            // Fiyat satirlari SALT OKUNUR: ayarlanabilir bir aralik ya da
            // secilebilir kaynak yok. Once dokununca "henüz hazır değil" diyen
            // birer chevron'du; simdi gercegi yazan bilgi satirlari.
            SectionLabel("Fiyatlar")
            AccountGroupCard {
                SettingsValueRow(title = "Güncelleme", value = state.priceRefreshLabel)
                KefeHairline()
                SettingsValueRow(title = "Kaynak", value = state.priceSourceLabel)
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
                // Bagliyken KAPALI (bkz. restoreLocked): baslik soluk, degeri
                // nedenini soyler; dokunmak yine calisir ve cikis yolunu anlatir.
                val restoreLocked = restoreLocked(state.cloudMode)
                SettingsValueRow(
                    title = "Geri yükle",
                    value = if (restoreLocked) RestoreLockedValue else null,
                    onClick = { onIntent(SettingsIntent.Restore) },
                    titleColor = if (restoreLocked) c.onSurfaceMuted else c.onSurface,
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
                    // Hesap isin icindeyse "Bu cihazı sıfırla": silme hesabi
                    // degil yalniz bu cihazi etkiler, once hesaptan cikilir.
                    Text(
                        deleteRowLabel(state.cloudMode),
                        style = t.body,
                        color = c.negative,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            // Kayitlarin kac kopyasi var: hesapsizken tek kopya bu cihazda.
            dataFootnote(state.cloudMode, state.cloudConfigured)?.let { note ->
                Spacer(Modifier.height(Space.x8))
                Text(
                    note,
                    style = t.micro,
                    color = c.onSurfaceMuted,
                    modifier = Modifier.padding(horizontal = Space.x4),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = Space.x24),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Surum satiri bilesen katalogunu acar: tasarim karsilastirmasini
                // ve acik/koyu tema kontrolunu tek ekranda yapmak icin.
                //
                // Gizlilik/Koşullar baglantilari KALDIRILDI: iki kisilik, magazaya
                // cikmayan bir uygulamada olmayan bir belgeye baglanti vermek yanlis.
                // Play'e cikma ani gelince (gercek bir URL gerektiginde) geri gelir.
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
            }
        }
    }

        // Gorunurluk `if` ile DEGIL parametreyle verilir: kutunun geri
        // isleyicisi kosulsuz bestelenmeli (bkz. KefeBackHandler).
        // Metin moda gore (bkz. deleteDialog): hesapsizken tek kopya burada,
        // hesapliyken hesaptakiler ve esin telefonu etkilenmez.
        val delete = deleteDialog(state.cloudMode, state.partnerName)
        KefeConfirmDialog(
            visible = state.confirmDelete,
            title = delete.title,
            message = delete.message,
            confirmLabel = delete.confirmLabel,
            onConfirm = { onIntent(SettingsIntent.ConfirmDeleteAllData) },
            onDismiss = { onIntent(SettingsIntent.DismissDeleteConfirm) },
        )

        // Hesaptan cikis: kayitlar kalir, esin telefonu etkilenmez; gitmemis
        // degisiklik varsa yalniz bu cihazda kalir (bkz. signOutDialog).
        val signOut = signOutDialog(state.partnerName, state.unsentChanges)
        KefeConfirmDialog(
            visible = state.confirmSignOut,
            title = signOut.title,
            message = signOut.message,
            confirmLabel = signOut.confirmLabel,
            onConfirm = { onIntent(SettingsIntent.ConfirmSignOut) },
            onDismiss = { onIntent(SettingsIntent.DismissSignOutConfirm) },
        )

        // Geri yukleme de yikicidir: yedek mevcut verinin USTUNE degil YERINE
        // gecer. Birlestirme yapilmiyor cunku hangi kaydin daha yeni oldugunu
        // soyleyecek bir zaman damgasi yok.
        KefeConfirmDialog(
            visible = state.confirmRestore,
            title = "Yedeği geri yükle",
            message = "Şu anki varlıklarınız, işlemleriniz ve hedefleriniz " +
                "yedektekilerle DEĞİŞTİRİLECEK. Bu işlem geri alınamaz — " +
                "önce mevcut halin yedeğini almak isteyebilirsiniz.",
            confirmLabel = "Dosya seç",
            onConfirm = { onIntent(SettingsIntent.ConfirmRestore) },
            onDismiss = { onIntent(SettingsIntent.DismissRestoreConfirm) },
        )
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
            Text("Profiller", style = t.bodyStrong, color = c.onSurface)
            Spacer(Modifier.height(2.dp))
            Text(state.shareSummary, style = t.micro, color = c.onSurfaceMuted)
        }

        KefeIcon(KefeIcons.ChevronRight, null, size = 20.dp, tint = c.onSurfaceMuted)
    }
}

// --- Hesap ve esitleme ------------------------------------------------------

/**
 * Hesap bolumu: durum satiri (isaretli, dokunulmaz), bagliyken "Son eşitleme",
 * sonra moda gore eylemler. Icerik [accountSection]'dan gelir; bu fonksiyon
 * yalniz cizer.
 */
@Composable
private fun AccountSectionCard(
    section: AccountSection,
    mode: CloudMode?,
    onAction: (AccountAction) -> Unit,
) {
    val c = KefeTheme.colors
    val t = KefeTheme.type

    AccountGroupCard {
        SettingsRow(onClick = null) {
            if (mode != null) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(KefeShapes.boxSmall)
                        .background(c.surfaceSunken),
                    contentAlignment = Alignment.Center,
                ) {
                    KefeCloudMark(mode)
                }
            }
            Column(Modifier.weight(1f)) {
                Text(section.statusTitle, style = t.body, color = c.onSurface)
                section.statusSubtitle?.let {
                    Spacer(Modifier.height(2.dp))
                    Text(it, style = t.micro, color = c.onSurfaceMuted)
                }
            }
        }
        section.lastSynced?.let { label ->
            KefeHairline()
            SettingsValueRow(title = "Son eşitleme", value = label)
        }
        section.actions.forEach { row ->
            KefeHairline()
            SettingsRow(onClick = { onAction(row.action) }) {
                Column(Modifier.weight(1f)) {
                    Text(row.title, style = t.body, color = c.onSurface)
                    row.subtitle?.let {
                        Spacer(Modifier.height(2.dp))
                        Text(it, style = t.micro, color = c.onSurfaceMuted)
                    }
                }
                KefeIcon(KefeIcons.ChevronRight, null, size = 18.dp, tint = c.onSurfaceMuted)
            }
        }
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
private fun SettingsValueRow(
    title: String,
    value: String?,
    onClick: (() -> Unit)? = null,
    titleColor: Color = KefeTheme.colors.onSurface,
) {
    val c = KefeTheme.colors
    val t = KefeTheme.type

    SettingsRow(onClick = onClick) {
        Text(title, style = t.body, color = titleColor, modifier = Modifier.weight(1f))
        if (value != null) {
            Text(value, style = t.caption, color = c.onSurfaceMuted)
        }
        // Chevron yalniz DOKUNULABILIR satirda. Salt okunur bilgi satirinda
        // (fiyat kaynagi gibi) ok koymak "buraya girilebilir" yalanidir.
        if (onClick != null) {
            KefeIcon(KefeIcons.ChevronRight, null, size = 18.dp, tint = c.onSurfaceMuted)
        }
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
