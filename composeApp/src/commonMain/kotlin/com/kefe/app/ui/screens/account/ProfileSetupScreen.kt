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
import com.kefe.app.ui.format.trUpper
import com.kefe.app.ui.theme.KefeShapes
import com.kefe.app.ui.theme.KefeTheme
import com.kefe.app.ui.theme.Sizes
import com.kefe.app.ui.theme.Space

/**
 * "Profiller / Bu telefon kimin?" adimi.
 *
 * Girisliyse once hesap indirilir ("Hesabınız getiriliyor…"). Hesapta profil
 * varsa adlar oradan gelir ve yalniz bu telefonun hangisi oldugu secilir;
 * yoksa iki ad yazilir. Girissizken "Hesabım var, giriş yap" hesaba gecirir -
 * o yol donuste bu ekrana geri gelir ve hesap indirilir.
 *
 * Secim onemlidir: bu telefondan eklenen her islem secilen profile yazilir.
 */
@Composable
fun ProfileSetupScreen(
    state: ProfileSetupUiState,
    onIntent: (ProfileSetupIntent) -> Unit,
    onSignIn: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = KefeTheme.colors
    val t = KefeTheme.type

    LaunchedEffect(state.done) { if (state.done) onDone() }

    val picking = state.phase == ProfileSetupPhase.Ready && !state.editingNames

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
            Text(if (picking) "Bu telefon kimin?" else "Profiller", style = t.h1, color = c.onSurface)
            Spacer(Modifier.height(6.dp))

            when (state.phase) {
                // Oturum okunana kadar yalniz baslik: "olustur" metni bir kare
                // parlayip "getiriliyor"a donmesin.
                ProfileSetupPhase.Checking -> Unit

                ProfileSetupPhase.Syncing -> {
                    BodyText("Hesabınız getiriliyor… Profilleriniz ve kayıtlarınız buluttan indiriliyor.")
                    Spacer(Modifier.height(Space.x28))
                    AccountFilledButton(
                        text = "Getiriliyor…",
                        onClick = {},
                        modifier = Modifier.fillMaxWidth(),
                        enabled = false,
                    )
                }

                ProfileSetupPhase.Failed -> {
                    Text(
                        "Hesabınıza ulaşılamadı. İnternet bağlantınızı kontrol edip tekrar deneyin.",
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
                    AccountFlatButton(
                        text = "Bağlanmadan devam et",
                        onClick = { onIntent(ProfileSetupIntent.ContinueOffline) },
                        contentColor = c.onSurfaceMuted,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                ProfileSetupPhase.Ready -> ReadyContent(state, onIntent, onSignIn)
            }
            Spacer(Modifier.height(Space.x24))
        }
    }
}

@Composable
private fun ReadyContent(
    state: ProfileSetupUiState,
    onIntent: (ProfileSetupIntent) -> Unit,
    onSignIn: () -> Unit,
) {
    val c = KefeTheme.colors
    val t = KefeTheme.type

    if (state.editingNames) {
        BodyText(
            if (state.accountHasProfiles) {
                "Profil adlarını düzenleyin. Bu telefondan eklediğiniz her kayıt, seçtiğiniz profile yazılır."
            } else {
                "İki profil oluşturun. Bu telefondan eklediğiniz her kayıt, seçtiğiniz profile yazılır."
            },
        )
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
    } else {
        BodyText(
            if (state.accountHasProfiles) {
                "Hesabınızda iki profil bulduk. Bu telefondan eklediğiniz her kayıt, seçtiğiniz profile yazılır."
            } else {
                // Yalniz "Bağlanmadan devam et"ten gelinir: hesap indirilemedi,
                // baglanti yazilmadi, mod "Bağlantı yarım". NEYDI: "kayitlar
                // baglanti gelince kendiliginden gelir" deniyordu - oysa esitleme
                // artik yalniz BAGLI cihazda calisiyor; kayitlar ancak Ozet'teki
                // (ya da Ayarlar'daki) "Tamamla" ile iner.
                "Bu telefondan eklediğiniz her kayıt, seçtiğiniz profile yazılır. " +
                    "Bağlantı gelince Özet'teki \"Tamamla\" ile hesabınızdaki kayıtları indirin."
            },
        )
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

    Spacer(Modifier.height(Space.x28))
    AccountFilledButton(
        text = if (state.saving) "Kaydediliyor…" else "Devam",
        onClick = { onIntent(ProfileSetupIntent.Save) },
        modifier = Modifier.fillMaxWidth(),
        enabled = state.canSave,
    )

    if (!state.editingNames) {
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

    // HESABI OLAN BURADA KAYBOLMAMALI. Girissiz gelindiyse (yeni telefon,
    // "Hesapsız başla" ya da kilit ekranindan gecilmis bir ilk acilis) mevcut
    // hesaba gecmenin yolu tam bu ekranda durur; giris sonrasi bu ekrana
    // donulur ve hesaptaki profillerden secilir.
    if (!state.signedIn) {
        Spacer(Modifier.height(Space.x28))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
        ) {
            Text("Zaten bir hesabınız var mı? ", style = t.caption, color = c.onSurfaceMuted)
            Text(
                "Hesabım var, giriş yap",
                style = t.caption.copy(fontWeight = FontWeight.SemiBold),
                color = c.accent,
                modifier = Modifier.clickable(
                    indication = null,
                    interactionSource = null,
                    role = Role.Button,
                    onClick = onSignIn,
                ),
            )
        }
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
