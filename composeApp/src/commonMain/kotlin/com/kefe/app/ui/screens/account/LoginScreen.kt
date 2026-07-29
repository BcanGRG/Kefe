package com.kefe.app.ui.screens.account

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.kefe.app.ui.components.KefeIconButton
import com.kefe.app.ui.format.Money
import com.kefe.app.ui.format.trUpper
import com.kefe.app.ui.icons.KefeIcon
import com.kefe.app.ui.icons.KefeIcons
import com.kefe.app.ui.theme.KefeShapes
import com.kefe.app.ui.theme.KefeTheme
import com.kefe.app.ui.theme.Sizes
import com.kefe.app.ui.theme.Space
import com.kefe.app.ui.theme.tabular

/**
 * Giris, baslangic (yeni portfoy / davet kodu) ve cihaz kilidi.
 *
 * Birincil yol sifresiz giristir: e-postaya tek kullanimlik baglanti. Sifre
 * yolu bilerek ikincil butondadir.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun LoginScreen(
    state: LoginUiState,
    onIntent: (LoginIntent) -> Unit,
    onStartOnboarding: () -> Unit,
    onEnterApp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(state.unlocked) { if (state.unlocked) onEnterApp() }
    LaunchedEffect(state.portfolioCreated) { if (state.portfolioCreated) onStartOnboarding() }

    // Giris ekraninin ASAMALARI ayri gezinme girdisi degil, tek ekranin durumu.
    // Sistem geri tusu bunu bilmedigi icin kod kutusundayken ya da Başlangıç
    // adimindayken UYGULAMADAN CIKIYORDU: kullanici bir adim geri gitmek isterken
    // kendini ana ekranda buluyordu.
    //
    // Kilit asamasi bilerek disarida: kilitliyken geri tusu uygulamadan cikarir,
    // kilidi acmaz. Kapali tutmak da kullaniciyi ekranda hapsederdi.
    val backStep: (() -> Unit)? = when {
        state.stage == LoginStage.Start -> {
            { onIntent(LoginIntent.GoToSignIn) }
        }
        state.stage == LoginStage.SignIn && state.codeSent -> {
            { onIntent(LoginIntent.EditEmail) }
        }
        else -> null
    }
    BackHandler(enabled = backStep != null) { backStep?.invoke() }

    // Form masaustunde pencere boyunca uzarsa alan ve butonlar okunaksiz olur;
    // ust cubuk da icerikle ayni dar kolonda kalsin diye birlikte ortalanir.
    // Telefonda (390) sinir devreye girmez.
    Column(modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        if (state.stage == LoginStage.Start) {
            AccountTopBar(
                title = "Başlangıç",
                onBack = { onIntent(LoginIntent.GoToSignIn) },
                modifier = Modifier.widthIn(max = Sizes.formMaxWidth),
            )
        }

        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // widthIn ONCE gelmeli: fillMaxWidth once uygulanirsa asagi kesin
            // genislik kisiti iner ve sinir hicbir sey yapmaz.
            Column(
                Modifier
                    .widthIn(max = Sizes.formMaxWidth)
                    .fillMaxWidth(),
            ) {
                when (state.stage) {
                    LoginStage.SignIn -> SignInStage(state, onIntent)
                    LoginStage.Start -> StartStage(state, onIntent)
                    LoginStage.Locked -> LockStage(state, onIntent)
                }
            }
        }
    }
}

// --- Giris -----------------------------------------------------------------

@Composable
private fun SignInStage(state: LoginUiState, onIntent: (LoginIntent) -> Unit) {
    val c = KefeTheme.colors
    val t = KefeTheme.type

    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = Space.x24, end = Space.x24, top = Space.x40),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(Space.x24))
                .background(c.accentMuted),
            contentAlignment = Alignment.Center,
        ) {
            KefeIcon(KefeIcons.Balance, null, size = Space.x40, tint = c.accent)
        }
        Spacer(Modifier.height(Space.x20))
        Text("Kefe", style = t.h1, color = c.onSurface)
        Spacer(Modifier.height(6.dp))
        Text(
            "Birikiminiz bir kefede,\nhedefiniz diğerinde.",
            style = t.body.copy(lineHeight = 22.sp),
            color = c.onSurfaceMuted,
            textAlign = TextAlign.Center,
        )
    }

    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = Space.x24, end = Space.x24, top = 36.dp),
    ) {
        // Kod gonderilmeden once e-posta, gonderildikten sonra kod kutusu. Ikisi
        // ayni anda durmaz: kullanicinin o an yapacagi tek bir is var.
        if (!state.codeSent) {
            Text("e-posta".trUpper(), style = t.label(11, 0.06), color = c.onSurfaceMuted)
            Spacer(Modifier.height(6.dp))

            EmailField(
                value = state.email,
                onValueChange = { onIntent(LoginIntent.ChangeEmail(it)) },
                hasError = state.emailError != null,
            )

            Spacer(Modifier.height(Space.x12))
            AccountFilledButton(
                text = if (state.sendingCode) "Gönderiliyor…" else "Giriş kodu gönder",
                onClick = { onIntent(LoginIntent.SendCode) },
                modifier = Modifier.fillMaxWidth(),
                enabled = state.canSendCode,
            )
        } else {
            Text("giriş kodu".trUpper(), style = t.label(11, 0.06), color = c.onSurfaceMuted)
            Spacer(Modifier.height(6.dp))

            // Davet kodu kutusuyla AYNI bilesen: ikisi de alti haneli sayi ve
            // ayni gorunumde. Ikinci bir kopya cikarmak, birinde yapilan
            // duzeltmenin digerinde eksik kalmasi demekti.
            InviteCodeInput(
                code = state.code,
                onCodeChange = { onIntent(LoginIntent.ChangeCode(it)) },
                length = LoginCodeLength,
            )

            Spacer(Modifier.height(Space.x12))
            AccountFilledButton(
                text = if (state.verifying) "Kontrol ediliyor…" else "Giriş yap",
                onClick = { onIntent(LoginIntent.VerifyCode) },
                modifier = Modifier.fillMaxWidth(),
                enabled = state.canVerify,
            )
            Spacer(Modifier.height(Space.x8))
            AccountFlatButton(
                text = "E-postayı düzelt",
                onClick = { onIntent(LoginIntent.EditEmail) },
                contentColor = c.onSurfaceMuted,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(Modifier.height(Space.x10))
        // Ayni satir uc bilgiyi tasir: aciklama, hata ve gonderim onayi.
        val noteIcon = if (state.codeSent) KefeIcons.Check else KefeIcons.Info
        val noteColor = if (state.emailError != null) c.negative else c.onSurfaceMuted
        val noteText = state.emailError
            ?: if (state.codeSent) {
                // Hane sayisi metne SABIT yazilmaz: kod uzunlugu Supabase
                // ayarindan geliyor, ikisi ayrilinca cumle yalan soyler.
                "${state.email} adresine ${LoginCodeLength} haneli bir kod gönderdik."
            } else {
                "Şifre yok: e-postanıza tek kullanımlık bir kod gelir. " +
                    "Verileriniz iki telefonda da güncel kalsın diye."
            }

        Row(horizontalArrangement = Arrangement.spacedBy(Space.x8)) {
            KefeIcon(
                icon = noteIcon,
                contentDescription = null,
                modifier = Modifier.padding(top = 2.dp),
                size = 15.dp,
                tint = noteColor,
            )
            Text(noteText, style = t.micro.copy(lineHeight = 17.sp), color = noteColor)
        }

        // Tasarimdaki "Şifreyle giriş yap" secenegi KALDIRILDI. Kimlik parolasiz
        // kuruldu - hesabin parolasi hic yok, dolayisiyla bu dugmenin bir gun
        // isleyecek bir karsiligi da yok. Dokununca "henüz hazır değil" diyen
        // kalici bir dugme birakmak, olmayan bir ozellik vaat etmekti.
        Spacer(Modifier.height(Space.x28))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
        ) {
            Text("Hesabınız yok mu? ", style = t.caption, color = c.onSurfaceMuted)
            Text(
                "Yeni portföy oluştur",
                style = t.caption.copy(fontWeight = FontWeight.SemiBold),
                color = c.accent,
                modifier = Modifier.clickable(
                    indication = null,
                    interactionSource = null,
                    role = Role.Button,
                ) { onIntent(LoginIntent.GoToStart) },
            )
        }
        Spacer(Modifier.height(Space.x24))
    }
}

/**
 * Tasarimdaki 52dp e-posta alani. Ortak [com.kefe.app.ui.components.KefeTextField]
 * cukur yuzey ve 14dp yaricap kullanir; buradaki alan yukseltilmis yuzey ve
 * 12dp yaricap ister, bu yuzden ayri yazildi.
 */
@Composable
private fun EmailField(value: String, onValueChange: (String) -> Unit, hasError: Boolean) {
    val c = KefeTheme.colors
    val t = KefeTheme.type
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()

    val borderColor = when {
        hasError -> c.negative
        focused || value.isNotEmpty() -> c.accent
        else -> c.outline
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(Sizes.buttonPrimary)
            .clip(KefeShapes.button)
            .background(c.surfaceElevated)
            .border(Sizes.hairline, borderColor, KefeShapes.button)
            .padding(horizontal = Space.x14),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            KefeIcon(AccountIcons.Mail, null, size = 20.dp, tint = c.onSurfaceMuted)
            Spacer(Modifier.width(Space.x10))
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                textStyle = t.body.copy(color = c.onSurface),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                cursorBrush = SolidColor(c.accent),
                interactionSource = interaction,
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (value.isEmpty()) {
                            Text(
                                "volkan@ornek.com",
                                style = t.body,
                                color = c.onSurfaceMuted,
                                maxLines = 1,
                            )
                        }
                        inner()
                    }
                },
            )
        }
    }
}

// --- Baslangic: yeni portfoy / davet kodu ----------------------------------

@Composable
private fun StartStage(state: LoginUiState, onIntent: (LoginIntent) -> Unit) {
    val c = KefeTheme.colors
    val t = KefeTheme.type

    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = Space.x20, end = Space.x20, top = Space.x8, bottom = Space.x24),
    ) {
        Text("Nasıl başlıyoruz?", style = t.h1, color = c.onSurface)
        Spacer(Modifier.height(Space.x8))
        Text(
            "Kefe tek kişilik de tam çalışır. Paylaşmak bir özellik, zorunluluk değil.",
            style = t.body.copy(lineHeight = 22.sp),
            color = c.onSurfaceMuted,
        )

        Spacer(Modifier.height(Space.x24))
        StartCard(borderColor = c.accent) {
            Box(
                modifier = Modifier
                    .size(Space.x40)
                    .clip(KefeShapes.button)
                    .background(c.accentMuted),
                contentAlignment = Alignment.Center,
            ) {
                KefeIcon(KefeIcons.Plus, null, size = 22.dp, tint = c.accent)
            }
            Spacer(Modifier.height(Space.x12))
            Text("Yeni portföy oluştur", style = t.bodyStrong, color = c.onSurface)
            Spacer(Modifier.height(Space.x4))
            Text(
                "Sıfırdan başlayın, eşinizi sonra davet edin.",
                style = t.caption.copy(lineHeight = 19.sp),
                color = c.onSurfaceMuted,
            )
            Spacer(Modifier.height(Space.x14))
            AccountFilledButton(
                text = "Portföy oluştur",
                onClick = { onIntent(LoginIntent.CreatePortfolio) },
                modifier = Modifier.fillMaxWidth(),
                height = Sizes.fieldDefault,
            )
        }

        Spacer(Modifier.height(Space.x12))
        StartCard(borderColor = c.outline) {
            Box(
                modifier = Modifier
                    .size(Space.x40)
                    .clip(KefeShapes.button)
                    .background(c.surfaceSunken),
                contentAlignment = Alignment.Center,
            ) {
                KefeIcon(AccountIcons.UserPlus, null, size = 22.dp, tint = c.onSurfaceMuted)
            }
            Spacer(Modifier.height(Space.x12))
            Text("Davet koduyla katıl", style = t.bodyStrong, color = c.onSurface)
            Spacer(Modifier.height(Space.x4))
            Text(
                "Eşiniz sizi davet ettiyse altı haneli kodu girin.",
                style = t.caption.copy(lineHeight = 19.sp),
                color = c.onSurfaceMuted,
            )
            Spacer(Modifier.height(Space.x14))
            InviteCodeInput(
                code = state.inviteCode,
                onCodeChange = { onIntent(LoginIntent.ChangeInviteCode(it)) },
            )
            if (state.inviteError != null) {
                Spacer(Modifier.height(Space.x8))
                Text(state.inviteError, style = t.micro, color = c.negative)
            }
            Spacer(Modifier.height(Space.x12))
            AccountOutlineButton(
                text = "Katıl",
                onClick = { onIntent(LoginIntent.Join) },
                modifier = Modifier.fillMaxWidth(),
                height = Sizes.fieldDefault,
                enabled = state.canJoin,
            )
        }
    }
}

/** Baslangic kartlari: 18dp dolgu, 16dp yaricap, tek renkli kenarlik. */
@Composable
private fun StartCard(borderColor: Color, content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(KefeShapes.card)
            .background(KefeTheme.colors.surfaceElevated)
            .border(Sizes.hairline, borderColor, KefeShapes.card)
            .padding(18.dp),
    ) {
        content()
    }
}

/**
 * Hane hane kod kutusu. Kutular gorseldir; giris gorunmez bir metin alanina
 * yapilir - boylece sistem klavyesi, yapistirma ve otomatik doldurma calisir.
 *
 * Uzunluk PARAMETRE: davet kodu alti hane, Supabase giris kodu sekiz. Sabit
 * kaldigi surece giris kutusu hicbir zaman dolmuyor ve dugme acilmiyordu.
 */
@Composable
private fun InviteCodeInput(
    code: String,
    onCodeChange: (String) -> Unit,
    length: Int = InviteCodeLength,
) {
    val c = KefeTheme.colors
    val t = KefeTheme.type
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()

    Box(Modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (index in 0 until length) {
                val filled = index < code.length
                // Vurgulu kenarlik siradaki bos haneyi isaret eder.
                val active = focused && index == code.length.coerceAtMost(length - 1)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(Sizes.buttonPrimary)
                        .clip(KefeShapes.button)
                        .background(c.surface)
                        .border(
                            width = Sizes.hairline,
                            color = if (active) c.accent else c.outline,
                            shape = KefeShapes.button,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (filled) code[index].toString() else "·",
                        style = if (filled) t.h2.tabular() else t.h2.copy(fontWeight = FontWeight.Normal),
                        color = if (filled) c.onSurface else c.onSurfaceMuted,
                    )
                }
            }
        }

        // Gorunmez giris katmani kutularin tamamini kaplar.
        BasicTextField(
            value = code,
            onValueChange = { onCodeChange(it.filter(Char::isDigit).take(length)) },
            modifier = Modifier.matchParentSize(),
            textStyle = TextStyle(color = Color.Transparent),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            cursorBrush = SolidColor(Color.Transparent),
            interactionSource = interaction,
        )
    }
}

// --- Kilit -----------------------------------------------------------------

@Composable
private fun LockStage(state: LoginUiState, onIntent: (LoginIntent) -> Unit) {
    val c = KefeTheme.colors
    val t = KefeTheme.type

    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = Space.x24, end = Space.x24, top = 96.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(c.accentMuted),
            contentAlignment = Alignment.Center,
        ) {
            KefeIcon(KefeIcons.Balance, null, size = 30.dp, tint = c.accent)
        }
        Spacer(Modifier.height(Space.x16))
        Text(state.portfolioName, style = t.bodyStrong, color = c.onSurface)
        Spacer(Modifier.height(6.dp))
        Text(
            text = Money.masked(state.maskedTotalDigits),
            // Tabular varyant maske ile rakam arasinda genislik farki birakmaz.
            style = t.display.copy(letterSpacing = 0.08.em).tabular(),
            color = c.onSurfaceMuted,
        )
        Spacer(Modifier.height(2.dp))
        Text("Kilitli", style = t.caption, color = c.onSurfaceMuted)
    }

    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = Space.x24, end = Space.x24, top = 56.dp, bottom = Space.x24),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Kilit ekrani acilir acilmaz istem gosterilir: kullanicinin buraya
        // gelme sebebi zaten kilidi acmak. Iptal ederse ekran kilitli kalir ve
        // asagidaki buyuk parmak izi dugmesiyle tekrar deneyebilir.
        LaunchedEffect(Unit) { onIntent(LoginIntent.Unlock) }

        val unlockInteraction = remember { MutableInteractionSource() }
        val hovered by unlockInteraction.collectIsHoveredAsState()

        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(RoundedCornerShape(32.dp))
                .background(if (hovered) lerp(c.accentMuted, Color.White, 0.12f) else c.accentMuted)
                .border(Sizes.hairline, c.accent, RoundedCornerShape(32.dp))
                .hoverable(unlockInteraction)
                .clickable(
                    interactionSource = unlockInteraction,
                    indication = null,
                    role = Role.Button,
                ) { onIntent(LoginIntent.Unlock) },
            contentAlignment = Alignment.Center,
        ) {
            KefeIcon(KefeIcons.Fingerprint, "Parmak izi ile aç", size = 48.dp, tint = c.accent)
        }

        Spacer(Modifier.height(Space.x20))
        Text("Parmak izi ile aç", style = t.bodyStrong, color = c.onSurface)
        Spacer(Modifier.height(6.dp))
        Text(
            text = state.unlockError
                ?: "Bakiyeler yalnız siz açtıktan sonra görünür. " +
                "Fiyat güncellemesi arka planda sürer.",
            style = t.caption.copy(lineHeight = 19.sp),
            color = if (state.unlockError != null) c.negative else c.onSurfaceMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 280.dp),
        )
        // Tasarimdaki "Şifreyle aç" dugmesi KALDIRILDI. Hesabin parolasi yok -
        // giris tek kullanimlik e-posta koduyla yapiliyor - yani bu dugmenin bir
        // gun isleyecek karsiligi da yok. Sistem istemi zaten PIN/desen secenegi
        // sunuyor: cihaz kimligi de kabul ediliyor.
    }
}

// --- Hesap ekranlarinin ortak parcalari ------------------------------------

/**
 * Hesap bolumunun ust cubugu: 44dp geri butonu, 22dp baslik ve istege bagli
 * sag eylem. Paylasim, Aktivite ve Ayarlar da bunu kullanir.
 */
@Composable
internal fun AccountTopBar(
    title: String,
    // null ise geri okU CIZILMEZ. Alt navigasyondan acilan bir sekmede geri
    // gidilecek bir yer yoktur; ok orada "bir onceki ekrana don" diye bir soz
    // veriyor ve o soz karsiliksiz.
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                // Geri okU yokken baslik satirin basina gecer; ikon butonunun
                // negatif dolgusu olmadigi icin sol bosluk elle verilir.
                start = if (onBack == null) Space.x16 else Space.x8,
                end = Space.x8,
                bottom = Space.x8,
            ),
        horizontalArrangement = Arrangement.spacedBy(Space.x4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            KefeIconButton(
                icon = KefeIcons.ArrowBack,
                contentDescription = "Geri",
                onClick = onBack,
                tint = KefeTheme.colors.onSurface,
            )
        }
        Text(
            text = title,
            style = KefeTheme.type.h2,
            color = KefeTheme.colors.onSurface,
            modifier = Modifier.weight(1f),
        )
        if (action != null) {
            action()
            Spacer(Modifier.width(Space.x8))
        }
    }
}

/**
 * Bas harf avatari. Ortak `KefeAvatar` punto secimini boyuttan turetir;
 * tasarim ayni boyutta farkli puntolar kullandigi icin burada punto disaridan
 * verilir. Zemin renkleri ayni iki notr tondan gelir.
 */
@Composable
internal fun AccountAvatar(
    initials: String,
    index: Int,
    size: Dp,
    fontSize: TextUnit,
    modifier: Modifier = Modifier,
) {
    val c = KefeTheme.colors
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(if (index % 2 == 0) c.avatarA else c.avatarB),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initials.trim().take(2).trUpper(),
            style = KefeTheme.type.micro.copy(
                fontSize = fontSize,
                lineHeight = fontSize * 1.15f,
                fontWeight = FontWeight.SemiBold,
            ),
            color = c.onSurface,
            maxLines = 1,
        )
    }
}

/**
 * Hap bicimli segment. Ortak `KefeSegmentedControl` cukur zemin ve kayan
 * yuzey kullanir; hesap ekranlarindaki segment vurgulu zemin ister.
 */
@Composable
internal fun AccountPillSegment(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = KefeTheme.colors
    if (options.isEmpty()) return

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(KefeShapes.pill)
            .background(c.surface)
            .border(Sizes.hairline, c.outline, KefeShapes.pill)
            .padding(Space.x4),
        horizontalArrangement = Arrangement.spacedBy(Space.x4),
    ) {
        options.forEachIndexed { index, label ->
            val active = index == selectedIndex
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(Sizes.segment)
                    .clip(KefeShapes.pill)
                    .background(if (active) c.accent else Color.Transparent)
                    .clickable(
                        indication = null,
                        interactionSource = null,
                        role = Role.Tab,
                    ) { onSelect(index) }
                    .padding(horizontal = Space.x8),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style = KefeTheme.type.captionSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = if (active) c.onAccent else c.onSurfaceMuted,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * Satirlari tasiyan yukseltilmis kutu. Ayirici cizgileri cagiran taraf
 * [com.kefe.app.ui.components.KefeHairline] ile koyar.
 */
@Composable
internal fun AccountGroupCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = KefeTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(KefeShapes.card)
            .background(c.surfaceElevated)
            .border(Sizes.hairline, c.outline, KefeShapes.card),
        content = content,
    )
}

/** Hover: yuzeye beyaz %8 karistirma. */
private fun Color.hoverLift(): Color = lerp(this, Color.White, 0.08f)

/** Basili: yuzeye siyah %8 karistirma. */
private fun Color.pressSink(): Color = lerp(this, Color.Black, 0.08f)

/**
 * Dolu buton. Ortak `KefePrimaryButton` yuksekligi 52dp'ye sabitler; tasarimda
 * 48dp ve 44dp varyantlari da var, bu yuzden yukseklik parametreli.
 */
@Composable
internal fun AccountFilledButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = Sizes.buttonPrimary,
    horizontalPadding: Dp = Space.x20,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
) {
    val c = KefeTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val pressed by interaction.collectIsPressedAsState()

    val container = when {
        !enabled -> Color.Transparent
        pressed -> c.accent.pressSink()
        hovered -> c.accent.hoverLift()
        else -> c.accent
    }
    val contentColor = if (enabled) c.onAccent else c.onSurfaceMuted

    Box(
        modifier = modifier
            .height(height)
            .clip(KefeShapes.button)
            .background(container)
            .then(
                if (enabled) Modifier
                else Modifier.border(Sizes.hairline, c.outline, KefeShapes.button)
            )
            .hoverable(interaction, enabled = enabled)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = horizontalPadding),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(Space.x8),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leadingIcon != null) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(18.dp),
                )
            }
            Text(text, style = KefeTheme.type.bodyStrong, color = contentColor)
        }
    }
}

/** Kenarlikli buton. Devre disi halde metin de kenarlik da soluk kalir. */
@Composable
internal fun AccountOutlineButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = Sizes.buttonPrimary,
    horizontalPadding: Dp = Space.x20,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
    textStyle: TextStyle? = null,
) {
    val c = KefeTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val pressed by interaction.collectIsPressedAsState()

    val container = when {
        !enabled -> Color.Transparent
        pressed -> c.surfaceElevated.pressSink()
        hovered -> c.surfaceElevated
        else -> Color.Transparent
    }
    val contentColor = if (enabled) c.onSurface else c.onSurfaceMuted

    Box(
        modifier = modifier
            .height(height)
            .clip(KefeShapes.button)
            .background(container)
            .border(Sizes.hairline, c.outline, KefeShapes.button)
            .hoverable(interaction, enabled = enabled)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = horizontalPadding),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(Space.x8),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leadingIcon != null) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(18.dp),
                )
            }
            Text(
                text = text,
                style = textStyle ?: KefeTheme.type.bodyStrong,
                color = contentColor,
            )
        }
    }
}

/** Kenarliksiz, zemini olmayan buton - "Portföyden çıkar", "Daveti iptal et". */
@Composable
internal fun AccountFlatButton(
    text: String,
    onClick: () -> Unit,
    contentColor: Color,
    modifier: Modifier = Modifier,
    height: Dp = Sizes.touchTarget,
    horizontalPadding: Dp = 0.dp,
    hoverBackground: Color? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val background = if (hovered) {
        hoverBackground ?: KefeTheme.colors.surfaceElevated
    } else {
        Color.Transparent
    }

    Box(
        modifier = modifier
            .height(height)
            .clip(KefeShapes.button)
            .background(background)
            .hoverable(interaction)
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = horizontalPadding),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = KefeTheme.type.caption.copy(fontWeight = FontWeight.SemiBold),
            color = contentColor,
        )
    }
}

// --- Bu bolume ozgu ikonlar ------------------------------------------------

/**
 * Ortak ikon setinde bulunmayan, yalniz hesap ekranlarinda gecen bicimler.
 * Ayni govde kurallari: 24x24 kutu, 2px outline, yuvarlak uc.
 */
internal object AccountIcons {

    private var _mail: ImageVector? = null
    val Mail: ImageVector
        get() = _mail ?: accountIcon("KefeMail") {
            moveTo(6f, 5.5f)
            lineTo(18f, 5.5f)
            arcTo(3f, 3f, 0f, false, true, 21f, 8.5f)
            lineTo(21f, 15.5f)
            arcTo(3f, 3f, 0f, false, true, 18f, 18.5f)
            lineTo(6f, 18.5f)
            arcTo(3f, 3f, 0f, false, true, 3f, 15.5f)
            lineTo(3f, 8.5f)
            arcTo(3f, 3f, 0f, false, true, 6f, 5.5f)
            close()
            moveTo(4f, 7f)
            lineTo(12f, 13f)
            lineTo(20f, 7f)
        }.also { _mail = it }

    private var _userPlus: ImageVector? = null
    val UserPlus: ImageVector
        get() = _userPlus ?: accountIcon("KefeUserPlus") {
            moveTo(5.8f, 8f)
            arcTo(3.2f, 3.2f, 0f, false, true, 12.2f, 8f)
            arcTo(3.2f, 3.2f, 0f, false, true, 5.8f, 8f)
            close()
            moveTo(3.5f, 19f)
            arcTo(5.5f, 5.5f, 0f, false, true, 14.5f, 19f)
            moveTo(17f, 8f)
            lineTo(17f, 14f)
            moveTo(14f, 11f)
            lineTo(20f, 11f)
        }.also { _userPlus = it }

    private var _upload: ImageVector? = null
    val Upload: ImageVector
        get() = _upload ?: accountIcon("KefeUpload") {
            moveTo(4f, 12f)
            lineTo(4f, 19f)
            arcTo(1f, 1f, 0f, false, false, 5f, 20f)
            lineTo(19f, 20f)
            arcTo(1f, 1f, 0f, false, false, 20f, 19f)
            lineTo(20f, 12f)
            moveTo(12f, 3f)
            lineTo(12f, 15f)
            moveTo(8f, 7f)
            lineTo(12f, 3f)
            lineTo(16f, 7f)
        }.also { _upload = it }
}

private fun accountIcon(name: String, pathBuilder: PathBuilder.() -> Unit): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
            pathBuilder = pathBuilder,
        )
    }.build()
