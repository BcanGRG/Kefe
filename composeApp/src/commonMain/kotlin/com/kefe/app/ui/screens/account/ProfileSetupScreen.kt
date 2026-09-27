package com.kefe.app.ui.screens.account

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kefe.app.data.sync.ConflictChoice
import com.kefe.app.ui.format.trUpper
import com.kefe.app.ui.theme.KefeShapes
import com.kefe.app.ui.theme.KefeTheme
import com.kefe.app.ui.theme.Sizes
import com.kefe.app.ui.theme.Space

/**
 * "Profiller / Bu telefon kimin?" adimi.
 *
 * Girisliyse once hesaba bakilir ("Hesabınız kontrol ediliyor…") - cihaza
 * HENUZ bir sey yazilmaz. Cihazda da hesapta da kayit varsa once ne olacagi
 * sorulur ("Hesaptakileri kullan" / "Birleştir" / "Vazgeç"). Hesapta profil
 * varsa adlar oradan gelir ve yalniz bu telefonun hangisi oldugu secilir; yoksa
 * iki ad yazilir. "Devam" hepsini tek islemde yazar. Girissizken alttaki
 * "Hesaba bağla" hesaba gecirir - o yol donuste bu ekrana geri gelir.
 *
 * Secim onemlidir: bu telefondan eklenen her islem secilen profile yazilir.
 *
 * [onLink]: girissizken "Hesaba bağla" - giris ekranini (baglama amaciyla) iter.
 *
 * [onUseAnotherEmail]: girisliyken "Farklı e-postayla gir" - oturumu birakir ve
 * giris ekranini yeniden acar. Baglanti henuz yazilmadigi icin (bkz.
 * ProfileSetupViewModel) hicbir kayit etkilenmez.
 *
 * [onCancelLink]: cakismada "Vazgeç" - yalniz bu cihazin oturumu kapanir, akisin
 * basladigi yere donulur. Hicbir sey yazilmamistir.
 *
 * [onBackup]: cakismada "Önce yedek al" - Ayarlar'daki yedekle ayni is.
 */
@Composable
fun ProfileSetupScreen(
    state: ProfileSetupUiState,
    onIntent: (ProfileSetupIntent) -> Unit,
    onLink: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    onUseAnotherEmail: () -> Unit = {},
    onCancelLink: () -> Unit = {},
    onBackup: () -> Unit = {},
) {
    val c = KefeTheme.colors
    val t = KefeTheme.type

    LaunchedEffect(state.done) { if (state.done) onDone() }

    val picking = state.phase == ProfileSetupPhase.Ready && !state.editingNames
    val conflict = conflictCopy(state.conflictLocal, state.conflictServer)
    val title = when {
        state.phase == ProfileSetupPhase.Conflict -> conflict.title
        picking -> "Bu telefon kimin?"
        else -> "Profiller"
    }

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            Modifier
                .widthIn(max = Sizes.formMaxWidth)
                .fillMaxWidth()
                .padding(start = Space.x24, end = Space.x24, top = Space.x40),
        ) {
            Text(title, style = t.h1, color = c.onSurface)
            Spacer(Modifier.height(6.dp))

            when (state.phase) {
                // Oturum okunana kadar yalniz baslik: "olustur" metni bir kare
                // parlayip "getiriliyor"a donmesin.
                ProfileSetupPhase.Checking -> Unit

                // Bu asamada hicbir sey YAZILMAZ: hesaba yalniz bakilir (bkz.
                // AccountLinker.preview). "İndiriliyor" demek dogru - cihaza
                // ancak kullanici karar verince uygulanir.
                ProfileSetupPhase.Syncing -> {
                    BodyText("Hesabınız kontrol ediliyor… Hesaptaki profiller ve kayıtlar indiriliyor.")
                    Spacer(Modifier.height(Space.x28))
                    AccountFilledButton(
                        text = "Kontrol ediliyor…",
                        onClick = {},
                        modifier = Modifier.fillMaxWidth(),
                        enabled = false,
                    )
                }

                ProfileSetupPhase.Conflict -> ConflictContent(
                    copy = conflict,
                    onIntent = onIntent,
                    onCancel = onCancelLink,
                    onBackup = onBackup,
                )

                ProfileSetupPhase.Failed -> {
                    // Hesaba ulasilamadi mi, yoksa baglanti cihaza mi
                    // yazilamadi: iki ayri metin (bkz. failureMessage).
                    Text(
                        state.failureMessage(),
                        style = t.body.copy(lineHeight = 22.sp),
                        color = c.negative,
                    )
                    state.failureDetail?.let {
                        Spacer(Modifier.height(Space.x8))
                        Text("Ayrıntı: $it", style = t.micro, color = c.onSurfaceMuted)
                    }
                    Spacer(Modifier.height(Space.x28))
                    AccountFilledButton(
                        text = "Tekrar dene",
                        onClick = { onIntent(ProfileSetupIntent.Retry) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(Space.x8))
                    // Oturum KALIR ama baglanti yazilmaz: mod "Bağlantı yarım",
                    // hicbir sey gonderilmez ve cekilmez. Ozet'teki "Tamamla"
                    // hesabi indirip secimi yeniden sorar.
                    AccountFlatButton(
                        text = "Şimdilik hesapsız devam et",
                        onClick = { onIntent(ProfileSetupIntent.ContinueOffline) },
                        contentColor = c.onSurfaceMuted,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    AccountFooter(state, onUseAnotherEmail)
                }

                ProfileSetupPhase.Ready -> {
                    ReadyContent(state, onIntent, onLink)
                    AccountFooter(state, onUseAnotherEmail)
                }
            }
            Spacer(Modifier.height(Space.x24))
        }
    }
}

@Composable
private fun ReadyContent(
    state: ProfileSetupUiState,
    onIntent: (ProfileSetupIntent) -> Unit,
    onLink: () -> Unit,
) {
    val c = KefeTheme.colors
    val t = KefeTheme.type

    // Govde metni duruma gore tek yerden (bkz. readyBody): hesap sozu yalniz
    // girisliyken, hesapsiz olusturmada profillerin nerede durdugu.
    BodyText(state.readyBody())

    if (state.editingNames) {
        Spacer(Modifier.height(Space.x28))
        Text("İSİMLER", style = t.label(11, 0.06), color = c.onSurfaceMuted)
        Spacer(Modifier.height(Space.x10))
        NameField(
            value = state.ownerName,
            onValueChange = { onIntent(ProfileSetupIntent.ChangeOwnerName(it)) },
            placeholder = "Örn. Volkan",
        )
        Spacer(Modifier.height(Space.x10))
        NameField(
            value = state.partnerName,
            onValueChange = { onIntent(ProfileSetupIntent.ChangePartnerName(it)) },
            placeholder = "Örn. Ayşe",
        )
        Spacer(Modifier.height(Space.x28))
        Text("BU TELEFON KİMİN?", style = t.label(11, 0.06), color = c.onSurfaceMuted)
    }
    Spacer(Modifier.height(Space.x10))

    // Isim bosken bile secim yapilabilsin diye yer tutucu ad gosterilir.
    DeviceChoice(
        name = state.ownerName.ifBlank { "1. profil" },
        index = 0,
        selected = state.thisDeviceIsOwner == true,
        onClick = { onIntent(ProfileSetupIntent.SelectThisDevice(true)) },
    )
    Spacer(Modifier.height(Space.x8))
    DeviceChoice(
        name = state.partnerName.ifBlank { "2. profil" },
        index = 1,
        selected = state.thisDeviceIsOwner == false,
        onClick = { onIntent(ProfileSetupIntent.SelectThisDevice(false)) },
    )

    // Secim eski profilden farkliysa cihazda girilmis kayitlarin akibeti; cakisma
    // secildiyse "Devam"in ne yapacagi. "Devam"dan ONCE okunmali.
    state.remapNote()?.let { note ->
        Spacer(Modifier.height(Space.x10))
        Text(note, style = t.micro, color = c.onSurfaceMuted)
    }
    state.choiceNote()?.let { note ->
        Spacer(Modifier.height(Space.x10))
        Text(note, style = t.micro, color = c.onSurfaceMuted)
    }

    Spacer(Modifier.height(Space.x28))
    AccountFilledButton(
        text = if (state.saving) "Kaydediliyor…" else "Devam",
        onClick = { onIntent(ProfileSetupIntent.Save) },
        modifier = Modifier.fillMaxWidth(),
        enabled = state.canSave,
    )

    // Cakismadan gelindiyse secim geri alinabilir: "Hesaptakileri kullan"i
    // yanlislikla secen kullanici cihazdakileri silmeden donebilmeli.
    if (state.conflictChoice != null) {
        Spacer(Modifier.height(Space.x8))
        AccountFlatButton(
            text = "Seçimi değiştir",
            onClick = { onIntent(ProfileSetupIntent.BackToConflict) },
            contentColor = c.onSurfaceMuted,
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.saving,
        )
    }

    // Adsiz secimde (hesap indirilemeden gecildi) duzenleme yok: yazilan adlar
    // hesap indiginde hesabinkilerle degisir (bkz. canEditNames).
    if (state.canEditNames) {
        Spacer(Modifier.height(Space.x8))
        AccountFlatButton(
            text = "Adları düzenle",
            onClick = { onIntent(ProfileSetupIntent.EditNames) },
            contentColor = c.onSurfaceMuted,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            "Adları sonra Ayarlar › Profiller'den de değiştirebilirsiniz.",
            style = t.micro,
            color = c.onSurfaceMuted,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
    }

    // HESABA GECMENIN YOLU BURADA DA DURUR. Hosgeldin'de "Bu cihazda kullan"i
    // secen biri esiyle iki telefonda kullanacagini sonradan fark edebilir;
    // giris bitince bu ekrana donulur ve hesaptaki profillerden secilir. Iki
    // satir: tek satira sigmayinca baglanti cumlenin ortasindan kiriliyordu.
    if (state.showLinkFooter) {
        Spacer(Modifier.height(Space.x28))
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "Eşinizle iki telefonda mı kullanacaksınız?",
                style = t.caption,
                color = c.onSurfaceMuted,
                textAlign = TextAlign.Center,
            )
            Text(
                "Hesaba bağla",
                style = t.caption.copy(fontWeight = FontWeight.SemiBold),
                color = c.accent,
                modifier = Modifier
                    .padding(top = Space.x4)
                    .clickable(
                        indication = null,
                        interactionSource = null,
                        role = Role.Button,
                        onClick = onLink,
                    ),
            )
        }
    }
}

/**
 * Girisliyken: hangi e-postayla girildigi ve "Farklı e-postayla gir".
 *
 * NEYDI: hosgeldinde "Hesapla, iki telefonda" deyip kodu dogrulayan kullanici
 * bu ekrana KOK olarak geliyordu - geri tusu uygulamadan cikiyor, yeniden
 * acilis da buraya donuyordu. E-postayi yanlis yazdiysa (ya da esinden farkli
 * birini kullandiysa) tek yol profilleri olusturup yanlis hesaba baglanmak,
 * sonra Ayarlar'dan cikmakti. Adres de hic gorunmuyordu.
 */
@Composable
private fun AccountFooter(state: ProfileSetupUiState, onUseAnotherEmail: () -> Unit) {
    if (!state.showAccountFooter) return
    val c = KefeTheme.colors
    val t = KefeTheme.type
    Spacer(Modifier.height(Space.x28))
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "${state.accountEmail} ile giriş yaptınız.",
            style = t.caption,
            color = c.onSurfaceMuted,
            textAlign = TextAlign.Center,
        )
        Text(
            "Farklı e-postayla gir",
            style = t.caption.copy(fontWeight = FontWeight.SemiBold),
            color = c.accent,
            modifier = Modifier
                .padding(top = Space.x4)
                .clickable(
                    enabled = !state.saving,
                    indication = null,
                    interactionSource = null,
                    role = Role.Button,
                    onClick = onUseAnotherEmail,
                ),
        )
    }
}

/**
 * Cihazda da hesapta da kayit var: ne olacagini kullanici secer. Uc yol esit
 * agirlikta degil - ikisi kart (ne olacagi altinda yazili), "Vazgeç" duz
 * dugme. Hicbiri varsayilan degil; secilene kadar hicbir sey yazilmaz.
 */
@Composable
private fun ConflictContent(
    copy: ConflictCopy,
    onIntent: (ProfileSetupIntent) -> Unit,
    onCancel: () -> Unit,
    onBackup: () -> Unit,
) {
    val c = KefeTheme.colors
    val t = KefeTheme.type

    BodyText(copy.body)
    Spacer(Modifier.height(Space.x28))

    ConflictOption(
        title = copy.useAccountTitle,
        note = copy.useAccountNote,
        onClick = { onIntent(ProfileSetupIntent.ChooseConflict(ConflictChoice.UseAccount)) },
    )
    // Silinecek olanin yedegi secmeden ONCE alinabilsin. Ayarlar'daki yedekle
    // ayni is; dosya kullanicinin sectigi yere gider, ekran burada kalir.
    Text(
        "Önce yedek al",
        style = t.caption.copy(fontWeight = FontWeight.SemiBold),
        color = c.accent,
        modifier = Modifier
            .padding(top = Space.x8, start = Space.x4)
            .clickable(
                indication = null,
                interactionSource = null,
                role = Role.Button,
                onClick = onBackup,
            ),
    )
    Spacer(Modifier.height(Space.x16))
    ConflictOption(
        title = copy.mergeTitle,
        note = copy.mergeNote,
        onClick = { onIntent(ProfileSetupIntent.ChooseConflict(ConflictChoice.Merge)) },
    )

    Spacer(Modifier.height(Space.x16))
    AccountFlatButton(
        text = "Vazgeç",
        onClick = onCancel,
        contentColor = c.onSurfaceMuted,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Cakisma secenegi: baslik ve ne olacagini soyleyen not. */
@Composable
private fun ConflictOption(title: String, note: String, onClick: () -> Unit) {
    val c = KefeTheme.colors
    val t = KefeTheme.type
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .background(c.surfaceElevated, KefeShapes.button)
            .border(Sizes.hairline, c.outline, KefeShapes.button)
            .padding(horizontal = Space.x14, vertical = Space.x12),
    ) {
        Text(title, style = t.bodyStrong, color = c.onSurface)
        Spacer(Modifier.height(Space.x4))
        Text(note, style = t.micro, color = c.onSurfaceMuted)
    }
}

@Composable
private fun BodyText(text: String) {
    Text(
        text,
        style = KefeTheme.type.body.copy(lineHeight = 22.sp),
        color = KefeTheme.colors.onSurfaceMuted,
    )
}

/** Secilebilen profil satiri: avatar + ad, secili ise accent kenarlik. */
@Composable
private fun DeviceChoice(
    name: String,
    index: Int,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val c = KefeTheme.colors
    val t = KefeTheme.type
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(Sizes.buttonPrimary)
            .clickable(onClick = onClick)
            .background(
                if (selected) c.accentMuted else c.surfaceElevated,
                KefeShapes.button,
            )
            .border(
                Sizes.hairline,
                if (selected) c.accent else c.outline,
                KefeShapes.button,
            )
            .padding(horizontal = Space.x14),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.x12),
    ) {
        AccountAvatar(
            initials = name.trim().take(1),
            index = index,
            size = 28.dp,
            fontSize = 12.sp,
        )
        Text(
            text = name,
            style = t.body.copy(fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal),
            color = c.onSurface,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Text("Bu telefon", style = t.micro, color = c.accent)
        }
    }
}

/** Isim alani - LoginScreen'in e-posta alaninin ikonsuz karsiligi. */
@Composable
private fun NameField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
) {
    val c = KefeTheme.colors
    val t = KefeTheme.type
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val borderColor = if (focused || value.isNotEmpty()) c.accent else c.outline

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(Sizes.buttonPrimary)
            .background(c.surfaceElevated, KefeShapes.button)
            .border(Sizes.hairline, borderColor, KefeShapes.button)
            .padding(horizontal = Space.x14),
        contentAlignment = Alignment.CenterStart,
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            textStyle = t.body.copy(color = c.onSurface),
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            cursorBrush = SolidColor(c.accent),
            interactionSource = interaction,
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) {
                        Text(placeholder, style = t.body, color = c.onSurfaceMuted, maxLines = 1)
                    }
                    inner()
                }
            },
        )
    }
}
